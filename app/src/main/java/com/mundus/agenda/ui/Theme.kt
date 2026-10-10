package com.mundus.agenda.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Agenda "default" palette from imago-gpui `THEMES` (zeron oklch→sRGB,
 * same values desktop renders) mapped onto Material 3 tokens.
 * `AgendaExtra` carries tokens M3 has no slot for.
 */
private val AgendaLight =
    lightColorScheme(
        primary = Color(0xFF7239CA),
        onPrimary = Color(0xFFF9F9FA),
        primaryContainer = Color(0xFFD1C0EC),
        onPrimaryContainer = Color(0xFF5E33A1),
        secondary = Color(0xFF986ED7),
        onSecondary = Color(0xFFF9F9FA),
        secondaryContainer = Color(0xFFEBEBED),
        onSecondaryContainer = Color(0xFF222027),
        background = Color(0xFFF8F7F9),
        onBackground = Color(0xFF222027),
        surface = Color(0xFFF8F7F9),
        onSurface = Color(0xFF222027),
        surfaceVariant = Color(0xFFF3F2F4),
        onSurfaceVariant = Color(0xFF615F67),
        surfaceContainer = Color(0xFFF3F2F4),
        surfaceContainerHigh = Color(0xFFFAFAFB),
        outline = Color(0xFFDEDDE0),
        outlineVariant = Color(0xFFE2E1E4),
        error = Color(0xFFCE222C),
        onError = Color(0xFFF9F9FA),
        errorContainer = Color(0xFFFBE9EA),
        onErrorContainer = Color(0xFFCE222C),
        inverseSurface = Color(0xFF222027),
        inverseOnSurface = Color(0xFFF8F7F9),
        inversePrimary = Color(0xFFAF94F9),
        scrim = Color(0x80222027),
    )

private val AgendaDark =
    darkColorScheme(
        primary = Color(0xFFAF94F9),
        onPrimary = Color(0xFF0C0A0F),
        primaryContainer = Color(0xFF453C5E),
        onPrimaryContainer = Color(0xFFBDA8F5),
        secondary = Color(0xFF826EB8),
        onSecondary = Color(0xFF0C0A0F),
        secondaryContainer = Color(0xFF1C1B20),
        onSecondaryContainer = Color(0xFFE7E5EA),
        background = Color(0xFF0D0C10),
        onBackground = Color(0xFFE7E5EA),
        surface = Color(0xFF0D0C10),
        onSurface = Color(0xFFE7E5EA),
        surfaceVariant = Color(0xFF131216),
        onSurfaceVariant = Color(0xFF8D8A94),
        surfaceContainer = Color(0xFF131216),
        surfaceContainerHigh = Color(0xFF18161C),
        outline = Color(0xFF27262A),
        outlineVariant = Color(0xFF1B1A1E),
        error = Color(0xFFFA686A),
        onError = Color(0xFF0C0A0F),
        errorContainer = Color(0xFF45262B),
        onErrorContainer = Color(0xFFFA686A),
        inverseSurface = Color(0xFFE7E5EA),
        inverseOnSurface = Color(0xFF0D0C10),
        inversePrimary = Color(0xFF7239CA),
        scrim = Color(0x8009080C),
    )

class AgendaExtra(
    val success: Color,
    val warn: Color,
    val sidebar: Color,
    val sidebarDivider: Color,
)

val LocalAgendaExtra =
    staticCompositionLocalOf {
        AgendaExtra(Color(0xFF03A14A), Color(0xFFE58212), Color(0xFFF3F2F4), Color(0xFFE2E1E4))
    }

private val ExtraLight =
    AgendaExtra(
        success = Color(0xFF03A14A),
        warn = Color(0xFFE58212),
        sidebar = Color(0xFFF3F2F4),
        sidebarDivider = Color(0xFFE2E1E4),
    )

private val ExtraDark =
    AgendaExtra(
        success = Color(0xFF5BBD74),
        warn = Color(0xFFF4A25C),
        sidebar = Color(0xFF09080C),
        sidebarDivider = Color(0xFF1B1A1E),
    )

object AgendaTheme {
    val extra: AgendaExtra
        @Composable get() = LocalAgendaExtra.current
}

@Composable
fun AgendaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAgendaExtra provides if (darkTheme) ExtraDark else ExtraLight) {
        MaterialTheme(
            colorScheme = if (darkTheme) AgendaDark else AgendaLight,
            content = content,
        )
    }
}
