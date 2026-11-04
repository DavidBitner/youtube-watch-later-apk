package com.davidbitner.watchlater.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.davidbitner.watchlater.Notifications
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.ytdlp.YtDlp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @Volatile
    private var currentId: String? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            runSync()
        } catch (e: CancellationException) {
            Repo.setSync(false, "Stopped")
            throw e
        } catch (e: Exception) {
            Repo.setSync(false, "Sync failed: ${e.message?.lineSequence()?.firstOrNull()?.take(140) ?: "unknown error"}")
            Result.success()
        } finally {
            Repo.setDownloading(null, 0f)
            Repo.refreshFiles()
        }
    }

    override fun onStopped() {
        currentId?.let { YtDlp.cancel(it) }
    }

    private suspend fun runSync(): Result {
        val ctx = applicationContext
        if (Repo.settings.value.playlistUrl.isBlank()) return Result.success()
        Repo.setSync(true, "Checking playlist…")
        showForeground("Checking playlist…", -1)

        YtDlp.ensureInit(ctx)
        YtDlp.updateIfStale(ctx)

        val settings = Repo.settings.value
        val (title, list) = YtDlp.fetchPlaylist(settings.playlistUrl)
        Repo.applyPlaylist(title, list)

        val queue = Repo.downloadQueue()
        val capBytes = settings.capGb * 1_073_741_824L
        var done = 0
        var failed = 0
        var stoppedReason: String? = null

        for ((index, video) in queue.withIndex()) {
            if (isStopped) break
            if (capBytes > 0 && Repo.usedBytes() >= capBytes) {
                stoppedReason = "Storage limit reached"
                break
            }
            if (Repo.videosDir.usableSpace < 1_500_000_000L) {
                stoppedReason = "Device storage almost full"
                break
            }

            val label = "Downloading ${index + 1} of ${queue.size}"
            Repo.setSync(true, label)
            Repo.setDownloading(video.id, 0f)
            currentId = video.id
            var lastPercent = -1
            try {
                YtDlp.download(video, Repo.videosDir, settings.maxHeight) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        Repo.setDownloading(video.id, p)
                        updateNotification("$label · ${video.title}", percent)
                    }
                }
                done++
            } catch (e: Exception) {
                if (isStopped) break
                failed++
            } finally {
                currentId = null
                Repo.setDownloading(null, 0f)
                Repo.refreshFiles()
            }
        }

        val message = when {
            isStopped -> "Stopped"
            stoppedReason != null -> "$stoppedReason · $done downloaded"
            failed > 0 -> "Done · $done downloaded, $failed failed"
            done > 0 -> "Up to date · $done new"
            else -> "Up to date"
        }
        Repo.setSync(false, message)
        return Result.success()
    }

    private suspend fun showForeground(text: String, percent: Int) {
        val notification = buildNotification(text, percent)
        try {
            setForeground(foregroundInfo(notification))
        } catch (_: Exception) {
            updateNotification(text, percent)
        }
    }

    private fun updateNotification(text: String, percent: Int) {
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, buildNotification(text, percent))
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(text: String, percent: Int) =
        NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Syncing Watch Later")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }

    companion object {
        private const val NOTIFICATION_ID = 4101
    }
}
