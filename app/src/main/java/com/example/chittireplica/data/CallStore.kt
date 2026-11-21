package com.example.chittireplica.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory store for the prototype. Notes reset when the app process dies. */
object CallStore {
    private val _notes = MutableStateFlow<List<CallNote>>(emptyList())
    val notes: StateFlow<List<CallNote>> = _notes.asStateFlow()

    fun addNote(note: CallNote) {
        _notes.update { listOf(note) + it }
    }

    fun markSpam(id: String, spam: Boolean) {
        _notes.update { notes ->
            notes.map { if (it.id == id) it.copy(isSpam = spam) else it }
        }
    }

    fun getNote(id: String): CallNote? = _notes.value.firstOrNull { it.id == id }
}
