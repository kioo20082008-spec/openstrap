package org.openstrap.app.ui.vessel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.glow.rememberPausableClock
import org.openstrap.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 * The vessel design's one component. A vessel is a glass body with graduations at a quarter, a half and three
 * quarters, holding a liquid whose level IS the reading: a surface with two waves running opposite ways, a light band
 * under it and a thin meniscus. A dashed line across it marks a comparison (yesterday, the usual by now, a limit); an
 * empty one has a dashed rim and waits to be filled. Text and icons go over it as [content]; the vessel only draws.
 *
 * Motion is read while drawing, so a vessel never recomposes for its waves. It holds still while the page is off
 * screen or scrolling, with the system's animations off, and when [calm] (small vessels in rows).
 */

/** How long each wave takes to travel one wavelength, the front one way and the back the other, seconds. */
private const val WAVE_FRONT_S = 3.4f
private const val WAVE_BACK_S = 5.2f
private val Rise = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** [target] (0 to 1) as a level that rises from empty when first shown and eases to each new value. */
@Composable
internal fun rememberLevel(target: Float?): State<Float> {
    val level = remember { Animatable(0f) }
    val shown = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) {
        level.animateTo((target ?: 0f).coerceIn(0f, 1f), tween(1400, 120, Rise)) { shown.floatValue = value }
    }
    return shown
}

/**
 * A vertical vessel filled to [fill] (0 to 1); null draws it empty, dashed. [top] is how far down from the top edge the
 * scale begins, so a title or an icon can sit inside the vessel above the liquid's highest point. [ghost] and [marks]
 * are dashed lines at other levels (0 to 1 on the same scale). [dim] fades the whole vessel (a day not yet lived).
 */
@Composable
fun Vessel(
    fill: Float?,
    hue: VesselHue,
    modifier: Modifier = Modifier,
    ghost: Float? = null,
    marks: List<Float> = emptyList(),
    top: Dp = 0.dp,
    corner: Dp = 26.dp,
    ticks: Boolean = true,
    dim: Boolean = false,
    calm: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val level = rememberLevel(fill)
    val moving = !calm && fill != null && fill > 0f
    val seconds = rememberPausableClock(enabled = moving)
    val front = remember { Path() }
    val back = remember { Path() }
    val edge = remember { Path() }
    Box(modifier.clip(RoundedCornerShape(corner))) {
        Canvas(Modifier.matchParentSize().then(if (dim) Modifier.alpha(0.42f) else Modifier)) {
            drawVessel(
                hue = hue, level = level.value, empty = fill == null, top = top.toPx(), t = if (moving) seconds.value else 0f,
                ghost = ghost, marks = marks, ticks = ticks && size.width >= 34.dp.toPx(), corner = corner.toPx(),
                front = front, back = back, edge = edge,
            )
        }
        content()
    }
}

/** A small, still vessel for a row or a chip: no graduations, a tight corner. */
@Composable
fun MiniVessel(fill: Float?, hue: VesselHue, modifier: Modifier = Modifier, ghost: Float? = null, width: Dp = 22.dp, height: Dp = 32.dp) {
    Vessel(fill, hue, modifier.size(width, height), ghost = ghost, corner = 8.dp, ticks = false, calm = true)
}

