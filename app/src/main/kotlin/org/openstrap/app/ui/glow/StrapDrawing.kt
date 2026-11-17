package org.openstrap.app.ui.glow

import androidx.compose.animation.core.LinearEasing
import org.openstrap.app.ui.theme.AppColors
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The strap as a flat drawing in the rings theme, shared by the Strap page's card and Today's pairing stage.
 */

/**
 * The strap seen from the front, flat: the band running top to bottom as one quiet solid strip, the pod a solid
 * rounded block on it with its face set in, and its light as a dot inside a thin ring of its own hue, the rings
 * theme's mark. While it syncs, an arc runs round that ring. When it isn't connected the drawing dims and the light
 * goes out to an empty ring. No gradients and no glow.
 */
@Composable
internal fun StrapDrawing(led: Color, lit: Boolean, syncing: Boolean, modifier: Modifier) {
    // The turn is a State read only in draw, so a syncing strap redraws each frame without recomposing.
    val turn: State<Float> = if (syncing) {
        val t = rememberInfiniteTransition(label = "strap sync")
        t.animateFloat(
            0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "strap sync turn",
        )
    } else NoTurn
    Spacer(
        modifier.graphicsLayer { alpha = if (lit) 1f else 0.45f }.drawWithCache {
            // Drawn on a 120 by 150 grid, the card slot's shape.
            val k = size.width / 120f
            fun o(x: Float, y: Float) = Offset(x * k, y * k)
            val ink = AppColors.ink
            val band = ink.copy(alpha = 0.10f)
            val pod = AppColors.raised
            val face = AppColors.page
            val c = o(60f, 75f)
            onDrawBehind {
                // The band, flat, with a few quiet notches for its weave.
                drawRoundRect(band, o(44f, 0f), Size(32f * k, 150f * k), CornerRadius(6f * k))
                for (y in Notches) drawLine(ink.copy(alpha = 0.08f), o(50f, y), o(70f, y), 1.2f * k, StrokeCap.Round)
                // The pod and its face.
                drawRoundRect(pod, o(34f, 40f), Size(52f * k, 70f * k), CornerRadius(12f * k))
                drawRoundRect(face, o(40f, 46f), Size(40f * k, 58f * k), CornerRadius(8f * k))
                // The light: a dot in a ring of its own hue.
                val ring = 9f * k
                val stroke = 2.4f * k
                val tl = Offset(c.x - ring, c.y - ring)
                val box = Size(ring * 2, ring * 2)
                if (lit) {
                    drawArc(led.copy(alpha = 0.2f), 0f, 360f, false, tl, box, style = Stroke(stroke))
                    drawCircle(led, 3.4f * k, c)
                    if (syncing) rotate(turn.value, c) {
                        drawArc(led, -90f, 90f, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
                    } else drawArc(led, -90f, 360f, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
                } else {
                    drawArc(ink.copy(alpha = 0.3f), 0f, 360f, false, tl, box, style = Stroke(stroke))
                    drawCircle(ink.copy(alpha = 0.4f), 2.6f * k, c)
                }
            }
        },
    )
}

/** Where the band's notches sit, above and below the pod, on the 120 by 150 grid. */
private val Notches = floatArrayOf(8f, 16f, 24f, 32f, 118f, 126f, 134f, 142f)

/** The resting turn when the strap isn't syncing. */
private val NoTurn: State<Float> = mutableFloatStateOf(0f)

/** The strap's own light, a soft green, when connected. */
internal val StrapLight = Color(0xFF7DE0C0)
