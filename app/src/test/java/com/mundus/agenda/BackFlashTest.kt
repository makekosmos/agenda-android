package com.mundus.agenda

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mundus.agenda.ui.AgendaHome
import com.mundus.agenda.ui.AgendaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackFlashTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val board =
        runBlocking {
            (FakeRepository().boardFlow().first() as BoardState.Ready).board
        }

    private fun texts(): List<String> {
        val texts = mutableListOf<String>()
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode) {
            if (SemanticsProperties.Text in node.config) {
                node.config[SemanticsProperties.Text].forEach { texts.add(it.text) }
            }
            node.children.forEach(::collect)
        }
        collect(composeTestRule.onRoot().fetchSemanticsNode())
        return texts
    }

    private fun openAppearancePage() {
        composeTestRule.setContent { AgendaTheme { AgendaHome(board = board) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Страницы").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Другое")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Отображение")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
    }

    private fun ev(p: Float) = BackEventCompat(12f, 900f, p, BackEventCompat.EDGE_LEFT)

    /** After the committed pop lands on «Другое», «Отображение» must not
     * come back. A replayed exit transition keeps it composed for the
     * whole 300 ms; the settled swap drops it within a couple frames. */
    private fun framesUntilAppearanceGone(): Int {
        var frames = -1
        repeat(60) { i ->
            composeTestRule.mainClock.advanceTimeByFrame()
            val t = texts()
            val appearance = "Светлая" in t
            if (frames < 0 && "База данных" in t && !appearance) frames = i
            if (frames >= 0 && appearance) {
                fail("«Отображение» re-composed after landing on «Другое» at frame $i")
            }
        }
        return frames
    }

    @Test
    fun committedSwipeBackLandsOnPreviousPage() {
        openAppearancePage()

        composeTestRule.mainClock.autoAdvance = false
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(ev(0.05f))
            dispatcher.dispatchOnBackProgressed(ev(0.4f))
        }
        var previewEngaged = false
        repeat(4) {
            composeTestRule.mainClock.advanceTimeByFrame()
            val t = texts()
            if ("Светлая" in t && "База данных" in t) previewEngaged = true
        }
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackProgressed(ev(0.7f))
        }
        repeat(4) {
            composeTestRule.mainClock.advanceTimeByFrame()
            val t = texts()
            if ("Светлая" in t && "База данных" in t) previewEngaged = true
        }
        assert(previewEngaged) { "gesture preview never composed both pages" }
        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }

        val frames = framesUntilAppearanceGone()
        assert(frames >= 0) { "never landed on «Другое»" }
        assert(frames < 14) {
            "«Отображение» stayed composed $frames frames after commit — " +
                "the exit transition replayed instead of settling the preview"
        }
    }

    /** A fast flick can deliver a trailing progress=0 event before the
     * commit even though the preview ran. The handler must key off the
     * gesture's peak, not the last sample — otherwise the leaving page
     * is pushed through the full Pop transition again and lingers ~19
     * frames instead of settling out in ~10. */
    @Test
    fun gestureEndingAtZeroProgressDoesNotReplayExit() {
        openAppearancePage()

        composeTestRule.mainClock.autoAdvance = false
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(ev(0.05f))
            dispatcher.dispatchOnBackProgressed(ev(0.5f))
        }
        var previewEngaged = false
        repeat(4) {
            composeTestRule.mainClock.advanceTimeByFrame()
            val t = texts()
            if ("Светлая" in t && "База данных" in t) previewEngaged = true
        }
        assert(previewEngaged) { "gesture preview never composed both pages" }
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackProgressed(ev(0.0f))
            dispatcher.onBackPressed()
        }

        val frames = framesUntilAppearanceGone()
        assert(frames >= 0) { "never landed on «Другое»" }
        assert(frames < 14) {
            "«Отображение» stayed composed $frames frames after commit — " +
                "the exit transition replayed instead of settling the preview"
        }
    }

    /** gesturePop must be reset once the gesture pop settles — and the
     * transition spec must only read it. A later plain back press (no
     * progress events) has to run the normal Pop transition: if the flag
     * stuck true, «Другое» would be swapped out instantly and vanish
     * within a frame or two of the press. */
    @Test
    fun backPressAfterGesturePopStillAnimates() {
        openAppearancePage()

        composeTestRule.mainClock.autoAdvance = false
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(ev(0.05f))
            dispatcher.dispatchOnBackProgressed(ev(0.6f))
            dispatcher.onBackPressed()
        }
        val frames = framesUntilAppearanceGone()
        assert(frames >= 0) { "never landed on «Другое»" }

        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }
        var otherFrames = 0
        repeat(30) {
            composeTestRule.mainClock.advanceTimeByFrame()
            if ("База данных" in texts()) otherFrames++
        }
        assert(otherFrames > 2) {
            "«Другое» vanished $otherFrames frames after the back press — " +
                "the Pop transition never ran (gesturePop stuck?)"
        }
    }
}
