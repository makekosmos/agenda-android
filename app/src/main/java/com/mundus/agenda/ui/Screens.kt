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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.R
import com.mundus.agenda.SmartList
import com.mundus.agenda.Task
import com.mundus.agenda.updater.UpdateManager
import com.mundus.agenda.updater.UpdateState

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

/**
 * Settings — only what desktop has AND a phone can back
 * (chrome.rs:295-372 settings sidebar):
 * «Отображение» theme tiles (settings.rs:100-103), «Статистика» counters
 * (statistics.rs:59-63). VSync / dev tools / hotkeys are desktop-only —
 * no row at all.
 */
@Composable
fun SettingsScreen(
    board: Board,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    onAbout: () -> Unit,
    onStatistics: () -> Unit = {},
) {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 96.dp)
    ) {
        ASectionHeader("Отображение")
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
        // Statistics is its own page like desktop (settings sidebar →
        // Route::Statistics, chrome.rs:335-346) — no counters here.
        ASectionHeader("Другое")
        SettingsGroup {
            ARow(onClick = onStatistics) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AIcon(R.drawable.ic_chart, tint = glyph.copy(alpha = 0.85f))
                    Spacer(Modifier.width(12.dp))
                    Text("Статистика", style = AgendaType.body, color = glyph)
                }
            }
            ADivider(indent = 44.dp)
            ARow(onClick = onAbout) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AIcon(R.drawable.ic_help_circle, tint = glyph.copy(alpha = 0.85f))
                    Spacer(Modifier.width(12.dp))
                    Text("О приложении", style = AgendaType.body, color = glyph)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.sidebarBg)
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.sidebarBg)
    ) {
        ARow(
            onClick = {
                when (val s = mgr.state) {
                    is UpdateState.Available -> mgr.download(s.feed)
                    is UpdateState.ReadyToInstall -> installUpdate(context, mgr, s)
                    else -> {}
                }
            }
        ) { glyph ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    style = AgendaType.body,
                    color = glyph,
                    modifier = Modifier.weight(1f),
                )
                if (action != null) Text(action, style = AgendaType.body, color = c.accent)
            }
        }
        if (state is UpdateState.Available) {
            ADivider(indent = 16.dp)
            ARow(onClick = { mgr.snooze(state.feed.version) }) { glyph ->
                Text("Позже", style = AgendaType.body, color = glyph)
            }
        }
    }
}

/** Hands the verified APK to the system installer; routes through the
 * unknown-sources permission screen first when needed (API 26+). */
private fun installUpdate(
    context: android.content.Context,
    mgr: UpdateManager,
    state: UpdateState.ReadyToInstall,
) {
    if (mgr.canRequestInstalls()) {
        context.startActivity(mgr.installIntent(state.file))
    } else {
        mgr.openInstallPermissionSettings()
    }
}

/** One-line status for the About card — mirrors the status JSON states
 * (state.rs:56-76). */
private fun updateStatusLine(state: UpdateState): String =
    when (val s = state) {
        is UpdateState.Idle -> "Не проверялось"
        is UpdateState.Checking -> "Проверка…"
        is UpdateState.Available -> "Доступно обновление ${s.feed.version}"
        is UpdateState.Downloading -> "Загрузка… ${s.percent}%"
        is UpdateState.ReadyToInstall -> "Готово к установке"
        is UpdateState.NotAvailable -> "Актуальная версия"
        is UpdateState.Error -> s.message
    }

/**
 * About — the desktop About page (pages/settings.rs:127-191): hero +
 * the one real diagnostics check (Engine data access; the screen only
 * renders when the board loaded, so it is Ok) + the update card
 * (views/updates.rs:8-55).
 */
@Composable
fun AboutScreen(updateManager: UpdateManager? = null) {
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
        updateManager?.let { mgr ->
            val context = androidx.compose.ui.platform.LocalContext.current
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        updateStatusLine(mgr.state),
                        style = AgendaType.body,
                        color = c.fg,
                        modifier = Modifier.weight(1f),
                    )
                }
                ADivider(indent = 16.dp)
                ARow(
                    onClick = {
                        when (val s = mgr.state) {
                            is UpdateState.Available -> mgr.download(s.feed)
                            is UpdateState.ReadyToInstall -> installUpdate(context, mgr, s)
                            else -> mgr.check()
                        }
                    }
                ) { glyph ->
                    Text(
                        when (mgr.state) {
                            is UpdateState.Available -> "Скачать"
                            is UpdateState.ReadyToInstall -> "Установить"
                            else -> "Проверить обновления"
                        },
                        style = AgendaType.body,
                        color = glyph,
                    )
                }
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
