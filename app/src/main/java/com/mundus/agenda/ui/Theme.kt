package com.mundus.agenda.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

val PlexMonoFamily =
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    )

/** Desktop type scale (base 13px Inter, tight lh) mapped per
 * docs/design-tokens.md. */
object AgendaType {
    val pageTitle =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.3).sp,
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

@Composable
fun AgendaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkColors else LightColors
    // M3 stays only as plumbing for the few framework surfaces we still
    // use (ModalBottomSheet mechanics). Everything visual reads
    // AgendaTheme.colors / AgendaType instead.
    val scheme =
        if (darkTheme)
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
