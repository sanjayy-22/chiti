package com.example.chittireplica.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chittireplica.data.CallStore
import com.example.chittireplica.data.Speaker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(nav: NavController, id: String) {
    val context = LocalContext.current
    val note = CallStore.getNote(id)
    if (note == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Note not found.")
            Button(onClick = { nav.popBackStack() }) { Text("Back") }
        }
        return
    }
    var spam by remember(note.isSpam) { mutableStateOf(note.isSpam) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐱 Call note") },
                navigationIcon = {
                    TextButton(onClick = { nav.popBackStack("home", false) }) { Text("Home") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.callerNumber, style = MaterialTheme.typography.titleLarge)
                    Text(formatTime(note.startedAtMs), style = MaterialTheme.typography.bodySmall)
                    Text(note.summary, style = MaterialTheme.typography.bodyLarge)
                    if (note.actionItems.isNotEmpty()) {
                        Text("Suggested follow-ups:", style = MaterialTheme.typography.titleSmall)
                        note.actionItems.forEach { Text("• $it") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${note.callerNumber}"))
                    )
                }) { Text("Call back") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${note.callerNumber}"))
                    )
                }) { Text("Message") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val insert = Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, note.callerNumber)
                    context.startActivity(insert)
                }) { Text("Add contact") }
                OutlinedButton(onClick = {
                    CallStore.markSpam(note.id, !spam)
                    spam = !spam
                }) { Text(if (spam) "Unmark spam" else "Report spam") }
            }
            Text("Transcript", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(note.transcript) { turn ->
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
        }
    }
}
