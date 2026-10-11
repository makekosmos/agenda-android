package com.mundus.agenda.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** After an in-app update the downloaded APK in cache/updates is dead
 * weight equal to the full APK size; drop it as soon as the new package
 * replaces the old one. */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        thread {
            try {
                UpdateManager.sweepUpdateCache(context)
            } finally {
                pending.finish()
            }
        }
    }
}
