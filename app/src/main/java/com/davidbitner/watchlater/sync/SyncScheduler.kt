package com.davidbitner.watchlater.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.davidbitner.watchlater.data.Repo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val MANUAL = "sync"
    private const val PERIODIC = "periodic-sync"

    private fun constraints(batteryNotLow: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (Repo.settings.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(batteryNotLow)
            .build()

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(batteryNotLow = false))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
    }

    fun restartSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(batteryNotLow = false))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints(batteryNotLow = true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun stop(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(MANUAL)
        wm.cancelUniqueWork(PERIODIC)
        Repo.setSync(false, "Stopped")
        schedulePeriodic(context)
    }

    fun isWaiting(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(MANUAL)
            .map { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED } }
}
