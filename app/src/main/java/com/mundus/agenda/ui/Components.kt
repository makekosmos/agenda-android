package com.mundus.agenda.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mundus.agenda.R
import com.mundus.agenda.Task

/** Desktop `ease_emphasized` cubic-bezier(0.22,1,0.36,1)
 * (imago-gpui theme.rs:210). */
val EaseEmphasized = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Desktop `ease_standard` cubic-bezier(0.25,0.1,0.25,1) (theme.rs:215). */
val EaseStandard = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private const val PRESS_MS = 120

/** Tinted desktop SVG icon — 24-viewport VectorDrawables tinted like
 * `icon(path, 16, rgba(FG, alpha))`. */
@Composable
fun AIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, desc: String? = null) {
    Icon(
        painter = painterResource(res),
        contentDescription = desc,
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}

/** One flat row in the desktop sidebar language: press fills `fg` 8%
 * (desktop hover `rgba(FG,0.08)`), glyph alpha 0.6 → 1.0. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ARow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 12.dp,
    minHeight: Dp = 48.dp,
    content: @Composable (glyph: Color) -> Unit,
) {
    val c = AgendaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by
        animateColorAsState(
            if (pressed && enabled) c.fg(0.08f) else Color.Transparent,
            animationSpec = tween(PRESS_MS, easing = EaseStandard),
            label = "row-press",
        )
    val glyph = if (enabled) c.fg else c.mutedFg.copy(alpha = 0.5f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && onClick != null,
                    role = Role.Button,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(glyph)
    }
}

/** Section label («Проекты», «Другое») — desktop 12px medium at fg 0.6. */
@Composable
fun ASectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = AgendaType.section,
        color = AgendaTheme.colors.fg(0.6f),
        modifier = modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp, end = 12.dp),
    )
}

/** Hairline row separator in the palette border color. */
@Composable
fun ADivider(modifier: Modifier = Modifier, indent: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = indent)
            .height(0.5.dp)
            .background(AgendaTheme.colors.border)
    )
}

/** Flat icon button — press dims through the same fg-8% fill. */
@Composable
fun AIconButton(
    @DrawableRes res: Int,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AgendaTheme.colors.fg,
    enabled: Boolean = true,
) {
    val c = AgendaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by
        animateColorAsState(
            if (pressed && enabled) c.fg(0.08f) else Color.Transparent,
            animationSpec = tween(PRESS_MS, easing = EaseStandard),
            label = "btn-press",
        )
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AIcon(res, tint = if (enabled) tint else c.mutedFg.copy(alpha = 0.4f), desc = desc)
    }
}

/** One task property row — the desktop qe_prop_row twin: label on the
 * left, current value (or a dropdown) on the right. */
@Composable
fun PropertyRow(
    label: String,
    value: String,
    hasValue: Boolean,
    menuContent: (@Composable () -> Unit)? = null,
) {
    val c = AgendaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        ARow(
            onClick = { if (menuContent != null) open = true },
            enabled = menuContent != null,
            horizontalPadding = 16.dp,
            verticalPadding = 11.dp,
            minHeight = 44.dp,
        ) { _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = AgendaType.body,
                    color = c.mutedFg,
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    value,
                    style = AgendaType.body,
                    color = if (hasValue) c.fg else c.mutedFg,
                )
            }
        }
        if (menuContent != null) {
            AMenu(open = open, onDismiss = { open = false }, content = menuContent)
        }
    }
}

/**
 * The single top bar every page shares: one 40dp back slot (empty on
 * home), the page title in `pageTitle` type at a fixed baseline. Home and
 * inner pages get identical geometry — same padding, height and text
 * metrics, so drilling in never shifts the title.
 */
@Composable
fun AgendaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AgendaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AIconButton(R.drawable.ic_arrow_left, desc = "Назад", onClick = onBack)
        } else {
            Spacer(Modifier.width(16.dp))
        }
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                title,
                style = AgendaType.pageTitle,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Predictive-back preview, driven by gesture [progress]: the page being
 * left slides right and fades like the committed back transition's exit
 * (+it/4, fadeOut), home slides in from the left like its enter (-it/4→0,
 * fadeIn — the same pair mirrored).
 */
@Composable
fun BackGesturePreview(
    progress: Float,
    behind: @Composable () -> Unit,
    front: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -w / 6f * (1f - progress)
                    alpha = progress
                }
        ) {
            behind()
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = w / 4f * progress
                    alpha = 1f - progress
                }
        ) {
            front()
        }
    }
}

/**
 * Editable top-bar title — the desktop project-page heading
 * (pages/project.rs:67-87): opened focused with the whole name selected,
 * Enter commits. `onEdit` reports the draft, `onCommit` fires on Done.
 */
