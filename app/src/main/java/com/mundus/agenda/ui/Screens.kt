package com.mundus.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.SmartList
import com.mundus.agenda.Task

private fun emptyHint(list: SmartList): Pair<String, String> =
    when (list) {
        SmartList.INBOX -> "Входящие пустые" to "Создай любую задачу и она появится здесь"
        SmartList.TODAY -> "Сегодня пусто" to "Назначь задачу на сегодня"
        SmartList.WEEK -> "На этой неделе пусто" to "Назначь задачу на эту неделю"
        SmartList.NEXT_WEEK -> "На следующей неделе пусто" to "Поставь задаче дату следующей недели"
        SmartList.SOMEDAY -> "Потом пуст" to "Отложенные задачи появятся здесь"
        SmartList.LOGBOOK -> "Архив пуст" to "Завершённые задачи хранятся здесь"
        SmartList.TRASH -> "Корзина пуста" to "Удалённые задачи попадают сюда"
    }

@Composable
private fun EmptyState(list: SmartList) {
    val (title, hint) = emptyHint(list)
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/**
 * Desktop `status_ring` (widgets.rs): Done = accent fill + check in
 * accent-fg, Canceled = muted ring + ×, Started = warn ring + half fill,
 * everything else a plain muted ring.
 */
@Composable
private fun CheckRing(task: Task) {
    val status =
        when {
            task.isCompleted -> "done"
            task.isCancelled -> "canceled"
            task.status == "started" -> "started"
            else -> "open"
        }
    val warnColor = AgendaTheme.extra.warn
    when (status) {
        "done" ->
            Box(
                modifier =
                    Modifier.size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        "canceled" ->
            Box(
                modifier =
                    Modifier.size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        "started" ->
            Canvas(Modifier.size(18.dp)) {
                val r = size.minDimension / 2
                drawCircle(
                    color = warnColor,
                    style = Stroke(width = 1.5.dp.toPx()),
                    radius = r - 0.75.dp.toPx(),
                )
                drawArc(
                    color = warnColor,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        center.x - r * 0.77f, center.y - r * 0.77f
                    ),
                    size = androidx.compose.ui.geometry.Size(r * 1.54f, r * 1.54f),
                )
            }
        else ->
            Box(
                modifier =
                    Modifier.size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun TaskRow(
    board: Board,
    task: Task,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hideProjectMeta: Boolean = false,
) {
    val dateView = board.dateView(task)
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckRing(task)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration =
                        if (task.isCompleted || task.isCancelled) TextDecoration.LineThrough
                        else null,
                    color =
                        if (task.isCompleted || task.isCancelled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    val project = task.projectId?.let { board.project(it) }
                    if (!hideProjectMeta && project != null) add(project.title)
                    for (tagId in task.tagIds) board.tag(tagId)?.let { add(it.title) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.checklist.isNotEmpty()) {
                val doneCount = task.checklist.count { it.isCompleted }
                Text(
                    "$doneCount/${task.checklist.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            dateView.dateKey?.let {
                Spacer(Modifier.width(8.dp))
                Chip(
                    dateView.label,
                    if (dateView.overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * List screen for every smart list. Logbook additionally renders the
 * desktop «Проекты» section: completed (status 2) projects below the
 * archived tasks (pages/logbook.rs).
 */
@Composable
fun TaskListScreen(board: Board, list: SmartList, onTaskClick: (String) -> Unit) {
    val tasks = board.list(list)
    val archivedProjects =
        if (list == SmartList.LOGBOOK) board.projects.filter { it.status == 2 } else emptyList()
    if (tasks.isEmpty() && archivedProjects.isEmpty()) {
        EmptyState(list)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (list == SmartList.LOGBOOK && tasks.isNotEmpty()) {
            item(key = "header-tasks") { SectionHeader("Задачи") }
        }
        items(tasks, key = { it.id }) { task ->
            TaskRow(board, task, onClick = { onTaskClick(task.id) })
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 50.dp),
            )
        }
        if (archivedProjects.isNotEmpty()) {
            item(key = "header-projects") { SectionHeader("Проекты") }
            items(archivedProjects, key = { it.id }) { project ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        project.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 50.dp),
                )
            }
        }
    }
}

@Composable
fun ProjectScreen(
    board: Board,
    projectId: String,
    hideProjectMeta: Boolean = false,
    onTaskClick: (String) -> Unit,
) {
    val tasks = board.projectTasks[projectId].orEmpty()
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Проект пуст",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Добавь задачу в этот проект",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        return
    }
    val active = tasks.filter { !it.isCompleted && !it.isCancelled }
    val done = tasks.filter { it.isCompleted || it.isCancelled }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(active, key = { it.id }) { task ->
            TaskRow(board, task, onClick = { onTaskClick(task.id) }, hideProjectMeta = hideProjectMeta)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 50.dp),
            )
        }
        if (done.isNotEmpty()) {
            item(key = "done-header") {
                Text(
                    "Завершённые",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            items(done, key = { it.id }) { task ->
                TaskRow(board, task, onClick = { onTaskClick(task.id) }, hideProjectMeta = hideProjectMeta)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 50.dp),
                )
            }
        }
    }
}

@Composable
fun TagScreen(board: Board, tagId: String, onTaskClick: (String) -> Unit) {
    val tasks = board.tagTasks[tagId].orEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(board, task, onClick = { onTaskClick(task.id) })
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 50.dp),
            )
        }
    }
}
