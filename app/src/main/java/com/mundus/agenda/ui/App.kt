package com.mundus.agenda.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.BoardState
import com.mundus.agenda.R
import com.mundus.agenda.SmartList
import com.mundus.agenda.WriteConflict
import com.mundus.agenda.updater.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Route {
    data object Home : Route
    data class List(val list: SmartList) : Route
    data class Project(val id: String) : Route
    data object Settings : Route
    data object Statistics : Route
    data object About : Route
}

class BoardViewModel(repository: BoardRepository) : ViewModel() {
    val board: StateFlow<BoardState> =
        repository
            .boardFlow()
            // An engine that fails to open (e.g. corrupt ark.db) throws from
            // subscribe(); show the error screen instead of crashing.
            .catch { e -> emit(BoardState.Error(e.message ?: "engine error")) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, BoardState.Loading)
}

class BoardViewModelFactory(private val repository: BoardRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BoardViewModel(repository) as T
}

@Composable
fun AgendaApp(
    repository: BoardRepository,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
) {
    val viewModel: BoardViewModel = viewModel(factory = BoardViewModelFactory(repository))
    val state by viewModel.board.collectAsStateWithLifecycle()
    val c = AgendaTheme.colors
    when (val s = state) {
        is BoardState.Loading ->
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
        is BoardState.Error ->
            Box(Modifier.fillMaxSize().background(c.bg).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Не удалось открыть локальные данные: ${s.message}",
                    color = c.destructive,
                    style = AgendaType.body,
                )
            }
        is BoardState.Ready ->
            AgendaHome(s.board, repository, themeMode = themeMode, onThemeMode = onThemeMode)
    }
}

/** Animations honoring the system "remove animations" setting
 * (Animator duration scale = 0). */
@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
}

/**
 * App shell: the home page is the root (Microsoft To Do style — no
 * drawer anywhere). Routes push/pop on a small string stack; system back
 * returns home.
 */
