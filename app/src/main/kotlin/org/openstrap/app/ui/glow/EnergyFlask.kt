package org.openstrap.app.ui.glow

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.openstrap.app.data.Recovery
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.TabCopy
import org.openstrap.app.presentation.chargeShown
import org.openstrap.app.ui.dashboard.Narrative
import org.openstrap.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * What Today's Energy flask shows, worked out without a screen: Energy now ([level], whole as the Energy detail's
 * headline, [chargeShown]) with its band's [word], the [line] under the number, and Energy at waking for the figures
 * under the hero ([atWake], "At start" when [atStart]).
 */
@Immutable
internal data class FlaskModel(
    /** Energy now, 0 to 100; null leaves the flask empty: before the first night, or no value yet. */
    val level: Int?,
    /** The level's word from Energy's scale ("Good"); null without a level. */
    val word: String?,
    /** Under the number, as Today's Energy card said it: since waking, or why there's no number yet. */
    val line: String,
    /** Energy at waking; null before the first night or without one. */
    val atWake: Int?,
    /** After a night with the strap off the day restarted from an estimate, so [atWake] is "At start". */
    val atStart: Boolean,
) {
    /** How full the flask is, 0 to 1. */
    val fill: Float get() = (level ?: 0).coerceIn(0, FULL) / FULL.toFloat()

    companion object {
        const val FULL = 100
    }
}

/**
 * The flask for today's [charge] as Recovery works it out, null when that failed or hasn't arrived. Charge has no
 * forecast of when it runs low, so the line under the number is the Energy card's own ([TabCopy.chargeLine]): how much
 * was used or gained since waking, and before a number why there's none. A failed or missing read waits as a day
 * with no heart rate yet, as the Energy widget does: the first night's line is only for before it.
 */
internal fun flaskModel(charge: Recovery.ChargeDay?, strings: Strings = Strings.app): FlaskModel {
    val day = charge?.takeIf { it.anchored }
    val now = day?.latest?.let(::chargeShown)
    val atWake = day?.atWake?.let(::chargeShown)
    return FlaskModel(
        level = now,
        word = now?.let { Narrative.chargeHeadline(it, strings) },
        line = TabCopy.chargeLine(now, atWake, anchored = charge?.anchored ?: true, strings = strings),
        atWake = atWake,
        atStart = day?.strapOffOvernight == true,
    )
}

private val Liquid = listOf(Color(0xFFFFB067), Color(0xFFFF6B5B))
private val WaveFront = Color(0xFFFFB067)
private val WaveBack = Color(0xFFFF8A5B).copy(alpha = 0.7f)
/** Energy's warm hue for the glow around the glass. */
private val FlaskGlow = Color(0xFFFF8A5B)
private val Faint: Color get() = AppColors.muted

/** How long each wave takes to travel one wavelength, the front one way and the back the other, seconds. */
private const val WAVE_FRONT_S = 3f
private const val WAVE_BACK_S = 4.6f

/** A bubble: where it rises across the glass (share of the width), its size, its cycle and its delay, seconds. */
private class Bubble(val x: Float, val radiusDp: Float, val period: Float, val delay: Float)

private val Bubbles = listOf(Bubble(0.40f, 3f, 3.5f, 0f), Bubble(0.58f, 3f, 3.5f, 1.6f), Bubble(0.49f, 2f, 4.1f, 0.9f))

/**
 * Today's hero: Energy as a round flask, filled from the bottom to its level with two waves on the surface running
 * opposite ways and a few bubbles rising, under it the number thin with a small "%" and the [FlaskModel.line]. The
 * level fills up from empty when it first shows, as the other heroes count up. Without a level the flask stays empty
 * and only the line shows, no lone dash. The motion is read in the draw phase only, so it never recomposes, and it
 * holds still while the list scrolls, the page is off screen, or the system's animations are off.
 */
