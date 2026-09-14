package com.zaid.latch

import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaid.latch.data.UserPreferences
import com.zaid.latch.ui.*

class MainActivity : ComponentActivity() {
    private var showDownloads by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showDownloads = intent.getBooleanExtra("downloads", false)
        setContent {
            val preferences by graph.preferences.flow.collectAsStateWithLifecycle(UserPreferences())
            LatchTheme(preferences.darkMode) { LatchHome(showDownloads) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showDownloads = intent.getBooleanExtra("downloads", false)
    }
}