@Composable
fun AgendaHome(
    board: Board,
    repository: BoardRepository? = null,
    initialRoute: Route = Route.Home,
    initialCreate: Boolean = false,
    initialEditTaskId: String? = null,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
) {
    var route by rememberSaveable { mutableStateOf(routeKey(initialRoute)) }
    // Direction of the last nav: 1 = drill in, -1 = back. Drives the
    // shared-axis-ish slide in the AnimatedContent below.
    var navDir by rememberSaveable { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var createOpen by rememberSaveable { mutableStateOf(initialCreate) }
    var editingTaskId by rememberSaveable { mutableStateOf(initialEditTaskId) }
    // Project rename: the project page title becomes an editable heading,
    // like desktop edit_project_title (app/projects.rs:111-114).
    var renamingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val c = AgendaTheme.colors

    // Self-update: one check ~5 s after start (cortex service.rs:275-283)
    // plus a >4h-stale re-check on resume.
    val appContext = LocalContext.current.applicationContext
    val updateManager = remember { UpdateManager(appContext) }
    LaunchedEffect(Unit) { updateManager.checkOnStart() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) updateManager.checkIfStale()
            }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            updateManager.close()
        }
    }

    fun showToast(msg: String) {
        scope.launch {
            toast = msg
            delay(2600)
            if (toast == msg) toast = null
        }
    }

    // Every write goes through this boundary: the engine write itself is
    // local (optimistic by construction), errors land as a quiet toast.
    fun run(op: suspend () -> Unit) {
        scope.launch {
            try {
                op()
            } catch (_: WriteConflict) {
                showToast("Задача изменилась на другом устройстве")
            } catch (_: Exception) {
                showToast("Не удалось сохранить")
            }
        }
    }
    fun navigate(r: Route) {
        navDir = 1
        route = routeKey(r)
    }
    fun commitRename(title: String) {
        val pid = renamingProjectId ?: return
        board.project(pid)?.let { p -> run { repository?.renameProject(p, title) } }
        renamingProjectId = null
    }
    fun back() {
        // Leaving the page commits a pending rename (desktop commits on
        // Enter/Blur — blur here is navigation away).
        if (renamingProjectId != null) commitRename(renameDraft)
        if (route != "home") {
            navDir = -1
            route = "home"
        }
    }
    fun onCreateProject() {
        scope.launch {
            try {
                val id = repository?.createProject() ?: return@launch
                renameDraft = "Новый проект"
                renamingProjectId = id
                navigate(Route.Project(id))
            } catch (_: Exception) {
                showToast("Не удалось сохранить")
            }
        }
    }

    val actions =
        TaskActions(
            onOpen = { editingTaskId = it.id },
            onToggle = { t -> run { repository?.toggleComplete(t) } },
            onTrash = { t -> run { repository?.setTrashed(t, true) } },
            onRestore = { t -> run { repository?.setTrashed(t, false) } },
            onProjectOpen = { p -> navigate(Route.Project(p.id)) },
            onProjectRename = { p ->
                renameDraft = p.title
                renamingProjectId = p.id
                navigate(Route.Project(p.id))
            },
            onProjectArchive = { p -> run { repository?.setProjectStatus(p, 2) } },
            onProjectRestore = { p -> run { repository?.setProjectStatus(p, 0) } },
            onProjectDelete = { p ->
                // Deleting the open project returns home (desktop
                // navigates to Inbox first, app/projects.rs:133-135).
                if (parseRoute(route).let { it is Route.Project && it.id == p.id }) back()
                run { repository?.deleteProject(p) }
            },
        )
    // Predictive back: gesture progress drives the same slide+fade pair the
    // committed transition runs (forward = +it/4 in, -it/6 out; back
    // reverses them). A bare KEYCODE_BACK delivers no progress events, so
    // the flow completes empty and the committed back transition runs.
    var backPreview by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = route != "home") { events ->
        var maxProgress = 0f
        try {
            events.collect { ev ->
                maxProgress = maxOf(maxProgress, ev.progress)
                backPreview = ev.progress
            }
            backPreview = 0f
            android.util.Log.d("AgendaNav", "back committed (maxProgress=$maxProgress)")
            back()
        } catch (_: kotlinx.coroutines.CancellationException) {
            android.util.Log.d("AgendaNav", "back cancelled (maxProgress=$maxProgress)")
            backPreview = 0f
        }
    }

    val current = parseRoute(route)
    val title =
        when (val r = current) {
            is Route.Home -> "Agenda"
            is Route.List -> r.list.titleRu
            is Route.Project -> board.project(r.id)?.title ?: "Проект"
            is Route.Settings -> "Настройки"
            is Route.Statistics -> "Статистика"
            is Route.About -> "О приложении"
        }
    val animate = animationsEnabled()
    val slideDur = if (animate) 240 else 0

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Shared top bar: identical title geometry on every page. On a
            // project page in rename mode the title is the desktop editable
            // heading — focused with the whole name selected.
            val renamingProject =
                (current as? Route.Project)
                    ?.takeIf { renamingProjectId == it.id }
            AgendaTopBar(
                title = title,
                onBack = if (current !is Route.Home) ::back else null,
                titleContent =
                    renamingProject?.let { r ->
                        {
                            InlineTitleEditor(
                                initial = board.project(r.id)?.title ?: renameDraft,
                                onEdit = { renameDraft = it },
                                onCommit = ::commitRename,
                            )
                        }
                    },
            )
            @Composable
            fun PageContent(r: Route) {
                when (r) {
                    is Route.Home ->
                        HomePage(
                            board = board,
                            onNavigate = ::navigate,
                            actions = actions,
                            onCreateProject = ::onCreateProject,
                            updateManager = updateManager,
                        )
                    is Route.List ->
                        TaskListScreen(board = board, list = r.list, actions = actions)
                    is Route.Settings ->
                        SettingsScreen(
                            board = board,
                            themeMode = themeMode,
                            onThemeMode = onThemeMode,
                            onAbout = { navigate(Route.About) },
                            onStatistics = { navigate(Route.Statistics) },
                        )
                    is Route.Statistics -> StatisticsScreen(board = board)
                    is Route.About -> AboutScreen(updateManager)
                    is Route.Project ->
                        ProjectScreen(
                            board = board,
                            projectId = r.id,
                            hideProjectMeta = true,
                            actions = actions,
                        )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (backPreview > 0f) {
                    BackGesturePreview(
                        progress = backPreview,
                        behind = { PageContent(Route.Home) },
                        front = { PageContent(current) },
                    )
                } else {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            if (navDir >= 0) {
                                (slideInHorizontally(tween(slideDur, easing = EaseEmphasized)) { it / 4 } +
                                    fadeIn(tween(slideDur))) togetherWith
                                    (slideOutHorizontally(tween(slideDur, easing = EaseEmphasized)) { -it / 6 } +
                                        fadeOut(tween(slideDur)))
                            } else {
                                (slideInHorizontally(tween(slideDur, easing = EaseEmphasized)) { -it / 4 } +
                                    fadeIn(tween(slideDur))) togetherWith
                                    (slideOutHorizontally(tween(slideDur, easing = EaseEmphasized)) { it / 6 } +
                                        fadeOut(tween(slideDur)))
                            }
                        },
                        label = "nav",
                    ) { r -> PageContent(r) }
                }
            }
        }

        // Desktop only offers task creation on task pages (not
        // Settings/Statistics/About).
        if (current !is Route.Settings && current !is Route.Statistics && current !is Route.About) {
            QuickEntryButton(
                onClick = { createOpen = true },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }

        AToast(
            message = toast,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }

    // Desktop quick-entry/panel: create from the + button, edit on row tap.
    if (createOpen) {
        QuickEntrySheet(
            board = board,
            repository = repository,
            editing = null,
            onDismiss = { createOpen = false },
            run = ::run,
            initialWeek = current == Route.List(SmartList.WEEK),
        )
    }
    editingTaskId?.let { id ->
        board.task(id)?.let { task ->
            QuickEntrySheet(
                board = board,
                repository = repository,
                editing = task,
                onDismiss = { editingTaskId = null },
                run = ::run,
            )
        }
    }
}

