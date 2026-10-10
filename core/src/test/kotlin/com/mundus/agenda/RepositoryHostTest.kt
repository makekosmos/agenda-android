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
                repository.quickEntry("Call mom", null, null, false, false, null, false)

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
    fun quickEntryPreviewThenCreate() = runBlocking {
        val dataDir = Files.createTempDirectory("agenda-host-test").toString()
        try {
            EngineAgendaRepository(dataDir).use { repository ->
                // The real Engine path that crashed on the "+" sheet: the
                // UniFFI quick_entry_preview JSON decodes into the Kotlin
                // QuickEntryPreview, then quickEntry writes the task.
                val preview =
                    repository.quickEntryPreview(
                        "купить молоко завтра",
                        null,
                        false,
                        false,
                        null,
                        false,
                    )
                assertEquals("купить молоко", preview.title)
                assertEquals(
                    LocalDate.now().plusDays(1).toString(),
                    preview.scheduledDate,
                )

                repository.quickEntry(
                    "купить молоко завтра",
                    null,
                    null,
                    false,
                    false,
                    null,
                    false,
                )
                val board = (repository.boardFlow().first() as BoardState.Ready).board
                assertTrue(
                    board.list(SmartList.WEEK).any { it.title == "купить молоко" }
                )
            }
        } finally {
            java.io.File(dataDir).deleteRecursively()
        }
    }

    @Test
    fun projectLifecycle() = runBlocking {
        val dataDir = Files.createTempDirectory("agenda-host-test").toString()
        try {
            EngineAgendaRepository(dataDir).use { repository ->
                // Desktop create_project (app.rs:1372-1397): «Новый проект»
                // lands at the bottom of the sidebar and can be opened.
                val id = repository.createProject()
                var board = (repository.boardFlow().first() as BoardState.Ready).board
                val created = board.projects.single { it.id == id }
                assertEquals("Новый проект", created.title)
                assertEquals(0, created.status)

                // Heading edit commits a rename (projects.rs:57-108).
                repository.renameProject(created, "Переезд в Казань")
                board = (repository.boardFlow().first() as BoardState.Ready).board
                assertEquals("Переезд в Казань", board.project(id)!!.title)

                // Quick entry into the project via @mention.
                repository.quickEntry(
                    "собрать коробки @Переезд в Казань",
                    null, null, false, false, null, false,
                )
                board = (repository.boardFlow().first() as BoardState.Ready).board
                val boxed = board.todos.single { it.title == "собрать коробки" }
                assertEquals(id, boxed.projectId)

                // Archive (set_project_status 2) removes it from the active
                // sidebar set; restore brings it back.
                repository.setProjectStatus(board.project(id)!!, 2)
                board = (repository.boardFlow().first() as BoardState.Ready).board
                assertEquals(2, board.project(id)!!.status)

                repository.setProjectStatus(board.project(id)!!, 0)
                board = (repository.boardFlow().first() as BoardState.Ready).board
                assertEquals(0, board.project(id)!!.status)

                // Delete (delete_project): soft-deleted object, tasks go
                // back to Inbox.
                repository.deleteProject(board.project(id)!!)
                board = (repository.boardFlow().first() as BoardState.Ready).board
                assertTrue("deleted project must not be listed",
                    board.projects.none { it.id == id })
                val moved = board.todos.single { it.title == "собрать коробки" }
                assertEquals(null, moved.projectId)
                assertTrue(board.list(SmartList.INBOX).any { it.id == moved.id })
            }
        } finally {
            java.io.File(dataDir).deleteRecursively()
        }
    }

    @Test
    fun quickEntrySchedulesDate() = runBlocking {
        val dataDir = Files.createTempDirectory("agenda-host-test").toString()
        try {
            EngineAgendaRepository(dataDir).use { repository ->
                repository.quickEntry("позвонить маме завтра", null, null, false, false, null, false)
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
