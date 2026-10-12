package com.mundus.agenda.sync

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mundus.agenda.AgendaApplication
import com.mundus.agenda.R
import com.mundus.agenda.syncPass

/**
 * One background sync pass, started by a [SyncSlots] alarm. The worker
 * holds the process awake for [syncPass]: redial, wait for the paired
 * device, stay until changes settle. Returning early would let the OS
 * freeze the process before the connection is up.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as AgendaApplication).repository
        return try {
            repository.syncPass(Build.MODEL, repository.boardFlow())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Expedited work runs as a foreground service below Android 12. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Синхронизация", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_repeat)
                .setContentTitle("Синхронизация")
                .setSilent(true)
                .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NAME = "agenda-sync-slot"
        private const val CHANNEL = "sync"
        private const val NOTIFICATION_ID = 7

        /** One pass now, from a slot alarm; a pass still running absorbs it. */
        fun runNow(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Drops the unaligned 15 min periodic job older builds enqueued. */
        fun cancelLegacyPeriodic(context: Context) {
            // false only under Robolectric, where the initializer provider
            // never runs.
            if (WorkManager.isInitialized()) {
                WorkManager.getInstance(context).cancelUniqueWork("agenda-sync")
            }
        }
    }
}
