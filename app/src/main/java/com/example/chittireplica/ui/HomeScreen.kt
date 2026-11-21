package com.example.chittireplica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chittireplica.call.SimulatedCallManager
import com.example.chittireplica.data.CallStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val notes by CallStore.notes.collectAsState()
    var number by remember { mutableStateOf(SimulatedCallManager.callerNumber) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chitti Replica") },
                actions = { TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulate an incoming call", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Caller number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            SimulatedCallManager.start(number)
                            nav.navigate("incoming")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ring my phone (simulation)") }
                }
            }
            Text("Call notes", style = MaterialTheme.typography.titleMedium)
            if (notes.isEmpty()) {
                Text("No calls yet. Run a simulation and Chitti's note will appear here.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(onClick = { nav.navigate("note/${note.id}") }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                note.callerNumber + if (note.isSpam) " • SPAM" else "",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(note.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(formatTime(note.startedAtMs), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
