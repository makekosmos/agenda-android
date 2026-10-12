package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mundus.agenda.ui.AgendaHome
import com.mundus.agenda.ui.AgendaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Bottom-bar navigation (p9): the app opens on the «Эта неделя» tab,
 * the last bar button opens the page menu (Проекты, Потом, Архив,
 * Корзина, Статистика, Настройки), and drill-ins
 * push/pop while back at a tab root exits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    private fun openPage(label: String) {
        composeTestRule.onNodeWithContentDescription("Страницы").performClick()
        composeTestRule.waitForIdle()
        // performClick() dispatches a real touch at the node centre; in
        // Robolectric the floating chrome can swallow it, so drive the
        // row's OnClick semantics action directly.
        composeTestRule
            .onNodeWithText(label)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
    }

    @Test
    fun pickingPaletteRethemesApp() {
        var seenBg = androidx.compose.ui.graphics.Color.Unspecified
        composeTestRule.setContent {
            var palette by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.mundus.agenda.ui.AgendaPalette)
            }
            AgendaTheme(darkTheme = false, palette = palette) {
                seenBg = com.mundus.agenda.ui.AgendaTheme.colors.bg
                AgendaHome(
                    board = board,
                    initialRoute = com.mundus.agenda.ui.Route.Settings,
                    palette = palette,
                    onPalette = { palette = it },
                )
            }
        }
        composeTestRule
            .onNode(androidx.compose.ui.test.hasScrollToKeyAction())
            .performScrollToKey("dracula")
        composeTestRule
            .onNodeWithText("Dracula")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF282A36), seenBg)
    }

    @Test
    fun pageMenuListsPagesInOrder() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        // Opens on «Эта неделя» by default (p9 item 4).
        composeTestRule.onNodeWithText("Созвон по дизайну").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Страницы").performClick()
        composeTestRule.waitForIdle()
        val expected = listOf("Проекты", "Потом", "Архив", "Корзина", "Статистика", "Настройки")
        val texts = textsInOrder()
        val indices = expected.map { texts.indexOf(it) }
        assert(indices.none { it < 0 } && indices.zipWithNext().all { (a, b) -> a < b }) {
            "menu rows out of order: $expected at $indices in $texts"
        }
        assert(texts.none { it == "Другое" }) { "«Другое» is gone from the menu: $texts" }
    }

    @Test
    fun swipeUpOnBarOpensPageMenu() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        assert(composeTestRule.onAllNodes(hasText("Статистика")).fetchSemanticsNodes().isEmpty())
        composeTestRule
            .onNodeWithContentDescription("Входящие")
            .performTouchInput { swipeUp(startY = bottom, endY = top - 200f) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Статистика").assertIsDisplayed()
        composeTestRule.onNodeWithText("Созвон по дизайну").assertExists()
    }

    @Test
    fun projectsPageListsProjectsWithoutCounts() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        openPage("Проекты")
        val texts = textsInOrder()
        for (label in listOf("Релиз Agenda 1.0", "Дом и быт")) {
            assert(label in texts) { "project row missing: $label in $texts" }
        }
        val bareCounts = texts.filter { it.matches(Regex("\\d+")) }
        assert(bareCounts.isEmpty()) { "unexpected count labels on projects: $bareCounts" }
    }

    private fun textsInOrder(): List<String> {
        // Composition order matches visual order within a Column.
        val texts = mutableListOf<String>()
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode) {
            if (androidx.compose.ui.semantics.SemanticsProperties.Text in node.config) {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                    .forEach { texts.add(it.text) }
            }
            node.children.forEach(::collect)
        }
        collect(composeTestRule.onRoot().fetchSemanticsNode())
        return texts
    }

    @Test
    fun settingsAndAboutOpenAndReturn() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        openPage("Настройки")
        // Settings mirrors the desktop sidebar: Отображение tiles +
        // Статистика counters, no desktop-only rows.
        composeTestRule.onNodeWithText("Отображение").assertIsDisplayed()
        composeTestRule.onNodeWithText("Светлая").assertIsDisplayed()
        // Below the fold at w411-h891: exists, may need scroll to display.
        assert(
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasText("Статистика"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) { "settings missing Статистика" }

        composeTestRule
            .onNodeWithText("О приложении")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Agenda").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Доступ к данным Agenda через Engine")
            .assertIsDisplayed()
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Отображение").assertIsDisplayed()
    }

    @Test
    fun createProjectOpensRenameHeading() {
        val repo = FakeRepository()
        composeTestRule.setContent {
            AgendaTheme { AgendaHome(board = board, repository = repo) }
        }
        composeTestRule.waitForIdle()
        openPage("Проекты")
        // Sidebar «+» on the «Проекты» header (desktop chrome.rs:457-474).
        // Page title plus the section header carrying the «+».
        assert(composeTestRule.onAllNodes(hasText("Проекты")).fetchSemanticsNodes().size == 2)
        composeTestRule
            .onNodeWithContentDescription("Новый проект")
            .performClick()
        composeTestRule.waitForIdle()
        // Lands on the new project page with the heading editable —
        // desktop edit_project_title focuses the title with select-all.
        composeTestRule
            .onNode(hasSetTextAction())
            .assertIsDisplayed()
    }

    @Test
    fun backGesturePreviewLayersBothPages() {
        // The predictive-back progress path: mid-gesture, the page being
        // left and home are both composed (slide+fade driven by progress).
        composeTestRule.setContent {
            AgendaTheme {
                com.mundus.agenda.ui.BackGesturePreview(
                    progress = 0.5f,
                    behind = { androidx.compose.material3.Text("HOME_PAGE") },
                    front = { androidx.compose.material3.Text("INNER_PAGE") },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("HOME_PAGE").assertIsDisplayed()
        composeTestRule.onNodeWithText("INNER_PAGE").assertIsDisplayed()
    }

    @Test
    fun quickEntryEnterCreatesTitledTaskAndStaysOpen() {
        // p4-fix (g) regression: run{} defers to the next main-loop turn,
        // so quickEntry must capture the field values before they clear —
        // otherwise tasks land in the engine with empty titles.
        val repo = FakeRepository()
        composeTestRule.setContent {
            AgendaTheme {
                AgendaHome(board = board, repository = repo, initialCreate = true)
            }
        }
        composeTestRule.waitForIdle()
        // The title field is the one with ImeAction.Done (notes is plain).
        val field = {
            composeTestRule.onNode(
                hasSetTextAction() and
                    hasImeAction(androidx.compose.ui.text.input.ImeAction.Done)
            )
        }
        field().performTextInput("первая задача")
        field().performImeAction()
        composeTestRule.waitForIdle()
        field().performTextInput("вторая задача")
        field().performImeAction()
        composeTestRule.waitForIdle()
        assert(repo.quickEntryCalls == listOf("первая задача", "вторая задача")) {
            "quickEntry titles: ${repo.quickEntryCalls}"
        }
        // Sheet stays open for the next entry (multi-entry flow).
        assert(
            composeTestRule
                .onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) { "quick entry sheet closed after Enter" }
    }

    @Test
    fun taskTapOpensFullScreenTaskPage() {
        // KOS-381 rework: a task-row tap drills into the full-screen task
        // page (large title + property rows), not a bottom sheet. The
        // week tab is the default tab (p9), so the task is already there.
        composeTestRule.setContent {
            AgendaTheme { AgendaHome(board = board, repository = FakeRepository()) }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Созвон по дизайну").performClick()
        composeTestRule.waitForIdle()
        // Task page: the title is an editable field, rows Дата/Проект exist.
        assert(
            composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        ) { "task page has no editable fields" }
        composeTestRule.onNodeWithText("Дата").assertIsDisplayed()
        composeTestRule.onNodeWithText("Проект").assertIsDisplayed()
        composeTestRule.onNodeWithText("Задача").assertIsDisplayed()
    }

    @Test
    fun quickEntryMaximizeCreatesAndOpensTask() {
        val repo = FakeRepository()
        composeTestRule.setContent {
            AgendaTheme { AgendaHome(board = board, repository = repo, initialCreate = true) }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                hasSetTextAction() and
                    hasImeAction(androidx.compose.ui.text.input.ImeAction.Done)
            )
            .performTextInput("развернуть меня")
        composeTestRule
            .onNodeWithContentDescription("Открыть полностью")
            .performClick()
        composeTestRule.waitForIdle()
        assert(repo.quickEntryCalls == listOf("развернуть меня")) {
            "quickEntry calls: ${repo.quickEntryCalls}"
        }
        // Landed on the task page; sheet is gone (no ImeAction.Done field).
        composeTestRule.onNodeWithText("Задача").assertIsDisplayed()
        composeTestRule.onNodeWithText("Дата").assertIsDisplayed()
    }

    @Test
    fun tapDrillsInAndBackReturnsToTab() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        // Default tab is the week list; a task tap drills in, back pops
        // to the same tab root.
        composeTestRule.onNodeWithText("Созвон по дизайну").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Задача").assertIsDisplayed()
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Созвон по дизайну").assertIsDisplayed()
    }

    @Test
    fun bottomBarSwitchesTabs() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        // Default week tab, then inbox via the bottom bar.
        composeTestRule.onNodeWithText("Созвон по дизайну").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Входящие").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Созвон по дизайну").assertDoesNotExist()
        // «На следующей неделе» tab then «Потом» from the page menu; the
        // menu button then wears that page's glyph.
        composeTestRule.onNodeWithContentDescription("На следующей неделе").performClick()
        composeTestRule.waitForIdle()
        openPage("Потом")
        composeTestRule.onNodeWithText("Потом").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Страницы").assertExists()
    }
}
