package com.mundus.agenda.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One bottom-bar destination: route key, glyph, accessible name. */
data class TabItem(val key: String, val icon: Int, val desc: String)

// Underdamped: the lens overshoots its tab a little and settles.
private val LensSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
private val PickerSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

// Fling speed, in menu heights per second, that decides open/closed
// regardless of where the finger let go.
private const val FlingOpenSpeed = 1.2f

/**
 * How far the page menu is open, 0 (just the capsule) to 1. A finger on
 * the capsule or the card drives it directly; letting go hands it to a
 * spring that carries the release velocity. Read [progress] only in
 * draw/layer lambdas so a moving menu never recomposes.
 */
@Stable
class PagePickerState internal constructor(private val scope: CoroutineScope, travelPx: Float) {
    private val anim = Animatable(0f)
    val progress: Float get() = anim.value

    /** Finger travel for 0→1: the card's height above the capsule. */
    internal var travelPx by mutableFloatStateOf(travelPx)

    /** Whether any of the card shows; flips only at the closed edge. */
    val visible by derivedStateOf { anim.value > 0.001f || anim.targetValue > 0f }

    fun drag(dy: Float) {
        scope.launch { anim.snapTo((anim.value - dy / travelPx).coerceIn(0f, 1f)) }
    }

    fun release(velocityY: Float) {
        val v = -velocityY / travelPx
        val target =
            when {
                v > FlingOpenSpeed -> 1f
                v < -FlingOpenSpeed -> 0f
                anim.value > 0.5f -> 1f
                else -> 0f
            }
        scope.launch { anim.animateTo(target, PickerSpring, initialVelocity = v) }
    }

    fun open() {
        scope.launch { anim.animateTo(1f, PickerSpring) }
    }

    fun close() {
        scope.launch { anim.animateTo(0f, PickerSpring) }
    }

    fun snapClosed() {
        scope.launch { anim.snapTo(0f) }
    }
}

/** [rows] seeds the drag travel until the card first measures itself. */
@Composable
fun rememberPagePickerState(rows: Int): PagePickerState {
    val scope = rememberCoroutineScope()
    val travel = with(LocalDensity.current) { (rows * 44 + 12 - 56).dp.toPx() }
    return remember { PagePickerState(scope, travel) }
}

/** Vertical drag with release velocity, for the menu's open fraction. */
private suspend fun PointerInputScope.detectPickerDrag(picker: PagePickerState) {
    val tracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = { tracker.resetTracking() },
        onDragEnd = { picker.release(tracker.calculateVelocity().y) },
        onDragCancel = { picker.release(0f) },
    ) { change, dy ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        picker.drag(dy)
    }
}

/**
 * The Linear-style floating bar: tab glyphs on a glass capsule with an
 * accent lens under the current tab, then the «+» button. The lens
 * springs between tabs, stretches with its speed and, after a long press
 * (with a haptic), follows the finger along the bar; letting go selects
 * the tab under it. The last
 * tab is the page menu button: a tap or a drag that ends on it opens
 * [PagePicker], and a vertical drag on the capsule pulls the menu out
 * with the finger. Every per-frame value is read in a layer or draw
 * lambda, so lens and menu motion never recompose the bar.
 */
