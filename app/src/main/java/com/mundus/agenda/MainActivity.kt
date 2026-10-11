package com.mundus.agenda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mundus.agenda.ui.AgendaApp
import com.mundus.agenda.ui.AgendaTheme
import com.mundus.agenda.ui.DebugPairing

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readPairingExtra(intent)
        val app = application as AgendaApplication
        setContent {
            AgendaTheme(
                darkTheme =
                    when (app.themeMode.value) {
                        "light" -> false
                        "dark" -> true
                        else -> androidx.compose.foundation.isSystemInDarkTheme()
                    }
            ) {
                AgendaApp(
                    app.repository,
                    themeMode = app.themeMode.value,
                    onThemeMode = app::setThemeMode,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPairingExtra(intent)
    }

    private fun readPairingExtra(intent: Intent?) {
        // Debug-only hook for on-device sync e2e: `am start --es pair_ticket
        // <ticket>` prefills the Sync screen's peer-code field so the real
        // pairing UI flow runs without typing a 90-char ticket on TCG.
        if (BuildConfig.DEBUG) {
            intent?.getStringExtra("pair_ticket")?.let { DebugPairing.pendingTicket = it }
        }
    }
}