fun routeKey(route: Route): String =
    when (route) {
        is Route.Home -> "home"
        is Route.List -> "list:${route.list.name}"
        is Route.Project -> "project:${route.id}"
        is Route.Settings -> "settings"
        is Route.Statistics -> "statistics"
        is Route.About -> "about"
    }

fun parseRoute(key: String): Route =
    when (key.substringBefore(':')) {
        "settings" -> Route.Settings
        "statistics" -> Route.Statistics
        "about" -> Route.About
        "project" -> Route.Project(key.substringAfter(':'))
        "list" ->
            Route.List(
                runCatching { SmartList.valueOf(key.substringAfter(':')) }
                    .getOrDefault(SmartList.INBOX)
            )
        else -> Route.Home
    }

/** One home section group — the desktop sidebar "card" (sidebar_bg
 * surface, 8dp radius, hairline between rows). */
@Composable
private fun HomeGroup(content: @Composable () -> Unit) {
    val c = AgendaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(c.sidebarBg)
    ) {
        content()
    }
}

@Composable
private fun HomeRow(
    iconRes: Int?,
    label: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    ARow(onClick = onClick, enabled = enabled, onLongClick = onLongClick) { glyph ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                AIcon(iconRes, tint = glyph.copy(alpha = if (enabled) 0.85f else 0.5f))
            } else {
                // Desktop project row: 8px muted dot (chrome.rs sb_item None icon).
                Box(
                    Modifier
                        .size(20.dp)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(AgendaTheme.colors.mutedFg.copy(alpha = if (enabled) 0.9f else 0.4f))
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = AgendaType.body,
                color = glyph,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The home page — the desktop sidebar as a page, in its exact order:
 * nav lists (chrome.rs:375-385), active projects under «Проекты»
 * (chrome.rs:389-535), «Другое» popover items (chrome.rs:1054-1067).
 * No counts: the desktop sidebar renders none.
 */
@Composable
fun HomePage(
    board: Board,
    onNavigate: (Route) -> Unit,
    actions: TaskActions = TaskActions(),
    onCreateProject: () -> Unit = {},
    updateManager: UpdateManager? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 96.dp)
    ) {
        updateManager?.let {
            UpdateBanner(it, Modifier.padding(bottom = 8.dp))
        }
        HomeGroup {
            HomeRow(R.drawable.ic_inbox, SmartList.INBOX.titleRu) {
                onNavigate(Route.List(SmartList.INBOX))
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_calendar_01, SmartList.WEEK.titleRu) {
                onNavigate(Route.List(SmartList.WEEK))
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_calendar_02, SmartList.NEXT_WEEK.titleRu) {
                onNavigate(Route.List(SmartList.NEXT_WEEK))
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_clock_01, SmartList.SOMEDAY.titleRu) {
                onNavigate(Route.List(SmartList.SOMEDAY))
            }
        }

        // Desktop renders the «Проекты» group unconditionally with a «+»
        // button on its header (chrome.rs:457-474) — same here, including
        // the empty state.
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ASectionHeader("Проекты", Modifier.weight(1f))
            AIconButton(
                R.drawable.ic_plus,
                desc = "Новый проект",
                onClick = onCreateProject,
                modifier = Modifier.size(32.dp),
            )
        }
        val projects = board.projects.filter { it.status == 0 }
        if (projects.isNotEmpty()) {
            HomeGroup {
                projects.forEachIndexed { i, p ->
                    if (i > 0) ADivider(indent = 44.dp)
                    var menuOpen by remember(p.id) { mutableStateOf(false) }
                    Box {
                        HomeRow(
                            iconRes = null,
                            label = p.title,
                            onLongClick = { menuOpen = true },
                            onClick = { actions.onProjectOpen(p) },
                        )
                        // Desktop sidebar right-click menu (chrome.rs:184-215).
                        AMenu(open = menuOpen, onDismiss = { menuOpen = false }) {
                            AMenuItem("Переименовать") {
                                menuOpen = false
                                actions.onProjectRename(p)
                            }
                            AMenuItem("Архивировать") {
                                menuOpen = false
                                actions.onProjectArchive(p)
                            }
                            AMenuItem("Удалить", destructive = true) {
                                menuOpen = false
                                actions.onProjectDelete(p)
                            }
                        }
                    }
                }
            }
        }

        ASectionHeader("Другое")
        HomeGroup {
            HomeRow(R.drawable.ic_book_open, SmartList.LOGBOOK.titleRu) {
                onNavigate(Route.List(SmartList.LOGBOOK))
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_delete, SmartList.TRASH.titleRu) {
                onNavigate(Route.List(SmartList.TRASH))
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_settings, "Настройки") {
                onNavigate(Route.Settings)
            }
            ADivider(indent = 44.dp)
            HomeRow(R.drawable.ic_help_circle, "О приложении") {
                onNavigate(Route.About)
            }
        }
    }
}
