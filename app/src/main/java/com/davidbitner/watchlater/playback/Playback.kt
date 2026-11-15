package com.davidbitner.watchlater.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.davidbitner.watchlater.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Playback {
    private var exo: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var durations: Map<String, Long> = emptyMap()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun current(): ExoPlayer? = exo

    fun player(context: Context): ExoPlayer = exo ?: create(context.applicationContext).also { exo = it }

    private fun create(context: Context): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId
                val previous = _currentId.value
                if (newId != null && previous != null && newId != previous) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) Repo.markWatched(previous, true)
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    ) {
                        val saved = resumePosition(newId)
                        if (saved > 5000L) player.seekTo(saved)
                    }
                }
                _currentId.value = newId
                if (newId != null) Repo.setLastPlayed(newId)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                val id = oldPosition.mediaItem?.mediaId ?: return
                Repo.savePosition(id, oldPosition.positionMs, durations[id] ?: 0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) _currentId.value?.let { Repo.markWatched(it, true) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) saveCurrent()
            }
        })
        scope.launch {
            while (true) {
                delay(5000)
                if (exo?.isPlaying == true) saveCurrent()
            }
        }
        return player
    }

    private fun resumePosition(id: String): Long =
        if (Repo.isWatched(id)) 0L else Repo.positionMs(id)

    fun saveCurrent() {
        val p = exo ?: return
        val id = _currentId.value ?: return
        if (p.mediaItemCount == 0) return
        val duration = if (p.duration > 0) p.duration else durations[id] ?: 0L
        Repo.savePosition(id, p.currentPosition, duration)
    }

    fun play(context: Context, id: String) {
        val app = context.applicationContext
        val p = player(app)
        if (_currentId.value == id && p.mediaItemCount > 0) {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
            p.play()
            return
        }
        val playlist = Repo.playable()
        val index = playlist.indexOfFirst { it.first.id == id }
        if (index < 0) return
        saveCurrent()
        durations = playlist.associate { it.first.id to it.first.durationSec * 1000L }
        val items = playlist.map { (video, file) ->
            MediaItem.Builder()
                .setMediaId(video.id)
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.channel)
                        .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/${video.id}/mqdefault.jpg"))
                        .build()
                )
                .build()
        }
        _currentId.value = id
        Repo.setLastPlayed(id)
        p.setMediaItems(items, index, resumePosition(id))
        p.setPlaybackSpeed(Repo.settings.value.speed)
        p.prepare()
        p.playWhenReady = true
        app.startService(Intent(app, PlaybackService::class.java))
    }

    fun toggle() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        exo?.let {
            saveCurrent()
            it.stop()
            it.clearMediaItems()
        }
        _currentId.value = null
        _isPlaying.value = false
        app.stopService(Intent(app, PlaybackService::class.java))
    }
}