private fun DrawScope.drawVessel(
    hue: VesselHue, level: Float, empty: Boolean, top: Float, t: Float, ghost: Float?, marks: List<Float>,
    ticks: Boolean, corner: Float, front: Path, back: Path, edge: Path,
) {
    val w = size.width
    val h = size.height
    val scale = (h - top).coerceAtLeast(1f)
    val ink = AppColors.ink
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
    fun yAt(l: Float) = top + scale * (1f - l.coerceIn(0f, 1f))
    if (empty) {
        val inset = 0.75.dp.toPx()
        drawRoundRect(
            hue.body.copy(alpha = hue.body.alpha * 0.55f), Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), CornerRadius(corner),
        )
        drawRoundRect(
            ink.copy(alpha = 0.16f), Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), CornerRadius(corner),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))),
        )
        if (ticks) for (q in QUARTERS) {
            val y = yAt(q)
            drawLine(ink.copy(alpha = 0.2f), Offset(10.dp.toPx(), y), Offset(tickEnd(q), y), 1.2.dp.toPx())
        }
        if (ghost != null) {
            val y = yAt(ghost)
            drawLine(ink.copy(alpha = 0.35f), Offset(if (ticks) 32.dp.toPx() else 6.dp.toPx(), y), Offset(w - if (ticks) 10.dp.toPx() else 6.dp.toPx(), y), 1.2.dp.toPx(), pathEffect = dash)
        }
        return
    }
    drawRect(Brush.verticalGradient(listOf(hue.body, hue.body.copy(alpha = hue.body.alpha * 0.75f))))
    val surface = yAt(level)
    if (level > 0f) {
        val amp = min(4.dp.toPx(), w / 14f)
        val shiftFront = (t / WAVE_FRONT_S) % 1f
        val shiftBack = -(t / WAVE_BACK_S) % 1f
        // The back wave first, faint and running the other way; then the front one, its light band and the meniscus.
        fillWave(back, w, surface - 2.dp.toPx(), amp, shiftBack, -1f, h)
        drawPath(back, hue.c.copy(alpha = 0.3f))
        fillWave(front, w, surface, amp, shiftFront, +1f, h)
        drawPath(front, Brush.verticalGradient(listOf(hue.c, hue.k), startY = surface - amp, endY = h))
        drawPath(front, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), startY = surface - amp, endY = surface + 30.dp.toPx()))
        surfaceLine(edge, w, surface, amp, shiftFront, +1f)
        drawPath(edge, Color.White.copy(alpha = 0.55f), style = Stroke(1.3.dp.toPx()))
    }
    val surfaceY = if (level > 0f) surface else h
    if (ticks) for (q in QUARTERS) {
        val y = yAt(q)
        val inside = surfaceY < y
        drawLine(if (inside) Color.Black.copy(alpha = 0.22f) else ink.copy(alpha = 0.22f), Offset(10.dp.toPx(), y), Offset(tickEnd(q), y), 1.2.dp.toPx())
    }
    val from = if (ticks) 32.dp.toPx() else 6.dp.toPx()
    val to = w - if (ticks) 10.dp.toPx() else 6.dp.toPx()
    for (g in marks + listOfNotNull(ghost)) {
        val y = yAt(g)
        val inside = y > surfaceY
        drawLine(if (inside) Color.Black.copy(alpha = 0.35f) else ink.copy(alpha = 0.5f), Offset(from, y), Offset(to, y), 1.2.dp.toPx(), pathEffect = dash)
    }
}

private val QUARTERS = listOf(0.25f, 0.5f, 0.75f)

private fun DrawScope.tickEnd(q: Float): Float = (if (q == 0.5f) 26 else 19).dp.toPx()

/** A sine surface across [width] about [mid], closed down to [floor] (the filled liquid), into [into]. */
private fun fillWave(into: Path, width: Float, mid: Float, amp: Float, shift: Float, sign: Float, floor: Float): Path = into.apply {
    rewind()
    val step = width / 40f
    moveTo(0f, floor)
    var x = 0f
    while (x <= width + step) {
        lineTo(x, mid - sign * amp * sin(2 * PI * (x / width + shift)).toFloat())
        x += step
    }
    lineTo(width + step, floor)
    close()
}

/** Only the surface of that wave (the meniscus), open. */
private fun surfaceLine(into: Path, width: Float, mid: Float, amp: Float, shift: Float, sign: Float): Path = into.apply {
    rewind()
    val step = width / 40f
    var x = 0f
    moveTo(0f, mid - sign * amp * sin(2 * PI * shift).toFloat())
    while (x <= width + step) {
        lineTo(x, mid - sign * amp * sin(2 * PI * (x / width + shift)).toFloat())
        x += step
    }
}

/**
 * A horizontal vessel (a bar, a progress, a download) that fills from the start side: the right in Arabic. [ghost] is a
 * dashed marker at that share of the length (the usual by now, a goal). [corner] null makes it a pill.
 */
