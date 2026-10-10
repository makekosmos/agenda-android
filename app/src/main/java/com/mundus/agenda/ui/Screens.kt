package com.mundus.agenda.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.SmartList
import com.mundus.agenda.Task

private fun emptyHint(list: SmartList): Pair<String, String> =
    when (list) {
        SmartList.INBOX -> "Входящие пустые" to "Создай любую задачу и она появится здесь"
        SmartList.WEEK -> "На этой неделе пусто" to "Назначь задачу на эту неделю"
        SmartList.NEXT_WEEK -> "На следующей неделе пусто" to "Поставь задаче дату следующей недели"
        SmartList.SOMEDAY -> "Потом пуст" to "Отложенные задачи появятся здесь"
        SmartList.LOGBOOK -> "Архив пуст" to "Завершённые задачи хранятся здесь"
        SmartList.TRASH -> "Корзина пуста" to "Удалённые задачи попадают сюда"
    }

@Composable
private fun EmptyState(list: SmartList) {
    val c = AgendaTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(list.titleRu, style = AgendaType.barTitle, color = c.fg)
        Spacer(Modifier.height(8.dp))
        Text(
            emptyHint(list).first,
            style = AgendaType.body,
            color = c.mutedFg,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            emptyHint(list).second,
            style = AgendaType.meta,
            color = c.mutedFg.copy(alpha = 0.8f),
        )
    }
}

/** Write actions a task row exposes — no-ops by default so Roborazzi
 * boards render without a repository. */
class TaskActions(
    val onOpen: (Task) -> Unit = {},
    val onToggle: (Task) -> Unit = {},
    val onTrash: (Task) -> Unit = {},
    val onRestore: (Task) -> Unit = {},
    val onMoveToWeek: (Task) -> Unit = {},
    // Project actions — the desktop sidebar right-click menu
    // (chrome.rs:184-215) and the logbook row menu (logbook.rs:39-63).
    val onProjectOpen: (com.mundus.agenda.Project) -> Unit = {},
    val onProjectRename: (com.mundus.agenda.Project) -> Unit = {},
    val onProjectArchive: (com.mundus.agenda.Project) -> Unit = {},
    val onProjectRestore: (com.mundus.agenda.Project) -> Unit = {},
    val onProjectDelete: (com.mundus.agenda.Project) -> Unit = {},
)

