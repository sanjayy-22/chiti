package com.example.chittireplica.ai

import android.content.Context
import com.example.chittireplica.data.ChatTurn
import com.example.chittireplica.data.Settings
import com.example.chittireplica.data.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Msg(val fromCaller: Boolean, val text: String)
data class NoteDraft(val summary: String, val actionItems: List<String>)

fun List<ChatTurn>.toMsgs() = map { Msg(it.speaker == Speaker.CALLER, it.text) }

interface AssistantEngine {
    suspend fun reply(history: List<Msg>): String
    suspend fun summarize(transcript: List<Msg>, callerNumber: String): NoteDraft
}

/** Offline fallback used when no Groq key is configured. Also powers typed demos. */
class ScriptedEngine : AssistantEngine {
    override suspend fun reply(history: List<Msg>): String {
        return when (history.count { it.fromCaller }) {
            0 -> "Hi! This is Chitti, answering on your friend's behalf. May I ask who's calling?"
            1 -> "Nice to meet you! And what is this call about?"
            2 -> "Got it, I'll pass that along. Anything else they should know?"
            else -> "Thanks, I've noted everything down. They'll get back to you soon. Bye for now!"
        }
    }

    override suspend fun summarize(transcript: List<Msg>, callerNumber: String): NoteDraft {
        val said = transcript.filter { it.fromCaller }.map { it.text }.filter { it.isNotBlank() }
        return if (said.isEmpty()) {
            NoteDraft("Answered the call from $callerNumber, but the caller said nothing.", emptyList())
        } else {
            NoteDraft(
                summary = "Call from $callerNumber about: ${said.joinToString(" ")}.take(300)",
                actionItems = listOf("Call back $callerNumber")
            )
        }
    }
}

/**
 * Real engine on Groq Cloud (OpenAI-compatible API).
 * Chat -> POST /chat/completions. Speech-to-text -> Groq Whisper POST /audio/transcriptions.
 * Speech output uses Android's built-in TextToSpeech (no key needed), handled by the UI.
 */
class GroqEngine(private val apiKey: String, private val model: String) : AssistantEngine {

    companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"
        private const val SYSTEM_PROMPT =
            "You are Chitti, a friendly cat call assistant answering a phone call " +
                "on behalf of the phone owner, who is busy. Be warm, polite and brief: " +
                "1-2 short sentences per reply. Your goals, in order: learn the caller's " +
                "name, learn what the call is about, note anything urgent, then close politely. " +
                "If it sounds like spam or a scam, stay courteous and end the call quickly."
        private const val SUMMARY_INSTRUCTION =
            "Summarize this phone call transcript for the phone owner. " +
                "Reply with strict JSON only: {\"summary\": \"one or two sentences: who called " +
                "and what they wanted\", \"actions\": [\"short follow-up actions, e.g. 'Call back +15550100'\"]}. " +
                "If the caller said nothing useful, say so in the summary and use []."
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val conn = (URL("$BASE_URL/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun reply(history: List<Msg>): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(20).forEach {
            messages.put(
                JSONObject()
                    .put("role", if (it.fromCaller) "user" else "assistant")
                    .put("content", it.text)
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 150)
        val res = postJson("$BASE_URL/chat/completions", body)
        res.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    override suspend fun summarize(transcript: List<Msg>, callerNumber: String): NoteDraft =
        withContext(Dispatchers.IO) {
            val lines = transcript.joinToString("\n") {
                (if (it.fromCaller) "Caller: " else "Chitti: ") + it.text
            }
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SUMMARY_INSTRUCTION))
                        .put(
                            JSONObject().put("role", "user")
                                .put("content", "Caller number: $callerNumber\n$lines")
                        )
                )
                .put("temperature", 0.2)
                .put("max_tokens", 300)
                .put("response_format", JSONObject().put("type", "json_object"))
            try {
                val res = postJson("$BASE_URL/chat/completions", body)
                val content = res.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                val json = JSONObject(content)
                val actions = json.optJSONArray("actions")?.let { arr ->
                    List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
                } ?: emptyList()
                NoteDraft(
                    summary = json.optString("summary", "Call from $callerNumber.").ifBlank {
                        "Call from $callerNumber."
                    },
                    actionItems = actions.ifEmpty { listOf("Call back $callerNumber") }
                )
            } catch (e: Exception) {
                NoteDraft("Call from $callerNumber.", listOf("Call back $callerNumber"))
            }
        }

    /** Uploads an m4a recording to Groq Whisper and returns the transcript text. */
    suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val boundary = "Boundary-${System.currentTimeMillis()}"
        val conn = (URL("$BASE_URL/audio/transcriptions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connectTimeout = 30000
            readTimeout = 90000
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes(
                        "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                            "$value\r\n"
                    )
                }
                field("model", Settings.WHISPER_MODEL)
                out.writeBytes(
                    "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; " +
                        "filename=\"${audio.name}\"\r\n" +
                        "Content-Type: audio/mp4\r\n\r\n"
                )
                audio.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IOException("Groq Whisper HTTP $code: ${text.take(300)}")
            JSONObject(text).getString("text").trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30000
            readTimeout = 60000
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IOException("Groq HTTP $code: ${text.take(300)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

object Engines {
    fun create(context: Context): AssistantEngine {
        val key = Settings.getGroqKey(context.applicationContext)
        return if (key.isBlank()) ScriptedEngine()
        else GroqEngine(key, Settings.getModel(context.applicationContext))
    }
}
