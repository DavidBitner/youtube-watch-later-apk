package com.davidbitner.watchlater.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.playback.Playback
import kotlinx.coroutines.delay

private const val SEEK_BACK_MS = 5_000L
private const val SEEK_FORWARD_MS = 15_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val exo = remember { Playback.player(context) }
    val currentId by Playback.currentId.collectAsStateWithLifecycle()
    val error by Playback.error.collectAsStateWithLifecycle()
    var speed by remember { mutableFloatStateOf(exo.playbackParameters.speed) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speedMenu by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var seekAccum by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentId) {
        if (currentId == null) onClose()
    }

    LaunchedEffect(seekTick) {
        if (seekTick > 0) {
            delay(800)
            seekAccum = 0
        }
    }

    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(onBack = onClose)

    val title = currentId?.let { Repo.video(it)?.title }.orEmpty()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            update = { view -> if (view.player !== exo) view.player = exo },
            onRelease = { view -> view.player = null },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    playerView = this
                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
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

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { currentId?.let { Playback.play(context, it, reload = true) } }) {
                    Text("Retry")
                }
            }
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
