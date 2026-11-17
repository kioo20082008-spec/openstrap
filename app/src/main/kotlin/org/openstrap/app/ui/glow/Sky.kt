package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

/**
 * The rings design's background: one flat page colour from the theme ([AppColors.page]), with no glow, gradient or
 * grain. [tint] stays in the signature, so every tab still names its own sky.
 */
@Composable
fun SkyBackground(tint: SkyTint, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Read while drawing, so a theme switch redraws the page without composing anything.
            drawRect(AppColors.page)
        }
        content()
    }
}

/** Glass: what a card is drawn in over the sky, in the theme's colours. */
internal val GlassFill: Color get() = AppColors.glass
internal val GlassEdge: Color get() = AppColors.hairline