@Composable
private fun DateChip(text: String, overdue: Boolean) {
    val c = AgendaTheme.colors
    val color = if (overdue) c.destructive else c.mutedFg
    Text(
        text,
        style = AgendaType.label,
        color = color,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.10f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Task row in the desktop language (rows/task.rs): status ring in a 20dp
 * cell on the left grid line, one-line title, muted meta, hairline
 * separator below. Long-press opens the same context menu as desktop
 * right-click (Восстановить in trash, Удалить otherwise, «На неделю» on
 * overdue).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    board: Board,
    task: Task,
    modifier: Modifier = Modifier,
    hideProjectMeta: Boolean = false,
    actions: TaskActions = TaskActions(),
) {
    val c = AgendaTheme.colors
    val dateView = board.dateView(task)
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val done = task.isCompleted || task.isCancelled

    Box {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(if (pressed) c.fg(0.04f) else androidx.compose.ui.graphics.Color.Transparent)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { actions.onOpen(task) },
                        onLongClick = { menuOpen = true },
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusRing(task, actions.onToggle)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = AgendaType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) c.mutedFg else c.fg,
                )
                val meta = buildList {
                    val project = task.projectId?.let { board.project(it) }
                    if (!hideProjectMeta && project != null) add(project.title)
                    for (tagId in task.tagIds) board.tag(tagId)?.let { add(it.title) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = AgendaType.meta,
                        color = c.mutedFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.checklist.isNotEmpty()) {
                val doneCount = task.checklist.count { it.isCompleted }
                Text(
                    "$doneCount/${task.checklist.size}",
                    style = AgendaType.meta,
                    color = c.mutedFg,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            dateView.dateKey?.let {
                Spacer(Modifier.width(8.dp))
                DateChip(dateView.label, dateView.overdue)
            }
        }
        // Desktop right-click menu (rows/task.rs).
        AMenu(open = menuOpen, onDismiss = { menuOpen = false }) {
            if (task.isTrashed) {
                AMenuItem("Восстановить") { menuOpen = false; actions.onRestore(task) }
            } else {
                if (dateView.overdue) {
                    AMenuItem("На неделю") { menuOpen = false; actions.onMoveToWeek(task) }
                }
                AMenuItem("Удалить") { menuOpen = false; actions.onTrash(task) }
            }
        }
    }
}

/** Row hairline — desktop `border_mix(0.55)` ≈ border at 55% under the
 * title column (left of the ring cell, matching CONTENT_GRID_X). */
@Composable
private fun RowDivider() {
    ADivider(indent = 48.dp)
}

/**
 * List screen for every smart list. Logbook additionally renders the
 * desktop «Проекты» section: completed (status 2) projects below the
 * archived tasks (pages/logbook.rs).
 */
@Composable
fun TaskListScreen(board: Board, list: SmartList, actions: TaskActions = TaskActions()) {
    val c = AgendaTheme.colors
    val tasks = board.list(list)
    val archivedProjects =
        if (list == SmartList.LOGBOOK) board.projects.filter { it.status == 2 } else emptyList()
    if (tasks.isEmpty() && archivedProjects.isEmpty()) {
        EmptyState(list)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (list == SmartList.LOGBOOK && tasks.isNotEmpty()) {
            item(key = "header-tasks") { ASectionHeader("Задачи") }
        }
        items(tasks, key = { it.id }) { task ->
            TaskRow(board, task, modifier = Modifier.animateItem(), actions = actions)
            RowDivider()
        }
        if (archivedProjects.isNotEmpty()) {
            item(key = "header-projects") { ASectionHeader("Проекты") }
            items(archivedProjects, key = { it.id }) { project ->
                // Desktop logbook row menu (logbook.rs:39-63):
                // Восстановить + destructive Удалить.
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    ARow(
                        onClick = { actions.onProjectOpen(project) },
                        onLongClick = { menuOpen = true },
                    ) { glyph ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier.size(8.dp)
                                        .clip(CircleShape)
                                        .background(c.mutedFg),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                project.title,
                                style = AgendaType.body,
                                color = c.mutedFg,
                            )
                        }
                    }
                    AMenu(open = menuOpen, onDismiss = { menuOpen = false }) {
                        AMenuItem("Восстановить") {
                            menuOpen = false
                            actions.onProjectRestore(project)
                        }
                        AMenuItem("Удалить", destructive = true) {
                            menuOpen = false
                            actions.onProjectDelete(project)
                        }
                    }
                }
                RowDivider()
            }
        }
    }
}

@Composable
fun ProjectScreen(
    board: Board,
    projectId: String,
    hideProjectMeta: Boolean = false,
    actions: TaskActions = TaskActions(),
) {
    val c = AgendaTheme.colors
    val tasks = board.projectTasks[projectId].orEmpty()
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Проект пуст",
                style = AgendaType.body,
                color = c.mutedFg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Добавь задачу в этот проект",
                style = AgendaType.meta,
                color = c.mutedFg.copy(alpha = 0.8f),
            )
        }
        return
    }
    val active = tasks.filter { !it.isCompleted && !it.isCancelled }
    val done = tasks.filter { it.isCompleted || it.isCancelled }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(active, key = { it.id }) { task ->
            TaskRow(board, task, modifier = Modifier.animateItem(), hideProjectMeta = hideProjectMeta, actions = actions)
            RowDivider()
        }
        if (done.isNotEmpty()) {
            item(key = "done-header") { ASectionHeader("Завершённые") }
            items(done, key = { it.id }) { task ->
                TaskRow(board, task, modifier = Modifier.animateItem(), hideProjectMeta = hideProjectMeta, actions = actions)
                RowDivider()
            }
        }
    }
}
