package com.mundus.agenda.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.Task
import com.mundus.agenda.SmartList

/** Desktop task-panel title (quick_entry/fields.rs:16-18): 24px/32 bold. */
private val TaskTitleStyle =
    AgendaType.pageTitle.copy(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
    )

/**
 * Full-screen task page — the drill-in every task row opens and the
 * sheet's «maximize» lands on. Layout mirrors the agenda-gpui task panel
 * (pages/quick_entry.rs:229-266): an H1 title field on top
 * (fields.rs:5-25), then the Дата and Проект property rows
 * (quick_entry.rs:241-253), then notes filling the remaining page like a
 * free-form body (fields.rs:27-50).
 *
 * Property picks apply instantly (desktop edit mode). Title/notes persist
 * on Done / navigation away (desktop `panel_persist`); a live board
 * refresh never clobbers in-flight typing because drafts key off
 * `task.id` once, like `input_task` reseed in quick_entry.rs:25-37.
 */
@Composable
fun TaskScreen(
    board: Board,
    taskId: String,
    repository: BoardRepository?,
    run: (suspend () -> Unit) -> Unit,
    fallback: Task? = null,
) {
    val c = AgendaTheme.colors
    val task = board.task(taskId) ?: fallback?.takeIf { it.id == taskId }
    if (task == null) {
        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
            Text("Задача не найдена", style = AgendaType.body, color = c.mutedFg)
        }
        return
    }

    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes ?: "") }
    var nextMonday by remember { mutableStateOf<String?>(null) }
    var dateLabel by remember(task.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) { nextMonday = repository?.nextMondayKey() }

    val dateKey = task.deadline ?: task.scheduledDate
    LaunchedEffect(task.id, dateKey, task.isToday, task.isSomeday) {
        if (repository == null) return@LaunchedEffect
        dateLabel =
            when {
                task.isToday && task.scheduledDate == null -> SmartList.WEEK.titleRu
                task.isSomeday -> "Потом"
                dateKey == nextMonday -> SmartList.NEXT_WEEK.titleRu
                dateKey != null -> repository.dayLabel(dateKey)
                else -> null
            }
    }
    val dateValue = dateLabel ?: "Пусто"

    fun persist() {
        if (title.trim() != task.title || notes != (task.notes ?: "")) {
            run { repository?.editTitleNotes(task, title.trim(), notes.ifBlank { null }) }
        }
    }
    DisposableEffect(task.id) { onDispose { persist() } }

    Column(Modifier.fillMaxSize()) {
        // Title — the Linear-style page heading.
        SheetField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Название задачи",
            style = TaskTitleStyle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
            PropertyRow(
                label = "Дата",
                value = dateValue,
                hasValue = task.isToday || task.isSomeday || dateKey != null,
            ) {
                // Desktop DropKind::Date (dropdown.rs:44-75).
                AMenuItem(SmartList.WEEK.titleRu) {
                    run { repository?.moveToWeek(task) }
                }
                AMenuItem(SmartList.NEXT_WEEK.titleRu) {
                    run { repository?.setScheduled(task, nextMonday) }
                }
                AMenuItem("Без даты") {
                    run { repository?.setScheduled(task, null) }
                }
            }
            PropertyRow(
                label = "Проект",
                value =
                    task.projectId?.let { pid ->
                        board.projects.firstOrNull { it.id == pid }?.title
                    } ?: "Без проекта",
                hasValue = task.projectId != null,
            ) {
                AMenuItem("Без проекта") {
                    run { repository?.moveToProject(task, null) }
                }
                for (p in board.projects.filter { it.status != 2 }) {
                    AMenuItem(p.title) {
                        run { repository?.moveToProject(task, p.id) }
                    }
                }
            }
        }
        // Notes — free-form page body under the properties, filling the
        // remaining height (fields.rs:27-50).
        SheetField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Заметки",
            style = AgendaType.body,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).weight(1f),
        )
    }
}
