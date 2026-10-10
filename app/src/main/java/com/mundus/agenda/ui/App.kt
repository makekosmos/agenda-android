package com.mundus.agenda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mundus.agenda.Board
import com.mundus.agenda.BoardRepository
import com.mundus.agenda.SmartList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val NAV_LISTS =
    listOf(SmartList.INBOX, SmartList.WEEK, SmartList.NEXT_WEEK, SmartList.SOMEDAY)
private val MORE_LISTS = listOf(SmartList.LOGBOOK, SmartList.TRASH)

sealed interface Route {
    data class List(val list: SmartList) : Route
    data class Project(val id: String) : Route
}

class BoardViewModel(repository: BoardRepository) : ViewModel() {
    val board: StateFlow<com.mundus.agenda.BoardState> =
        repository
            .boardFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.mundus.agenda.BoardState.Loading)
}

class BoardViewModelFactory(private val repository: BoardRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BoardViewModel(repository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaApp(repository: BoardRepository) {
    val viewModel: BoardViewModel = viewModel(factory = BoardViewModelFactory(repository))
    val state by viewModel.board.collectAsStateWithLifecycle()
    when (val s = state) {
        is com.mundus.agenda.BoardState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        is com.mundus.agenda.BoardState.Error ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Не удалось открыть локальные данные: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        is com.mundus.agenda.BoardState.Ready -> AgendaHome(s.board)
    }
}

/** Route-free content root: Roborazzi tests render this with a fixed board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaHome(
    board: Board,
    initialRoute: Route = Route.List(SmartList.INBOX),
) {
    var route by rememberSaveable { mutableStateOf(routeKey(initialRoute)) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun back() {
        route = routeKey(Route.List(SmartList.INBOX))
    }
    BackHandler(enabled = route != routeKey(Route.List(SmartList.INBOX))) {
        // Non-Inbox destination selected: fall back to Inbox instead of
        // trapping the event. On Inbox the handler is disabled and the
        // system finishes the activity.
        back()
    }

    val current = parseRoute(route)
    val topTitle =
        when (val r = current) {
            is Route.List -> r.list.titleRu
            is Route.Project -> board.project(r.id)?.title ?: "Проект"
        }
    val isTopLevel = current is Route.List

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AgendaTheme.extra.sidebar,
                modifier = Modifier.width(300.dp),
            ) {
                DrawerContent(
                    board = board,
                    current = current,
                    onNavigate = {
                        scope.launch { drawerState.close() }
                        route = it
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            topTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (isTopLevel) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Меню")
                            }
                        } else {
                            IconButton(onClick = { back() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                )
            },
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val r = current) {
                    is Route.List -> TaskListScreen(board = board, list = r.list)
                    is Route.Project ->
                        ProjectScreen(
                            board = board,
                            projectId = r.id,
                            hideProjectMeta = true,
                        )
                }
            }
        }
    }
}

fun routeKey(route: Route): String =
    when (route) {
        is Route.List -> "list:${route.list.name}"
        is Route.Project -> "project:${route.id}"
    }

fun parseRoute(key: String): Route {
    val (kind, id) = key.split(":", limit = 2)
    return when (kind) {
        "project" -> Route.Project(id)
        else ->
            Route.List(runCatching { SmartList.valueOf(id) }.getOrDefault(SmartList.INBOX))
    }
}

@Composable
private fun DrawerContent(board: Board, current: Route, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp).verticalScroll(rememberScrollState())
    ) {
        Text(
            "Agenda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
        // Desktop sidebar nav order (chrome.rs:375-383).
        for (list in NAV_LISTS) {
            NavigationDrawerItem(
                label = { Text(list.titleRu) },
                selected = current is Route.List && current.list == list,
                onClick = { onNavigate(routeKey(Route.List(list))) },
                colors =
                    NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = Color.Transparent,
                    ),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        // Desktop sidebar shows active (status 0) projects only —
        // someday lives in «Потом», completed in the Logbook.
        val drawerProjects = board.projects.filter { it.status == 0 }
        if (drawerProjects.isNotEmpty()) {
            DrawerHeader("Проекты")
            for (p in drawerProjects) {
                NavigationDrawerItem(
                    label = { Text(p.title) },
                    selected = current is Route.Project && current.id == p.id,
                    icon = { ColorDot(MaterialTheme.colorScheme.primary) },
                    onClick = { onNavigate(routeKey(Route.Project(p.id))) },
                    colors =
                        NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                        ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
        // Desktop «Другое» footer menu (chrome.rs:1056-1067): only the
        // entries with real screens exist here.
        DrawerHeader("Другое")
        for (list in MORE_LISTS) {
            NavigationDrawerItem(
                label = { Text(list.titleRu) },
                selected = current is Route.List && current.list == list,
                onClick = { onNavigate(routeKey(Route.List(list))) },
                colors =
                    NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = Color.Transparent,
                    ),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun DrawerHeader(title: String) {
    HorizontalDivider(
        color = AgendaTheme.extra.sidebarDivider,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
    )
}
