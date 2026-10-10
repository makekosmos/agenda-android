package com.mundus.agenda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
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

sealed interface Route {
    data class List(val list: SmartList) : Route
    data class Task(val id: String) : Route
    data class Project(val id: String) : Route
    data class Tag(val id: String) : Route
}

class BoardViewModel(repository: BoardRepository) : ViewModel() {
    val board: StateFlow<Board> =
        repository.boardFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Board())
}

class BoardViewModelFactory(private val repository: BoardRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BoardViewModel(repository) as T
}

private fun tagColor(name: String): Color =
    when (name) {
        "red" -> Color(0xFFCE222C)
        "blue" -> Color(0xFF3B82F6)
        "green" -> Color(0xFF03A14A)
        "yellow" -> Color(0xFFE58212)
        else -> Color(0xFF8D8A94)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaApp(repository: BoardRepository) {
    val viewModel: BoardViewModel = viewModel(factory = BoardViewModelFactory(repository))
    val board by viewModel.board.collectAsStateWithLifecycle()
    AgendaHome(board)
}

/** Route-free content root: Roborazzi tests render this with a fixed board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaHome(
    board: Board,
    initialRoute: Route = Route.List(SmartList.INBOX),
) {
    var route by rememberSaveable { mutableStateOf(routeKey(initialRoute)) }
    var backKey by rememberSaveable { mutableStateOf("") }
    val back = backKey.split("|").filter { it.isNotEmpty() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun go(r: String) {
        backKey = (back + route).joinToString("|")
        route = r
    }
    fun back() {
        if (back.isNotEmpty()) {
            route = back.last()
            backKey = back.dropLast(1).joinToString("|")
        }
    }
    BackHandler(enabled = back.isNotEmpty() || route != routeKey(Route.List(SmartList.INBOX))) {
        back()
    }

    val current = parseRoute(route)
    val topTitle =
        when (val r = current) {
            is Route.List -> r.list.titleRu
            is Route.Task -> board.task(r.id)?.title ?: ""
            is Route.Project -> board.project(r.id)?.title ?: "Проект"
            is Route.Tag -> board.tag(r.id)?.title ?: "Тег"
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
                        backKey = ""
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
                    is Route.List ->
                        TaskListScreen(
                            board = board,
                            list = r.list,
                            onTaskClick = { go(routeKey(Route.Task(it))) },
                        )
                    is Route.Task -> TaskScreen(board = board, taskId = r.id)
                    is Route.Project ->
                        ProjectScreen(
                            board = board,
                            projectId = r.id,
                            hideProjectMeta = true,
                            onTaskClick = { go(routeKey(Route.Task(it))) },
                        )
                    is Route.Tag ->
                        TagScreen(
                            board = board,
                            tagId = r.id,
                            onTaskClick = { go(routeKey(Route.Task(it))) },
                        )
                }
            }
        }
    }
}

fun routeKey(route: Route): String =
    when (route) {
        is Route.List -> "list:${route.list.name}"
        is Route.Task -> "task:${route.id}"
        is Route.Project -> "project:${route.id}"
        is Route.Tag -> "tag:${route.id}"
    }

fun parseRoute(key: String): Route {
    val (kind, id) = key.split(":", limit = 2)
    return when (kind) {
        "task" -> Route.Task(id)
        "project" -> Route.Project(id)
        "tag" -> Route.Tag(id)
        else -> Route.List(SmartList.valueOf(id))
    }
}

@Composable
private fun DrawerContent(board: Board, current: Route, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "Agenda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
        for (list in SmartList.entries) {
            val count = board.count(list)
            NavigationDrawerItem(
                label = { Text(list.titleRu) },
                selected = current is Route.List && current.list == list,
                badge = {
                    if (count > 0) {
                        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = { onNavigate(routeKey(Route.List(list))) },
                colors =
                    NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = Color.Transparent,
                    ),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        if (board.projects.isNotEmpty()) {
            DrawerHeader("Проекты")
            for (p in board.projects) {
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
        if (board.tags.isNotEmpty()) {
            DrawerHeader("Теги")
            for (t in board.tags) {
                NavigationDrawerItem(
                    label = { Text(t.title) },
                    selected = current is Route.Tag && current.id == t.id,
                    icon = { ColorDot(tagColor(t.color)) },
                    onClick = { onNavigate(routeKey(Route.Tag(t.id))) },
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
