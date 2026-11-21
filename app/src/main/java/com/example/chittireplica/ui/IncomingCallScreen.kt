package com.example.chittireplica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.chittireplica.call.SimulatedCallManager
import com.example.chittireplica.data.CallNote
import com.example.chittireplica.data.CallStore

@Composable
fun IncomingCallScreen(nav: NavController) {
    val number = SimulatedCallManager.callerNumber
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐱", fontSize = 72.sp)
        Text("Incoming call", style = MaterialTheme.typography.titleLarge)
        Text(number, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Chitti the cat will answer and leave you a note.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = {
                CallStore.addNote(
                    CallNote(
                        callerNumber = number,
                        summary = "Declined the call from $number."
                    )
                )
                nav.popBackStack()
            }) { Text("Decline") }
            Button(onClick = { nav.navigate("conversation") }) { Text("Let Chitti answer") }
        }
    }
}
