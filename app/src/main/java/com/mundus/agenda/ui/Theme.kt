package com.mundus.agenda.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mundus.agenda.R

/**
 * Agenda "default" palette verbatim from imago-gpui THEMES[0]
 * (`imago-gpui/src/palettes.rs:40-42`, token layout `theme.rs:16-33`).
 * These are the only color tokens the UI reads — M3 stays for
 * MaterialTheme plumbing but every custom component pulls from here.
 */
class AgendaColors(
    val bg: Color,
    val fg: Color,
    val mutedFg: Color,
    val border: Color,
    val accent: Color,
    val accentFg: Color,
    val accentDim: Color,
    val secondary: Color,
    val card: Color,
    val popover: Color,
    val sidebarBg: Color,
    val sidebarDivider: Color,
    val destructive: Color,
    val warn: Color,
    val success: Color,
    val qeChipBg: Color,
    val qeChipFg: Color,
) {
    /** Desktop `rgba(FG, a)` — hover/press/active fills and glyph dimming. */
    fun fg(alpha: Float): Color = fg.copy(alpha = alpha)

    fun accent(alpha: Float): Color = accent.copy(alpha = alpha)

    /** imago `UiStyle::card` (settings.rs:121-133): `rgba(foreground, 0.045)`
     * flattened over the page so grouped rows read as a raised surface. */
    val group: Color get() = fg(0.045f).compositeOver(bg)
}

private val LightColors =
    AgendaColors(
        bg = Color(0xFFF8F7F9),
        fg = Color(0xFF222027),
        mutedFg = Color(0xFF615F67),
        border = Color(0xFFDEDDE0),
        accent = Color(0xFF7239CA),
        accentFg = Color(0xFFF9F9FA),
        accentDim = Color(0xFF986ED7),
        secondary = Color(0xFFEBEBED),
        card = Color(0xFFF3F2F4),
        popover = Color(0xFFFAFAFB),
        sidebarBg = Color(0xFFF3F2F4),
        sidebarDivider = Color(0xFFE2E1E4),
        destructive = Color(0xFFCE222C),
        warn = Color(0xFFE58212),
        success = Color(0xFF03A14A),
        qeChipBg = Color(0xFFD1C0EC),
        qeChipFg = Color(0xFF5E33A1),
    )

private val DarkColors =
    AgendaColors(
        bg = Color(0xFF0D0C10),
        fg = Color(0xFFE7E5EA),
        mutedFg = Color(0xFF8D8A94),
        border = Color(0xFF27262A),
        accent = Color(0xFFAF94F9),
        accentFg = Color(0xFF0C0A0F),
        accentDim = Color(0xFF826EB8),
        secondary = Color(0xFF1C1B20),
        card = Color(0xFF131216),
        popover = Color(0xFF18161C),
        sidebarBg = Color(0xFF09080C),
        sidebarDivider = Color(0xFF1B1A1E),
        destructive = Color(0xFFFA686A),
        warn = Color(0xFFF4A25C),
        success = Color(0xFF5BBD74),
        qeChipBg = Color(0xFF453C5E),
        qeChipFg = Color(0xFFBDA8F5),
    )

val LocalAgendaColors = compositionLocalOf { LightColors }

/** Inter is a variable font — weights come through `fontVariationSettings`
 * wght so SemiBold renders real SemiBold, not a synthetic bold. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int, weightObj: FontWeight) =
    Font(
        R.font.inter,
        weight = weightObj,
        variationSettings =
            FontVariation.Settings(FontVariation.weight(weight)),
    )

val InterFamily =
    FontFamily(
        inter(400, FontWeight.Normal),
        inter(500, FontWeight.Medium),
        inter(600, FontWeight.SemiBold),
        inter(700, FontWeight.Bold),
    )

/** M3's default scale with Inter, for Material components that read
 * MaterialTheme.typography instead of AgendaType. */
private val InterTypography =
    Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = InterFamily),
            displayMedium = displayMedium.copy(fontFamily = InterFamily),
            displaySmall = displaySmall.copy(fontFamily = InterFamily),
            headlineLarge = headlineLarge.copy(fontFamily = InterFamily),
            headlineMedium = headlineMedium.copy(fontFamily = InterFamily),
            headlineSmall = headlineSmall.copy(fontFamily = InterFamily),
            titleLarge = titleLarge.copy(fontFamily = InterFamily),
            titleMedium = titleMedium.copy(fontFamily = InterFamily),
            titleSmall = titleSmall.copy(fontFamily = InterFamily),
            bodyLarge = bodyLarge.copy(fontFamily = InterFamily),
            bodyMedium = bodyMedium.copy(fontFamily = InterFamily),
            bodySmall = bodySmall.copy(fontFamily = InterFamily),
            labelLarge = labelLarge.copy(fontFamily = InterFamily),
            labelMedium = labelMedium.copy(fontFamily = InterFamily),
            labelSmall = labelSmall.copy(fontFamily = InterFamily),
        )
    }

