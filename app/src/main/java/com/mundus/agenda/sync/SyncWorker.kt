package com.mundus.agenda.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mundus.agenda.AgendaApplication
import com.mundus.agenda.syncPass
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync (program.md: WorkManager periodic + sync on
 * open + manual). Every ~15 min, network permitting, the worker holds the
 * process awake for one [syncPass]: redial, wait for the paired device,
 * stay until changes settle. Returning early would let the OS freeze the
 * process before the connection is up.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as AgendaApplication).repository
        return try {
            repository.syncPass(android.os.Build.MODEL, repository.boardFlow())
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
