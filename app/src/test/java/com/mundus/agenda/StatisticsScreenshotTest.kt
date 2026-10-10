package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mundus.agenda.ui.AgendaTheme
import com.mundus.agenda.ui.StatisticsScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi records of the Statistics page in both themes, same harness as
 * [ScreenshotTest]. `today` is pinned to the fixture day (2026-10-12,
 * Monday) so the heatmap and streaks are deterministic.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StatisticsScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    private fun shot(name: String, dark: Boolean, b: Board = board) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) {
                StatisticsScreen(board = b, today = LocalDate.of(2026, 10, 12))
            }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun statisticsLight() = shot("statistics-light", false)
    @Test fun statisticsDark() = shot("statistics-dark", true)
    @Test fun statisticsEmptyLight() = shot("statistics-empty-light", false, Board())
    @Test fun statisticsEmptyDark() = shot("statistics-empty-dark", true, Board())

    @Test
    fun `share card renders non-blank pixels`() {
        val today = LocalDate.of(2026, 10, 12)
        val stats =
            com.mundus.agenda.stats.statistics(board.todos, today)
        val colors =
            com.mundus.agenda.ui.ShareCardColors(
                cardBg = 0xFFFFFFFF.toInt(),
                border = 0xFFDDDDDD.toInt(),
                fg = 0xFF1A1A1A.toInt(),
                muted = 0xFF777777.toInt(),
                accent = 0xFF7C3AED.toInt(),
            )
        val bmp = com.mundus.agenda.ui.renderShareCard(stats, today, colors)
        assertTrue(bmp.width >= 1120 && bmp.height > 300)
        var opaque = 0
        for (x in 0 until bmp.width step 37) {
            for (y in 0 until bmp.height step 37) {
                if (bmp.getPixel(x, y) != 0) opaque++
            }
        }
        assertTrue("share card rendered blank", opaque > 50)
    }

    @Test
    fun `heatmap scrolls horizontally`() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = false) {
                StatisticsScreen(board = board, today = LocalDate.of(2026, 10, 12))
            }
        }
        val hscroll =
            androidx.compose.ui.test.SemanticsMatcher("hscroll") {
                it.config.getOrElseNullable(
                    androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
                ) { null } != null
            }
        val hnode = composeTestRule.onNode(hscroll).fetchSemanticsNode()
        composeTestRule.onNode(hscroll).performScrollToIndex(40)
        composeTestRule.waitForIdle()
        val offset =
            hnode.config.getOrElseNullable(
                androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
            ) { null }!!.value()
        assertTrue("scroll offset after scrollToIndex(40) = $offset", offset > 100f)
    }
}
