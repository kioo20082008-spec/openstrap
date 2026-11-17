package org.openstrap.app.ui.glow

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.openstrap.app.ui.dashboard.EntryLog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * The tabs' share of the alive kit ([Alive.kt]): the same motions, but on a clock that only runs while the tab is on
 * screen and still (every tab stays composed in the pager), and entrances that wait for the tab to be first seen.
 */

/**
 * Whether this tab is (even partly) on screen. The pager provides it per page; outside the pager everything counts as
 * seen. Read through snapshot state, so waiting on it with [snapshotFlow] wakes when the tab comes in.
 */
val LocalTabSeen = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * Seconds that run only while the page is on screen and still, the list isn't flinging and the system's animations are
 * on, so the rings' life costs nothing on the tabs kept ready either side. Read it in a draw or layer block only.
 */
@Composable
internal fun rememberLiveClock(): State<Float> = rememberPausableClock()

/**
 * The one clock behind every looping motion: seconds that run only while the page is on screen and still
 * ([LocalGlowPagePaused]), the list isn't scrolling ([LocalGlowPaused]) and the system's animations are on. With [wrapS]
 * above zero the seconds wrap at it (one lap of a single loop, so floats stay exact however long the app is open).
 * [enabled] false never starts it. Read it in a draw or layer block only.
 */
@Composable
internal fun rememberPausableClock(enabled: Boolean = true, wrapS: Float = 0f): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val listPaused by rememberUpdatedState(LocalGlowPaused.current)
    val pagePaused by rememberUpdatedState(LocalGlowPagePaused.current)
    if (animate && enabled) LaunchedEffect(wrapS) {
        val paused = { listPaused() || pagePaused() }
        var last = withFrameNanos { it }
        while (true) {
            if (paused()) {
                snapshotFlow { paused() }.first { !it }
                last = withFrameNanos { it }
            }
            withFrameNanos { now ->
                // A long gap (a dropped frame or a resume) moves at most a tenth of a second, so nothing jumps.
                val next = seconds.floatValue + ((now - last) / 1e9f).coerceAtMost(0.1f)
                seconds.floatValue = if (wrapS > 0f) next % wrapS else next
                last = now
            }
        }
    }
    return seconds
}

private const val TAU = (2 * PI).toFloat()

/** 0..1..0 once every [period] seconds at [t] seconds, [phase] laps on. */
internal fun wave(t: Float, period: Float, phase: Float = 0f): Float = 0.5f - 0.5f * cos(TAU * (t / period + phase))

/** 0..1, wrapping, once every [period] seconds. */
internal fun lap(t: Float, period: Float): Float = (t / period) % 1f

/** A lub-dub at [bpm]: a strong beat, then a softer one a fifth of the cycle later, 0 at rest and 1 at the first peak. */
internal fun heartbeat(t: Float, bpm: Float = 64f): Float {
    val p = (t * bpm / 60f) % 1f
    fun bump(at: Float, w: Float) = exp(-((p - at) / w).let { it * it })
    return maxOf(bump(0.08f, 0.05f), 0.6f * bump(0.28f, 0.055f))
}

/**
 * The screen whose heroes play their entrance (rings filling, numbers counting up) once a session ([EntryLog]); null,
 * the default, plays it each time the piece is first composed. Provided by Today, Recovery, Sleep and Activity.
 */
val LocalEntryScreen = staticCompositionLocalOf<String?> { null }

/** What a piece with an entrance needs, fixed when it's first composed: its screen, and whether the entrance plays. */
private class Entry(val screen: String?, val plays: Boolean)

@Composable
private fun rememberEntry(): Entry {
    val screen = LocalEntryScreen.current
    return remember { Entry(screen, EntryLog.session.plays(screen, SystemClock.uptimeMillis())) }
}

/**
 * Waits until the entrance may start: the tab is first seen, a beat passes, the page has settled (not moving, covered
 * or off screen) and the list isn't scrolling (given up after a moment, so figures never wait out a long scroll).
 */
private suspend fun awaitEntry(seen: () -> Boolean, page: () -> Boolean, list: () -> Boolean, delayMs: Long) {
    snapshotFlow { seen() }.first { it }
    delay(delayMs)
    snapshotFlow { page() }.first { !it }
    withTimeoutOrNull(LIST_WAIT_MS) { snapshotFlow { list() }.first { !it } }
}

private const val LIST_WAIT_MS = 700L

/**
 * [target] (0 to 1) filling in from empty once the tab is first seen, then easing to later changes. Read its value in a
 * draw or layer block, so only that redraws. Plays once a session for a screen that provides [LocalEntryScreen]: back
 * on it, the share starts at [target].
 */
