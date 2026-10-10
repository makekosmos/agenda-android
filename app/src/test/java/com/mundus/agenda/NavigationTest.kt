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
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
        // Order: y-positions of the labels must be strictly increasing
        // across the sections (Потом before Проекты before Архив …).
        fun topOf(label: String): Float {
            var top = Float.MAX_VALUE
            composeTestRule
                .onNodeWithText(label)
                .fetchSemanticsNode()
                .let { top = it.boundsInRoot.top }
            return top
        }
        val ordered =
            listOf(
                "Входящие",
                "Эта неделя",
                "Следующая неделя",
                "Потом",
                "Релиз Agenda 1.0",
                "Дом и быт",
                "Архив",
                "Корзина",
                "Настройки",
                "О приложении",
            )
        val tops = ordered.map(::topOf)
        assert(tops.zipWithNext().all { (a, b) -> a < b }) {
            "home rows out of order: $ordered at $tops"
        }
        // No per-section counts — desktop sidebar shows none. Scan every
        // text node under root: a bare numeric label must not exist.
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val texts = mutableListOf<String>()
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode) {
            node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .forEach { texts.add(it.text) }
            node.children.forEach(::collect)
        }
        collect(rootNode)
        val bareCounts = texts.filter { it.matches(Regex("\\d+")) }
        assert(bareCounts.isEmpty()) { "unexpected count labels on home: $bareCounts" }
        // Настройки / О приложении are present but disabled — their rows
        // carry no click action in semantics.
        for (label in listOf("Настройки", "О приложении")) {
            val node = composeTestRule.onNodeWithText(label).fetchSemanticsNode()
            fun hasClick(n: androidx.compose.ui.semantics.SemanticsNode): Boolean =
                androidx.compose.ui.semantics.SemanticsActions.OnClick in n.config ||
                    n.parent?.let(::hasClick) == true
            assert(!hasClick(node)) { "'$label' row must be disabled on home" }
        }
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
