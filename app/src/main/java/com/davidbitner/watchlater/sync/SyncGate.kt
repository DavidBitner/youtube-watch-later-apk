package com.davidbitner.watchlater.sync

import android.app.PendingIntent
import android.content.Context
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.youtube.YouTubeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SyncGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var launcher: ((PendingIntent) -> Unit)? = null
    private var prompting = false
    private var pendingRestart = false

    fun attach(launch: (PendingIntent) -> Unit) {
        launcher = launch
    }

    fun detach() {
        launcher = null
        prompting = false
    }

    fun request(context: Context, restart: Boolean = false) {
        if (prompting) return
        if (!restart && Repo.home.value.sync.running) return
        val app = context.applicationContext
        scope.launch {
            try {
                val result = YouTubeApi.authorize(app)
                val pending = result.pendingIntent
                val open = launcher
                if (result.hasResolution() && pending != null && open != null) {
                    prompting = true
                    pendingRestart = restart
                    open(pending)
                } else {
                    enqueue(app, restart, fallback = false)
                }
            } catch (_: Exception) {
                enqueue(app, restart, fallback = true)
            }
        }
    }

    fun onPromptResult(context: Context, signedIn: Boolean) {
        prompting = false
        enqueue(context.applicationContext, pendingRestart, fallback = !signedIn)
    }

    private fun enqueue(context: Context, restart: Boolean, fallback: Boolean) {
        if (restart) SyncScheduler.restartSync(context, fallback) else SyncScheduler.syncNow(context, fallback)
    }
}
