package com.mundus.agenda.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

private fun tagColor(name: String): Color =
    when (name) {
        "red" -> Color(0xFFCE222C)
        "blue" -> Color(0xFF3B82F6)
        "green" -> Color(0xFF03A14A)
        "yellow" -> Color(0xFFE58212)
        else -> Color(0xFF8D8A94)
    }

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

@Composable
private fun CheckRing(task: Task) {
    val done = task.isCompleted
    val canceled = task.isCancelled
    val border =
        when {
            canceled -> MaterialTheme.colorScheme.onSurfaceVariant
            done -> AgendaTheme.extra.success
            task.priority >= 3 -> MaterialTheme.colorScheme.error
            task.priority == 2 -> AgendaTheme.extra.warn
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier.size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, border, CircleShape)
                .then(
                    if (done)
                        Modifier.background(AgendaTheme.extra.success, CircleShape)
                    else Modifier,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp),
            )
        }
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
fun TaskListScreen(board: Board, list: SmartList, onTaskClick: (String) -> Unit) {
    val tasks = board.list(list)
    if (tasks.isEmpty()) {
        EmptyState(list)
        return
    }
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
