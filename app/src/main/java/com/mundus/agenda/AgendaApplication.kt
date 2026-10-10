package com.mundus.agenda

import android.app.Application

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
}
