package com.mundus.agenda

import com.mundus.engine.ChangeListener
import com.mundus.engine.MobileEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The app's single boundary to the Engine: opens `MobileEngine` on
 * `dataDir` (`Context.filesDir` on Android), routes every read/write through
 * the ark op surface, and exposes engine changes as a cold [Flow].
 * All engine calls are synchronous UniFFI calls, so they run on
 * [Dispatchers.IO]; nothing here does I/O on the caller's thread.
 */
class AgendaRepository(dataDir: String) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }

    internal val engine: MobileEngine = MobileEngine.open(dataDir)

    private fun call(op: String, params: JsonObject): kotlinx.serialization.json.JsonElement {
        val response =
            json.parseToJsonElement(engine.call(op, params.toString())).jsonObject
        check(response["ok"]?.jsonPrimitive?.boolean == true) {
            "$op rejected: ${response["error"]}"
        }
        return response["data"] ?: JsonNull
    }

    /** The local civil day the Rust rules need: `YYYY-MM-DD` + UTC offset. */
    private fun localDay(): Pair<String, Int> {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        return LocalDate.now(zone).toString() to
            zone.rules.getOffset(now).totalSeconds
    }

    /** Tasks the Inbox smart list shows, in display order. */
    suspend fun tasks(): List<Task> = withContext(Dispatchers.IO) {
        val objects =
            call(
                    "list_objects_by_type",
                    buildJsonObject { put("type_id", "com.kosmos.task") },
                )
                .jsonArray
        val todosJson = objects.map { taskFromEngineObject(it.toString()) }
        if (todosJson.isEmpty()) {
            return@withContext emptyList()
        }
        val (today, offset) = localDay()
        val indices =
            smartListIndices(
                SmartListName.INBOX,
                "[${todosJson.joinToString(",")}]",
                today,
                offset,
            )
        indices.map { json.decodeFromString<Task>(todosJson[it.toInt()]) }
    }

    /**
     * Emits the current task list, then re-emits on every engine change
     * event (`object_upserted`, `entity_changed`, ...). The listener lives
     * until the collector cancels.
     */
    fun tasksFlow(): Flow<List<Task>> =
        callbackFlow {
                val subscription =
                    engine.subscribe(
                        object : ChangeListener {
                            override fun onChange(eventJson: String) {
                                trySend(Unit)
                            }
                        }
                    )
                trySend(Unit)
                awaitClose { subscription.close() }
            }
            .conflate()
            .map { tasks() }
            .flowOn(Dispatchers.IO)

    /**
     * Quick-entry add: runs the title through the agenda-core parser so
     * "завтра" and friends become `scheduled_date`, then writes the same
     * `com.kosmos.task` object shape agenda-gpui produces.
     */
    suspend fun addTask(rawTitle: String): Unit = withContext(Dispatchers.IO) {
        val (today, offset) = localDay()
        val parsed = parseQuickEntry(rawTitle, null, today, offset)
        val id = "task-${UUID.randomUUID()}"
        val todo =
            buildJsonObject {
                json.parseToJsonElement(newTaskJson(id, parsed.title)).jsonObject.forEach {
                    (key, value) ->
                    put(key, value)
                }
                parsed.scheduledDate?.let { put("scheduled_date", JsonPrimitive(it)) }
            }
        val objectJson =
            taskToEngineObject(
                todo.toString(),
                existingObjectJson = null,
                beforeTodoJson = null,
                now = Instant.now().toString(),
                today = today,
                utcOffsetSeconds = offset,
            )
        call(
            "upsert_object",
            buildJsonObject { put("object", json.parseToJsonElement(objectJson)) },
        )
    }

    override fun close() {
        engine.shutdown()
    }
}
