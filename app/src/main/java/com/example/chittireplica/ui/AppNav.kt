package com.example.chittireplica.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("incoming") { IncomingCallScreen(nav) }
        composable("conversation") { ConversationScreen(nav) }
        composable("note/{id}") { entry ->
            NoteScreen(nav, entry.arguments?.getString("id").orEmpty())
        }
        composable("settings") { SettingsScreen(nav) }
    }
}
