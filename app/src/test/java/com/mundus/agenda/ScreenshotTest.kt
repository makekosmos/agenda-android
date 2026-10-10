package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mundus.agenda.ui.AgendaHome
import com.mundus.agenda.ui.AgendaTheme
import com.mundus.agenda.ui.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Gate B (KOS-370 Phase 1): Roborazzi records of every read screen in both
 * themes, rendered against [FakeRepository] (the native .so does not load
 * under Robolectric, and no date logic lives in these screens anyway).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    private fun shot(name: String, dark: Boolean, route: Route) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) { AgendaHome(board = board, initialRoute = route) }
        }
        // Goldens live in app/roborazzi/ (the dir verifyRoborazziDebug
        // compares against — paths resolve relative to the module dir).
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun inboxLight() = shot("inbox-light", false, Route.List(SmartList.INBOX))
    @Test fun inboxDark() = shot("inbox-dark", true, Route.List(SmartList.INBOX))
    @Test fun weekLight() = shot("week-light", false, Route.List(SmartList.WEEK))
    @Test fun weekDark() = shot("week-dark", true, Route.List(SmartList.WEEK))
    @Test fun somedayLight() = shot("later-light", false, Route.List(SmartList.SOMEDAY))
    @Test fun somedayDark() = shot("later-dark", true, Route.List(SmartList.SOMEDAY))
    @Test fun logbookLight() = shot("logbook-light", false, Route.List(SmartList.LOGBOOK))
    @Test fun logbookDark() = shot("logbook-dark", true, Route.List(SmartList.LOGBOOK))
    @Test fun trashLight() = shot("trash-light", false, Route.List(SmartList.TRASH))
    @Test fun trashDark() = shot("trash-dark", true, Route.List(SmartList.TRASH))
    @Test fun projectLight() = shot("project-light", false, Route.Project("dev-proj-release"))
    @Test fun projectDark() = shot("project-dark", true, Route.Project("dev-proj-release"))
    @Test fun nextWeekLight() = shot("nextweek-light", false, Route.List(SmartList.NEXT_WEEK))
    @Test fun nextWeekDark() = shot("nextweek-dark", true, Route.List(SmartList.NEXT_WEEK))

    private fun drawerShot(name: String, dark: Boolean) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) { AgendaHome(board = board) }
        }
        composeTestRule.onNodeWithContentDescription("Меню").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun drawerLight() = drawerShot("drawer-light", false)
    @Test fun drawerDark() = drawerShot("drawer-dark", true)
}
