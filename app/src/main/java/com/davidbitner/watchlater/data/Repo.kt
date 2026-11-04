package com.davidbitner.watchlater.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Repo {
    private val LIST_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")
    private val ID_REGEX = Regex("^(PL|UU|FL|OL|LL)[A-Za-z0-9_-]{10,}$")

    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var watchPrefs: SharedPreferences
    private lateinit var baseDir: File
    private lateinit var filesRoot: File
    private lateinit var playlistFile: File
    private var initialised = false

    lateinit var videosDir: File
        private set

    private var videos: List<Video> = emptyList()
    private var title: String = "Watch Later"
    private var files: Map<String, File> = emptyMap()
    private var usedBytes: Long = 0L
    private var downloadingId: String? = null
    private var downloadProgress: Float = 0f
    private var syncStatus = SyncStatus()

    val settings = MutableStateFlow(AppSettings())
    val home = MutableStateFlow(HomeState())

    @Synchronized
    fun init(context: Context) {
        if (initialised) return
        val app = context.applicationContext
        settingsPrefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        watchPrefs = app.getSharedPreferences("watch", Context.MODE_PRIVATE)
        filesRoot = app.filesDir
        baseDir = File(app.getExternalFilesDir(null) ?: app.filesDir, "videos")
        settings.value = readSettings()
        activate()
        initialised = true
    }

    private fun keyOf(url: String): String {
        val id = LIST_REGEX.find(url)?.groupValues?.get(1)
        return id ?: Integer.toHexString(url.hashCode())
    }

    private fun activate() {
        val url = settings.value.playlistUrl
        videos = emptyList()
        title = "Watch Later"
        files = emptyMap()
        usedBytes = 0L
        downloadingId = null
        downloadProgress = 0f
        if (url.isBlank()) {
            playlistFile = File(filesRoot, "playlist_none.json")
            videosDir = File(baseDir, "none")
            rebuild()
            return
        }
        val key = keyOf(url)
        playlistFile = File(filesRoot, "playlist_$key.json")
        videosDir = File(baseDir, key).also { it.mkdirs() }
        loadPlaylist()
        refreshFiles()
    }

    fun normalizeUrl(input: String): String? {
        val text = input.trim()
        val fromUrl = LIST_REGEX.find(text)?.groupValues?.get(1)
        val id = fromUrl ?: if (ID_REGEX.matches(text)) text else null
        return id?.let { "https://www.youtube.com/playlist?list=$it" }
    }

    @Synchronized
    fun selectPlaylist(url: String) {
        val key = keyOf(url)
        val existing = settings.value.history.firstOrNull { keyOf(it.url) == key }
        val ref = PlaylistRef(url, existing?.title ?: "")
        updateSettings {
            it.copy(
                playlistUrl = url,
                history = listOf(ref) + it.history.filter { h -> keyOf(h.url) != key },
            )
        }
        activate()
    }

    @Synchronized
    fun removePlaylist(url: String) {
        val key = keyOf(url)
        File(baseDir, key).deleteRecursively()
        File(filesRoot, "playlist_$key.json").delete()
        val remaining = settings.value.history.filter { keyOf(it.url) != key }
        val wasActive = keyOf(settings.value.playlistUrl) == key && settings.value.playlistUrl.isNotBlank()
        updateSettings {
            it.copy(
                history = remaining,
                playlistUrl = if (wasActive) remaining.firstOrNull()?.url ?: "" else it.playlistUrl,
            )
        }
        if (wasActive) activate()
    }

    private fun readSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            playlistUrl = settingsPrefs.getString("playlistUrl", d.playlistUrl) ?: d.playlistUrl,
            speed = settingsPrefs.getFloat("speed", d.speed),
            maxHeight = settingsPrefs.getInt("maxHeight", d.maxHeight),
            capGb = settingsPrefs.getInt("capGb", d.capGb),
            wifiOnly = settingsPrefs.getBoolean("wifiOnly", d.wifiOnly),
            reverseOrder = settingsPrefs.getBoolean("reverseOrder", d.reverseOrder),
            autoDeleteWatched = settingsPrefs.getBoolean("autoDeleteWatched", d.autoDeleteWatched),
            lastYtDlpUpdate = settingsPrefs.getLong("lastYtDlpUpdate", d.lastYtDlpUpdate),
            history = readHistory(),
        )
    }

    private fun readHistory(): List<PlaylistRef> = try {
        val arr = JSONArray(settingsPrefs.getString("history", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlaylistRef(o.getString("url"), o.optString("title"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun updateSettings(block: (AppSettings) -> AppSettings) {
        val s = block(settings.value)
        settings.value = s
        settingsPrefs.edit()
            .putString("playlistUrl", s.playlistUrl)
            .putFloat("speed", s.speed)
            .putInt("maxHeight", s.maxHeight)
            .putInt("capGb", s.capGb)
            .putBoolean("wifiOnly", s.wifiOnly)
            .putBoolean("reverseOrder", s.reverseOrder)
            .putBoolean("autoDeleteWatched", s.autoDeleteWatched)
            .putLong("lastYtDlpUpdate", s.lastYtDlpUpdate)
            .putString(
                "history",
                JSONArray().also { arr ->
                    s.history.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
                }.toString(),
            )
            .apply()
        rebuild()
    }

    private fun loadPlaylist() {
        if (!playlistFile.exists()) return
        try {
            val root = JSONObject(playlistFile.readText())
            title = root.optString("title", "Watch Later").ifBlank { "Watch Later" }
            val arr = root.optJSONArray("videos") ?: JSONArray()
            videos = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Video(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    channel = o.optString("channel"),
                    durationSec = o.optLong("duration"),
                    position = o.optInt("position"),
                )
            }
        } catch (_: Exception) {
            videos = emptyList()
        }
    }

    private fun savePlaylist() {
        val arr = JSONArray()
        videos.forEach { v ->
            arr.put(
                JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("channel", v.channel)
                    .put("duration", v.durationSec)
                    .put("position", v.position)
            )
        }
        playlistFile.writeText(JSONObject().put("title", title).put("videos", arr).toString())
    }

    @Synchronized
    fun applyPlaylist(newTitle: String, list: List<Video>) {
        title = newTitle.ifBlank { "Watch Later" }
        videos = list
        savePlaylist()
        val activeKey = keyOf(settings.value.playlistUrl)
        updateSettings { cur ->
            cur.copy(history = cur.history.map { if (keyOf(it.url) == activeKey) it.copy(title = title) else it })
        }
        val keep = list.map { it.id }.toSet()
        videosDir.listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.')
            if (id !in keep) f.delete()
        }
        refreshFiles()
    }

    @Synchronized
    fun refreshFiles() {
        val map = HashMap<String, File>()
        var total = 0L
        videosDir.listFiles()?.forEach { f ->
            total += f.length()
            val dot = f.name.indexOf('.')
            if (dot > 0) {
                val ext = f.name.substring(dot + 1)
                if (ext == "mkv" || ext == "mp4" || ext == "webm") {
                    map[f.name.substring(0, dot)] = f
                }
            }
        }
        files = map
        usedBytes = total
        rebuild()
    }

    @Synchronized
    fun usedBytes(): Long = usedBytes

    @Synchronized
    fun setDownloading(id: String?, progress: Float) {
        downloadingId = id
        downloadProgress = progress
        rebuild()
    }

    @Synchronized
    fun setSync(running: Boolean, message: String, detail: String = "") {
        syncStatus = SyncStatus(running, message, detail)
        rebuild()
    }

    fun isSkipped(id: String): Boolean = watchPrefs.getBoolean("skip_$id", false)

    fun isWatched(id: String): Boolean = watchPrefs.getBoolean("done_$id", false)

    fun positionMs(id: String): Long = watchPrefs.getLong("pos_$id", 0L)

    @Synchronized
    fun savePosition(id: String, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs >= durationMs * 95 / 100) {
            markWatched(id, true)
            return
        }
        watchPrefs.edit().putLong("pos_$id", positionMs.coerceAtLeast(0L)).apply()
        rebuild()
    }

    @Synchronized
    fun markWatched(id: String, watched: Boolean) {
        val edit = watchPrefs.edit().putBoolean("done_$id", watched)
        if (watched) edit.putLong("pos_$id", 0L)
        edit.apply()
        if (watched && settings.value.autoDeleteWatched) {
            deleteDownload(id)
        } else {
            rebuild()
        }
    }

    @Synchronized
    fun deleteDownload(id: String) {
        videosDir.listFiles()?.forEach { f ->
            if (f.name.substringBefore('.') == id) f.delete()
        }
        watchPrefs.edit().putBoolean("skip_$id", true).apply()
        refreshFiles()
    }

    @Synchronized
    fun requestDownload(id: String) {
        watchPrefs.edit().putBoolean("skip_$id", false).apply()
        rebuild()
    }

    @Synchronized
    fun resetDownloads() {
        videosDir.listFiles()?.forEach { it.delete() }
        val edit = watchPrefs.edit()
        videos.forEach { edit.putBoolean("skip_${it.id}", false) }
        edit.apply()
        refreshFiles()
    }

    private fun ordered(): List<Video> =
        if (settings.value.reverseOrder) videos.sortedByDescending { it.position }
        else videos.sortedBy { it.position }

    @Synchronized
    fun downloadQueue(): List<Video> {
        val autoDelete = settings.value.autoDeleteWatched
        return ordered().filter { v ->
            files[v.id] == null && !isSkipped(v.id) && !(autoDelete && isWatched(v.id))
        }
    }

    @Synchronized
    fun playable(): List<Pair<Video, File>> =
        ordered().mapNotNull { v -> files[v.id]?.let { v to it } }

    @Synchronized
    fun video(id: String): Video? = videos.firstOrNull { it.id == id }

    @Synchronized
    private fun rebuild() {
        val items = ordered().map { v ->
            val file = files[v.id]
            val state = when {
                v.id == downloadingId -> DownloadState.DOWNLOADING
                file != null -> DownloadState.DOWNLOADED
                isSkipped(v.id) -> DownloadState.NOT_DOWNLOADED
                else -> DownloadState.QUEUED
            }
            VideoItem(
                video = v,
                state = state,
                downloadProgress = if (v.id == downloadingId) downloadProgress else 0f,
                fileSize = file?.length() ?: 0L,
                positionMs = positionMs(v.id),
                watched = isWatched(v.id),
            )
        }
        home.value = HomeState(title, items, usedBytes, videos.size, syncStatus, settings.value.playlistUrl.isBlank())
    }
}
