package com.davidbitner.watchlater.data

data class PlaylistRef(
    val url: String,
    val title: String,
)

data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val position: Int,
)

enum class DownloadState { DOWNLOADED, DOWNLOADING, QUEUED, NOT_DOWNLOADED }

data class VideoItem(
    val video: Video,
    val state: DownloadState,
    val downloadProgress: Float,
    val fileSize: Long,
    val positionMs: Long,
    val watched: Boolean,
)

data class SyncStatus(
    val running: Boolean = false,
    val message: String = "",
    val detail: String = "",
)

data class HomeState(
    val playlistTitle: String = "Watch Later",
    val items: List<VideoItem> = emptyList(),
    val usedBytes: Long = 0L,
    val totalCount: Int = 0,
    val sync: SyncStatus = SyncStatus(),
    val noPlaylist: Boolean = false,
)

data class AppSettings(
    val playlistUrl: String = "",
    val history: List<PlaylistRef> = emptyList(),
    val speed: Float = 1.5f,
    val maxHeight: Int = 1080,
    val capGb: Int = 0,
    val wifiOnly: Boolean = true,
    val reverseOrder: Boolean = false,
    val autoDeleteWatched: Boolean = false,
    val lastYtDlpUpdate: Long = 0L,
)
