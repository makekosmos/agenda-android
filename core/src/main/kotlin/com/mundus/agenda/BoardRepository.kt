package com.mundus.agenda

import kotlinx.coroutines.flow.Flow

/**
 * Read surface every screen consumes. `boardFlow` emits a fresh [Board]
 * on subscribe and after every engine change; reads are local-only.
 * Implemented by [EngineAgendaRepository] against the real engine and by a
 * fake in tests/screenshots.
 */
interface BoardRepository : AutoCloseable {
    fun boardFlow(): Flow<Board>

    suspend fun addTask(rawTitle: String)

    override fun close() {}
}
