package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
        // Настройки / О приложении are present but disabled — their rows
        // carry the Disabled semantics flag.
        for (label in listOf("Настройки", "О приложении")) {
            val node = composeTestRule.onNodeWithText(label).fetchSemanticsNode()
            fun isDisabled(n: androidx.compose.ui.semantics.SemanticsNode): Boolean =
                androidx.compose.ui.semantics.SemanticsProperties.Disabled in n.config ||
                    n.parent?.let(::isDisabled) == true
            assert(isDisabled(node)) { "'$label' row must be disabled on home" }
        }
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
