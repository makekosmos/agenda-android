package com.mundus.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.QuickEntryPreview
import com.mundus.agenda.Task
import kotlinx.coroutines.delay

/** Quiet sheet button — text-only, press dims to fg 8% like every other
 * flat control. [primary] uses accent text (restrained, no fill). */
@Composable
private fun SheetButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = AgendaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by
        animateColorAsState(
            if (pressed) c.fg(0.08f) else Color.Transparent,
            animationSpec = tween(120, easing = EaseStandard),
            label = "sheet-btn",
        )
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style =
                AgendaType.body.copy(
                    fontWeight =
                        if (primary) androidx.compose.ui.text.font.FontWeight.SemiBold
                        else androidx.compose.ui.text.font.FontWeight.Normal
                ),
            color = if (primary) c.accent else c.mutedFg,
        )
    }
}

/** One task property row — the desktop qe_prop_row twin: label on the
 * left, current value (or a dropdown) on the right. */
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    hasValue: Boolean,
    menuContent: (@Composable () -> Unit)? = null,
) {
    val c = AgendaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        ARow(
            onClick = { if (menuContent != null) open = true },
            enabled = menuContent != null,
            horizontalPadding = 16.dp,
            verticalPadding = 11.dp,
            minHeight = 44.dp,
        ) { _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = AgendaType.body,
                    color = c.mutedFg,
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    value,
                    style = AgendaType.body,
                    color = if (hasValue) c.fg else c.mutedFg,
                )
            }
        }
        if (menuContent != null) {
            AMenu(open = open, onDismiss = { open = false }, content = menuContent)
        }
    }
}

/** Parsed-input preview chip — desktop `qe_chip` (qe_chip_bg/qe_chip_fg). */
@Composable
private fun PreviewChip(text: String) {
    val c = AgendaTheme.colors
    Text(
        text,
        style = AgendaType.label,
        color = c.qeChipFg,
        maxLines = 1,
        modifier =
            Modifier
                .padding(horizontal = 4.dp)
                .background(c.qeChipBg, RoundedCornerShape(6.dp))
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
    val c = AgendaTheme.colors
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }

    // Draft state (create) / current values (edit) for the two properties.
    // Desktop QeDate offers Эта неделя / Следующая неделя / Потом / Без даты
    // (dropdown.rs); typed text still parses via the engine preview.
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
        if (title.isBlank() && !weekFlag && !somedayFlag && projectId == null &&
            pickedDate == null
        ) {
            preview = null
            return@LaunchedEffect
        }
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

    // The title field gets focus and the keyboard on open — quick entry
    // starts typing immediately (Microsoft To Do / Todoist behavior).
    val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun dismissWithSave() {
        val t = editing
        if (t != null && (title.trim() != t.title || notes != (t.notes ?: ""))) {
            run { repository?.editTitleNotes(t, title, notes.ifBlank { null }) }
        }
        onDismiss()
    }

    // Enter/Done in create mode: write the task and keep the sheet open,
    // fields cleared, title still focused — rapid multi-entry like
    // Todoist / Microsoft To Do (Jack's p4 requirement; desktop's panel
    // closes, but the sheet staying open is the phone flow).
    fun createAndContinue() {
        if (title.isBlank()) return
        // Snapshot before run{}: the lambda executes on the next main-loop
        // turn, after the fields below are already cleared.
        val t = title
        val n = notes.ifBlank { null }
        val d = pickedDate
        val w = weekFlag
        val s = somedayFlag
        val p = projectId
        val pt = projectTouched
        run { repository?.quickEntry(t, n, d, w, s, p, pt) }
        title = ""
        notes = ""
        preview = null
        titleFocus.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithSave() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.popover,
        contentColor = c.fg,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 2.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.border)
            )
        },
    ) {
        Column(modifier = Modifier.imePadding().padding(bottom = 8.dp)) {
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        chips.forEach { PreviewChip(it) }
                    }
                }
            }

            ADivider(modifier = Modifier.padding(horizontal = 12.dp))
            PropertyRow(
                label = "Дата",
                value = dateValue,
                hasValue = weekFlag || somedayFlag || effectiveDate != null,
            ) {
                // Desktop DropKind::Date (edit) / QeDate (create).
                AMenuItem("Эта неделя") {
                    if (editing != null) run { repository?.moveToWeek(editing) }
                    weekFlag = true; somedayFlag = false; pickedDate = null
                    dateTouched = true
                }
                AMenuItem("Следующая неделя") {
                    if (editing != null) run { repository?.setScheduled(editing, nextMonday) }
                    weekFlag = false; somedayFlag = false; pickedDate = nextMonday
                    dateTouched = true
                }
                if (editing == null) {
                    AMenuItem("Потом") {
                        weekFlag = false; somedayFlag = true; pickedDate = null
                        dateTouched = true
                    }
                }
                AMenuItem("Без даты") {
                    if (editing != null) run { repository?.setScheduled(editing, null) }
                    weekFlag = false; somedayFlag = false; pickedDate = null
                    dateTouched = true
                }
            }
            PropertyRow(
                label = "Проект",
                value = projectValue,
                hasValue = projectId != null,
            ) {
                AMenuItem("Без проекта") {
                    if (editing != null) run { repository?.moveToProject(editing, null) }
                    projectId = null; projectTouched = true
                }
                for (p in projectChoices) {
                    AMenuItem(p.title) {
                        if (editing != null) run { repository?.moveToProject(editing, p.id) }
                        projectId = p.id; projectTouched = true
                    }
                }
            }
            ADivider(modifier = Modifier.padding(horizontal = 12.dp))

            Spacer(Modifier.height(6.dp))
            SheetField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Заметки",
                style = AgendaType.inputMeta,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Title input pinned at the bottom, right above the keyboard
            // (Todoist-style quick add).
            SheetField(
                value = title,
                onValueChange = { title = it },
                placeholder = if (editing == null) "Новая задача" else "",
                style = AgendaType.input,
                modifier = Modifier.padding(horizontal = 16.dp),
                singleLine = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (editing == null) createAndContinue()
                            else dismissWithSave()
                        }
                    ),
                focusRequester = titleFocus,
            )
            // After the field exists: focus it and show the keyboard.
            LaunchedEffect(Unit) {
                titleFocus.requestFocus()
                keyboard?.show()
            }

            if (editing == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetButton("Отмена") { onDismiss() }
                    Spacer(Modifier.width(4.dp))
                    SheetButton("Сохранить", primary = true) { createAndContinue() }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
