package com.mundus.agenda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One bottom-bar destination: route key, glyph, accessible name. */
data class TabItem(val key: String, val icon: Int, val desc: String)

// Underdamped: the lens overshoots its tab a little and settles.
private val LensSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)

/**
 * The Linear-style floating bar: tab glyphs on a glass capsule with an
 * accent lens under the current tab, then the «+» button. The lens
 * springs between tabs, stretches with its speed and follows a finger
 * dragged along the bar; letting go selects the tab under it. The last
 * tab is a slot: tapping it while it is current (or a long press) opens
 * the page picker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaBottomBar(
    tabs: List<TabItem>,
    selected: String,
    onTab: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onCreate: () -> Unit,
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

    fun settle() {
        dragging = false
        val target = lens.value.roundToInt().coerceIn(0, last)
        scope.launch { lens.animateTo(target.toFloat(), LensSpring) }
        if (target != selectedIndex) onTab(tabs[target].key)
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
                    .pointerInput(tabs.size) {
                        var lastTick = -1
                        detectHorizontalDragGestures(
                            onDragStart = { at ->
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
                        ) { change, dx ->
                            change.consume()
                            scope.launch {
                                val next = (lens.value + dx / slotPx).coerceIn(0f, last.toFloat())
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
            // Speed in tabs/s squashes the lens into a moving drop.
            val stretch = (abs(lens.velocity) / 22f).coerceAtMost(0.45f)
            val slotDp = with(density) { slotPx.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((lens.value * slotPx).roundToInt(), 0) }
                    .size(width = slotDp, height = 48.dp)
                    .graphicsLayer {
                        scaleX = 1f + stretch + lift * 0.14f
                        scaleY = 1f - stretch * 0.28f + lift * 0.14f
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.accent(0.20f + lift * 0.10f))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, tab ->
                    // Glyphs pick up the accent as the lens passes over them.
                    val near = (1f - abs(lens.value - i)).coerceIn(0f, 1f)
                    val isSlot = i == last
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onLongClick =
                                        if (isSlot) {
                                            {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onOpenPicker()
                                            }
                                        } else {
                                            null
                                        },
                                    onClick = {
                                        if (isSlot && i == selectedIndex) onOpenPicker() else onTab(tab.key)
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AIcon(
                            tab.icon,
                            tint = lerp(c.fg(0.45f), c.accent, near),
                            desc = tab.desc,
                            modifier =
                                Modifier.graphicsLayer {
                                    val s = 1f + near * lift * 0.12f
                                    scaleX = s
                                    scaleY = s
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

/**
 * The slot tab's page picker: a glass card that grows out of the bar's
 * right end, one row per destination, the current one in accent.
 */
@Composable
fun BoxScope.PagePicker(
    open: Boolean,
    pages: List<TabItem>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    lightFraction: Float,
) {
    val c = AgendaTheme.colors
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    if (open) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }
    AnimatedVisibility(
        visibleState = visible,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 26.dp + 56.dp + 10.dp, bottom = 24.dp + 56.dp + 10.dp),
        enter =
            fadeIn(tween(120)) +
                scaleIn(
                    spring(dampingRatio = 0.68f, stiffness = 520f),
                    initialScale = 0.6f,
                    transformOrigin = TransformOrigin(0.85f, 1f),
                ),
        exit =
            fadeOut(tween(120)) +
                scaleOut(tween(140), targetScale = 0.8f, transformOrigin = TransformOrigin(0.85f, 1f)),
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .widthIn(min = 220.dp)
                .glassChrome(hazeState, lightFraction, shape)
                .padding(6.dp)
        ) {
            pages.forEach { page ->
                val sel = page.key == current
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (sel) Modifier.background(c.accent(0.20f)) else Modifier)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { onPick(page.key) },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .widthIn(min = 196.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AIcon(page.icon, tint = if (sel) c.accent else c.fg(0.7f))
                    Spacer(Modifier.width(12.dp))
                    Text(page.desc, style = AgendaType.body, color = if (sel) c.accent else c.fg)
                }
            }
        }
    }
}
