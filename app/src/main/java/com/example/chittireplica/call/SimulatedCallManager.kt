package com.example.chittireplica.call

/** Holds the simulated-call state shared between the Home, Incoming and Conversation screens. */
object SimulatedCallManager {
    var callerNumber: String = "+1 555-0100"
        private set
    var startedAtMs: Long = 0L
        private set

    fun start(number: String) {
        callerNumber = number.ifBlank { "+1 555-0100" }
        startedAtMs = System.currentTimeMillis()
    }
}
