package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random
import org.openstrap.app.ui.theme.AppColors

/** Each tab's sky: the glow at its top, in the tab's own colour. */
enum class SkyTint(val glow: Color, val second: Color) {
    Today(Color(0xFFFF9662), Color(0xFF7888FF)),
    Recovery(Color(0xFFFF9662), Color(0xFFFF6B5B)),
    Sleep(Color(0xFF6C8CFF), Color(0xFFC5A3FF)),
    Activity(Color(0xFF7FE3C0), Color(0xFF3CA096)),
}

/** A fine grain over the sky, so it reads as a surface rather than flat colour: one tile, drawn once. */
private val Grain: ImageBitmap by lazy {
    val n = 128
    val px = IntArray(n * n)
    val rnd = Random(11)
    for (i in px.indices) {
        val v = rnd.nextInt(256)
        px[i] = android.graphics.Color.argb(rnd.nextInt(10, 26), v, v, v)
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The redesign's background: the theme's sky ([AppColors]: navy, black, cream or dusk) with a soft glow at the top in
 * [tint], held back as the theme says (OLED black keeps it low), and grain over it.
 */
@Composable
fun SkyBackground(tint: SkyTint, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val grain = remember { ShaderBrush(ImageShader(Grain, TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Read while drawing, so a theme switch redraws the sky without composing anything.
            val t = AppColors.current
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(0f to t.skyTop, 0.45f to t.skyMid, 1f to t.skyLow))
            drawRect(
                Brush.radialGradient(
                    listOf(tint.glow.copy(alpha = 0.5f * t.glow), tint.glow.copy(alpha = 0.22f * t.glow), Color.Transparent),
                    Offset(w / 2, -h * 0.06f), w * 1.15f,
                ),
            )
            drawRect(Brush.radialGradient(listOf(tint.second.copy(alpha = 0.14f * t.glow), Color.Transparent), Offset(w * 0.85f, h * 0.28f), w * 0.8f))
            drawRect(grain, alpha = t.grain.coerceIn(0f, 1f))
        }
        content()
    }
}

/** Glass: what a card is drawn in over the sky, in the theme's colours. */
internal val GlassFill: Color get() = AppColors.glass
internal val GlassEdge: Color get() = AppColors.hairline
