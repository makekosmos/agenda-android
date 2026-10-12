package com.mundus.agenda.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/** One zeron `Seeds` literal (builtins.rs), colors as 0xRRGGBB. */
class ZeronSeed(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Int,
    val shell: Int,
    val raised: Int,
    val card: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val accent: Int,
    val danger: Int,
    val warning: Int,
    val success: Int,
)

class ZeronFamily(val id: String, val name: String, val variants: List<ZeronSeed>) {
    /** The variant for the requested appearance; single-appearance
     * families (Dracula, Nord, ...) keep their only look. */
    fun variant(dark: Boolean): ZeronSeed = variants.firstOrNull { it.dark == dark } ?: variants.first()
}

/** Port of zeron `Color` (crates/theme/src/lib.rs): u8 channels, f32 math,
 * round-half-away-from-zero, so derived tokens match zeron to the byte. */
private data class Rgba(val r: Int, val g: Int, val b: Int, val a: Int = 255) {
    fun withAlpha(alpha: Float) = copy(a = Math.round(alpha.coerceIn(0f, 1f) * 255f))

    fun blendOver(bg: Rgba): Rgba {
        val alpha = a / 255f
        fun ch(f: Int, b: Int) = Math.round(f * alpha + b * (1f - alpha))
        return Rgba(ch(r, bg.r), ch(g, bg.g), ch(b, bg.b))
    }

    fun mix(other: Rgba, amount: Float): Rgba {
        val t = amount.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = Math.round(x + (y - x) * t)
        return Rgba(ch(r, other.r), ch(g, other.g), ch(b, other.b), ch(a, other.a))
    }

    fun contrast(bg: Rgba): Float {
        val fg = if (a == 255) this else blendOver(bg)
        val (x, y) = fg.luminance() to bg.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    fun bestOn(): Rgba = if (WHITE.contrast(this) >= BLACK.contrast(this)) WHITE else BLACK

    fun ensureContrast(bg: Rgba, minimum: Float): Rgba {
        if (contrast(bg) >= minimum) return this
        val target = if (BLACK.contrast(bg) >= WHITE.contrast(bg)) BLACK else WHITE
        for (step in 1..20) {
            val candidate = mix(target, step / 20f)
            if (candidate.contrast(bg) >= minimum) return candidate
        }
        return target
    }

    private fun luminance(): Float {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }

    fun toColor() = Color(r, g, b, a)

    companion object {
        val BLACK = Rgba(0, 0, 0)
        val WHITE = Rgba(255, 255, 255)

        fun of(rgb: Int) = Rgba(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    }
}

/**
 * zeron `variant()` + `AccentRoles::derive` (builtins.rs:97-170,
 * lib.rs:357-390), then each zeron role onto the Agenda token that plays
 * the same part. Alpha roles are flattened over the surface they sit on.
 */
fun ZeronSeed.toAgendaColors(): AgendaColors {
    val background = Rgba.of(background)
    val shell = Rgba.of(shell)
    val raised = Rgba.of(raised)
    val card = Rgba.of(card)
    val text = Rgba.of(text)
    val muted = Rgba.of(muted).ensureContrast(background, 4.5f)

    val primary = Rgba.of(accent).ensureContrast(background, 3.0f)
    val on = primary.bestOn()
    val glyphLight = primary.mix(if (dark) Rgba.WHITE else background, if (dark) 0.28f else 0.18f)
    val glyphDeep = primary.mix(Rgba.BLACK, if (dark) 0.18f else 0.26f)
    val wash = primary.withAlpha(if (dark) 0.22f else 0.12f)

    val borderTone = if (dark) Rgba.WHITE else Rgba.BLACK
    val border = borderTone.withAlpha(if (dark) 0.10f else 0.12f)
    val hover = borderTone.withAlpha(if (dark) 0.11f else 0.06f)

    return AgendaColors(
        bg = background.toColor(),
        fg = text.toColor(),
        mutedFg = muted.toColor(),
        border = border.blendOver(background).toColor(),
        accent = primary.toColor(),
        accentFg = on.toColor(),
        accentDim = (if (dark) glyphDeep else glyphLight).toColor(),
        secondary = hover.blendOver(background).toColor(),
        card = card.toColor(),
        popover = card.mix(raised, if (dark) 0.18f else 0.04f).toColor(),
        sidebarBg = shell.toColor(),
        sidebarDivider = border.blendOver(shell).toColor(),
        destructive = Rgba.of(danger).toColor(),
        warn = Rgba.of(warning).toColor(),
        success = Rgba.of(success).toColor(),
        qeChipBg = wash.blendOver(background).toColor(),
        qeChipFg = (if (dark) glyphLight else glyphDeep).toColor(),
    )
}

/**
 * Re-derives the accent family ([AgendaColors.accent], `accentFg`,
 * `accentDim`, `qeChipBg`, `qeChipFg`) on an already-resolved palette with
 * the same AccentRoles math as [toAgendaColors]; chrome tokens pass
 * through. The Mundus theme uses this to give each app its own accent on
 * the shared chrome.
 */
fun AgendaColors.withAccent(accent: Int, dark: Boolean): AgendaColors {
    val background = Rgba.of(bg.toArgb() and 0xFFFFFF)
    val primary = Rgba.of(accent).ensureContrast(background, 3.0f)
    val on = primary.bestOn()
    val glyphLight = primary.mix(if (dark) Rgba.WHITE else background, if (dark) 0.28f else 0.18f)
    val glyphDeep = primary.mix(Rgba.BLACK, if (dark) 0.18f else 0.26f)
    val wash = primary.withAlpha(if (dark) 0.22f else 0.12f)

    return AgendaColors(
        bg = bg,
        fg = fg,
        mutedFg = mutedFg,
        border = border,
        accent = primary.toColor(),
        accentFg = on.toColor(),
        accentDim = (if (dark) glyphDeep else glyphLight).toColor(),
        secondary = secondary,
        card = card,
        popover = popover,
        sidebarBg = sidebarBg,
        sidebarDivider = sidebarDivider,
        destructive = destructive,
        warn = warn,
        success = success,
        qeChipBg = wash.blendOver(background).toColor(),
        qeChipFg = (if (dark) glyphLight else glyphDeep).toColor(),
    )
}
