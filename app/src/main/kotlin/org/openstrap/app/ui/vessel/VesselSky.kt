package org.openstrap.app.ui.vessel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import org.openstrap.app.ui.theme.AppColors

/**
 * The vessel design's page: the theme's sky from top to bottom (the sky colours are the theme's [AppColors] tokens) and,
 * at its upper end, one wide soft glow in [glow], the hue of what the page is about. Pages over a tab use the same, so
 * a detail keeps the colour of the tab it opened from. The page's mood tint goes over it.
 */
@Composable
fun VesselSky(glow: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Read while drawing, so a theme switch redraws the page without composing anything.
            val t = AppColors.current
            drawRect(Brush.verticalGradient(0f to t.skyTop, 0.55f to t.skyMid, 1f to t.skyLow))
            val g = glow.copy(alpha = (glow.alpha * t.glow).coerceIn(0f, 1f))
            if (g.alpha > 0f) {
                // An ellipse 110% of the width across and 38% of the height down, centred right of middle, just over the top.
                val cx = size.width * 0.75f
                val cy = -size.height * 0.04f
                val rx = size.width * 1.1f
                val ry = size.height * 0.38f
                withTransform({ scale(1f, ry / rx, Offset(cx, cy)) }) {
                    drawCircle(
                        Brush.radialGradient(0f to g, 0.7f to Color.Transparent, 1f to Color.Transparent, center = Offset(cx, cy), radius = rx),
                        radius = rx, center = Offset(cx, cy),
                    )
                }
            }
            drawRect(AppColors.mood)
        }
        content()
    }
}
