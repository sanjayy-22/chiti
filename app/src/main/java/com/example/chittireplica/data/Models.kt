package com.example.chittireplica.data

import java.util.UUID

enum class Speaker { CALLER, ASSISTANT }

data class ChatTurn(
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CallNote(
    val id: String = UUID.randomUUID().toString(),
    val callerNumber: String,
    val startedAtMs: Long = System.currentTimeMillis(),
    val summary: String,
    val actionItems: List<String> = emptyList(),
    val transcript: List<ChatTurn> = emptyList(),
    val isSpam: Boolean = false
)
