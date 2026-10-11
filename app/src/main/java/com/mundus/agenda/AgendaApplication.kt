package com.mundus.agenda

import android.app.Application
import android.os.Build
import com.mundus.agenda.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The repository (and the engine inside it) lives for the whole process,
 * not per Activity: recreating it on every configuration change would
 * leak the old `MobileEngine` holding the data dir. Opened lazily on first
 * access so app startup does no I/O on the main thread.
 */
class AgendaApplication : Application() {
    val repository: EngineAgendaRepository by lazy {
        EngineAgendaRepository(filesDir.absolutePath)
    }

    // «Отображение» theme (desktop settings.rs appearance tiles):
    // "system" | "light" | "dark", persisted across launches.
    private val prefs by lazy { getSharedPreferences("agenda", MODE_PRIVATE) }

    val themeMode by lazy {
        androidx.compose.runtime.mutableStateOf(prefs.getString("theme", "system") ?: "system")
    }

    fun setThemeMode(mode: String) {
        themeMode.value = mode
        prefs.edit().putString("theme", mode).apply()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Sync on open + periodic (program.md): start_sync is idempotent
        // enough for the keepalive worker to re-assert when stopped.
        SyncWorker.schedule(this)
        scope.launch {
            runCatching {
                if (!repository.syncSnapshot().running) repository.startSync(Build.MODEL)
            }
        }
    }
}
