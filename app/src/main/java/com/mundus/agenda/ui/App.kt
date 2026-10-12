package com.mundus.agenda.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import com.mundus.agenda.Task
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.mundus.agenda.WriteConflict
import com.mundus.agenda.updater.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Route {
    data object Other : Route
    data class List(val list: SmartList) : Route
    data class Project(val id: String) : Route
    data class Task(val id: String) : Route
    data object Settings : Route
    data object Sync : Route
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

/** Bottom-bar tabs in Jack's Linear-style order (p9): week, next week,
 * inbox, then the page menu button that opens [PICKER_PAGES]. The tab
 * bar carries a separate «+» create button. */
private val TAB_WEEK = "list:WEEK"
private val TAB_NEXT_WEEK = "list:NEXT_WEEK"
private val TAB_INBOX = "list:INBOX"
private val TAB_KEYS = listOf(TAB_WEEK, TAB_NEXT_WEEK, TAB_INBOX)
private val PICKER_BUTTON = TabItem("menu", R.drawable.ic_grid, "Страницы")

private val FIXED_TABS =
    listOf(
        TabItem(TAB_WEEK, R.drawable.ic_calendar_01, "На этой неделе"),
        TabItem(TAB_NEXT_WEEK, R.drawable.ic_calendar_02, "На следующей неделе"),
        TabItem(TAB_INBOX, R.drawable.ic_inbox, SmartList.INBOX.titleRu),
    )

/** The menu behind the last bar button, like Linear's. While one of these
 * pages is open the button wears its glyph under the lens. */
private val PICKER_PAGES =
    listOf(
        TabItem("other", R.drawable.ic_folder, "Проекты"),
        TabItem("list:SOMEDAY", R.drawable.ic_clock_01, SmartList.SOMEDAY.titleRu),
        TabItem("list:LOGBOOK", R.drawable.ic_book_open, SmartList.LOGBOOK.titleRu),
        TabItem("list:TRASH", R.drawable.ic_delete, SmartList.TRASH.titleRu),
        TabItem("statistics", R.drawable.ic_chart, "Статистика"),
        TabItem("settings", R.drawable.ic_settings, "Настройки"),
    )

private fun isRootKey(route: String) = route in TAB_KEYS || PICKER_PAGES.any { it.key == route }

private fun isTabRoot(route: String, stack: List<String>) = stack.isEmpty() && isRootKey(route)

/**
 * App shell: a Linear-style bottom bar with 4 tabs + a separate create
 * button is the root; «На этой неделе» opens by default. Drill-ins push
 * on a small string stack; system back at a tab root exits the app.
 */
@Composable
fun AgendaHome(
    board: Board,
    repository: BoardRepository? = null,
    initialRoute: Route = Route.List(SmartList.WEEK),
    initialCreate: Boolean = false,
    initialEditTaskId: String? = null,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
) {
    val initialKey = routeKey(initialEditTaskId?.let { Route.Task(it) } ?: initialRoute)
    var route by rememberSaveable { mutableStateOf(initialKey) }
    // Drill-in stack of route keys; tab switches replace `route` and
    // clear it, so back at a tab root falls through to the system
    // (exits the app). A non-tab initial route (deep link, tests) gets
    // the week tab under it so back has somewhere to land.
    var stack by rememberSaveable {
        mutableStateOf(if (isRootKey(initialKey)) emptyList() else listOf(TAB_WEEK))
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var navMotion by remember { mutableStateOf(NavMotion.Tab) }
    val picker = rememberPagePickerState(PICKER_PAGES.size)
    var createOpen by rememberSaveable { mutableStateOf(initialCreate) }
    // The just-created task a sheet «maximize» navigates to — the board
    // flow may lag one emission behind, so the page renders this
    // snapshot until the id shows up in `board`.
    var createdTaskFallback by remember { mutableStateOf<Task?>(null) }
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
        navMotion = NavMotion.Push
        stack = stack + route
        route = routeKey(r)
    }
    fun selectTab(tabKey: String) {
        if (route == tabKey && stack.isEmpty()) return
        navMotion = NavMotion.Tab
        stack = emptyList()
        route = tabKey
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
        if (stack.isNotEmpty()) {
            navMotion = NavMotion.Pop
            route = stack.last()
            stack = stack.dropLast(1)
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
            onOpen = { navigate(Route.Task(it.id)) },
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
    PredictiveBackHandler(enabled = stack.isNotEmpty()) { events ->
        var maxProgress = 0f
        try {
            events.collect { ev ->
                maxProgress = maxOf(maxProgress, ev.progress)
                backPreview = ev.progress
            }
            android.util.Log.d("AgendaNav", "back committed (maxProgress=$maxProgress)")
            // Finish the gesture's own slide, then swap without a second
            // transition replaying from the start.
            val gestured = backPreview > 0f
            if (gestured) {
                animate(backPreview, 1f, animationSpec = tween(NAV_MS / 2, easing = EaseEmphasized)) { v, _ ->
                    backPreview = v
                }
            }
            back()
            if (gestured) navMotion = NavMotion.None
            backPreview = 0f
        } catch (_: kotlinx.coroutines.CancellationException) {
            android.util.Log.d("AgendaNav", "back cancelled (maxProgress=$maxProgress)")
            backPreview = 0f
        }
    }

    val current = parseRoute(route)
    val title =
        when (val r = current) {
            is Route.Other -> "Проекты"
            is Route.List -> r.list.titleRu
            is Route.Project -> board.project(r.id)?.title ?: "Проект"
            is Route.Task -> "Задача"
            is Route.Sync -> "Синхронизация"
            is Route.Settings -> "Настройки"
            is Route.Statistics -> "Статистика"
            is Route.About -> "О приложении"
        }
    val animate = animationsEnabled()
    val navMs = if (animate) NAV_MS else 0

    val hazeState = rememberHazeState()
    var glassLight by remember { mutableFloatStateOf(0.32f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .glassLightDrag { glassLight = (glassLight + it * 0.0004f).coerceIn(0f, 1f) },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Shared top bar: identical title geometry on every page. On a
            // project page in rename mode the title is the desktop editable
            // heading — focused with the whole name selected.
            val renamingProject =
                (current as? Route.Project)
                    ?.takeIf { renamingProjectId == it.id }
            AgendaTopBar(
                title = title,
                onBack = if (stack.isNotEmpty()) ::back else null,
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
                    is Route.Other ->
                        OtherPage(
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
                            onSync = { navigate(Route.Sync) },
                            updateManager = updateManager,
                        )
                    is Route.Sync ->
                        SyncScreen(sync = repository as? com.mundus.agenda.SyncApi, run = ::run)
                    is Route.Statistics -> StatisticsScreen(board = board)
                    is Route.About -> AboutScreen()
                    is Route.Project ->
                        ProjectScreen(
                            board = board,
                            projectId = r.id,
                            hideProjectMeta = true,
                            actions = actions,
                        )
                    is Route.Task ->
                        TaskScreen(
                            board = board,
                            taskId = r.id,
                            repository = repository,
                            run = ::run,
                            fallback =
                                createdTaskFallback?.takeIf { it.id == r.id },
                        )
                }
            }

            // adjustResize + edge-to-edge: pages keep focused fields above
            // the keyboard themselves; the quick-entry sheet rides it instead.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (createOpen) Modifier else Modifier.imePadding())
                    .hazeSource(hazeState)
            ) {
                if (backPreview > 0f) {
                    BackGesturePreview(
                        progress = backPreview,
                        behind = { PageContent(stack.lastOrNull()?.let(::parseRoute) ?: current) },
                        front = { PageContent(current) },
                    )
                } else {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = { navTransition(navMotion, navMs) },
                        label = "nav",
                    ) { r -> PageContent(r) }
                }
            }
        }

        // Linear-style bottom chrome (p9): a floating tab bar on the 4
        // tab roots with a separate accent «+» button on the right. On
        // list/project drill-ins just the «+» survives, like the old FAB
        // (desktop offers creation on task pages only, not on
        // Settings/Statistics/About — and the open task page has none).
        val atTabRoot = isTabRoot(route, stack)
        LaunchedEffect(atTabRoot) { if (!atTabRoot) picker.snapClosed() }
        if (atTabRoot) {
            val slot =
                PICKER_PAGES.firstOrNull { it.key == route }?.copy(desc = PICKER_BUTTON.desc)
                    ?: PICKER_BUTTON
            AgendaBottomBar(
                tabs = FIXED_TABS + slot,
                selected = route,
                onTab = ::selectTab,
                onCreate = { createOpen = true },
                picker = picker,
                hazeState = hazeState,
                lightFraction = glassLight,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
            )
        } else if (current is Route.List || current is Route.Project) {
            QuickEntryButton(
                onClick = { createOpen = true },
                hazeState = hazeState,
                lightFraction = glassLight,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }

        PagePicker(
            picker = picker,
            pages = PICKER_PAGES,
            current = route,
            onPick = { key ->
                picker.close()
                selectTab(key)
            },
            hazeState = hazeState,
            lightFraction = glassLight,
        )

        AToast(
            message = toast,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )

        // Desktop quick-entry panel: create from the + button; a task-row
        // tap drills into the full-screen task page. In-window (not a
        // dialog) so it can frost the page behind and follow the IME.
        if (createOpen) {
            QuickEntrySheet(
                board = board,
                repository = repository,
                onDismiss = { createOpen = false },
                run = ::run,
                onExpand = { task ->
                    createdTaskFallback = task
                    navigate(Route.Task(task.id))
                },
                initialWeek = current == Route.List(SmartList.WEEK),
                hazeState = hazeState,
            )
        }
    }
}

private enum class NavMotion { Tab, Push, Pop, None }

private const val NAV_MS = 300

/** Tabs cross through transparent: the old page fades to 0, then the new
 * one fades in. Drill-in and back use shared axis X: the outgoing page
 * fades over the first 30% while both slide a tenth of the width. */
private fun navTransition(motion: NavMotion, durationMs: Int): ContentTransform {
    if (motion == NavMotion.None || durationMs == 0) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val outMs = durationMs * 3 / 10
    val inMs = durationMs - outMs
    val exit = fadeOut(tween(outMs, easing = FastOutLinearInEasing))
    val enter = fadeIn(tween(inMs, delayMillis = outMs, easing = LinearOutSlowInEasing))
    if (motion == NavMotion.Tab) {
        // Opacity only: down to 0, then back up — no scale or slide.
        val half = durationMs / 2
        return fadeIn(tween(half, delayMillis = half, easing = EaseInOut)) togetherWith
            fadeOut(tween(half, easing = EaseInOut))
    }
    val dir = if (motion == NavMotion.Push) 1 else -1
    val slide = tween<IntOffset>(durationMs, easing = FastOutSlowInEasing)
    return enter + slideInHorizontally(slide) { dir * it / 10 } togetherWith
        exit + slideOutHorizontally(slide) { -dir * it / 10 }
}

fun routeKey(route: Route): String =
    when (route) {
        is Route.Other -> "other"
        is Route.List -> "list:${route.list.name}"
        is Route.Project -> "project:${route.id}"
        is Route.Task -> "task:${route.id}"
        is Route.Sync -> "sync"
        is Route.Settings -> "settings"
        is Route.Statistics -> "statistics"
        is Route.About -> "about"
    }

fun parseRoute(key: String): Route =
    when (key.substringBefore(':')) {
        "sync" -> Route.Sync
        "settings" -> Route.Settings
        "statistics" -> Route.Statistics
        "about" -> Route.About
        "project" -> Route.Project(key.substringAfter(':'))
        "task" -> Route.Task(key.substringAfter(':'))
        "list" ->
            Route.List(
                runCatching { SmartList.valueOf(key.substringAfter(':')) }
                    .getOrDefault(SmartList.INBOX)
            )
        else -> Route.Other
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
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(c.group)
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
 * The «Проекты» page from the bar's page menu: the update banner, then
 * all projects with create-project. The other former home rows live in
 * the menu itself; «О приложении» stays under Настройки. No counts: the
 * desktop sidebar renders none.
 */
@Composable
fun OtherPage(
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
    }
}
