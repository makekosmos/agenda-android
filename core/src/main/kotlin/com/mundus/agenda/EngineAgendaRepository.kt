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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The app's single boundary to the Engine: opens `MobileEngine` on
 * `dataDir` (`Context.filesDir` on Android), routes every read/write
 * through the ark op surface, and exposes engine changes as a cold [Flow].
 * All engine calls are synchronous UniFFI calls, so they run on
 * [Dispatchers.IO]; nothing here does I/O on the caller's thread.
 *
 * Every membership, ordering and date rule is evaluated inside
 * agenda-core through the UniFFI exports — Kotlin only shuttles JSON.
 */
class EngineAgendaRepository(dataDir: String) : BoardRepository {
    private val json = Json { ignoreUnknownKeys = true }

    internal val engine: MobileEngine = MobileEngine.open(dataDir)

    internal fun call(op: String, params: JsonObject): JsonElement {
        val response = json.parseToJsonElement(engine.call(op, params.toString())).jsonObject
        check(response["ok"]?.jsonPrimitive?.boolean == true) {
            "$op rejected: ${response["error"]}"
        }
        return response["data"] ?: JsonNull
    }

    private fun listObjects(typeId: String): JsonArray =
        call("list_objects_by_type", buildJsonObject { put("type_id", typeId) }).jsonArray

    /** The local civil day the Rust rules need: `YYYY-MM-DD` + UTC offset. */
    private fun localDay(): Pair<String, Int> {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        return LocalDate.now(zone).toString() to zone.rules.getOffset(now).totalSeconds
    }

    internal fun todoJsons(): List<String> =
        listObjects("com.kosmos.task").map { taskFromEngineObject(it.toString()) }

    private fun projectsAndTags(): Pair<List<Project>, List<Tag>> {
        val objects = listObjects("com.kosmos.project")
        val live =
            objects
                .filter { it.jsonObject["deletedAt"]?.jsonPrimitive?.isString != true }
                .map { json.decodeFromString<Project>(projectFromEngineObject(it.toString())) }
        val known = objects.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet()
        val references =
            listObjects("com.kosmos.agenda.references")
                .filter { it.jsonObject["deletedAt"]?.jsonPrimitive?.isString != true }
                .maxByOrNull { it.jsonObject["updatedAt"]?.jsonPrimitive?.content ?: "" }
        if (references == null) {
            return live.sortedBy { it.sortOrder } to emptyList()
        }
        val pair = json.parseToJsonElement(referencesFromEngineObject(references.toString())).jsonArray
        val legacy =
            json.decodeFromString<List<Project>>(pair[0].toString()).filter { it.id !in known }
        val tags = json.decodeFromString<List<Tag>>(pair[1].toString())
        return (live + legacy).sortedBy { it.sortOrder } to tags
    }

    private fun board(): Board {
        val (today, offset) = localDay()
        val todoJsons = todoJsons()
        if (todoJsons.isEmpty()) {
            val (projects, tags) = projectsAndTags()
            return Board(projects = projects, tags = tags)
        }
        val todosJson = "[${todoJsons.joinToString(",")}]"
        val todos = todoJsons.map { json.decodeFromString<Task>(it) }

        val (projects, tags) = projectsAndTags()
        val lists =
            mapOf(
                SmartList.INBOX to
                    smartListIndices(SmartListName.INBOX, todosJson, today, offset),
                SmartList.WEEK to
                    smartListIndices(SmartListName.WEEK, todosJson, today, offset),
                SmartList.NEXT_WEEK to
                    smartListIndices(SmartListName.NEXT_WEEK, todosJson, today, offset),
                SmartList.SOMEDAY to
                    smartListIndices(SmartListName.SOMEDAY, todosJson, today, offset),
                SmartList.LOGBOOK to
                    smartListIndices(SmartListName.LOGBOOK, todosJson, today, offset),
                SmartList.TRASH to
                    smartListIndices(SmartListName.TRASH, todosJson, today, offset),
                SmartList.TODAY to todayListIndices(todosJson, today, offset),
            ).mapValues { (_, idx) -> idx.map { todos[it.toInt()] } }
        val projectTasks =
            projects.associate { p ->
                p.id to
                    projectTaskIndices(p.id, todosJson, today, offset).map { todos[it.toInt()] }
            }
        val tagTasks =
            tags.associate { tag ->
                tag.id to tagTaskIndices(tag.id, todosJson).map { todos[it.toInt()] }
            }
        val dateViews =
            todos.zip(todoJsons).associate { (task, taskJson) ->
                task.id to
                    json.decodeFromString<TaskDateView>(taskDateView(taskJson, today, offset))
            }
        return Board(
            todos = todos.sortedBy { it.sortOrder },
            projects = projects,
            tags = tags,
            lists = lists,
            projectTasks = projectTasks,
            tagTasks = tagTasks,
            dateViews = dateViews,
        )
    }

    /**
     * Emits the current board, then re-emits on every engine change event
     * (`object_upserted`, `entity_changed`, ...). The listener lives until
     * the collector cancels.
     */
    override fun boardFlow(): Flow<Board> =
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
            .map { board() }
            .flowOn(Dispatchers.IO)

    /** Tasks the Inbox smart list shows, in display order. */
    suspend fun tasks(): List<Task> = withContext(Dispatchers.IO) {
        board().list(SmartList.INBOX)
    }

    /** Quick-entry add: runs the title through the agenda-core parser so
     * "завтра" and friends become `scheduled_date`, then writes the same
     * `com.kosmos.task` object shape agenda-gpui produces. */
    override suspend fun addTask(rawTitle: String): Unit = withContext(Dispatchers.IO) {
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
