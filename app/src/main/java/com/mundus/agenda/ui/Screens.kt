package com.mundus.agenda.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.R
import com.mundus.agenda.SmartList
import com.mundus.agenda.Task
import com.mundus.agenda.updater.UpdateManager
import com.mundus.agenda.updater.UpdateState
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.annotation.DrawableRes

private class EmptyHint(@DrawableRes val icon: Int, val title: String, val hint: String)

private fun emptyHint(list: SmartList): EmptyHint =
    when (list) {
        SmartList.INBOX -> EmptyHint(R.drawable.ic_inbox, "Входящие пустые", "Создай любую задачу и она появится здесь")
        SmartList.WEEK -> EmptyHint(R.drawable.ic_calendar_01, "На этой неделе пусто", "Назначь задачу на эту неделю")
        SmartList.NEXT_WEEK -> EmptyHint(R.drawable.ic_calendar_02, "На следующей неделе пусто", "Поставь задаче дату следующей недели")
        SmartList.SOMEDAY -> EmptyHint(R.drawable.ic_clock_01, "Потом пуст", "Отложенные задачи появятся здесь")
        SmartList.LOGBOOK -> EmptyHint(R.drawable.ic_book_open, "Архив пуст", "Завершённые задачи хранятся здесь")
        SmartList.TRASH -> EmptyHint(R.drawable.ic_delete, "Корзина пуста", "Удалённые задачи попадают сюда")
    }

@Composable
private fun EmptyState(list: SmartList) {
    val c = AgendaTheme.colors
    val empty = emptyHint(list)
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(empty.icon),
            contentDescription = null,
            tint = c.mutedFg.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(empty.title, style = AgendaType.body, color = c.mutedFg)
        Spacer(Modifier.height(2.dp))
        Text(
            empty.hint,
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
 * right-click (Восстановить in trash, Удалить otherwise; no
 * reschedule — date UI removed per Jack, p4-fix).
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
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomClearancePadding()) {
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

/** Settings: «Обновления» first, then one group of pages. Theme lives on
 * its own «Отображение» page. */
@Composable
fun SettingsScreen(
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onStatistics: () -> Unit = {},
    onSync: () -> Unit = {},
    onDatabase: () -> Unit = {},
    updateManager: UpdateManager? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 96.dp)
    ) {
        updateManager?.let { UpdateSection(it) }
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
            val pages =
                listOf(
                    Triple(R.drawable.ic_paint, "Отображение", onAppearance),
                    Triple(R.drawable.ic_repeat, "Синхронизация", onSync),
                    Triple(R.drawable.ic_database, "База данных", onDatabase),
                    Triple(R.drawable.ic_chart, "Статистика", onStatistics),
                    Triple(R.drawable.ic_help_circle, "О приложении", onAbout),
                )
            pages.forEachIndexed { i, (icon, label, onClick) ->
                if (i > 0) ADivider(indent = 44.dp)
                ARow(onClick = onClick) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AIcon(icon, tint = glyph.copy(alpha = 0.85f))
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = AgendaType.body, color = glyph)
                    }
                }
            }
        }
    }
}

/** «Отображение»: theme mode tiles (desktop settings.rs:100-103) and the
 * zeron palette picker. */
@Composable
fun AppearanceScreen(
    themeMode: String,
    onThemeMode: (String) -> Unit,
    palette: String = AgendaPalette,
    onPalette: (String) -> Unit = {},
) {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = bottomClearance())
    ) {
        ASectionHeader("Режим")
        SettingsGroup {
            for ((mode, label) in listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")) {
                if (mode != "system") ADivider(indent = 44.dp)
                ARow(onClick = { onThemeMode(mode) }) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = AgendaType.body,
                            color = glyph,
                            modifier = Modifier.weight(1f),
                        )
                        if (themeMode == mode) {
                            AIcon(R.drawable.ic_check, tint = c.accent)
                        }
                    }
                }
            }
        }
        ASectionHeader("Тема")
        PalettePicker(
            selected = palette,
            dark =
                when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                },
            onPick = onPalette,
        )
    }
}

