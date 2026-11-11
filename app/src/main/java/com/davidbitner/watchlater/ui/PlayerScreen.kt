package com.davidbitner.watchlater.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
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

private const val SEEK_BACK_MS = 5_000L
private const val SEEK_FORWARD_MS = 15_000L

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
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var seekAccum by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }

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

    LaunchedEffect(seekTick) {
        if (seekTick > 0) {
            delay(800)
            seekAccum = 0
        }
    }

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
            mediaSession.release()
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
                    playerView = this
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 120.dp),
        ) {
            SeekZone(
                modifier = Modifier.weight(0.38f).fillMaxHeight(),
                label = if (seekAccum < 0) "−${-seekAccum}s" else null,
                onToggleControls = {
                    playerView?.let { if (it.isControllerFullyVisible) it.hideController() else it.showController() }
                },
                isRepeat = { System.currentTimeMillis() - lastSeekAt < 700L },
                onSeek = {
                    exo.seekTo((exo.currentPosition - SEEK_BACK_MS).coerceAtLeast(0L))
                    seekAccum = if (seekAccum < 0) seekAccum - (SEEK_BACK_MS / 1000).toInt() else -(SEEK_BACK_MS / 1000).toInt()
                    lastSeekAt = System.currentTimeMillis()
                    seekTick++
                },
            )
            Spacer(Modifier.weight(0.24f))
            SeekZone(
                modifier = Modifier.weight(0.38f).fillMaxHeight(),
                label = if (seekAccum > 0) "+${seekAccum}s" else null,
                onToggleControls = {
                    playerView?.let { if (it.isControllerFullyVisible) it.hideController() else it.showController() }
                },
                isRepeat = { System.currentTimeMillis() - lastSeekAt < 700L },
                onSeek = {
                    val target = exo.currentPosition + SEEK_FORWARD_MS
                    val duration = exo.duration
                    exo.seekTo(if (duration > 0) minOf(target, duration) else target)
                    seekAccum = if (seekAccum > 0) seekAccum + (SEEK_FORWARD_MS / 1000).toInt() else (SEEK_FORWARD_MS / 1000).toInt()
                    lastSeekAt = System.currentTimeMillis()
                    seekTick++
                },
            )
        }

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
                        SPEED_OPTIONS.forEach { s ->
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

@Composable
private fun SeekZone(
    modifier: Modifier,
    label: String?,
    onToggleControls: () -> Unit,
    isRepeat: () -> Boolean,
    onSeek: () -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { if (isRepeat()) onSeek() else onToggleControls() },
                onDoubleTap = { onSeek() },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
