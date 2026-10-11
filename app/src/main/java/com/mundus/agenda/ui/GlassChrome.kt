package com.mundus.agenda.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/** Accessibility «high contrast text» — swap hairlines for solid outlines. */
@Composable
fun highContrastText(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            android.provider.Settings.Secure.getInt(
                ctx.contentResolver,
                "accessibility_high_text_contrast_enabled",
                0,
            ) != 0
        } catch (_: Exception) {
            false
        }
    }
}

fun Modifier.glassLightDrag(onDelta: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent()
                val dy = ev.changes
                    .filter { it.previousPosition != it.position }
                    .sumOf { (it.position.y - it.previousPosition.y).toDouble() }
                    .toFloat()
                if (dy != 0f) onDelta(dy)
            }
        }
    }

// Specular highlight: light amount peaks near the top edge and fades
// horizontally away from lightX. AGSL on API 33+; a static gradient
// does the same job below that.
private const val SPECULAR_AGSL = """
uniform float2 resolution;
uniform float lightX;
half4 main(float2 frag) {
    float lx = abs(frag.x - lightX) / resolution.x;
    float glow = exp(-lx * 9.0) * 0.65 + 0.35;
    float edge = smoothstep(0.42, 0.0, frag.y / resolution.y);
    return half4(1.0, 1.0, 1.0, edge * 0.12 * glow);
}
"""

fun Modifier.specularOverlay(shape: Shape, lightFraction: Float): Modifier =
    if (Build.VERSION.SDK_INT < 33) {
        drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.45f,
                ),
            )
        }
    } else {
        composed {
            val shader = remember {
                if (Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(SPECULAR_AGSL) else null
            }
            Modifier.drawWithContent {
                drawContent()
                if (shader != null) {
                    shader.setFloatUniform("resolution", size.width, size.height)
                    shader.setFloatUniform("lightX", lightFraction * size.width)
                    drawRect(brush = ShaderBrush(shader))
                }
            }
        }
    }

/** Linear-style floating chrome: blur of the content behind, dark
 * translucent surface with a faint vertical gradient for structure,
 * hairline (or solid) border, soft shadow, SDF-ish specular sheen. */
@Composable
fun Modifier.glassChrome(
    hazeState: HazeState,
    lightFraction: Float,
    shape: Shape,
): Modifier {
    val c = AgendaTheme.colors
    val hc = highContrastText()
    val borderW = if (hc) 1.5.dp else 0.5.dp
    val borderCol = if (hc) c.mutedFg else c.border
    return this
        .shadow(14.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .hazeChild(state = hazeState)
        .background(
            Brush.verticalGradient(
                listOf(c.popover.copy(alpha = 0.94f), c.popover.copy(alpha = 0.78f)),
            ),
        )
        .border(borderW, borderCol, shape)
        .specularOverlay(shape, lightFraction)
}
