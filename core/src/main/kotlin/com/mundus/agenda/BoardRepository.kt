package com.mundus.agenda

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Read surface every screen consumes. `boardFlow` emits a fresh [Board]
 * on subscribe and after every engine change; reads are local-only.
 * Implemented by [EngineAgendaRepository] against the real engine and by a
 * fake in tests/screenshots.
 */
/** A rejected `before` precondition (desktop `ErrorKind::Conflict`): the
 * engine's current object no longer matches the task the UI mutated. The
 * write was NOT applied — the caller reloads and shows a snackbar. */
class WriteConflict(message: String) : Exception(message)

/** The decision chips the quick-entry sheet shows while typing
 * (agenda-core `quick_entry_decision` — UI renders, never decides). */
@Serializable
data class QuickEntryPreview(
    val title: String,
    val scheduledDate: String? = null,
    val projectId: String? = null,
    val status: String = "inbox",
    val isToday: Boolean = false,
    val isSomeday: Boolean = false,
)

interface BoardRepository : AutoCloseable {
    fun boardFlow(): Flow<BoardState>

    /** Quick-entry save — desktop `quick_entry_save` semantics via
     * agenda-core `quick_entry_decision`. Returns the written task so the
     * caller can open it (quick-entry «maximize» → full task screen)
     * without waiting for the next board emission. */
    suspend fun quickEntry(
        rawTitle: String,
        notes: String?,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): Task

    // Mutating ops take the UI's snapshot [Task] — it is the `before`
    // precondition of desktop `Store::save`, so a task changed elsewhere
    // since the board was rendered is rejected as a WriteConflict.

    /** Checkbox toggle (desktop `CompleteTodo`): done → Todo via
     * `set_todo_status`, otherwise `complete_todo`. No-op on trashed rows. */
    suspend fun toggleComplete(task: Task)

    /** Status transition (desktop `set_todo_status`, non-Done arm only). */
    suspend fun setStatus(task: Task, status: String)

    /** Date dropdown (desktop `SetDate`): apply or clear the date. */
    suspend fun setScheduled(task: Task, date: String?)

    /** «На неделю» (desktop `move_to_week`). */
    suspend fun moveToWeek(task: Task)

    /** Project dropdown (desktop `SetProject` / `move_to_project`). */
    suspend fun moveToProject(task: Task, projectId: String?)

    /** Panel title/notes edit (desktop `panel_persist`). */
    suspend fun editTitleNotes(task: Task, title: String, notes: String?)

    /** Sidebar «+» (desktop `create_project`, app.rs:1372-1397): creates
     * «Новый проект» with sort_order = max+1 and returns its id so the
     * caller can open it for rename. */
    suspend fun createProject(): String

    /** Desktop `rename_project` — heading edit commits the new title. */
    suspend fun renameProject(project: Project, title: String)

    /** Archive / restore (desktop `set_project_status`, save.rs:108-124). */
    suspend fun setProjectStatus(project: Project, status: Int)

    /** Desktop `delete_project`: the project's tasks return to Inbox,
     * the object soft-deletes (`deletedAt`). */
    suspend fun deleteProject(project: Project)

    /** Trash / restore (desktop `TrashTodo` / `RestoreTodo`). */
    suspend fun setTrashed(task: Task, trashed: Boolean)

    /** Deletes these tasks for good; the engine syncs the deletion to peers. */
    suspend fun deleteForever(tasks: List<Task>)

    suspend fun quickEntryPreview(
        rawTitle: String,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): QuickEntryPreview

    /** Desktop date-property label for a picked day («Сегодня» /
     * «Следующая неделя» / «18 сент»). */
    suspend fun dayLabel(date: String): String

    /** Desktop `next_monday_key` — the «Следующая неделя» target. */
    suspend fun nextMondayKey(): String

    override fun close() {}
}
