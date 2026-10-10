package com.mundus.agenda

import com.mundus.engine.ChangeListener
import java.nio.file.Files
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the same generated Kotlin bindings the app ships, against the
 * host-built `libagenda_mobile.so` (x86_64-unknown-linux-gnu, loaded via
 * `jna.library.path` — see core/build.gradle.kts). Phase 0 has no emulator
 * on this box (no KVM), so this host test is the sync-gate stand-in.
 */
class RepositoryHostTest {
    @Test
    fun createListEventReopen() = runBlocking {
        val dataDir = Files.createTempDirectory("agenda-host-test").toString()
        val dataDirFile = java.io.File(dataDir)

        var repository = EngineAgendaRepository(dataDir)
        try {
            val events = LinkedBlockingQueue<String>()
            val subscription =
                repository.engine.subscribe(
                    object : ChangeListener {
                        override fun onChange(eventJson: String) {
                            events.offer(eventJson)
                        }
                    }
                )
            try {
                repository.addTask("Call mom")

                val sawUpsert =
                    withTimeout(10_000) {
                        while (true) {
                            val event = events.poll(1, TimeUnit.SECONDS) ?: continue
                            if (event.contains("\"object_upserted\"")) return@withTimeout true
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                assertTrue("no object_upserted event", sawUpsert)

                val tasks = repository.tasks()
                assertEquals(1, tasks.size)
                assertEquals("Call mom", tasks[0].title)

                val emitted =
                    withTimeout(10_000) {
                        repository.boardFlow().first()
                    }
                val ready = emitted as BoardState.Ready
                assertTrue(ready.board.list(SmartList.INBOX).any { it.title == "Call mom" })
            } finally {
                subscription.close()
            }
        } finally {
            repository.close()
        }

        repository = EngineAgendaRepository(dataDir)
        try {
            assertTrue(
                "task must survive reopen",
                repository.tasks().any { it.title == "Call mom" },
            )
        } finally {
            repository.close()
            dataDirFile.deleteRecursively()
        }
    }

    @Test
    fun quickEntrySchedulesDate() = runBlocking {
        val dataDir = Files.createTempDirectory("agenda-host-test").toString()
        try {
            EngineAgendaRepository(dataDir).use { repository ->
                repository.addTask("позвонить маме завтра")
                val board =
                    (repository.boardFlow().first() as BoardState.Ready).board
                // Desktop quick_entry_save: a parsed date is a commitment,
                // so the task is status Todo — in «Эта неделя», not Inbox.
                val task = board.list(SmartList.WEEK).single()
                assertEquals("позвонить маме", task.title)
                assertEquals(LocalDate.now().plusDays(1).toString(), task.scheduledDate)
            }
        } finally {
            java.io.File(dataDir).deleteRecursively()
        }
    }
}
