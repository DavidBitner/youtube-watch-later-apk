package com.davidbitner.watchlater

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.playback.Playback
import com.davidbitner.watchlater.sync.SyncGate
import com.davidbitner.watchlater.ui.MiniPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.davidbitner.watchlater.ui.HomeScreen
import com.davidbitner.watchlater.ui.PlayerScreen
import com.davidbitner.watchlater.ui.SettingsScreen
import com.davidbitner.watchlater.ui.WatchLaterTheme

class MainActivity : ComponentActivity() {
    private val syncAuthLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        SyncGate.onPromptResult(this, res.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        SyncGate.attach { pending ->
            syncAuthLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
        setContent {
            WatchLaterTheme {
                AppRoot()
            }
        }
        if (savedInstanceState == null && Repo.settings.value.playlistUrl.isNotBlank()) {
            SyncGate.request(this)
        }
    }

    override fun onDestroy() {
        SyncGate.detach()
        super.onDestroy()
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val playingId by Playback.currentId.collectAsStateWithLifecycle()

    LaunchedEffect(playingId) {
        if (playingId == null) showPlayer = false
    }

    if (showPlayer && playingId != null) {
        PlayerScreen(onClose = { showPlayer = false })
    } else {
        val miniVisible = playingId != null
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (miniVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                        else Modifier
                    ),
            ) {
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    HomeScreen(
                        onPlay = {
                            Playback.play(context, it)
                            showPlayer = true
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            if (miniVisible) MiniPlayer(onExpand = { showPlayer = true })
        }
    }
}
