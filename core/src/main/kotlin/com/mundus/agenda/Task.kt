package com.mundus.agenda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The full agenda-core `Todo` shape. Field names match the Rust serde
 * names; unknown fields are ignored on decode so the object survives
 * untouched inside the Engine.
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String? = null,
    val priority: Int = 0,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    val deadline: String? = null,
    @SerialName("reminder_date") val reminderDate: String? = null,
    @SerialName("is_today") val isToday: Boolean = false,
    @SerialName("is_evening") val isEvening: Boolean = false,
    @SerialName("is_someday") val isSomeday: Boolean = false,
    val status: String = "inbox",
    @SerialName("system_kind") val systemKind: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("is_cancelled") val isCancelled: Boolean = false,
    @SerialName("is_trashed") val isTrashed: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("heading_id") val headingId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("tag_ids") val tagIds: List<String> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val billable: Boolean = false,
    val price: Double? = null,
    val significance: Int? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class ChecklistItem(@SerialName("is_completed") val isCompleted: Boolean = false)

@Serializable
data class RecurrenceRule(
    val frequency: Int = 0,
    val interval: Int = 1,
    @SerialName("recurrence_type") val recurrenceType: Int = 0,
    @SerialName("days_of_week") val daysOfWeek: List<Int> = emptyList(),
)

@Serializable
data class Project(
    val id: String,
    val title: String,
    val status: Int = 0,
    val deadline: String? = null,
    val sortOrder: Int = 0,
    val areaId: String? = null,
)

@Serializable
data class Tag(val id: String, val title: String, val color: String = "")

/** Per-row date fields computed by agenda-core (`task_date_view`). */
@Serializable
data class TaskDateView(
    val dateKey: String? = null,
    val label: String = "",
    val conflict: Boolean = false,
    val overdue: Boolean = false,
    val deadlineLabel: String? = null,
    val reminderLabel: String? = null,
    val completedLabel: String? = null,
    val createdLabel: String? = null,
)

/** Smart lists, in desktop sidebar order (chrome.rs:375-383). Архив and
 * Корзина live under the desktop «Другое» footer menu (chrome.rs:1056-1067). */
enum class SmartList(val titleRu: String) {
    INBOX("Входящие"),
    WEEK("Неделя"),
    NEXT_WEEK("Следующая"),
    SOMEDAY("Потом"),
    LOGBOOK("Архив"),
    TRASH("Корзина"),
}

/** Repository state surfaced to the UI: loading until the first board
 * arrives, error if the engine fails before any board was produced. */
sealed interface BoardState {
    data object Loading : BoardState

    data class Ready(val board: Board) : BoardState

    data class Error(val message: String) : BoardState
}

/** Everything a screen needs, recomputed when the engine emits a change. */
data class Board(
    val todos: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lists: Map<SmartList, List<Task>> = emptyMap(),
    val projectTasks: Map<String, List<Task>> = emptyMap(),
    val dateViews: Map<String, TaskDateView> = emptyMap(),
) {
    fun list(smartList: SmartList): List<Task> = lists[smartList].orEmpty()

    fun count(smartList: SmartList): Int = list(smartList).size

    fun task(id: String): Task? = todos.firstOrNull { it.id == id }

    fun project(id: String): Project? = projects.firstOrNull { it.id == id }

    fun tag(id: String): Tag? = tags.firstOrNull { it.id == id }

    fun dateView(task: Task): TaskDateView = dateViews[task.id] ?: TaskDateView()
}
