package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mundus.agenda.ui.AgendaTheme
import com.mundus.agenda.ui.StatisticsScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
}
