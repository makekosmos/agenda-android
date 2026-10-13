package com.mundus.agenda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.QuickEntryPreview
import com.mundus.agenda.R
import com.mundus.agenda.Task
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.mundus.agenda.SmartList

/** Icon button on the sheet's bottom bar with its popup menu anchored on
 * it — Todoist's quick-add property strip. [active] tints the glyph when
 * the property already carries a value. The popup opens above the button
 * and keeps the keyboard up (non-focusable popup) so picking a date or
 * project never dismisses the IME mid-entry. */
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
        AMenu(
            open = open,
            onDismiss = { open = false },
            focusable = false,
            anchorAbove = true,
            content = menuContent,
        )
    }
}

private enum class SheetPhase { Opening, Open, Closing }

private const val SHEET_SLIDE_MS = 285
private const val IME_START_TIMEOUT_MS = 200L
private const val IME_HIDE_TIMEOUT_MS = 600L

/** Fraction of the running (or settled) soft-keyboard inset, from the
 * IME animation's own source/target; null while no keyboard is shown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun imeProgress(): Float? {
    val d = LocalDensity.current
    val full =
        maxOf(
            WindowInsets.imeAnimationSource.getBottom(d),
            WindowInsets.imeAnimationTarget.getBottom(d),
        )
    return if (full > 0) WindowInsets.ime.getBottom(d).toFloat() / full else null
}

/**
 * The quick-entry sheet (desktop quick_entry.rs create arm). Todoist
 * layout (Jack's KOS-381 correction): the title input stays at the top,
 * a bottom icon row carries the property pickers — project, date — plus
 * «maximize», which writes the draft and opens it in the full-screen
 * task page, the same page a task-row tap drills into.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun QuickEntrySheet(
    board: Board,
    repository: BoardRepository?,
    onDismiss: () -> Unit,
    run: (suspend () -> Unit) -> Unit,
    onExpand: (Task) -> Unit = {},
    initialWeek: Boolean = false,
    hazeState: HazeState? = null,
) {
    val c = AgendaTheme.colors
    var title by remember { mutableStateOf("") }

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
                weekFlag -> SmartList.WEEK.titleRu
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
        val d = pickedDate
        val w = weekFlag
        val s = somedayFlag
        val p = projectId
        val pt = projectTouched
        run { repository?.quickEntry(t, null, d, w, s, p, pt) }
        title = ""
        preview = null
        titleFocus.requestFocus()
    }

    // Maximize: write the draft and open it in the full-screen task page —
    // the same page a task-row tap drills into.
    fun expand() {
        if (title.isBlank()) return
        val t = title
        val d = pickedDate
        val w = weekFlag
        val s = somedayFlag
        val p = projectId
        val pt = projectTouched
        run {
            repository?.quickEntry(t, null, d, w, s, p, pt)?.let(onExpand)
        }
        onDismiss()
    }

    // The sheet rides the keyboard's own inset animation: while opening
    // or closing, its offset is the IME's progress, so both move on the
    // same frames with the system curve. Without a soft keyboard
    // (hardware keyboard, tests) it falls back to its own slide.
    val ime = imeProgress()
    val imeNow = rememberUpdatedState(ime)
    var phase by remember { mutableStateOf(SheetPhase.Opening) }
    val own = remember { Animatable(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        titleFocus.requestFocus()
        keyboard?.show()
        val imeStarted =
            withTimeoutOrNull(IME_START_TIMEOUT_MS) {
                snapshotFlow { imeNow.value }.first { it != null }
            }
        if (imeStarted == null) {
            own.animateTo(1f, tween(SHEET_SLIDE_MS, easing = EaseEmphasized))
        } else {
            snapshotFlow { imeNow.value }.first { it == null || it >= 0.999f }
        }
        own.snapTo(1f)
        phase = SheetPhase.Open
    }

    fun close() {
        if (phase == SheetPhase.Closing) return
        phase = SheetPhase.Closing
        scope.launch {
            if (imeNow.value != null) {
                own.snapTo(0f)
                focusManager.clearFocus()
                keyboard?.hide()
                withTimeoutOrNull(IME_HIDE_TIMEOUT_MS) {
                    snapshotFlow { imeNow.value }.first { it == null }
                }
            } else {
                own.animateTo(0f, tween(SHEET_SLIDE_MS, easing = EaseEmphasized))
            }
            onDismiss()
        }
    }

    BackHandler(enabled = phase != SheetPhase.Closing) { close() }

    val shown =
        when (phase) {
            SheetPhase.Opening -> maxOf(own.value, ime ?: 0f)
            SheetPhase.Open -> 1f
            SheetPhase.Closing -> ime ?: own.value
        }
    val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val dismissDragPx = with(LocalDensity.current) { 96.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown }
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - shown) * size.height + dragY }
                .draggable(
                    state = rememberDraggableState { dragY = (dragY + it).coerceAtLeast(0f) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (dragY > dismissDragPx || velocity > 1500f) {
                            close()
                        } else {
                            animate(dragY, 0f, animationSpec = tween(SHEET_SLIDE_MS, easing = EaseEmphasized)) { v, _ ->
                                dragY = v
                            }
                        }
                    },
                )
                .clip(sheetShape)
                // Opaque base: without RenderEffect (API < 31) haze only tints, and the bottom bar would show through.
                .background(c.popover)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(hazeState, style = HazeMaterials.thick(containerColor = c.popover))
                    } else {
                        Modifier
                    }
                )
                .border(0.5.dp, c.border, sheetShape)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 2.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.border)
            )
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                // Title first — a large, comfortable input line at the top.
                SheetField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Новая задача",
                    style = AgendaType.input,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    // Plain text keyboard so the user can still switch the
                    // input language (KOS-394). Autocorrect is off in
                    // SheetField, which keeps the crooked autocomplete
                    // artifact from p9 item 3 away.
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                    keyboardActions =
                        androidx.compose.foundation.text.KeyboardActions(
                            onDone = { createAndContinue() }
                        ),
                    focusRequester = titleFocus,
                )

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
                        AMenuItem("Без проекта", leading = { NoneIcon() }) {
                            projectId = null; projectTouched = true
                        }
                        // Desktop dropdowns skip completed (status 2) projects.
                        for (p in board.projects.filter { it.status != 2 }) {
                            AMenuItem(p.title, leading = { ProjectDot() }) {
                                projectId = p.id; projectTouched = true
                            }
                        }
                    }
                    SheetIconMenu(
                        icon = R.drawable.ic_calendar_02,
                        desc = "Дата",
                        active = dateSet,
                    ) {
                        AMenuItem(SmartList.WEEK.titleRu, leading = { AIcon(R.drawable.ic_calendar_01, tint = AgendaTheme.colors.fg(0.6f)) }) {
                            weekFlag = true; somedayFlag = false; pickedDate = null
                            dateTouched = true
                        }
                        AMenuItem(SmartList.NEXT_WEEK.titleRu, leading = { AIcon(R.drawable.ic_calendar_02, tint = AgendaTheme.colors.fg(0.6f)) }) {
                            weekFlag = false; somedayFlag = false; pickedDate = nextMonday
                            dateTouched = true
                        }
                        AMenuItem("Потом", leading = { AIcon(R.drawable.ic_clock_01, tint = AgendaTheme.colors.fg(0.6f)) }) {
                            weekFlag = false; somedayFlag = true; pickedDate = null
                            dateTouched = true
                        }
                        AMenuItem("Без даты", leading = { NoneIcon() }) {
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
                            modifier = Modifier.padding(start = 4.dp).weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
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
}
