package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import org.openstrap.app.ui.theme.MoodTint
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openstrap.app.ui.theme.AppColors

/** Each tab's sky: the glow at its top, in the tab's own colour. */
enum class SkyTint(val glow: Color, val second: Color) {
    Today(Color(0xFFFF9662), Color(0xFF7888FF)),
    Recovery(Color(0xFFFF9662), Color(0xFFFF6B5B)),
    Sleep(Color(0xFF6C8CFF), Color(0xFFC5A3FF)),
    Activity(Color(0xFF7FE3C0), Color(0xFF3CA096)),
}

/** The rings hue of each tab, for the faint living wash over its page. */
internal val SkyTint.ringHue: Color get() = when (this) {
    SkyTint.Today -> RingColors.Energy
    SkyTint.Recovery -> RingColors.Recovery
    SkyTint.Sleep -> RingColors.Sleep
    SkyTint.Activity -> RingColors.Steps
}

/**
 * The rings design's background: one flat page colour from the theme ([AppColors.page]), with no gradient or grain,
 * and over its upper part a very faint, slowly drifting wash of the tab's own hue ([LiveAura]), so the page stays
 * near-black but never quite still. The wash runs on the tab's clock, so it holds still off screen.
 */
@Composable
fun SkyBackground(tint: SkyTint, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val clock = rememberLiveClock()
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Read while drawing, so a theme switch redraws the page without composing anything.
            drawRect(AppColors.page)
            // The page's mood: recovery and the hour, a tint of a few percent, one more flat rectangle.
            drawRect(AppColors.mood)
        }
        // Fainter still on the light theme, where any hue reads stronger.
        LiveAura(tint.ringHue, clock, Modifier.fillMaxWidth().fillMaxHeight(0.7f), strength = if (AppColors.light) 0.18f else 0.32f, top = 0.18f)
        content()
    }
}

/** Glass: what a card is drawn in over the sky, in the theme's colours. */
internal val GlassFill: Color get() = AppColors.glass
internal val GlassEdge: Color get() = AppColors.hairline

/**
 * Sets the page's mood tint ([AppColors.mood]) from the day's Recovery [score] and the hour, once for the app. The hour
 * is looked at again every ten minutes (a sleeping coroutine, not an animation), and the tint is written only when it
 * changes, so nothing composes because of it.
 */
@Composable
fun MoodEffect(score: Int?) {
    val hour by produceState(java.time.LocalTime.now().hour) {
        while (true) {
            delay(HOUR_CHECK_MS)
            value = java.time.LocalTime.now().hour
        }
    }
    val light = AppColors.light
    val tint = remember(score, hour, light) { MoodTint.of(score, hour, light) }
    SideEffect { if (AppColors.mood != tint) AppColors.mood = tint }
    DisposableEffect(Unit) { onDispose { AppColors.mood = Color.Transparent } }
}

private const val HOUR_CHECK_MS = 10 * 60_000L