@Composable
internal fun rememberSeenShare(target: Float, durationMs: Int = 1300, delayMs: Int = 120): Animatable<Float, AnimationVector1D> {
    val entry = rememberEntry()
    val a = remember { Animatable(if (entry.plays) 0f else target.coerceIn(0f, 1f)) }
    val seen = LocalTabSeen.current
    val page by rememberUpdatedState(LocalGlowPagePaused.current)
    val list by rememberUpdatedState(LocalGlowPaused.current)
    LaunchedEffect(target) {
        if (a.value == 0f && target > 0f) {
            awaitEntry(seen, { page() }, { list() }, delayMs.toLong())
            EntryLog.session.begin(entry.screen, SystemClock.uptimeMillis())
        }
        a.animateTo(target.coerceIn(0f, 1f), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a
}

/**
 * [target] counted up from zero once the tab is first seen (and eased to any later change), once a session for a
 * screen that provides [LocalEntryScreen]. A state, read inside the small composable that shows it, so a count-up
 * recomposes that text only and not the hero round it.
 */
@Composable
internal fun rememberSeenCount(target: Int, durationMs: Int = 1200): State<Int> {
    val entry = rememberEntry()
    val a = remember { Animatable(if (entry.plays) 0f else target.toFloat()) }
    val seen = LocalTabSeen.current
    val page by rememberUpdatedState(LocalGlowPagePaused.current)
    val list by rememberUpdatedState(LocalGlowPaused.current)
    LaunchedEffect(target) {
        if (a.value == 0f && target > 0) {
            awaitEntry(seen, { page() }, { list() }, 120L)
            EntryLog.session.begin(entry.screen, SystemClock.uptimeMillis())
        }
        a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return remember(a) { derivedStateOf { a.value.toInt() } }
}

/**
 * When a tab's list first showed, so its cards cascade in once ([riseOnShow]) and a card scrolled to later simply
 * shows. One a list.
 */
internal class RiseGate {
    var seenAt = 0L
}

/**
 * Rises into place once, as [enterRise] does, but only when the tab is first seen: [index] steps after the first, so a
 * tab's cards cascade in as it comes on screen. A card first shown well after that (scrolled to) appears as it is.
 */
@Composable
internal fun Modifier.riseOnShow(index: Int, gate: RiseGate? = null): Modifier {
    val late = remember { gate != null && gate.seenAt != 0L && android.os.SystemClock.uptimeMillis() - gate.seenAt > RISE_WINDOW_MS }
    if (late) return this
    val p = remember { Animatable(0f) }
    val seen = LocalTabSeen.current
    LaunchedEffect(Unit) {
        snapshotFlow { seen() }.first { it }
        if (gate != null && gate.seenAt == 0L) gate.seenAt = android.os.SystemClock.uptimeMillis()
        delay((index.coerceIn(0, 9) * 65).toLong())
        p.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { 26.dp.toPx() }
    return graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * rise
        val s = 0.97f + 0.03f * v
        scaleX = s
        scaleY = s
    }
}

private const val RISE_WINDOW_MS = 1400L

/**
 * A slow living wash of [hue], as [AmbientAura], on the tab's [clock] so it holds still off screen. Not clipped: each
 * blob fades to nothing at its own edge, so it can spill past the hero without a seam. [top] is where its middle sits,
 * as a share of the height.
 */
@Composable
internal fun LiveAura(hue: Color, clock: State<Float>, modifier: Modifier = Modifier, strength: Float = 1f, top: Float = 0.45f) {
    // The wash drifts over 26 s and breathes over 7, so a quarter of the frames show it as smooth as all of them: it reads
    // the clock in steps of 1/AURA_FPS, and sits in a layer of its own so a redraw of it never redraws what is round it.
    val step = remember(clock) { derivedStateOf { (clock.value * AURA_FPS).toInt() } }
    Box(modifier.graphicsLayer()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = step.value / AURA_FPS
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.6f
            val k = (0.15f + 0.07f * wave(t, 7f)) * strength
            val ta = lap(t, 26f) * TAU
            val tb = lap(t, 38f) * TAU
            fun blob(cx: Float, cy: Float, radius: Float, alpha: Float) {
                drawCircle(
                    Brush.radialGradient(listOf(hue.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), radius),
                    radius, Offset(cx, cy),
                )
            }
            blob(w * (0.5f + 0.18f * cos(ta)), h * (top + 0.08f * sin(ta)), r, k)
            blob(w * (0.5f + 0.24f * sin(tb)), h * (top + 0.1f * cos(tb)), r * 0.78f, k * 0.7f)
            blob(w * (0.5f + 0.14f * cos(tb + 2f)), h * (top - 0.08f + 0.06f * sin(ta + 1f)), r * 0.55f, k * 0.6f)
        }
    }
}

private const val AURA_FPS = 20f
