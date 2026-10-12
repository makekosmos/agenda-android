package com.mundus.agenda

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mundus.agenda.ui.AgendaPalette
import com.mundus.agenda.ui.MundusAccents
import com.mundus.agenda.ui.MundusApp
import com.mundus.agenda.ui.ZeronFamilies
import com.mundus.agenda.ui.paletteColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeronPaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val (x, y) = a.luminance() to b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    @Test
    fun registryMatchesZeronBuiltins() {
        assertEquals(19, ZeronFamilies.size)
        assertEquals(30, ZeronFamilies.sumOf { it.variants.size })
        assertEquals(ZeronFamilies.size, ZeronFamilies.map { it.id }.toSet().size)
    }

    @Test
    fun zeronDarkDerivesLikeZeron() {
        val c = paletteColors("zeron", dark = true)
        assertEquals(Color(0xFF060606), c.bg)
        assertEquals(Color(0xFF8B7CF6), c.accent)
        assertEquals(Color.Black, c.accentFg)
        // border = WHITE α0.10 → round(0.1*255)=26/255 over #060606
        assertEquals(Color(0xFF1F1F1F), c.border)
    }

    @Test
    fun darkOnlyFamilyStaysDarkInLightMode() {
        assertEquals(Color(0xFF282A36), paletteColors("dracula", dark = false).bg)
    }

    @Test
    fun unknownIdFallsBackToAgenda() {
        assertEquals(paletteColors(AgendaPalette, false).bg, paletteColors("gone", false).bg)
        assertEquals(Color(0xFFF8F7F9), paletteColors(AgendaPalette, false).bg)
    }

    @Test
    fun mundusKeepsAgendaChromeWithTheAppAccent() {
        val light = paletteColors("mundus", dark = false)
        val base = paletteColors(AgendaPalette, dark = false)
        // Shared chrome: every non-accent token is Agenda's.
        assertEquals(base.bg, light.bg)
        assertEquals(base.fg, light.fg)
        assertEquals(base.mutedFg, light.mutedFg)
        assertEquals(base.border, light.border)
        assertEquals(base.destructive, light.destructive)
        // Accent is the app's own — Agenda's blue — in both appearances.
        assertEquals(Color(0xFF2563EB), light.accent)
        assertEquals(Color(0xFF60A5FA), paletteColors("mundus", dark = true).accent)
        // The map, not a hardcode, is what resolves it.
        val app = MundusAccents.getValue(MundusApp)
        assertEquals(0x2563EB, app.light)
        assertEquals(0x60A5FA, app.dark)
    }

    @Test
    fun everyVariantKeepsReadableMutedAndAccent() {
        for (f in ZeronFamilies) for (v in f.variants) {
            val c = paletteColors(f.id, v.dark)
            assertTrue("${v.id} muted", contrast(c.mutedFg, c.bg) >= 4.4f)
            assertTrue("${v.id} accent", contrast(c.accent, c.bg) >= 2.9f)
        }
    }
}