@Composable
fun AgendaBottomBar(
    tabs: List<TabItem>,
    selected: String,
    onTab: (String) -> Unit,
    onCreate: () -> Unit,
    picker: PagePickerState,
    hazeState: HazeState,
    lightFraction: Float,
    modifier: Modifier = Modifier,
) {
    val c = AgendaTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val selectedIndex = tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val lens = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var slotPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectedIndex) {
        if (!dragging) lens.animateTo(selectedIndex.toFloat(), LensSpring)
    }
    val lift by animateFloatAsState(
        if (dragging) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "lens-lift",
    )
    val last = tabs.lastIndex

    fun openPicker() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        picker.open()
    }

    fun settle() {
        dragging = false
        val target = lens.value.roundToInt().coerceIn(0, last)
        val landing = if (target == last) selectedIndex else target
        scope.launch { lens.animateTo(landing.toFloat(), LensSpring) }
        when {
            target == last -> openPicker()
            target != selectedIndex -> onTab(tabs[target].key)
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = 1f + lift * 0.03f
                        scaleY = 1f + lift * 0.03f
                        alpha = 1f - picker.progress
                    }
        ) {
            // The glass is a sibling under the lens, not its parent: a moving
            // lens then never invalidates (and re-blurs) the glass layer.
            Box(Modifier.matchParentSize().glassChrome(hazeState, lightFraction, CircleShape))
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .onSizeChanged { slotPx = it.width.toFloat() / tabs.size }
                        .pointerInput(picker) { detectPickerDrag(picker) }
                        .pointerInput(tabs.size) {
                            var lastTick = -1
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = true
                                    lastTick = selectedIndex
                                    scope.launch {
                                        lens.animateTo(
                                            (at.x / slotPx - 0.5f).coerceIn(0f, last.toFloat()),
                                            spring(stiffness = Spring.StiffnessMediumLow),
                                        )
                                    }
                                },
                                onDragEnd = ::settle,
                                onDragCancel = ::settle,
                            ) { change, drag ->
                                change.consume()
                                scope.launch {
                                    val next = (lens.value + drag.x / slotPx).coerceIn(0f, last.toFloat())
                                    lens.snapTo(next)
                                    val tick = next.roundToInt()
                                    if (tick != lastTick) {
                                        lastTick = tick
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        },
            ) {
                val slotDp = with(density) { slotPx.toDp() }
                Box(
                    Modifier
                        .offset { IntOffset((lens.value * slotPx).roundToInt(), 0) }
                        .size(width = slotDp, height = 48.dp)
                        .graphicsLayer {
                            // Speed in tabs/s squashes the lens into a moving drop.
                            val stretch = (abs(lens.velocity) / 22f).coerceAtMost(0.45f)
                            scaleX = 1f + stretch + lift * 0.14f
                            scaleY = 1f - stretch * 0.28f + lift * 0.14f
                        }
                        .drawBehind {
                            drawRoundRect(
                                c.accent(0.20f + lift * 0.10f),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                            )
                        }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { i, tab ->
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { if (i == last) openPicker() else onTab(tab.key) },
                                    )
                                    .graphicsLayer {
                                        val near = (1f - abs(lens.value - i)).coerceIn(0f, 1f)
                                        val s = 1f + near * lift * 0.12f
                                        scaleX = s
                                        scaleY = s
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Glyphs pick up the accent as the lens passes over
                            // them: an accent copy fades in over the grey one.
                            AIcon(tab.icon, tint = c.fg(0.45f), desc = tab.desc)
                            AIcon(
                                tab.icon,
                                tint = c.accent,
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = (1f - abs(lens.value - i)).coerceIn(0f, 1f)
                                    },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        QuickEntryButton(
            onClick = onCreate,
            hazeState = hazeState,
            lightFraction = lightFraction,
        )
    }
}

/** A rounded rect over the bottom of the bounds, starting [top] px down. */
private class RevealShape(private val top: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(0f, top, size.width, size.height, CornerRadius(radius)))
}

/**
 * The page menu, Linear-style: it takes the capsule's exact footprint
 * and grows upward out of it with [PagePickerState.progress], its rows
 * fading in while the capsule under it fades out. Dragging the card
 * moves it with the finger; a tap outside closes it.
 */
@Composable
fun BoxScope.PagePicker(
    picker: PagePickerState,
    pages: List<TabItem>,
    current: String,
    onPick: (String) -> Unit,
    hazeState: HazeState,
    lightFraction: Float,
) {
    if (!picker.visible) return
    val c = AgendaTheme.colors
    val density = LocalDensity.current
    val closedPx = with(density) { 56.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(picker) { detectTapGestures { picker.close() } }
    )
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            // The capsule's footprint: bar gutter, then «+» (56) and its gap.
            .padding(start = 26.dp, end = 26.dp + 10.dp + 56.dp, bottom = 24.dp)
            .fillMaxWidth()
            .onSizeChanged { picker.travelPx = (it.height - closedPx).coerceAtLeast(1f) }
            // Reveal by clipping a full-size card instead of resizing it:
            // the glass keeps its bounds, so its blur is not redone every
            // frame of the motion.
            .graphicsLayer {
                val p = picker.progress.coerceIn(0f, 1f)
                val visible = closedPx + (size.height - closedPx) * p
                clip = p < 0.999f
                shape = RevealShape(size.height - visible, (28f - 4f * p).dp.toPx())
            }
            .glassChrome(hazeState, lightFraction, RoundedCornerShape(24.dp))
            .pointerInput(picker) { detectPickerDrag(picker) }
            .graphicsLayer { alpha = ((picker.progress - 0.25f) / 0.75f).coerceIn(0f, 1f) }
            .padding(6.dp)
    ) {
        pages.forEach { page ->
            val sel = page.key == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .then(if (sel) Modifier.background(c.accent(0.20f)) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onPick(page.key) },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AIcon(page.icon, tint = if (sel) c.accent else c.fg(0.7f))
                Spacer(Modifier.width(12.dp))
                Text(page.desc, style = AgendaType.body, color = if (sel) c.accent else c.fg)
            }
        }
    }
}
