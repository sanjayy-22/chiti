package com.example.chittireplica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chittireplica.ai.GroqEngine
import com.example.chittireplica.data.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(Settings.getGroqKey(context)) }
    var model by remember { mutableStateOf(Settings.getModel(context)) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var savedTick by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Groq Cloud API", style = MaterialTheme.typography.titleMedium)
            Text(
                "Get a free key at console.groq.com/keys. It stays on this device " +
                    "(never committed anywhere). Without a key the app uses scripted replies.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; savedTick = false },
                label = { Text("Groq API key (gsk_…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; savedTick = false },
                label = { Text("Chat model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = {
                Settings.setGroqKey(context, key)
                Settings.setModel(context, model.ifBlank { Settings.DEFAULT_MODEL })
                savedTick = true
                result = null
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            if (savedTick) Text("Saved.")
            OutlinedButton(
                onClick = {
                    scope.launch {
                        testing = true
                        result = try {
                            val ok = GroqEngine(
                                key.ifBlank { Settings.getGroqKey(context) },
                                model.ifBlank { Settings.DEFAULT_MODEL }
                            ).testConnection()
                            if (ok) "Connection OK — key works." else "Failed (HTTP != 200)."
                        } catch (e: Exception) {
                            "Failed: ${e.message}"
                        }
                        testing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testing) "Testing…" else "Test connection") }
            if (result != null) Text(result!!)
            Text(
                "Voice notes: speech-to-text uses Groq Whisper (${Settings.WHISPER_MODEL}); " +
                    "spoken replies use Android's built-in text-to-speech (no key needed). " +
                    "Real-network call screening additionally needs the Call Screening role, " +
                    "granted on a device under Settings → Apps → Default apps.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
