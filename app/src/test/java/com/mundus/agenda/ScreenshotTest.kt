package com.mundus.agenda

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mundus.agenda.ui.AgendaHome
import com.mundus.agenda.ui.AgendaTheme
import com.mundus.agenda.ui.Route
import com.mundus.agenda.ui.UpdateBannerRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gate B (KOS-370 Phase 3): Roborazzi records of the home page and every
 * drill-in list in both themes, at a realistic phone size, rendered
 * against [FakeRepository] (the native .so does not load under
 * Robolectric, and no date logic lives in these screens anyway).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Version strings in «Другое»/Sync/About come from versionNameForUi;
    // pin them so a release bump never breaks these goldens.
    @org.junit.Before
    fun pinVersion() {
        com.mundus.agenda.ui.versionNameForUi = "0.0.0-test"
    }

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    private fun shot(name: String, dark: Boolean, route: Route) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) { AgendaHome(board = board, initialRoute = route) }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test
    fun syncLight() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = false) {
                AgendaHome(board = board, repository = FakeRepository(), initialRoute = Route.Sync)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/sync-light.png")
    }

    @Test
    fun syncDark() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = true) {
                AgendaHome(board = board, repository = FakeRepository(), initialRoute = Route.Sync)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/sync-dark.png")
    }

    @Test
    fun syncPairingDark() {
        val repo =
            FakeRepository().apply {
                snapshot =
                    snapshot.copy(
                        peers =
                            listOf(
                                SyncPeer(
                                    deviceId = "dev-mac",
                                    deviceName = "MacBook Pro",
                                    status = "online",
                                    platform = "macos",
                                    appVersion = "0.4.2",
                                )
                            ),
                        incomingPairingRequests =
                            listOf(IncomingPairing("dev-ipad", "iPad", "ipados", "0.1.10")),
                    )
            }
        composeTestRule.setContent {
            AgendaTheme(darkTheme = true) {
                AgendaHome(board = board, repository = repo, initialRoute = Route.Sync)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/sync-pairing-dark.png")
    }

    private fun paletteShot(name: String, dark: Boolean, palette: String, route: Route) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark, palette = palette) {
                AgendaHome(board = board, initialRoute = route, palette = palette)
            }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun otherLight() = paletteShot("other-light", false, "agenda", Route.Other)
    @Test fun otherDark() = paletteShot("other-dark", true, "agenda", Route.Other)
    @Test fun appearanceLight() = paletteShot("appearance-light", false, "agenda", Route.Appearance)
    @Test fun appearanceDark() = paletteShot("appearance-dark", true, "agenda", Route.Appearance)

    private fun databaseShot(name: String, dark: Boolean) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.background(com.mundus.agenda.ui.AgendaTheme.colors.bg)
                ) {
                com.mundus.agenda.ui.DatabaseScreen(
                    board = board,
                    usage = com.mundus.agenda.ui.StorageUsage(
                        database = 3_400_000, journal = 1_100_000, other = 600_000, deviceFree = 42_000_000_000,
                    ),
                )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun databaseLight() = databaseShot("database-light", false)
    @Test fun databaseDark() = databaseShot("database-dark", true)
    @Test fun weekCatppuccinLight() = paletteShot("week-catppuccin-light", false, "catppuccin", Route.List(SmartList.WEEK))
    @Test fun weekDracula() = paletteShot("week-dracula", true, "dracula", Route.List(SmartList.WEEK))
    @Test fun weekMundusLight() = paletteShot("week-mundus-light", false, "mundus", Route.List(SmartList.WEEK))
    @Test fun weekMundusDark() = paletteShot("week-mundus-dark", true, "mundus", Route.List(SmartList.WEEK))

    @Test fun projectsLight() = shot("projects-light", false, Route.Projects)
    @Test fun projectsDark() = shot("projects-dark", true, Route.Projects)
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

    private fun bannerShot(name: String, dark: Boolean) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(AgendaTheme.colors.bg)
                        .padding(8.dp)
                ) {
                    UpdateBannerRow(
                        text = "Доступно обновление 0.1.2",
                        action = "Обновить",
                        snooze = {},
                        onAction = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun updateBannerLight() = bannerShot("update-banner-light", false)
    @Test fun updateBannerDark() = bannerShot("update-banner-dark", true)

    @Test
    fun quickEntryLight() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = false) {
                AgendaHome(board = board, repository = FakeRepository(), initialCreate = true)
            }
        }
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("купить молоко завтра")
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/quickentry-light.png")
    }

    @Test
    fun quickEntryDark() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = true) {
                AgendaHome(board = board, repository = FakeRepository(), initialCreate = true)
            }
        }
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("купить молоко завтра")
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/quickentry-dark.png")
    }

    @Test
    fun taskScreenLight() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = false) {
                AgendaHome(
                    board = board,
                    repository = FakeRepository(),
                    initialEditTaskId = "dev-today-2",
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/taskscreen-light.png")
    }

    @Test
    fun taskScreenDark() {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = true) {
                AgendaHome(
                    board = board,
                    repository = FakeRepository(),
                    initialEditTaskId = "dev-today-2",
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("roborazzi/taskscreen-dark.png")
    }

    private val feed013 =
        com.mundus.agenda.updater.UpdateFeed(
            version = "0.1.3",
            versionCode = 4,
            path = "agenda-android-0.1.3-arm64-release.apk",
            sha256 = "ab12",
            size = 100,
        )

    /** Settings «Обновления» card, one golden per updater state
     * (updates.rs:13-140). */
    private fun updateShot(name: String, dark: Boolean, state: com.mundus.agenda.updater.UpdateState) {
        composeTestRule.setContent {
            AgendaTheme(darkTheme = dark) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(AgendaTheme.colors.bg)
                        .padding(bottom = 24.dp)
                ) {
                    com.mundus.agenda.ui.UpdateSection(
                        state = state,
                        currentVersion = "0.1.2",
                        onCheck = {},
                        onDownloadOrInstall = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test fun updatesIdleLight() = updateShot("updates-idle-light", false, com.mundus.agenda.updater.UpdateState.Idle)
    @Test fun updatesCheckingLight() = updateShot("updates-checking-light", false, com.mundus.agenda.updater.UpdateState.Checking)
    @Test fun updatesNotAvailableLight() = updateShot("updates-notavailable-light", false, com.mundus.agenda.updater.UpdateState.NotAvailable)
    @Test fun updatesAvailableLight() = updateShot("updates-available-light", false, com.mundus.agenda.updater.UpdateState.Available(feed013))
    @Test fun updatesDownloadingLight() = updateShot("updates-downloading-light", false, com.mundus.agenda.updater.UpdateState.Downloading(feed013, 42))
    @Test fun updatesReadyLight() = updateShot("updates-ready-light", false, com.mundus.agenda.updater.UpdateState.ReadyToInstall(feed013, java.io.File("/tmp/a.apk")))
    @Test fun updatesErrorLight() = updateShot("updates-error-light", false, com.mundus.agenda.updater.UpdateState.Error("Не удалось проверить обновления"))
    @Test fun updatesAvailableDark() = updateShot("updates-available-dark", true, com.mundus.agenda.updater.UpdateState.Available(feed013))
}