/** Desktop type scale (base 13px Inter, tight lh) mapped per
 * docs/design-tokens.md. */
object AgendaType {
    val pageTitle =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.6).sp,
        )
    val barTitle =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.2).sp,
        )
    val section =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.2.sp,
        )
    val body =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
    val meta =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
        )
    val label =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
        )
    val input =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
        )
    val inputMeta =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
}

object AgendaTheme {
    val colors: AgendaColors
        @Composable get() = LocalAgendaColors.current
}

/** Desktop tag colors — imago-gpui `theme::tag_color` verbatim. */
fun tagColor(name: String, fallback: Color): Color =
    when (name) {
        "red" -> Color(0xFFE66F64)
        "orange" -> Color(0xFFD9975C)
        "yellow" -> Color(0xFFCCAE59)
        "green" -> Color(0xFF3DB07C)
        "blue" -> Color(0xFF548BD0)
        "purple" -> Color(0xFFA074D0)
        "pink" -> Color(0xFFD981AC)
        "gray", "grey" -> Color(0xFF7A7A7A)
        else -> fallback
    }

/** Built-in Agenda palette id; every other id is a zeron family or the
 * built-in [MundusPalette]. */
const val AgendaPalette = "agenda"

/** Built-in Mundus palette id. */
const val MundusPalette = "mundus"

/** Which row of [MundusAccents] this build resolves — the Mundus app it is. */
const val MundusApp = "agenda"

/** One app's brand accent in both appearances, as 0xRRGGBB. Values are the
 * imago accent presets (appearance_editor/colors.rs). */
class AppAccent(val light: Int, val dark: Int)

/** Unlike the zeron themes, Mundus fixes the chrome and lets each app keep
 * its own accent colour: the theme is this map, not a special case. */
val MundusAccents: Map<String, AppAccent> =
    mapOf(
        "agenda" to AppAccent(light = 0x2563EB, dark = 0x60A5FA),
    )

/** [dark] is the variant actually shown: a dark-only zeron family stays
 * dark under a light system theme. */
fun paletteColors(palette: String, dark: Boolean): AgendaColors = resolveTheme(palette, dark).colors

private class ResolvedTheme(val colors: AgendaColors, val dark: Boolean)

private fun resolveTheme(palette: String, dark: Boolean): ResolvedTheme {
    val seed = ZeronFamilies.firstOrNull { it.id == palette }?.variant(dark)
    return when {
        seed != null -> ResolvedTheme(seed.toAgendaColors(), seed.dark)
        palette == MundusPalette -> ResolvedTheme(mundusColors(dark), dark)
        dark -> ResolvedTheme(DarkColors, true)
        else -> ResolvedTheme(LightColors, false)
    }
}

/** Mundus chrome is the shared Agenda palette; only the accent is the
 * app's own. */
private fun mundusColors(dark: Boolean): AgendaColors {
    val accent = MundusAccents.getValue(MundusApp)
    return (if (dark) DarkColors else LightColors)
        .withAccent(if (dark) accent.dark else accent.light, dark)
}

@Composable
fun AgendaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: String = AgendaPalette,
    content: @Composable () -> Unit,
) {
    val resolved = remember(palette, darkTheme) { resolveTheme(palette, darkTheme) }
    val c = resolved.colors
    val dark = resolved.dark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).run {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    // M3 stays only as plumbing for the few framework surfaces we still
    // use (ModalBottomSheet mechanics). Everything visual reads
    // AgendaTheme.colors / AgendaType instead.
    val scheme =
        if (dark)
            darkColorScheme(
                background = c.bg,
                surface = c.bg,
                surfaceVariant = c.popover,
                onSurface = c.fg,
                onSurfaceVariant = c.mutedFg,
                primary = c.accent,
                onPrimary = c.accentFg,
                outline = c.border,
                outlineVariant = c.sidebarDivider,
                error = c.destructive,
                scrim = Color(0x8009080C),
            )
        else
            lightColorScheme(
                background = c.bg,
                surface = c.bg,
                surfaceVariant = c.popover,
                onSurface = c.fg,
                onSurfaceVariant = c.mutedFg,
                primary = c.accent,
                onPrimary = c.accentFg,
                outline = c.border,
                outlineVariant = c.sidebarDivider,
                error = c.destructive,
                scrim = Color(0x80222027),
            )
    CompositionLocalProvider(LocalAgendaColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = InterTypography,
            shapes =
                Shapes(
                    small = RoundedCornerShape(6.dp),
                    medium = RoundedCornerShape(8.dp),
                    large = RoundedCornerShape(16.dp),
                ),
            content = content,
        )
    }
}
