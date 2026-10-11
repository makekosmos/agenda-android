package com.mundus.agenda.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.QuickEntryPreview
import com.mundus.agenda.R
import com.mundus.agenda.Task
import kotlinx.coroutines.delay

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

/** Icon button on the sheet's bottom bar with its popup menu anchored on
 * it — Todoist's quick-add property strip. [active] tints the glyph when
 * the property already carries a value. */
@Composable
private fun SheetIconMenu(
    icon: Int,
    desc: String,
    active: Boolean,
    menuContent: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AIconButton(
            icon,
            desc = desc,
            onClick = { open = true },
            tint = if (active) AgendaTheme.colors.accent else AgendaTheme.colors.fg(0.6f),
        )
        AMenu(open = open, onDismiss = { open = false }, content = menuContent)
    }
}

/**
 * The quick-entry sheet (desktop quick_entry.rs create arm). Todoist
 * layout (Jack's KOS-381 correction): the title input stays at the top,
 * a bottom icon row carries the property pickers — project, date — plus
 * «maximize», which writes the draft and opens it in the full-screen
 * task page, the same page a task-row tap drills into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    board: Board,
    repository: BoardRepository?,
    onDismiss: () -> Unit,
    run: (suspend () -> Unit) -> Unit,
    onExpand: (Task) -> Unit = {},
    initialWeek: Boolean = false,
) {
    val c = AgendaTheme.colors
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Draft values for the two properties. Desktop QeDate offers
    // Эта неделя / Следующая неделя / Потом / Без даты (dropdown.rs:76-113);
    // typed text still parses via the engine preview.
    var pickedDate by remember { mutableStateOf<String?>(null) }
    // Desktop presets qe_week when the sheet opens on the Week route.
    var weekFlag by remember { mutableStateOf(initialWeek) }
    var somedayFlag by remember { mutableStateOf(false) }
    var projectId by remember { mutableStateOf<String?>(null) }
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
        if (repository == null) return@LaunchedEffect
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
    // Until the user picks, the parsed date previews (desktop shows the
    // capture the same way); an explicit choice wins over parse.
    val effectiveDate = pickedDate ?: preview?.scheduledDate.takeIf { !dateTouched }

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

    val dateSet = weekFlag || somedayFlag || effectiveDate != null

    // The title field gets focus and the keyboard on open — quick entry
    // starts typing immediately (Microsoft To Do / Todoist behavior).
    val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Enter/Done: write the task and keep the sheet open, fields cleared,
    // title still focused — rapid multi-entry like Todoist / Microsoft
    // To Do (Jack's p4 requirement; desktop's panel closes, but the sheet
    // staying open is the phone flow).
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

    // Maximize: write the draft and open it in the full-screen task page —
    // the same page a task-row tap drills into.
    fun expand() {
        if (title.isBlank()) return
        val t = title
        val n = notes.ifBlank { null }
        val d = pickedDate
        val w = weekFlag
        val s = somedayFlag
        val p = projectId
        val pt = projectTouched
        run {
            repository?.quickEntry(t, n, d, w, s, p, pt)?.let(onExpand)
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        Column(modifier = Modifier.imePadding().padding(bottom = 4.dp)) {
            // Title first — a large, comfortable input line at the top.
            SheetField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Новая задача",
                style = AgendaType.input,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onDone = { createAndContinue() }
                    ),
                focusRequester = titleFocus,
            )
            LaunchedEffect(Unit) {
                titleFocus.requestFocus()
                keyboard?.show()
            }
            SheetField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Заметки",
                style = AgendaType.inputMeta,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

            // Parsed chips, like the desktop draft preview.
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    chips.forEach { PreviewChip(it) }
                }
            }

            ADivider(modifier = Modifier.padding(horizontal = 12.dp))
            // Bottom icon row: property pickers left, actions right.
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetIconMenu(
                    icon = R.drawable.ic_folder,
                    desc = "Проект",
                    active = projectId != null,
                ) {
                    AMenuItem("Без проекта") {
                        projectId = null; projectTouched = true
                    }
                    // Desktop dropdowns skip completed (status 2) projects.
                    for (p in board.projects.filter { it.status != 2 }) {
                        AMenuItem(p.title) {
                            projectId = p.id; projectTouched = true
                        }
                    }
                }
                SheetIconMenu(
                    icon = R.drawable.ic_calendar_02,
                    desc = "Дата",
                    active = dateSet,
                ) {
                    AMenuItem("Эта неделя") {
                        weekFlag = true; somedayFlag = false; pickedDate = null
                        dateTouched = true
                    }
                    AMenuItem("Следующая неделя") {
                        weekFlag = false; somedayFlag = false; pickedDate = nextMonday
                        dateTouched = true
                    }
                    AMenuItem("Потом") {
                        weekFlag = false; somedayFlag = true; pickedDate = null
                        dateTouched = true
                    }
                    AMenuItem("Без даты") {
                        weekFlag = false; somedayFlag = false; pickedDate = null
                        dateTouched = true
                    }
                }
                if (dateSet || projectId != null) {
                    Text(
                        buildList {
                            if (dateSet) add(dateLabel ?: "")
                            projectId?.let { pid ->
                                board.projects.firstOrNull { it.id == pid }?.let { add(it.title) }
                            }
                        }.filter { it.isNotBlank() }.joinToString(" · "),
                        style = AgendaType.meta,
                        color = c.mutedFg,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                val canSave = title.isNotBlank()
                AIconButton(
                    R.drawable.ic_maximize,
                    desc = "Открыть полностью",
                    onClick = ::expand,
                    enabled = canSave,
                    tint = c.fg(0.6f),
                )
                AIconButton(
                    R.drawable.ic_arrow_up,
                    desc = "Сохранить",
                    onClick = { createAndContinue() },
                    enabled = canSave,
                    tint = c.accent,
                )
            }
        }
    }
}
