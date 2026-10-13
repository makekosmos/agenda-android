package com.mundus.agenda

import android.app.Application
import android.os.Build
import com.mundus.agenda.sync.SyncSlots
import com.mundus.agenda.sync.SyncWorker
import com.mundus.agenda.updater.UpdateManager
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
        EngineAgendaRepository(filesDir.absolutePath, appVersion = BuildConfig.VERSION_NAME)
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

    /** `MundusPalette` or a zeron family id (ui/ZeronThemes.kt). The
     * retired "agenda" palette migrates to Mundus — same chrome with
     * agenda's accent. */
    val palette by lazy {
        androidx.compose.runtime.mutableStateOf(
            when (val stored = prefs.getString("palette", null)) {
                com.mundus.agenda.ui.AgendaPalette -> {
                    prefs.edit().putString("palette", com.mundus.agenda.ui.MundusPalette).apply()
                    com.mundus.agenda.ui.MundusPalette
                }
                null -> com.mundus.agenda.ui.MundusPalette
                else -> stored
            }
        )
    }

    fun setPalette(id: String) {
        palette.value = id
        prefs.edit().putString("palette", id).apply()
    }

    /** Accent preset name (ui/Theme.kt [AccentPresets]); null = «Цвет темы». */
    val accent by lazy {
        androidx.compose.runtime.mutableStateOf(prefs.getString("accent", null))
    }

    fun setAccent(name: String?) {
        accent.value = name
        if (name == null) prefs.edit().remove("accent").apply()
        else prefs.edit().putString("accent", name).apply()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Sync on open + at wall-clock slots (program.md).
        SyncWorker.cancelLegacyPeriodic(this)
        SyncSlots.schedule(this)
        scope.launch {
            // No install can be pending on a cold start: a downloaded APK
            // in cache/updates is only a footprint doubling (KOS-370).
            runCatching { UpdateManager.sweepUpdateCache(this@AgendaApplication) }
            runCatching {
                if (!repository.syncSnapshot().running) repository.startSync(Build.MODEL)
            }
        }
    }
}
