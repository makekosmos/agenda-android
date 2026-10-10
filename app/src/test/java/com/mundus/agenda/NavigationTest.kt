package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mundus.agenda.ui.AgendaHome
import com.mundus.agenda.ui.AgendaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Home-page navigation parity (KOS-370 Phase 3): the home page lists
 * exactly the desktop sidebar sections in order, with no counts; a tap
 * drills into the list and system back returns home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    @Test
    fun homeShowsDesktopItemsInOrderWithoutCounts() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        val expected =
            listOf(
                "Входящие",
                "Эта неделя",
                "Следующая неделя",
                "Потом",
                "Проекты",
                "Релиз Agenda 1.0",
                "Дом и быт",
                "Другое",
                "Архив",
                "Корзина",
                "Настройки",
                "О приложении",
            )
        // Ordered walk: every expected label must exist; the sequence is
        // checked by scanning semantics node positions.
        composeTestRule.onNodeWithText("Входящие").assertIsDisplayed()
        for (label in expected) {
            val found =
                composeTestRule
                    .onAllNodes(androidx.compose.ui.test.hasText(label))
                    .fetchSemanticsNodes()
            assert(found.isNotEmpty()) { "home row missing: $label" }
        }
        // Order: walk the semantics tree top-down (composition order
        // matches visual order in the Column) and check the labels form
        // an increasing subsequence.
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val texts = mutableListOf<String>()
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode) {
            if (androidx.compose.ui.semantics.SemanticsProperties.Text in node.config) {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                    .forEach { texts.add(it.text) }
            }
            node.children.forEach(::collect)
        }
        collect(rootNode)
        val ordered =
            listOf(
                "Входящие",
                "Эта неделя",
                "Следующая неделя",
                "Потом",
                "Проекты",
                "Релиз Agenda 1.0",
                "Дом и быт",
                "Другое",
                "Архив",
                "Корзина",
                "Настройки",
                "О приложении",
            )
        val indices = ordered.map { texts.indexOf(it) }
        assert(indices.none { it < 0 } && indices.zipWithNext().all { (a, b) -> a < b }) {
            "home rows out of order: $ordered at $indices in $texts"
        }
        val bareCounts = texts.filter { it.matches(Regex("\\d+")) }
        assert(bareCounts.isEmpty()) { "unexpected count labels on home: $bareCounts" }
        // Настройки / О приложении are enabled rows (p4-fix f): tapping
        // each opens the matching page, back returns home.
    }

    @Test
    fun settingsAndAboutOpenAndReturn() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Настройки").performScrollTo().performClick()
        composeTestRule.waitForIdle()
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
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Другое").assertIsDisplayed()

        composeTestRule.onNodeWithText("О приложении").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Agenda").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Доступ к данным Agenda через Engine")
            .assertIsDisplayed()
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Другое").assertIsDisplayed()
    }

    @Test
    fun createProjectOpensRenameHeading() {
        val repo = FakeRepository()
        composeTestRule.setContent {
            AgendaTheme { AgendaHome(board = board, repository = repo) }
        }
        composeTestRule.waitForIdle()
        // Sidebar «+» on the «Проекты» header (desktop chrome.rs:457-474).
        composeTestRule
            .onNodeWithText("Проекты")
            .assertIsDisplayed()
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
        val field = { composeTestRule.onAllNodes(hasSetTextAction())[0] }
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
    fun tapDrillsInAndBackReturnsHome() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Эта неделя").performClick()
        composeTestRule.waitForIdle()
        // Inside the list: tasks visible, back affordance present.
        composeTestRule.onNodeWithText("Созвон по дизайну").assertIsDisplayed()
        composeTestRule.onNodeWithText("Входящие").assertDoesNotExist()
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Входящие").assertIsDisplayed()
        composeTestRule.onNodeWithText("Созвон по дизайну").assertDoesNotExist()
    }
}
