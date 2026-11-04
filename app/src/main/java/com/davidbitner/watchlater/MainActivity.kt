package com.davidbitner.watchlater

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.davidbitner.watchlater.ui.HomeScreen
import com.davidbitner.watchlater.ui.PlayerScreen
import com.davidbitner.watchlater.ui.SettingsScreen
import com.davidbitner.watchlater.ui.WatchLaterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            WatchLaterTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }

    val id = playingId
    when {
        id != null -> PlayerScreen(startId = id, onClose = { playingId = null })
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(onBack = { showSettings = false })
        }
        else -> HomeScreen(
            onPlay = { playingId = it },
            onOpenSettings = { showSettings = true },
        )
    }
}
