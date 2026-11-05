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

/** Each tab's sky: the glow at its top, in the tab's own colour. */
enum class SkyTint(val glow: Color, val second: Color) {
    Today(Color(0xFFFF9662), Color(0xFF7888FF)),
    Recovery(Color(0xFFFF9662), Color(0xFFFF6B5B)),
    Sleep(Color(0xFF6C8CFF), Color(0xFFC5A3FF)),
    Activity(Color(0xFF7FE3C0), Color(0xFF3CA096)),
}

private val Top = Color(0xFF18264A)
private val Mid = Color(0xFF14213D)
private val Low = Color(0xFF0E1830)

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

/** The redesign's background: a deep navy sky with a soft glow at the top in [tint], and grain over it. */
@Composable
fun SkyBackground(tint: SkyTint, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val grain = remember { ShaderBrush(ImageShader(Grain, TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(0f to Top, 0.45f to Mid, 1f to Low))
            drawRect(Brush.radialGradient(listOf(tint.glow.copy(alpha = 0.5f), tint.glow.copy(alpha = 0.22f), Color.Transparent), Offset(w / 2, -h * 0.06f), w * 1.15f))
            drawRect(Brush.radialGradient(listOf(tint.second.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.85f, h * 0.28f), w * 0.8f))
            drawRect(grain)
        }
        content()
    }
}

/** Glass: what a card is drawn in over the sky. */
internal val GlassFill = Color.White.copy(alpha = 0.055f)
internal val GlassEdge = Color.White.copy(alpha = 0.10f)
