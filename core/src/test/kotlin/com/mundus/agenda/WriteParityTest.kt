package com.mundus.agenda

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Gate A (KOS-370 Phase 2): every write action the app exposes, run through
 * the real UniFFI bindings + host .so and a real `MobileEngine`, asserted
 * by the agenda-core smart lists the board recomputes. The dataset is the
 * seed golden at fixed day 2026-10-12 (Monday, UTC+3); semantics are the
 * desktop `app.rs`/`store.rs` paths the repository mirrors.
 */
class WriteParityTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var repository: EngineAgendaRepository
    private lateinit var dataDir: String

    @Before
    fun setUp() {
        val (repo, dir) = seededRepository(json, loadGolden(json))
        repository = repo
        dataDir = dir
    }

    @After
    fun tearDown() {
        repository.close()
        File(dataDir).deleteRecursively()
    }

    private suspend fun task(id: String) =
        board(repository).task(id) ?: throw AssertionError("task $id missing from board")

    /** The `project` object links whose source is this task. */
    private fun projectLinks(taskId: String) =
        repository.call("list_object_links", buildJsonObject {}).jsonArray
            .map { it.jsonObject }
            .filter {
                it["sourceObjectId"]?.jsonPrimitive?.content == taskId &&
                    it["linkType"]?.jsonPrimitive?.content == "project"
            }

    @Test
    fun quickEntryParsesRussianDateWord() = runBlocking {
        repository.quickEntry("купить молоко завтра", null, null, false, false, null, false)
        val b = board(repository)
        val task = b.list(SmartList.WEEK).single { it.title == "купить молоко" }
        // Desktop quick_entry_save: a parsed date commits the task to Todo.
        assertEquals("2026-10-13", task.scheduledDate)
        assertFalse(task.id in b.ids(SmartList.INBOX))
    }

    @Test
    fun quickEntryWithoutCommitmentStaysInbox() = runBlocking {
        repository.quickEntry("просто задача", null, null, false, false, null, false)
        val b = board(repository)
        assertTrue(b.list(SmartList.INBOX).any { it.title == "просто задача" })
    }

    @Test
    fun quickEntryResolvesProjectMention() = runBlocking {
        repository.quickEntry(
            "согласовать макеты @Релиз Agenda 1.0",
            null,
            null,
            false,
            false,
            null,
            false,
        )
        val b = board(repository)
        // Project is a commitment (status Todo) but without a date the task
        // lives on the project page, not in a dated smart list.
        val task = b.projectTasks["dev-proj-release"]!!.single {
            it.title == "согласовать макеты"
        }
        assertEquals("dev-proj-release", task.projectId)
        assertEquals("todo", task.status)
    }

    @Test
    fun quickEntrySelectedDateOverridesParse() = runBlocking {
        // Desktop: an explicit dropdown date wins and the date word stays in
        // the title (parse_quick_entry_capture early-returns on `selected`).
        repository.quickEntry(
            "купить молоко завтра",
            null,
            "2026-10-20",
            false,
            false,
            null,
            false,
        )
        val task =
            board(repository).list(SmartList.NEXT_WEEK).single {
                it.title == "купить молоко завтра"
            }
        assertEquals("2026-10-20", task.scheduledDate)
    }

    @Test
    fun quickEntrySomedayDefers() = runBlocking {
        repository.quickEntry("когда-нибудь потом", null, null, false, true, null, false)
        val b = board(repository)
        val task = b.list(SmartList.SOMEDAY).single { it.title == "когда-нибудь потом" }
        assertNull(task.scheduledDate)
        assertTrue(task.isSomeday)
    }

    @Test
    fun quickEntryWeekFlagMarksToday() = runBlocking {
        // Desktop: opening quick entry from «Эта неделя» presets qe_week.
        repository.quickEntry("планёрка", null, null, true, false, null, false)
        val b = board(repository)
        val task = b.list(SmartList.WEEK).single { it.title == "планёрка" }
        assertTrue(task.isToday)
        assertNull(task.scheduledDate)
    }

    @Test
    fun completeMovesToLogbook() = runBlocking {
        // Completed today stays visible in Week (completed_in_week); the
        // Logbook view holds only tasks whose completion date is in the
        // past, so cover it with the already-archived seed row.
        repository.toggleComplete(task("dev-week-4"))
        val b = board(repository)
        val done = b.task("dev-week-4")!!
        assertTrue(done.isCompleted)
        assertTrue(done.completedAt != null)
        assertTrue("dev-week-4" in b.ids(SmartList.WEEK))
        assertFalse("dev-week-4" in b.ids(SmartList.LOGBOOK))
        assertTrue("dev-done-yesterday" in b.ids(SmartList.LOGBOOK))
    }

    @Test
    fun uncompleteRestoresTodo() = runBlocking {
        repository.toggleComplete(task("dev-done-yesterday"))
        val b = board(repository)
        val t = b.task("dev-done-yesterday")!!
        assertFalse(t.isCompleted)
        assertNull(t.completedAt)
        assertEquals("todo", t.status)
        assertFalse("dev-done-yesterday" in b.ids(SmartList.LOGBOOK))
    }

    @Test
    fun setScheduledMovesBetweenWeeks() = runBlocking {
        val task = task("dev-week-4")
        repository.setScheduled(task, "2026-10-19")
        var b = board(repository)
        assertTrue("dev-week-4" in b.ids(SmartList.NEXT_WEEK))
        assertFalse("dev-week-4" in b.ids(SmartList.WEEK))
        repository.setScheduled(task("dev-week-4"), null)
        b = board(repository)
        assertFalse("dev-week-4" in b.ids(SmartList.NEXT_WEEK))
    }

    @Test
    fun moveToWeekCommitsInboxTask() = runBlocking {
        repository.moveToWeek(task("dev-inbox-1"))
        val b = board(repository)
        val t = b.list(SmartList.WEEK).single { it.id == "dev-inbox-1" }
        assertTrue(t.isToday)
        assertNull(t.scheduledDate)
        assertFalse("dev-inbox-1" in b.ids(SmartList.INBOX))
    }

    @Test
    fun moveToProjectCommitsTask() = runBlocking {
        repository.moveToProject(task("dev-inbox-2"), "dev-proj-home")
        val b = board(repository)
        val t = b.task("dev-inbox-2")!!
        assertEquals("dev-proj-home", t.projectId)
        assertEquals("todo", t.status)
        assertTrue("dev-inbox-2" in b.projectTasks["dev-proj-home"]!!.map { it.id })
    }

    @Test
    fun moveToProjectClearsSomeday() = runBlocking {
        repository.moveToProject(task("dev-someday-1"), "dev-proj-home")
        val b = board(repository)
        val t = b.task("dev-someday-1")!!
        // Desktop move_to_project clears the flag but not the Deferred
        // status, so the task keeps a project and stays in «Потом».
        assertFalse(t.isSomeday)
        assertEquals("dev-proj-home", t.projectId)
        assertTrue("dev-someday-1" in b.ids(SmartList.SOMEDAY))
        // A move replaces the old project link: exactly one is left, to the new project.
        val links = projectLinks("dev-someday-1")
        assertEquals(1, links.size)
        assertEquals("dev-proj-home", links.single()["targetObjectId"]?.jsonPrimitive?.content)
    }

    @Test
    fun moveToNoProjectRemovesLink() = runBlocking {
        repository.moveToProject(task("dev-week-3"), null)
        val b = board(repository)
        assertNull(b.task("dev-week-3")!!.projectId)
        assertTrue(projectLinks("dev-week-3").isEmpty())
    }

    @Test
    fun editTitleNotes() = runBlocking {
        repository.editTitleNotes(task("dev-inbox-1"), "Перечитать бриф v2", "заметка")
        val t = task("dev-inbox-1")
        assertEquals("Перечитать бриф v2", t.title)
        assertEquals("заметка", t.notes)
    }

    @Test
    fun editEmptyTitleKeepsOld() = runBlocking {
        // Desktop panel_persist: an emptied title keeps the stored one.
        repository.editTitleNotes(task("dev-inbox-1"), "  ", null)
        val t = task("dev-inbox-1")
        assertEquals("Перечитать бриф по лендингу", t.title)
        assertNull(t.notes)
    }

    @Test
    fun trashAndRestore() = runBlocking {
        val task = task("dev-inbox-3")
        repository.setTrashed(task, true)
        var b = board(repository)
        assertTrue("dev-inbox-3" in b.ids(SmartList.TRASH))
        assertFalse("dev-inbox-3" in b.ids(SmartList.INBOX))
        repository.setTrashed(task.copy(isTrashed = true), false)
        b = board(repository)
        assertFalse("dev-inbox-3" in b.ids(SmartList.TRASH))
        assertTrue("dev-inbox-3" in b.ids(SmartList.INBOX))
    }

    @Test
    fun trashGuardsStatusWrites() = runBlocking {
        // Desktop: trash rows are read-only (set_todo_status guard).
        val trashed = task("dev-trash-1")
        repository.setStatus(trashed, "todo")
        repository.toggleComplete(trashed)
        assertEquals("inbox", task("dev-trash-1").status)
    }

    @Test
    fun staleBeforeIsRejectedAndNothingWritten() = runBlocking {
        val stale = task("dev-week-4")
        repository.setScheduled(stale, "2026-10-19")
        // `stale` predates that write — desktop save() must reject.
        try {
            repository.editTitleNotes(stale, "Потерянное изменение", null)
            fail("expected WriteConflict")
        } catch (e: WriteConflict) {
            assertTrue(e.message!!.contains("precondition failed"))
        }
        val t = task("dev-week-4")
        assertEquals("Купить подарок Маше", t.title)
        assertEquals("2026-10-19", t.scheduledDate)
    }

    @Test
    fun optimisticBoardUpdatesImmediately() = runBlocking {
        // The engine write is local; the next board emission already shows
        // the mutation — no sync is awaited anywhere in the path.
        repository.setTrashed(task("dev-inbox-1"), true)
        assertTrue("dev-inbox-1" in board(repository).ids(SmartList.TRASH))
    }
}