@Composable
internal fun EnergyFlask(model: FlaskModel, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(model.fill) {
        fill.animateTo(model.fill, tween(1800, 300, CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    val seconds = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val animate = remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val listPaused by rememberUpdatedState(LocalGlowPaused.current)
    val pagePaused by rememberUpdatedState(LocalGlowPagePaused.current)
    val paused = { listPaused() || pagePaused() }
    val liquid = model.level != null
    // Time only runs while the glass is seen and still, as a glow card's core, so the waves pick up where they stopped.
    if (animate && liquid) LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            if (paused()) {
                snapshotFlow { paused() }.first { !it }
                last = withFrameNanos { it }
            }
            withFrameNanos { now ->
                seconds.floatValue += (now - last) / 1e9f
                last = now
            }
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(FLASK_DP.dp), contentAlignment = Alignment.Center) {
            // The warm glow around the glass, drawn past its edge.
            Canvas(Modifier.size(FLASK_DP.dp)) {
                val r = size.minDimension / 2
                val reach = r + 30.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, r / reach to FlaskGlow.copy(alpha = if (liquid) 0.22f else 0.12f), 1f to Color.Transparent,
                        center = center, radius = reach,
                    ),
                    reach,
                )
            }
            // The glass: everything inside is clipped to the circle.
            Canvas(Modifier.size(FLASK_DP.dp).clip(CircleShape)) {
                val w = size.width
                val h = size.height
                // Read while drawing, so a theme switch redraws the glass. On the light theme the glass is a touch of white
                // fading to a navy tint at its rim, so it reads as a sphere on cream and its white shine still shows.
                val light = AppColors.light
                val body = if (light) listOf(Color.White.copy(alpha = 0.55f), AppColors.ink.copy(alpha = 0.05f))
                else listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                drawRect(Brush.radialGradient(body, Offset(w * 0.35f, h * 0.30f), w * 0.955f))
                if (liquid) drawLiquid(fill.value, seconds.floatValue)
                // The shine, top left, tilted with the glass.
                val shineTopLeft = Offset(w * 34f / FLASK_DP, h * 26f / FLASK_DP)
                val shineSize = Size(w * 46f / FLASK_DP, h * 24f / FLASK_DP)
                rotate(-30f, shineTopLeft + Offset(shineSize.width / 2, shineSize.height / 2)) {
                    drawOval(Color.White.copy(alpha = if (light) 0.7f else 0.14f), shineTopLeft, shineSize)
                }
                val stroke = 1.5.dp.toPx()
                drawCircle(AppColors.ring, w / 2 - stroke / 2, style = Stroke(stroke))
            }
        }
        if (model.level != null) {
            Spacer(Modifier.height(8.dp))
            Ltr {
                Row(verticalAlignment = Alignment.Top) {
                    Text(model.level.toString(), color = AppColors.ink, style = GlowType.flaskNumber)
                    Text("%", Modifier.padding(top = 6.dp), color = Faint, style = GlowType.flaskUnit)
                }
            }
        } else Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(4.dp))
        Text(model.line, Modifier.padding(horizontal = 24.dp), color = Faint, textAlign = TextAlign.Center, style = GlowType.secondary)
    }
}

/** The glass's width and height, dp. */
private const val FLASK_DP = 190f

/** The liquid up to [level] (0 to 1) with its two waves and the bubbles, at [t] seconds into the motion. */
private fun DrawScope.drawLiquid(level: Float, t: Float) {
    val w = size.width
    val h = size.height
    val surface = h * (1f - level.coerceIn(0f, 1f))
    drawRect(Brush.verticalGradient(Liquid, startY = surface, endY = h), Offset(0f, surface), Size(w, h - surface))
    // The waves: one wavelength across the glass, a crest 4 dp above and below a line 4 dp over the surface, each
    // filled down to 3 dp into the liquid, as the mock's two SVG waves.
    val amp = 4.dp.toPx()
    val mid = surface - 4.dp.toPx()
    val floor = surface + 3.dp.toPx()
    drawPath(wave(w, mid, floor, amp, (t / WAVE_FRONT_S) % 1f, +1f), WaveFront)
    drawPath(wave(w, mid, floor, amp, -(t / WAVE_BACK_S) % 1f, -1f), WaveBack)
    // Bubbles rise from near the bottom to just under the surface, fading as they go; never above the liquid.
    val bottom = h - 12.dp.toPx()
    val travel = (bottom - surface - 10.dp.toPx()).coerceAtMost(90.dp.toPx())
    if (travel <= 0f) return
    clipRect(top = surface) {
        Bubbles.forEach { b ->
            val phase = (((t - b.delay) % b.period) + b.period) % b.period / b.period
            // Ease in, as the mock's: slow off the bottom, quicker as it nears the top.
            val p = phase * phase
            drawCircle(Color.White.copy(alpha = 0.5f * (1f - phase)), b.radiusDp.dp.toPx(), Offset(w * b.x, bottom - travel * p))
        }
    }
}

/**
 * A sine wave across [width] about [mid] with [amp], shifted by [shift] of a wavelength and drawn upright ([sign] 1)
 * or upside down (-1), closed down to [floor].
 */
private fun wave(width: Float, mid: Float, floor: Float, amp: Float, shift: Float, sign: Float): Path = Path().apply {
    val step = width / 48f
    moveTo(0f, floor)
    var x = 0f
    while (x <= width + step) {
        lineTo(x, mid - sign * amp * sin(2 * PI * (x / width + shift)).toFloat())
        x += step
    }
    lineTo(width + step, floor)
    close()
}