@Composable
fun HVessel(
    fill: Float?,
    hue: VesselHue,
    modifier: Modifier = Modifier,
    ghost: Float? = null,
    corner: Dp? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val level = rememberLevel(fill)
    val shape = if (corner == null) RoundedCornerShape(percent = 50) else RoundedCornerShape(corner)
    Box(modifier.clip(shape)) {
        Canvas(Modifier.matchParentSize()) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val w = size.width
            val h = size.height
            val ink = AppColors.ink
            if (fill == null) {
                val r = (corner ?: (h / 2).toDp()).toPx()
                val inset = 0.75.dp.toPx()
                drawRoundRect(hue.body.copy(alpha = hue.body.alpha * 0.55f), Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), CornerRadius(r))
                drawRoundRect(
                    ink.copy(alpha = 0.16f), Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), CornerRadius(r),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))),
                )
                return@Canvas
            }
            drawRect(hue.body)
            // Drawn as if filling from the left; in Arabic the whole thing is mirrored.
            withTransform({ if (rtl) scale(-1f, 1f, Offset(w / 2f, h / 2f)) }) {
                val x = w * level.value
                if (level.value > 0f) {
                    hFill(x, h).let { p ->
                        drawPath(p, Brush.horizontalGradient(listOf(hue.k, hue.c), startX = 0f, endX = x.coerceAtLeast(1f)))
                        drawPath(hEdge(x, h), Color.White.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx()))
                    }
                }
                if (ghost != null) {
                    val gx = w * ghost.coerceIn(0f, 1f)
                    drawLine(
                        if (ghost < level.value) Color.Black.copy(alpha = 0.45f) else ink.copy(alpha = 0.45f),
                        Offset(gx, h * 0.2f), Offset(gx, h * 0.8f), 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                }
            }
        }
        content()
    }
}

/** The filled part of a horizontal vessel up to [x], its far edge a gentle wave. */
private fun hFill(x: Float, h: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo((x - 2f).coerceAtLeast(0f), 0f)
    cubicTo(x + 3f, h * 0.3f, x - 3f, h * 0.65f, x + 1f, h)
    lineTo(0f, h)
    close()
}

private fun hEdge(x: Float, h: Float): Path = Path().apply {
    moveTo((x - 2f).coerceAtLeast(0f), 0f)
    cubicTo(x + 3f, h * 0.3f, x - 3f, h * 0.65f, x + 1f, h)
}

/** One layer of a [StackedVessel]: [share] of the whole scale (0 to 1), in [hue]. */
@Immutable
class VesselPart(val share: Float, val hue: VesselHue)

/**
 * A vessel of layers, bottom first, each as tall as its [VesselPart.share] of the scale (the weights of a score, the
 * parts of a day); the parts' shares add up to at most 1, and what is left above is empty glass.
 */
@Composable
fun StackedVessel(parts: List<VesselPart>, modifier: Modifier = Modifier, corner: Dp = 26.dp, ticks: Boolean = true, content: @Composable BoxScope.() -> Unit = {}) {
    val rise = rememberLevel(1f)
    Box(modifier.clip(RoundedCornerShape(corner))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val ink = AppColors.ink
            val body = parts.firstOrNull()?.hue?.body ?: AppColors.glass
            drawRect(body)
            var cum = 0f
            for ((i, p) in parts.withIndex()) {
                val bottom = h - cum * h * rise.value
                cum += p.share.coerceAtLeast(0f)
                val topY = h - cum.coerceAtMost(1f) * h * rise.value
                drawRect(Brush.verticalGradient(listOf(p.hue.c, p.hue.k), startY = topY, endY = bottom), Offset(0f, topY), Size(w, (bottom - topY).coerceAtLeast(0f)))
                if (i > 0) drawLine(AppColors.page.copy(alpha = 0.6f), Offset(0f, bottom), Offset(w, bottom), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, topY), Offset(w, topY), 1.dp.toPx())
            }
            if (ticks && w >= 34.dp.toPx()) for (q in QUARTERS) {
                val y = h * (1f - q)
                drawLine(ink.copy(alpha = 0.22f), Offset(10.dp.toPx(), y), Offset(tickEnd(q), y), 1.2.dp.toPx())
            }
        }
        content()
    }
}
