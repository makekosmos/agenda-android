package com.mundus.agenda

import com.mundus.engine.ChangeListener
import com.mundus.engine.MobileEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 * `dataDir` (`Context.filesDir` on Android), routes every read/write
 * through the ark op surface, and exposes engine changes as a cold [Flow].
 *
 * The engine handle is `lazy`: `MobileEngine.open` creates the data dir
 * and opens `ark.db` synchronously, and every call site below runs on
 * [Dispatchers.IO] — so no SQLite/registry work ever touches the main
 * thread, including the first frame.
 *
 * Every membership, ordering and date rule is evaluated inside
 * agenda-core through the UniFFI exports — Kotlin only shuttles JSON.
 */
class EngineAgendaRepository(
    private val dataDir: String,
    private val dayCheckMillis: Long = 10_000,
    /** Shown to peers next to our platform; engine-mobile otherwise sends its crate version. */
    private val appVersion: String? = null,
    private val localDayProvider: () -> Pair<String, Int> = {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        LocalDate.now(zone).toString() to zone.rules.getOffset(now).totalSeconds
    },
) : BoardRepository, SyncApi {
    // encodeDefaults: Rust side structs (Project, RecurrenceRule) require
    // fields whose values equal the Kotlin default — never elide them.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val engineLazy = lazy { MobileEngine.open(dataDir) }
    internal val engine: MobileEngine
        get() = engineLazy.value

    internal fun call(op: String, params: JsonObject): JsonElement =
        unwrap(engine.call(op, params.toString()), op)

    private fun unwrap(raw: String, op: String): JsonElement {
        val response = json.parseToJsonElement(raw).jsonObject
        check(response["ok"]?.jsonPrimitive?.boolean == true) {
            "$op rejected: ${response["error"]}"
        }
        return response["data"] ?: JsonNull
    }

    private fun listObjects(typeId: String): JsonArray =
        call("list_objects_by_type", buildJsonObject { put("type_id", typeId) }).jsonArray

    /** The local civil day the Rust rules need: `YYYY-MM-DD` + UTC offset.
     * Injectable so host tests can pin `today` against the golden dump. */
    internal fun localDay(): Pair<String, Int> = localDayProvider()

    private fun todoJsons(): List<String> =
        json.decodeFromString<List<String>>(
            tasksFromEngineObjects(listObjects("com.kosmos.task").toString())
        )

    /**
     * Live projects + the tag list, mirroring desktop `Engine::projects` /
     * `references`: newest non-deleted `com.kosmos.agenda.references`
     * object, with `get_object("agenda:references")` as the fallback for
     * databases where the type query comes back empty.
     */
    private fun projectsAndTags(): Pair<List<Project>, List<Tag>> {
        val objects = listObjects("com.kosmos.project")
        val live =
            objects
                .filter { it.jsonObject["deletedAt"]?.jsonPrimitive?.isString != true }
                .map { json.decodeFromString<Project>(projectFromEngineObject(it.toString())) }
        val known = objects.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet()
        var references =
            listObjects("com.kosmos.agenda.references")
                .filter { it.jsonObject["deletedAt"]?.jsonPrimitive?.isString != true }
                .maxByOrNull { it.jsonObject["updatedAt"]?.jsonPrimitive?.content ?: "" }
        if (references == null) {
            // get_object answers JsonNull for a missing id, not an object.
            val fallback =
                call("get_object", buildJsonObject { put("id", "agenda:references") })
            if (fallback is JsonObject && fallback.isNotEmpty()) {
                references = fallback
            }
        }
        if (references == null) {
            return live.sortedBy { it.sortOrder } to emptyList()
        }
        val pair =
            json.parseToJsonElement(referencesFromEngineObject(references.toString())).jsonArray
        val legacy =
            json.decodeFromString<List<Project>>(pair[0].toString()).filter { it.id !in known }
        val tags = json.decodeFromString<List<Tag>>(pair[1].toString())
        return (live + legacy).sortedBy { it.sortOrder } to tags
    }

    private fun board(): Board {
        val (today, offset) = localDay()
        val (projects, tags) = projectsAndTags()
        val todoJsons = todoJsons()
        if (todoJsons.isEmpty()) {
            return Board(projects = projects, tags = tags)
        }
        val todosJson = "[${todoJsons.joinToString(",")}]"
        val todos = todoJsons.map { json.decodeFromString<Task>(it) }

        // One FFI call computes every list, project/tag view and per-task
        // date view — all inside agenda-core.
        val view =
            json.parseToJsonElement(
                    boardLists(
                        todosJson,
                        "[${projects.joinToString(",") { "\"${it.id}\"" }}]",
                        today,
                        offset,
                    )
                )
                .jsonObject
        val lists = view["lists"]!!.jsonObject
        fun listIndices(key: String): List<Task> =
            lists[key]!!.jsonArray.map { todos[it.jsonPrimitive.content.toInt()] }
        val projectTasks =
            view["projectTasks"]!!.jsonObject.mapValues { (_, ids) ->
                ids.jsonArray.map { todos[it.jsonPrimitive.content.toInt()] }
            }
        val dateViews =
            todos
                .zip(json.decodeFromString<List<TaskDateView>>(view["dateViews"]!!.toString()))
                .associate { (task, v) -> task.id to v }
        return Board(
            todos = todos.sortedBy { it.sortOrder },
            projects = projects,
            tags = tags,
            lists =
                mapOf(
                    SmartList.INBOX to listIndices("inbox"),
                    SmartList.WEEK to listIndices("week"),
                    SmartList.NEXT_WEEK to listIndices("nextWeek"),
                    SmartList.SOMEDAY to listIndices("someday"),
                    SmartList.LOGBOOK to listIndices("logbook"),
                    SmartList.TRASH to listIndices("trash"),
                ),
            projectTasks = projectTasks,
            dateViews = dateViews,
        )
    }

    /**
     * Emits the current board, then re-emits on every engine change event
     * (`object_upserted`, `entity_changed`, ...). The listener lives until
     * the collector cancels. An engine failure before the first board
     * surfaces as [BoardState.Error]; after that it re-emits the last good
     * board so a transient rejection cannot wipe the screen.
     */
    override fun boardFlow(): Flow<BoardState> =
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
                // Week/next-week membership depends on today, which no engine
                // event announces: re-emit when the local day turns.
                val dayWatch = launch {
                    var day = localDay()
                    while (true) {
                        delay(dayCheckMillis)
                        val now = localDay()
                        if (now != day) {
                            day = now
                            trySend(Unit)
                        }
                    }
                }
                awaitClose {
                    dayWatch.cancel()
                    subscription.close()
                }
            }
            .conflate()
            .map {
                runCatching { board().also { lastBoard = it } }
                    .fold(
                        onSuccess = { BoardState.Ready(it) },
                        onFailure = { e ->
                            lastBoard
                                ?.let { BoardState.Ready(it) }
                                ?: BoardState.Error(e.message ?: "engine error")
                        },
                    )
            }
            .flowOn(Dispatchers.IO)

    @Volatile private var lastBoard: Board? = null

    /** Tasks the Inbox smart list shows, in display order. */
    internal suspend fun tasks(): List<Task> = withContext(Dispatchers.IO) {
        board().list(SmartList.INBOX)
    }

    /** Quick-entry save: desktop `quick_entry_save` end to end — the
     * agenda-core decision (date words, @project mention, status rule)
     * produces the full Todo, written as the same `com.kosmos.task` object
     * shape agenda-gpui produces. */
    override suspend fun quickEntry(
        rawTitle: String,
        notes: String?,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): Task = withContext(Dispatchers.IO) {
        val (today, offset) = localDay()
        val now = Instant.now().toString()
        val (projects, _) = projectsAndTags()
        val id = "task-${UUID.randomUUID()}"
        val todo =
            quickEntryNewTask(
                id = id,
                rawTitle = rawTitle,
                notes = notes,
                selectedDate = selectedDate,
                week = week,
                someday = someday,
                projectId = projectId,
                projectTouched = projectTouched,
                projectsJson = json.encodeToString(projects),
                sortOrder = todoJsons().size,
                createdAt = now,
                today = today,
                utcOffsetSeconds = offset,
            )
        val task = json.decodeFromString<Task>(todo)
        save(null, task)
        task
    }

    override suspend fun toggleComplete(task: Task): Unit = withContext(Dispatchers.IO) {
        // Both arms are no-ops on trashed rows (desktop complete_todo /
        // set_todo_status guards).
        if (task.isTrashed) return@withContext
        val (today, offset) = localDay()
        val now = Instant.now().toString()
        val beforeJson = json.encodeToString(task)
        if (taskStatusName(beforeJson) == "done") {
            save(task, json.decodeFromString(taskSetStatus(beforeJson, "todo", now)))
            return@withContext
        }
        val completed = json.decodeFromString<Task>(taskComplete(beforeJson, now))
        val next =
            taskNextRecurrence(
                    beforeJson,
                    json.encodeToString(completed),
                    UUID.randomUUID().toString(),
                    todoJsons().size,
                    now,
                    today,
                    offset,
                )
                ?.let { json.decodeFromString<Task>(it) }
        saveWithNext(task, completed, next)
    }

    override suspend fun setStatus(task: Task, status: String): Unit =
        withContext(Dispatchers.IO) {
            if (task.isTrashed) return@withContext
            val now = Instant.now().toString()
            save(task, json.decodeFromString(taskSetStatus(json.encodeToString(task), status, now)))
        }

    override suspend fun setScheduled(task: Task, date: String?): Unit =
        withContext(Dispatchers.IO) {
            save(task, json.decodeFromString(taskSetScheduled(json.encodeToString(task), date)))
        }

    override suspend fun moveToWeek(task: Task): Unit = withContext(Dispatchers.IO) {
        save(task, json.decodeFromString(taskMoveToWeek(json.encodeToString(task))))
    }

    override suspend fun moveToProject(task: Task, projectId: String?): Unit =
        withContext(Dispatchers.IO) {
            save(task, json.decodeFromString(taskMoveToProject(json.encodeToString(task), projectId)))
        }

    override suspend fun editTitleNotes(task: Task, title: String, notes: String?): Unit =
        withContext(Dispatchers.IO) {
            save(
                task,
                json.decodeFromString(taskEditTitleNotes(json.encodeToString(task), title, notes)),
            )
        }

    override suspend fun setTrashed(task: Task, trashed: Boolean): Unit =
        withContext(Dispatchers.IO) {
            save(task, json.decodeFromString(taskSetTrashed(json.encodeToString(task), trashed)))
        }

    override suspend fun deleteForever(tasks: List<Task>): Unit =
        withContext(Dispatchers.IO) {
            for (task in tasks) call("delete_object", buildJsonObject { put("id", task.id) })
        }

    // --- Projects: desktop app.rs create_project / projects.rs rename,
    // save.rs set_project_status, projects.rs delete_project + the engine's
    // soft delete (src/store/projects.rs). Same write pattern as tasks:
    // get_object precondition -> project_to_engine_object -> upsert_object.

    private fun projectObject(id: String): JsonElement =
        call("get_object", buildJsonObject { put("id", id) })

    private fun JsonElement.isPresent() = this is JsonObject && isNotEmpty()

    private fun upsertProjectObject(objectJson: JsonElement) {
        call("upsert_object", buildJsonObject { put("object", objectJson) })
    }

    private fun saveProject(before: Project?, project: Project) {
        val now = Instant.now().toString()
        val current = projectObject(project.id)
        val exists = current.isPresent()
        if (before != null) {
            val stored =
                if (exists) json.decodeFromString<Project>(
                    projectFromEngineObject(current.toString())
                ) else null
            if (stored != before) {
                throw WriteConflict("local: precondition failed (stale or missing)")
            }
        } else if (exists) {
            throw WriteConflict("local: object already exists")
        }
        upsertProjectObject(
            json.parseToJsonElement(
                projectToEngineObject(
                    json.encodeToString(project),
                    if (exists) current.toString() else null,
                    now,
                )
            )
        )
    }

    override suspend fun createProject(): String = withContext(Dispatchers.IO) {
        val (projects, _) = projectsAndTags()
        val id = UUID.randomUUID().toString()
        saveProject(
            null,
            Project(
                id = id,
                title = "Новый проект",
                status = 0,
                sortOrder = (projects.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ),
        )
        id
    }

    override suspend fun renameProject(project: Project, title: String): Unit =
        withContext(Dispatchers.IO) {
            // Desktop commit_project_title: an empty title keeps the old one.
            saveProject(project, project.copy(title = title.ifBlank { project.title }))
        }

    override suspend fun setProjectStatus(project: Project, status: Int): Unit =
        withContext(Dispatchers.IO) {
            saveProject(project, project.copy(status = status))
        }

    override suspend fun deleteProject(project: Project): Unit = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        // Tasks return to Inbox (desktop delete_project arm).
        for (t in board().todos.filter { it.projectId == project.id }) {
            val moved =
                t.copy(
                    projectId = null,
                    status =
                        if (t.status == "todo" && t.scheduledDate == null && !t.isToday)
                            "inbox"
                        else t.status,
                )
            if (moved != t) save(t, moved)
        }
        // Soft delete: the object stays with deletedAt, like trash.
        val current = projectObject(project.id)
        val objectJson =
            json.parseToJsonElement(
                projectToEngineObject(
                    json.encodeToString(project),
                    if (current.isPresent()) current.toString() else null,
                    now,
                )
            )
        upsertProjectObject(
            JsonObject(objectJson.jsonObject.toMutableMap().apply {
                put("deletedAt", JsonPrimitive(now))
            })
        )
    }

    /** `get_object` + `mapping::read` pair — the engine's current stored
     * object and its decoded Todo, or nulls when the id is unknown. */
    private fun currentTodo(id: String): Pair<JsonElement, Task?> {
        val obj = call("get_object", buildJsonObject { put("id", id) })
        if (obj is JsonNull || (obj is JsonObject && obj.isEmpty())) {
            return JsonNull to null
        }
        return obj to json.decodeFromString(taskFromEngineObject(obj.toString()))
    }

    /** Desktop `Store::save`: `before` is the precondition — when it no
     * longer matches the engine's stored object the write is rejected as a
     * [WriteConflict] and nothing is written. */
    private fun save(before: Task?, todo: Task) {
        val (today, offset) = localDay()
        val now = Instant.now().toString()
        val (currentObj, currentTodo) = currentTodo(todo.id)
        if (before != null) {
            if (currentTodo == null || currentTodo != before) {
                throw WriteConflict("local: precondition failed (stale or missing)")
            }
        } else if (currentTodo != null) {
            throw WriteConflict("local: object already exists")
        }
        val objectJson =
            taskToEngineObject(
                json.encodeToString(todo),
                existingObjectJson = if (currentTodo == null) null else currentObj.toString(),
                beforeTodoJson = before?.let { json.encodeToString(it) },
                now = now,
                today = today,
                utcOffsetSeconds = offset,
            )
        call(
            "upsert_object",
            buildJsonObject { put("object", json.parseToJsonElement(objectJson)) },
        )
    }

    /** Desktop `save_with_next`: write the completed todo, then the spawned
     * recurrence; on a lost acknowledgement re-read before deciding — a
     * stored-but-unacked next still counts as saved, a missing one rolls
     * the completion back. */
    private fun saveWithNext(before: Task, completed: Task, next: Task?) {
        save(before, completed)
        if (next == null) return
        try {
            save(null, next)
        } catch (e: Exception) {
            val (_, stored) = currentTodo(next.id)
            if (stored == next) return
            if (stored == null) {
                runCatching { save(completed, before) }
            }
            throw e
        }
    }

    override suspend fun quickEntryPreview(
        rawTitle: String,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): QuickEntryPreview = withContext(Dispatchers.IO) {
        val (today, offset) = localDay()
        val (projects, _) = projectsAndTags()
        json.decodeFromString(
            quickEntryPreview(
                rawTitle,
                selectedDate,
                week,
                someday,
                projectId,
                projectTouched,
                json.encodeToString(projects),
                today,
                offset,
            )
        )
    }

    override suspend fun dayLabel(date: String): String = withContext(Dispatchers.IO) {
        val (today, offset) = localDay()
        dayLabel(date, today, offset)
    }

    override suspend fun nextMondayKey(): String = withContext(Dispatchers.IO) {
        val (today, offset) = localDay()
        nextMondayKey(today, offset)
    }

    // -- SyncApi: pairing + sync status -------------------------------

    // ark-core keeps the pairing ticket only in the running session's
    // params, so a cold start would come up listen-only and neither side
    // would dial. The ticket we entered is replayed on every start.
    private val peerTicketFile get() = java.io.File(dataDir, "sync_peer_ticket")

    override suspend fun startSync(deviceName: String): Unit = withContext(Dispatchers.IO) {
        val ticket = peerTicketFile.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
        unwrap(
            engine.startSync(
                buildJsonObject {
                    put("device_name", deviceName)
                    if (appVersion != null) put("app_version", appVersion)
                    if (ticket != null) put("iroh_peer_ticket", ticket)
                }.toString()
            ),
            "start_sync",
        )
        Unit
    }

    override suspend fun restartSync(deviceName: String): Unit = withContext(Dispatchers.IO) {
        if (syncSnapshot().running) unwrap(engine.stopSync(), "stop_sync")
        startSync(deviceName)
    }

    override suspend fun syncSnapshot(): SyncSnapshot = withContext(Dispatchers.IO) {
        json.decodeFromString(unwrap(engine.syncStatus(), "get_sync_snapshot").toString())
    }

    override suspend fun showPairingCode(): String? = withContext(Dispatchers.IO) {
        unwrap(engine.showPairingCode(), "show_pairing_code")
            .let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    }

    override suspend fun ownIrohTicket(): String? = withContext(Dispatchers.IO) {
        unwrap(engine.ownIrohTicket(), "get_own_iroh_ticket")
            .let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    }

    override suspend fun connectWithPairingCode(code: String): String =
        withContext(Dispatchers.IO) {
            val data = unwrap(engine.connectWithPairingCode(code), "connect_with_pairing_code")
            peerTicketFile.writeText(code.trim())
            (data as? JsonObject)?.get("status")?.jsonPrimitive?.content ?: "pending"
        }

    override suspend fun acceptPairing(deviceId: String): Unit = withContext(Dispatchers.IO) {
        call("accept_pairing", buildJsonObject { put("device_id", deviceId) })
        Unit
    }

    override suspend fun declinePairing(deviceId: String): Unit = withContext(Dispatchers.IO) {
        call("decline_pairing", buildJsonObject { put("device_id", deviceId) })
        Unit
    }

    override suspend fun cancelPairing(): Unit = withContext(Dispatchers.IO) {
        call("cancel_pairing", buildJsonObject {})
        Unit
    }

    override suspend fun disconnectPeer(deviceId: String): Unit = withContext(Dispatchers.IO) {
        call("disconnect_peer", buildJsonObject { put("device_id", deviceId) })
        if (syncSnapshot().peers.isEmpty()) peerTicketFile.delete()
        Unit
    }

    override fun close() {
        if (engineLazy.isInitialized()) engineLazy.value.shutdown()
    }
}
