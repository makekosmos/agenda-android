package com.mundus.agenda.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mundus.agenda.DEFAULT_SYNC_INTERVAL
import com.mundus.agenda.nextSyncSlot
import java.time.ZonedDateTime

/**
 * Wall-clock sync slots (10:00, 10:15, ...): every device on the same
 * interval wakes at the same minute, so two phones meet online. A silent
 * alarm fires at each slot, enqueues an expedited [SyncWorker] pass and
 * arms the next slot. Exact while-idle alarms need the «Будильники и
 * напоминания» access on Android 12+; without it the alarm is inexact and
 * may land minutes late, past the pass's 1 min wait for a peer.
 */
object SyncSlots {
    private const val PREFS = "agenda"
    private const val KEY = "sync_interval"
    const val ACTION = "com.mundus.agenda.SYNC_SLOT"

    fun interval(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_SYNC_INTERVAL)

    fun setInterval(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, minutes).apply()
        schedule(context)
    }

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Arms the alarm for the next slot, replacing any armed one. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextSyncSlot(ZonedDateTime.now(), interval(context)).toInstant().toEpochMilli()
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SyncSlotReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (exactAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}

/** Slot alarm, plus the system events after which the alarm must be re-armed
 * (reboot, clock or zone change, exact-alarm access granted). */
class SyncSlotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SyncSlots.ACTION) SyncWorker.runNow(context)
        SyncSlots.schedule(context)
    }
}
