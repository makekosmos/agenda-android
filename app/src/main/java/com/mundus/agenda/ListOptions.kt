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

/** agenda-gpui board group order (pages/board.rs), headerless: «Без
 * проекта» first, then projects in order, orphans last; dates ascending,
 * undated last. Stable, so the sort holds inside each run. */
fun groupTasks(board: Board, tasks: List<Task>, key: GroupKey): List<Task> =
    when (key) {
        GroupKey.NONE -> tasks
        GroupKey.PROJECT -> {
            val rank = board.projects.withIndex().associate { (i, p) -> p.id to i + 1 }
            tasks.sortedBy { t -> t.projectId?.let { rank[it] ?: Int.MAX_VALUE } ?: 0 }
        }
        GroupKey.DATE -> tasks.sortedBy { board.dateView(it).dateKey ?: "9999" }
    }

fun arrangeTasks(board: Board, tasks: List<Task>, options: ListOptions): List<Task> =
    groupTasks(board, sortTasks(board, tasks, options.sort), options.group)

/** Every non-trashed task whose title contains the query, case-insensitive;
 * open tasks first, then completed or cancelled ones. */
fun searchTasks(board: Board, query: String): List<Task> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return board.todos
        .filter { !it.isTrashed && it.title.lowercase().contains(q) }
        .sortedBy { it.isCompleted || it.isCancelled }
}
