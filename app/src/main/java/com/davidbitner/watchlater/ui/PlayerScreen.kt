package com.davidbitner.watchlater.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.davidbitner.watchlater.data.Repo
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resumePosition(id: String): Long =
    if (Repo.isWatched(id)) 0L else Repo.positionMs(id)

@Composable
fun PlayerScreen(startId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val playlist = remember { Repo.playable() }
    val startIndex = remember { playlist.indexOfFirst { it.first.id == startId }.coerceAtLeast(0) }
    var currentId by remember { mutableStateOf(playlist.getOrNull(startIndex)?.first?.id ?: startId) }
    var speed by remember { mutableFloatStateOf(Repo.settings.value.speed) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speedMenu by remember { mutableStateOf(false) }

    val exo = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            val items = playlist.map { (video, file) ->
                MediaItem.Builder()
                    .setMediaId(video.id)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build())
                    .build()
            }
            setMediaItems(items, startIndex, resumePosition(startId))
            setPlaybackSpeed(speed)
            prepare()
            playWhenReady = true
        }
    }

    val mediaSession = remember(exo) { MediaSession.Builder(context, exo).build() }
    DisposableEffect(mediaSession) { onDispose { mediaSession.release() } }

    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }

    LaunchedEffect(exo) {
        while (true) {
            delay(5000)
            if (exo.isPlaying) {
                Repo.savePosition(currentId, exo.currentPosition, exo.duration.coerceAtLeast(0L))
            }
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    Repo.markWatched(currentId, true)
                    val saved = resumePosition(newId)
                    if (saved > 5000L) exo.seekTo(saved)
                }
                currentId = newId
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) Repo.markWatched(currentId, true)
            }
        }
        exo.addListener(listener)
        onDispose {
            Repo.savePosition(currentId, exo.currentPosition, exo.duration.coerceAtLeast(0L))
            exo.removeListener(listener)
            exo.release()
        }
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(onBack = onClose)

    val title = playlist.firstOrNull { it.first.id == currentId }?.first?.title.orEmpty()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exo
                    keepScreenOn = true
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    controllerShowTimeoutMs = 3000
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { speedMenu = true }) {
                        Text(formatSpeed(speed), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        SPEEDS.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(formatSpeed(s)) },
                                onClick = { speed = s; speedMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