@Composable
fun InlineTitleEditor(
    initial: String,
    onEdit: (String) -> Unit,
    onCommit: (String) -> Unit,
) {
    val c = AgendaTheme.colors
    var value by
        remember {
            mutableStateOf(
                androidx.compose.ui.text.input.TextFieldValue(
                    initial,
                    androidx.compose.ui.text.TextRange(0, initial.length),
                )
            )
        }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = {
            value = it
            onEdit(it.text)
        },
        textStyle = AgendaType.pageTitle.copy(color = c.fg),
        singleLine = true,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
        keyboardActions =
            androidx.compose.foundation.text.KeyboardActions(
                onDone = { onCommit(value.text) }
            ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 10.dp),
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Menu surface — the desktop popover look: `popover` bg, hairline
 * `border`, 8dp radius, 40dp items. [focusable] keeps default true;
 * pass false where the keyboard must stay up while the menu is shown
 * (a focusable popup window would steal window focus and hide the IME).
 * [anchorAbove] pins the menu above its anchor instead of the default
 * below-or-flip. */
@Composable
fun AMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean = true,
    anchorAbove: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!open) return
    val c = AgendaTheme.colors
    // Anchor below the row, flip above when the menu would leave the
    // window — the desktop popover grows from its button the same way.
    val positioner = remember(anchorAbove) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ): androidx.compose.ui.unit.IntOffset {
                val x =
                    anchorBounds.left.coerceIn(
                        0,
                        (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                    )
                val y =
                    when {
                        anchorAbove ->
                            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                        anchorBounds.bottom + popupContentSize.height <= windowSize.height ->
                            anchorBounds.bottom
                        else ->
                            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                    }
                return androidx.compose.ui.unit.IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = focusable, dismissOnBackPress = focusable),
    ) {
        Box(
            Modifier
                .widthIn(min = 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.popover)
                .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
fun AMenuItem(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ARow(onClick = onClick, enabled = enabled, minHeight = 40.dp, verticalPadding = 9.dp) { glyph ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                label,
                style = AgendaType.body,
                color = if (destructive) AgendaTheme.colors.destructive else glyph,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Empty-circle glyph for «Без даты» / «Без проекта» menu rows — the
 * desktop dropdown's none icon in the same 20dp cell as real icons. */
@Composable
fun NoneIcon() {
    Box(
        Modifier.size(20.dp).border(1.5.dp, AgendaTheme.colors.fg(0.6f), CircleShape)
    )
}

/** Muted project dot — the desktop sidebar's project-row icon. */
@Composable
fun ProjectDot() {
    Box(
        Modifier.size(20.dp).padding(6.dp).clip(CircleShape).background(AgendaTheme.colors.mutedFg)
    )
}

/**
 * Desktop `status_ring` (widgets.rs): Done = accent fill + accent-fg
 * check, Canceled = muted ring + ×, Started = warn ring + half fill,
 * otherwise a plain muted ring. Ring sits in a 20dp grid cell so every
 * row shares the desktop `CONTENT_GRID_X` left line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusRing(task: Task, onToggle: (Task) -> Unit = {}) {
    val c = AgendaTheme.colors
    val status =
        when {
            task.isCompleted -> "done"
            task.isCancelled -> "canceled"
            task.status == "started" -> "started"
            else -> "open"
        }
    // Completion lands with a short emphasized fill-in.
    val fill by
        animateFloatAsState(
            if (status == "done") 1f else 0f,
            animationSpec = tween(200, easing = EaseEmphasized),
            label = "ring-fill",
        )
    Box(
        modifier =
            Modifier.size(20.dp).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !task.isTrashed,
                role = Role.Checkbox,
                onClick = { onToggle(task) },
                onLongClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            "done" ->
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AIcon(R.drawable.ic_check, tint = c.accentFg, modifier = Modifier.size(14.dp))
                }
            "canceled" ->
                Box(
                    Modifier.size(18.dp).border(1.5.dp, c.mutedFg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AIcon(R.drawable.ic_status_x, tint = c.mutedFg, modifier = Modifier.size(13.dp))
                }
            "started" ->
                Canvas(Modifier.size(18.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(color = c.warn, style = Stroke(width = 1.5.dp.toPx()), radius = r - 0.75.dp.toPx())
                    drawArc(
                        color = c.warn,
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - r * 0.77f, center.y - r * 0.77f),
                        size = androidx.compose.ui.geometry.Size(r * 1.54f, r * 1.54f),
                    )
                }
            else -> {
                if (fill > 0f) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(c.accent.copy(alpha = fill)))
                }
                Box(Modifier.size(18.dp).border(1.5.dp, c.mutedFg, CircleShape))
            }
        }
    }
}

/** Circular glass create button — Linear's separate affordance to the
 * right of the tab pill, same chrome and height. Press lifts it. */
@Composable
fun QuickEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState,
    lightFraction: Float,
) {
    val c = AgendaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) 1.08f else 1f,
            animationSpec = tween(PRESS_MS, easing = EaseStandard),
            label = "qe-pulse",
        )
    Box(
        modifier =
            modifier
                .size(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glassChrome(hazeState, lightFraction, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AIcon(R.drawable.ic_compose, tint = c.fg, desc = "Новая задача")
    }
}

/** Quiet bottom toast — popover surface, hairline border, no M3 chrome. */
@Composable
fun AToast(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    val c = AgendaTheme.colors
    Row(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.popover)
                .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = AgendaType.meta, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Sheet text input — unstyled BasicTextField look (caret accent, no
 * outline/underline). */
@Composable
fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    imeAction: androidx.compose.ui.text.input.ImeAction =
        androidx.compose.ui.text.input.ImeAction.Default,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = AgendaTheme.colors
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = c.fg),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        singleLine = singleLine,
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = imeAction,
                keyboardType = keyboardType,
            ),
        keyboardActions = keyboardActions,
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = c.mutedFg)
                inner()
            }
        },
    )
}
