package com.example.chittireplica.ui

import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chittireplica.ai.AssistantEngine
import com.example.chittireplica.ai.Engines
import com.example.chittireplica.ai.GroqEngine
import com.example.chittireplica.ai.ScriptedEngine
import com.example.chittireplica.ai.toMsgs
import com.example.chittireplica.call.SimulatedCallManager
import com.example.chittireplica.data.CallNote
import com.example.chittireplica.data.CallStore
import com.example.chittireplica.data.ChatTurn
import com.example.chittireplica.data.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class ConversationViewModel(app: Application) : AndroidViewModel(app) {
    private val engine: AssistantEngine = Engines.create(app)
    val groqBacked: Boolean = engine is GroqEngine

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setError(msg: String?) {
        _error.value = msg
    }

    fun start() {
        if (_turns.value.isNotEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val greeting = engine.reply(emptyList())
                _turns.value = listOf(ChatTurn(Speaker.ASSISTANT, greeting))
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Assistant failed (${e.message}); using offline replies."
                _turns.value =
                    listOf(ChatTurn(Speaker.ASSISTANT, ScriptedEngine().reply(emptyList())))
            }
            _busy.value = false
        }
    }

    fun sendCallerText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _busy.value) return
        val updated = _turns.value + ChatTurn(Speaker.CALLER, clean)
        _turns.value = updated
        viewModelScope.launch {
            _busy.value = true
            try {
                val reply = engine.reply(updated.toMsgs())
                _turns.value = _turns.value + ChatTurn(Speaker.ASSISTANT, reply)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Assistant failed (${e.message}); using offline reply."
                _turns.value = _turns.value +
                    ChatTurn(Speaker.ASSISTANT, ScriptedEngine().reply(updated.toMsgs()))
            }
            _busy.value = false
        }
    }

    fun transcribeAndSend(audio: File) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val groq = engine as? GroqEngine
                    ?: throw IOException("No Groq key set — type the caller's words instead.")
                val text = groq.transcribe(audio)
                _busy.value = false
                if (text.isBlank()) {
                    _error.value = "Heard nothing — try again."
                    return@launch
                }
                sendCallerText(text)
            } catch (e: Exception) {
                _busy.value = false
                _error.value = "Transcription failed: ${e.message}"
            } finally {
                audio.delete()
            }
        }
    }

    suspend fun finishCall(number: String): CallNote {
        val transcript = _turns.value
        val draft = try {
            engine.summarize(transcript.toMsgs(), number)
        } catch (e: Exception) {
            ScriptedEngine().summarize(transcript.toMsgs(), number)
        }
        val note = CallNote(
            callerNumber = number,
            summary = draft.summary,
            actionItems = draft.actionItems,
            transcript = transcript
        )
        CallStore.addNote(note)
        return note
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(nav: NavController) {
    val context = LocalContext.current
    val vm: ConversationViewModel = viewModel()
    val turns by vm.turns.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val number = SimulatedCallManager.callerNumber
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    val recorderBox = remember { arrayOfNulls<MediaRecorder>(1) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var wantMic by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) wantMic = true else micDenied = true }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "caller_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorderBox[0] = rec
            audioFile = file
            recording = true
        } catch (e: Exception) {
            vm.setError("Mic failed: ${e.message}")
        }
    }

    fun stopAndSend() {
        try {
            recorderBox[0]?.apply { stop(); release() }
        } catch (_: Exception) {
        }
        recorderBox[0] = null
        recording = false
        audioFile?.let { vm.transcribeAndSend(it) }
        audioFile = null
    }

    val tts = remember { TextToSpeech(context.applicationContext, null) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    var lastSpoken by remember { mutableStateOf(-1L) }
    LaunchedEffect(turns.size) {
        val last = turns.lastOrNull()
        if (last != null && last.speaker == Speaker.ASSISTANT && last.timestampMs != lastSpoken) {
            lastSpoken = last.timestampMs
            tts.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "chitti-${last.timestampMs}")
        }
    }
    LaunchedEffect(Unit) { vm.start() }

    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐱 Chitti • $number") },
                actions = {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val note = vm.finishCall(number)
                            nav.navigate("note/${note.id}") { popUpTo("home") }
                        }
                    }) { Text("End call") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp).fillMaxSize()) {
            if (!vm.groqBacked) {
                Text(
                    "No Groq key — running scripted replies. Add a key in Settings for live AI.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (micDenied) {
                Text("Mic permission denied — type the caller's words instead.")
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(turns) { turn ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (turn.speaker == Speaker.CALLER) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (turn.speaker == Speaker.CALLER) "Caller" else "🐱 Chitti",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(turn.text)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Caller says… (simulation)") },
                    modifier = Modifier.weight(1f),
                    singleLine = false
                )
                Button(onClick = {
                    vm.sendCallerText(input)
                    input = ""
                }) { Text("Send") }
            }
            OutlinedButton(
                onClick = {
                    if (recording) {
                        stopAndSend()
                    } else if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (recording) "■ Stop & transcribe (Groq Whisper)" else "● Speak as caller (mic)") }
            LaunchedEffect(wantMic) {
                if (wantMic) {
                    wantMic = false
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    }
                }
            }
        }
    }
}
