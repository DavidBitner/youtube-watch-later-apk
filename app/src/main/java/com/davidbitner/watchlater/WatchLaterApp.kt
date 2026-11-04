package com.davidbitner.watchlater

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.sync.SyncScheduler
import com.davidbitner.watchlater.ytdlp.YtDlp
import kotlin.concurrent.thread

object Notifications {
    const val CHANNEL_ID = "sync"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class WatchLaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifications.createChannel(this)
        SyncScheduler.schedulePeriodic(this)
        thread(name = "ytdlp-init") {
            try {
                YtDlp.ensureInit(this)
            } catch (_: Exception) {
            }
        }
    }
}
