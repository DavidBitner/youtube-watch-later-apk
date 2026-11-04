package com.davidbitner.watchlater.ytdlp

import android.content.Context
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.data.Video
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

object YtDlp {
    @Volatile
    private var initialised = false

    @Synchronized
    fun ensureInit(context: Context) {
        if (initialised) return
        val app = context.applicationContext
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        initialised = true
    }

    fun version(context: Context): String? = try {
        ensureInit(context)
        YoutubeDL.getInstance().version(context.applicationContext)
    } catch (_: Exception) {
        null
    }

    fun update(context: Context): String {
        ensureInit(context)
        YoutubeDL.getInstance().updateYoutubeDL(
            context.applicationContext,
            YoutubeDL.UpdateChannel.STABLE,
        )
        Repo.updateSettings { it.copy(lastYtDlpUpdate = System.currentTimeMillis()) }
        return version(context) ?: "unknown"
    }

    fun updateIfStale(context: Context) {
        val age = System.currentTimeMillis() - Repo.settings.value.lastYtDlpUpdate
        if (age < 24L * 60 * 60 * 1000) return
        try {
            update(context)
        } catch (_: Exception) {
        }
    }

    fun fetchPlaylist(url: String): Pair<String, List<Video>> {
        val request = YoutubeDLRequest(url)
        request.addOption("--flat-playlist")
        request.addOption("-J")
        request.addOption("--no-warnings")
        val out = YoutubeDL.getInstance().execute(request).out
        val start = out.indexOf('{')
        if (start < 0) throw IllegalStateException("Empty playlist response")
        val root = JSONObject(out.substring(start))
        val title = root.optString("title", "Watch Later")
        val entries = root.optJSONArray("entries") ?: throw IllegalStateException("No entries in playlist")
        val list = ArrayList<Video>()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val id = e.optString("id")
            val name = e.optString("title")
            if (id.isBlank() || name == "[Private video]" || name == "[Deleted video]") continue
            val channel = e.optString("channel").ifBlank { e.optString("uploader") }
            list.add(
                Video(
                    id = id,
                    title = name,
                    channel = if (channel == "null") "" else channel,
                    durationSec = e.optDouble("duration", 0.0).toLong(),
                    position = i + 1,
                )
            )
        }
        return title to list
    }

    fun download(
        video: Video,
        dir: File,
        maxHeight: Int,
        onProgress: (Float) -> Unit,
    ) {
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${video.id}")
        request.addOption("-o", "${dir.absolutePath}/${video.id}.%(ext)s")
        request.addOption("-f", "bv*+ba/b")
        request.addOption("-S", "res:$maxHeight,vcodec:vp9,acodec:opus")
        request.addOption("--merge-output-format", "mkv")
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--retries", "5")
        request.addOption("--fragment-retries", "10")
        request.addOption("-N", "4")

        var stream = 0
        var last = 0f
        YoutubeDL.getInstance().execute(request, video.id) { progress, _, _ ->
            if (progress < last - 30f) stream++
            last = progress
            val overall = ((stream + progress / 100f) / 2f).coerceIn(0f, 0.99f)
            onProgress(overall)
        }
    }

    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }
}
