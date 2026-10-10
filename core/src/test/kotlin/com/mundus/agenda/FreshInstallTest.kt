package com.mundus.agenda

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First launch on a phone with no desktop paired: the app opens the
 * engine on an empty `filesDir`. It must come up with an empty local DB
 * (no seed repo, no network) instead of erroring.
 */
class FreshInstallTest {
    @Test
    fun emptyDataDirOpensEmptyBoard() = runBlocking {
        val dataDir = File(Files.createTempDirectory("agenda-fresh").toFile(), "files")
        val repository = EngineAgendaRepository(dataDir.absolutePath)
        try {
            val state = repository.boardFlow().first { it !is BoardState.Loading }
            assertTrue("expected Ready, got $state", state is BoardState.Ready)
            val board = (state as BoardState.Ready).board
            assertEquals(emptyList<Task>(), board.todos)
            assertEquals(emptyList<Project>(), board.projects)
            SmartList.entries.forEach { assertEquals(emptyList<Task>(), board.list(it)) }
            assertTrue("ark.db not created", File(dataDir, "ark.db").exists())
        } finally {
            repository.close()
        }
    }
}
