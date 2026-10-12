package com.mundus.agenda

/** Desktop display options (agenda-gpui `BoardOpts`, pages/options.rs). */
enum class SortKey(val label: String) {
    DEFAULT("По умолчанию"),
    DATE("По дате"),
    PRIORITY("По приоритету"),
    TITLE("По названию"),
}

enum class GroupKey(val label: String) {
    NONE("Нет"),
    PROJECT("По проекту"),
    DATE("По дате"),
}

data class ListOptions(
    val sort: SortKey = SortKey.DEFAULT,
    val group: GroupKey = GroupKey.NONE,
) : java.io.Serializable

/** A run of rows under an optional header; `null` label = ungrouped. */
data class TaskGroup(val label: String?, val tasks: List<Task>)

/** Desktop `view_options_key`: lists with display options; Корзина has none. */
fun listOptionsKey(list: SmartList): String? =
    when (list) {
        SmartList.INBOX -> "agenda.inbox.view"
        SmartList.WEEK -> "agenda.week.view"
        SmartList.NEXT_WEEK -> "agenda.next-week.view"
        SmartList.SOMEDAY -> "agenda.someday.view"
        SmartList.LOGBOOK -> "agenda.logbook.view"
        SmartList.TRASH -> null
    }

fun projectOptionsKey(id: String): String = "agenda.project.$id.view"

/** agenda-gpui `sort_idx` (model.rs): stable, ties keep sort_order. */
fun sortTasks(board: Board, tasks: List<Task>, key: SortKey): List<Task> =
    when (key) {
        SortKey.DEFAULT -> tasks
        SortKey.DATE -> tasks.sortedWith(compareBy({ board.dateView(it).dateKey ?: "9999" }, { it.sortOrder }))
        SortKey.PRIORITY -> tasks.sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.sortOrder })
        SortKey.TITLE -> tasks.sortedWith(compareBy({ it.title.lowercase() }, { it.sortOrder }))
    }

/** agenda-gpui board grouping (pages/board.rs); empty groups are dropped. */
fun groupTasks(board: Board, tasks: List<Task>, key: GroupKey): List<TaskGroup> {
    val groups =
        when (key) {
            GroupKey.NONE -> listOf(TaskGroup(null, tasks))
            GroupKey.PROJECT -> {
                val known = board.projects.map { it.id }.toSet()
                listOf(TaskGroup("Без проекта", tasks.filter { it.projectId == null })) +
                    board.projects.map { p -> TaskGroup(p.title, tasks.filter { it.projectId == p.id }) } +
                    TaskGroup("Проект", tasks.filter { it.projectId != null && it.projectId !in known })
            }
            GroupKey.DATE ->
                tasks
                    .map { board.dateView(it).dateKey }
                    .distinct()
                    .sortedBy { it ?: "9999" }
                    .map { k ->
                        val bucket = tasks.filter { board.dateView(it).dateKey == k }
                        TaskGroup(board.dateView(bucket.first()).label, bucket)
                    }
        }
    return groups.filter { it.tasks.isNotEmpty() }
}

fun arrangeTasks(board: Board, tasks: List<Task>, options: ListOptions): List<TaskGroup> =
    groupTasks(board, sortTasks(board, tasks, options.sort), options.group)

/** agenda-gpui `quick_matches` (app.rs): title substring, case-insensitive. */
fun searchBoard(board: Board, query: String): Pair<List<Task>, List<Project>> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList<Task>() to emptyList()
    val tasks = board.todos.filter { !it.isTrashed && it.title.lowercase().contains(q) }.take(8)
    val projects = board.projects.filter { it.title.lowercase().contains(q) }.take(5)
    return tasks to projects
}
