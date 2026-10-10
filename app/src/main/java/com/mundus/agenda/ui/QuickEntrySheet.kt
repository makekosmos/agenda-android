package com.mundus.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.QuickEntryPreview
import com.mundus.agenda.Task
import kotlinx.coroutines.delay

/** One task property row — the desktop qe_prop_row twin: label on the
 * left, current value (or a dropdown) on the right. */
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    hasValue: Boolean,
    menuContent: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
    Surface(
        onClick = { if (menuContent != null) open = true },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (hasValue) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
        if (menuContent != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                menuContent()
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.padding(horizontal = 4.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The desktop quick-entry panel (pages/quick_entry.rs): one sheet for
 * create AND edit (`qe_task` set = edit mode). Create gathers a draft —
 * parsed chips preview what agenda-core extracted; edit applies
 * date/project picks instantly and persists title/notes on save/close.
 *
 * [editing] null → create; non-null → the task snapshot being edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    board: Board,
    repository: BoardRepository?,
    editing: Task?,
    onDismiss: () -> Unit,
    run: (suspend () -> Unit) -> Unit,
    initialWeek: Boolean = false,
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }

    // Draft state (create) / current values (edit) for the two properties.
    var pickedDate by remember { mutableStateOf(editing?.scheduledDate) }
    // Desktop presets qe_week when the sheet opens on the Week route.
    var weekFlag by remember { mutableStateOf(editing?.isToday ?: initialWeek) }
    var somedayFlag by remember { mutableStateOf(editing?.isSomeday == true) }
    var projectId by remember { mutableStateOf(editing?.projectId) }
    var projectTouched by remember { mutableStateOf(false) }
    var dateTouched by remember { mutableStateOf(false) }

    var preview by remember { mutableStateOf<QuickEntryPreview?>(null) }
    var nextMonday by remember { mutableStateOf<String?>(null) }
    var dateLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        nextMonday = repository?.nextMondayKey()
    }

    // Live parse preview — the chips mirror desktop's parsed capture.
    LaunchedEffect(title, pickedDate, weekFlag, somedayFlag, projectId, projectTouched) {
        if (repository == null || editing != null) return@LaunchedEffect
        delay(120)
        preview =
            repository.quickEntryPreview(
                title,
                pickedDate,
                weekFlag,
                somedayFlag,
                projectId,
                projectTouched,
            )
        preview?.scheduledDate?.let { dateLabel = repository.dayLabel(it) }
    }
    // Until the user picks, the parsed date previews in the row (desktop
    // shows the capture the same way); an explicit choice wins over parse.
    val effectiveDate =
        pickedDate ?: preview?.scheduledDate.takeIf { editing == null && !dateTouched }

    LaunchedEffect(effectiveDate, weekFlag, somedayFlag) {
        if (repository == null) return@LaunchedEffect
        dateLabel =
            when {
                weekFlag -> "Эта неделя"
                somedayFlag -> "Потом"
                effectiveDate != null -> repository.dayLabel(effectiveDate)
                else -> null
            }
    }

    val dateValue =
        when {
            weekFlag -> "Эта неделя"
            somedayFlag -> "Потом"
            effectiveDate != null -> dateLabel ?: effectiveDate
            else -> "Пусто"
        }
    val projectValue =
        projectId?.let { pid -> board.projects.firstOrNull { it.id == pid }?.title }
            ?: "Без проекта"
    // Desktop dropdowns skip completed (status 2) projects.
    val projectChoices = board.projects.filter { it.status != 2 }

    fun dismissWithSave() {
        val t = editing
        if (t != null && (title.trim() != t.title || notes != (t.notes ?: ""))) {
            run { repository?.editTitleNotes(t, title, notes.ifBlank { null }) }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = { dismissWithSave() }) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(if (editing == null) "Новая задача" else "") },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Заметки") },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            // Parsed chips — only in create mode, like the desktop draft.
            if (editing == null) {
                val chips = buildList {
                    preview?.let { p ->
                        if (p.title.isNotBlank() && p.title != title.trim()) {
                            add("«${p.title}»")
                        }
                        if (p.isSomeday) add("Потом")
                        else if (p.isToday && p.scheduledDate == null) add("Эта неделя")
                        p.scheduledDate?.let { add(dateLabel ?: it) }
                        p.projectId?.let { pid ->
                            board.projects.firstOrNull { it.id == pid }?.let { add(it.title) }
                        }
                    }
                }
                if (chips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        chips.forEach { PreviewChip(it) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            PropertyRow(
                label = "Дата",
                value = dateValue,
                hasValue = weekFlag || somedayFlag || effectiveDate != null,
            ) {
                // Desktop DropKind::Date (edit) / QeDate (create).
                DropdownMenuItem(
                    text = { Text("Эта неделя") },
                    onClick = {
                        if (editing != null) run { repository?.moveToWeek(editing) }
                        weekFlag = true; somedayFlag = false; pickedDate = null
                        dateTouched = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Следующая неделя") },
                    onClick = {
                        if (editing != null) run { repository?.setScheduled(editing, nextMonday) }
                        weekFlag = false; somedayFlag = false; pickedDate = nextMonday
                        dateTouched = true
                    },
                )
                if (editing == null) {
                    DropdownMenuItem(
                        text = { Text("Потом") },
                        onClick = {
                            weekFlag = false; somedayFlag = true; pickedDate = null
                            dateTouched = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Без даты") },
                    onClick = {
                        if (editing != null) run { repository?.setScheduled(editing, null) }
                        weekFlag = false; somedayFlag = false; pickedDate = null
                        dateTouched = true
                    },
                )
            }
            PropertyRow(
                label = "Проект",
                value = projectValue,
                hasValue = projectId != null,
            ) {
                DropdownMenuItem(
                    text = { Text("Без проекта") },
                    onClick = {
                        if (editing != null) run { repository?.moveToProject(editing, null) }
                        projectId = null; projectTouched = true
                    },
                )
                for (p in projectChoices) {
                    DropdownMenuItem(
                        text = { Text(p.title) },
                        onClick = {
                            if (editing != null) run { repository?.moveToProject(editing, p.id) }
                            projectId = p.id; projectTouched = true
                        },
                    )
                }
            }

            if (editing == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onDismiss() }) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                run {
                                    repository?.quickEntry(
                                        title,
                                        notes.ifBlank { null },
                                        pickedDate,
                                        weekFlag,
                                        somedayFlag,
                                        projectId,
                                        projectTouched,
                                    )
                                }
                                onDismiss()
                            }
                        },
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
