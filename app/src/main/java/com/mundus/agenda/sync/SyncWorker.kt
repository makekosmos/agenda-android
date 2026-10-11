package com.mundus.agenda.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mundus.agenda.AgendaApplication
import java.util.concurrent.TimeUnit

/**
 * Periodic sync keepalive (program.md: WorkManager periodic + sync on
 * open + manual). The sync session inside the engine is continuous once
 * `start_sync` lands; the worker re-asserts it if the process restarted
 * or sync dropped.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as AgendaApplication).repository
        return try {
            if (!repository.syncSnapshot().running) {
                repository.startSync(android.os.Build.MODEL)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "agenda-sync"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            // false only under Robolectric, where the initializer provider
            // never runs — nothing else should skip scheduling.
            if (WorkManager.isInitialized()) {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