/** Agenda's own palette first, then zeron's families in registry order. */
private val Palettes = listOf(AgendaPalette to "Agenda") + ZeronFamilies.map { it.id to it.name }

/** Each tile is a tiny page in that palette's colors for the current
 * light/dark mode, so the choice is visual rather than a list of names. */
@Composable
private fun PalettePicker(selected: String, dark: Boolean, onPick: (String) -> Unit) {
    val c = AgendaTheme.colors
    val listState =
        rememberLazyListState(Palettes.indexOfFirst { it.first == selected }.coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Palettes, key = { it.first }) { (id, name) ->
            val p = paletteColors(id, dark)
            val isSelected = id == selected
            Column(
                modifier =
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(id) }
                        .semantics { this.selected = isSelected },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.bg)
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) c.accent else c.border,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(width = 44.dp, height = 6.dp).clip(CircleShape).background(p.fg))
                    Box(Modifier.size(width = 60.dp, height = 5.dp).clip(CircleShape).background(p.mutedFg))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (dot in listOf(p.accent, p.success, p.warn, p.destructive)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                        }
                    }
                }
                Text(
                    name,
                    style = AgendaType.label,
                    color = if (isSelected) c.fg else c.mutedFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.group)
    ) {
        content()
    }
}

/**
 * Slim home banner — the Android mirror of the desktop update overlay
 * offer (update_overlay.rs:45-56): non-blocking, dismissible (session
 * snooze = «Позже»), never takes over the page.
 */
@Composable
fun UpdateBanner(mgr: UpdateManager, modifier: Modifier = Modifier) {
    val c = AgendaTheme.colors
    val state = mgr.state
    val (text, action) =
        when (val s = state) {
            is UpdateState.Available ->
                if (s.feed.version == mgr.snoozedVersion) return
                else "Доступно обновление ${s.feed.version}" to "Обновить"
            is UpdateState.Downloading -> "Загрузка обновления… ${s.percent}%" to null
            is UpdateState.ReadyToInstall -> "Обновление готово" to "Установить"
            else -> return
        }
    val context = androidx.compose.ui.platform.LocalContext.current
    UpdateBannerRow(
        text = text,
        action = action,
        snooze = (state as? UpdateState.Available)?.let { { mgr.snooze(it.feed.version) } },
        onAction = {
            when (val s = mgr.state) {
                is UpdateState.Available -> mgr.download(s.feed)
                is UpdateState.ReadyToInstall -> installUpdate(context, mgr, s)
                else -> {}
            }
        },
        modifier = modifier,
    )
}

/** Stateless single-row banner body — title left, compact actions right. */
@Composable
fun UpdateBannerRow(
    text: String,
    action: String?,
    snooze: (() -> Unit)?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AgendaTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.group)
                .clickable(onClick = onAction)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = AgendaType.body,
            color = c.fg,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (snooze != null) {
            BannerAction("Позже", c.mutedFg, snooze)
        }
        if (action != null) {
            BannerAction(action, c.accent, onAction)
        }
    }
}

/** Compact banner text button — one row next to the title, never wraps. */
@Composable
private fun BannerAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        style = AgendaType.body.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * About — the desktop About page (pages/settings.rs:127-191): hero +
 * the one real diagnostics check (Engine data access; the screen only
 * renders when the board loaded, so it is Ok). The update card lives in
 * Settings now (desktop keeps it on About only because About is itself
 * a settings page there — views/mod.rs:46-52).
 */
@Composable
fun AboutScreen() {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AIcon(R.drawable.ic_calendar_01, tint = c.accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Agenda", style = AgendaType.pageTitle, color = c.fg)
        Spacer(Modifier.height(4.dp))
        Text("Версия ${com.mundus.agenda.BuildConfig.VERSION_NAME}", style = AgendaType.body, color = c.mutedFg)
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Доступ к данным Agenda через Engine",
                    style = AgendaType.body,
                    color = c.fg,
                    modifier = Modifier.weight(1f),
                )
                Text("Ok", style = AgendaType.body, color = c.success)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("© Yoso Industries 2025–2026", style = AgendaType.meta, color = c.mutedFg)
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
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomClearancePadding()) {
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
