package org.openstrap.app.ui.device

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.enterRise
import org.openstrap.app.ui.glow.rememberLoop
import org.openstrap.app.ui.theme.AppColors

/*
 * Life for the rings pages (You, Strap, Alarms, Firmware, Zepp): cards and rows cascade in by the order they first
 * show, rows sink under the finger, bars fill in and a sync runs as a travelling dot. Cheap: layer and draw reads only.
 */

/**
 * The order things first show in, so a page's cards cascade without each call site counting: everything composed
 * within [WINDOW_NS] of the last one takes the next step; after a pause (a scroll, a new page) it starts again at 0.
 * Composition runs on the main thread only.
 */
private object RiseOrder {
    private const val WINDOW_NS = 160_000_000L
    private var last = 0L
    private var count = 0
    fun next(): Int {
        val now = System.nanoTime()
        if (now - last > WINDOW_NS) count = 0
        last = now
        return count++
    }
}

/** Rises into place once ([enterRise]), at its turn among the things showing with it. */
@Composable
internal fun Modifier.riseIn(): Modifier {
    val index = remember { RiseOrder.next() }
    return enterRise(index)
}

/**
 * Sinks a little under the finger and springs back, without taking the press from the clickable inside it, so its
 * ripple, role and click stay as they were. A quick tap gives a short dip; a scroll that starts on it leaves it still.
 */
@Composable
internal fun Modifier.pressSink(enabled: Boolean = true, depth: Float = 0.97f): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // 1 = lifted quickly, 0 = cancelled (a scroll took it), null = still held after the wait.
            val early = withTimeoutOrNull(90L) { if (waitForUpOrCancellation() != null) 1 else 0 }
            when (early) {
                1 -> scope.launch {
                    scale.animateTo(depth, tween(70))
                    scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
                0 -> Unit
                else -> {
                    scope.launch { scale.animateTo(depth, tween(110)) }
                    waitForUpOrCancellation()
                    scope.launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                }
            }
        }
    }.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** A [PageRow] that rises in at its turn and, when it opens something, sinks under the finger. Same parameters. */
@Composable
fun RingsRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    below: Boolean = false,
    divider: Boolean = true,
    titleColor: Color = AppColors.ink,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    selected: Boolean = false,
    dot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().riseIn().pressSink(onClick != null && enabled, 0.98f)) {
        PageRow(title, value, onClick, below, divider, titleColor, enabled, icon, selected, dot, trailing)
    }
}

/** Grows in along its width from its start edge, [index] steps (90 ms) after the first. */
@Composable
internal fun Modifier.growIn(index: Int = 0): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200L + index * 90L)
        p.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        scaleX = p.value
        transformOrigin = TransformOrigin(1f, 0.5f)
    }
}

/**
 * A sync in progress: a quiet track in [hue] with a bright dot and its short tail travelling along it from the start
 * edge, one run every 1.4 s. Replaces the plain sliding bar on the Strap page.
 */
@Composable
internal fun SyncTrail(hue: Color, modifier: Modifier = Modifier.fillMaxWidth(), thickness: Dp = 4.dp) {
    val run = rememberLoop(1400)
    Canvas(modifier.height(thickness * 3)) {
        val y = size.height / 2
        val t = thickness.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        drawLine(hue.copy(alpha = 0.18f), Offset(t, y), Offset(size.width - t, y), t, StrokeCap.Round)
        val span = size.width - 2 * t
        val f = FastOutSlowInEasing.transform(run.value)
        val x = if (rtl) size.width - t - span * f else t + span * f
        val tail = size.width * 0.22f
        val from = if (rtl) (x + tail).coerceAtMost(size.width - t) else (x - tail).coerceAtLeast(t)
        drawLine(
            Brush.horizontalGradient(listOf(hue.copy(alpha = 0f), hue), startX = from, endX = x),
            Offset(from, y), Offset(x, y), t, StrokeCap.Round,
        )
        drawCircle(hue.copy(alpha = 0.25f), t * 1.5f, Offset(x, y))
        drawCircle(hue, t * 0.8f, Offset(x, y))
    }
}

/** A [org.openstrap.app.ui.glow.PageSwitchRow] that rises in at its turn and sinks under the finger. Same parameters. */
@Composable
fun RingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    divider: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Box(Modifier.fillMaxWidth().riseIn().pressSink(enabled, 0.98f)) {
        org.openstrap.app.ui.glow.PageSwitchRow(title, subtitle, checked, onChange, divider, enabled, icon)
    }
}
