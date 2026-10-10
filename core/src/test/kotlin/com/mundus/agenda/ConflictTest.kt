package com.mundus.agenda

import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Gate B (KOS-370 Phase 2): the `before` version check against a competing
 * writer. A "desktop-side" mutation lands in the same engine behind the
 * repository snapshot's back — exactly what a synced incoming change looks
 * like — and the stale write must be rejected with nothing overwritten.
 */
class ConflictTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
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
        board(repository).task(id) ?: throw AssertionError("task $id missing")

    /** A competing writer: reads the current object, applies a different
     * title through the same mapping the desktop uses, upserts directly —
     * the repository's snapshot knows nothing about it. */
    private fun desktopSideRename(id: String, newTitle: String) {
        val current =
            repository.call("get_object", buildJsonObject { put("id", id) })
        val todo =
            json.parseToJsonElement(taskFromEngineObject(current.toString()))
                .jsonObject
                .toMutableMap()
        todo["title"] = kotlinx.serialization.json.JsonPrimitive(newTitle)
        val (today, offset) = repository.localDay()
        val objectJson =
            taskToEngineObject(
                kotlinx.serialization.json.JsonObject(todo).toString(),
                current.toString(),
                null,
                Instant.now().toString(),
                today,
                offset,
            )
        repository.call(
            "upsert_object",
            buildJsonObject { put("object", json.parseToJsonElement(objectJson)) },
        )
    }

    @Test
    fun staleWriteRejectedCompetingWriteSurvives() = runBlocking {
        val stale = task("dev-inbox-1")
        desktopSideRename("dev-inbox-1", "Название с десктопа")
        try {
            repository.editTitleNotes(stale, "Телефонное название", null)
            fail("expected WriteConflict")
        } catch (_: WriteConflict) {}
        // Nothing was written over the competing change.
        assertEquals("Название с десктопа", task("dev-inbox-1").title)
    }

    @Test
    fun staleCompleteRejectedRecurringNotDuplicated() = runBlocking {
        val stale = task("dev-recurring-1")
        desktopSideRename("dev-recurring-1", "Поливать цветы (десктоп)")
        try {
            repository.toggleComplete(stale)
            fail("expected WriteConflict")
        } catch (_: WriteConflict) {}
        val b = board(repository)
        // The rejection happened before any spawn: no stray next instance.
        assertEquals(
            1,
            b.todos.count { it.title.startsWith("Поливать цветы") },
        )
    }

    @Test
    fun freshWriteAfterReloadSucceeds() = runBlocking {
        desktopSideRename("dev-inbox-1", "Название с десктопа")
        // The board reloaded with the competing version; the UI's new
        // snapshot is not stale, so the write lands.
        repository.editTitleNotes(task("dev-inbox-1"), "Телефонное название", null)
        assertEquals("Телефонное название", task("dev-inbox-1").title)
    }

    @Test
    fun trashRaceWithDesktopEdit() = runBlocking {
        // Phone decides to trash what desktop just renamed — the stale
        // snapshot loses, desktop's object is untouched.
        val stale = task("dev-inbox-2")
        desktopSideRename("dev-inbox-2", "Записаться к стоматологу!!")
        try {
            repository.setTrashed(stale, true)
            fail("expected WriteConflict")
        } catch (_: WriteConflict) {}
        val t = task("dev-inbox-2")
        assertEquals("Записаться к стоматологу!!", t.title)
        assertEquals(false, t.isTrashed)
    }
}
