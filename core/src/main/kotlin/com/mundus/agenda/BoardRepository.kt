package com.mundus.agenda

import kotlinx.coroutines.flow.Flow

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

interface BoardRepository : AutoCloseable {
    fun boardFlow(): Flow<BoardState>

    /** Quick-entry save — desktop `quick_entry_save` semantics via
     * agenda-core `quick_entry_decision`. */
    suspend fun quickEntry(
        rawTitle: String,
        notes: String?,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    )

    // Mutating ops take the UI's snapshot [Task] — it is the `before`
    // precondition of desktop `Store::save`, so a task changed elsewhere
    // since the board was rendered is rejected as a WriteConflict.

    /** Checkbox toggle (desktop `CompleteTodo`): done → Todo via
     * `set_todo_status`, otherwise `complete_todo` incl. the recurrence
     * spawn. No-op on trashed rows. */
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

    /** Trash / restore (desktop `TrashTodo` / `RestoreTodo`). */
    suspend fun setTrashed(task: Task, trashed: Boolean)

    override fun close() {}
}
