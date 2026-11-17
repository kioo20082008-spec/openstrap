package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.NumberFamily
import org.openstrap.app.ui.theme.RingsPalette

/**
 * Today's hero in the Rings look: three rings nested from the outside in, Energy, Recovery, Sleep, each in its own
 * colour on a track of the same colour at 20%, starting at the top and running clockwise with round ends. Energy's
 * value stands in the middle in bold numerals. The rings sweep up from empty once, when they first show.
 */
@Composable
internal fun RingsHero(energy: Int?, recovery: Int?, sleep: Int?, modifier: Modifier = Modifier) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1200, 100, CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
    val rings = listOf(
        Triple(energy, RingsPalette.Energy, RingsPalette.EnergyTrack),
        Triple(recovery, RingsPalette.Recovery, RingsPalette.RecoveryTrack),
        Triple(sleep, RingsPalette.Sleep, RingsPalette.SleepTrack),
    )
    Box(modifier.size(RINGS_DP.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RINGS_DP.dp)) {
            val stroke = size.width * 0.085f
            val gap = 5.dp.toPx()
            rings.forEachIndexed { i, (value, color, track) ->
                val inset = stroke / 2 + i * (stroke + gap)
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                val share = ((value ?: 0).coerceIn(0, 100) / 100f) * sweep.value
                if (share > 0f) drawArc(color, -90f, 360f * share, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                buildAnnotatedString {
                    append(energy?.toString() ?: "–")
                    if (energy != null) withStyle(SpanStyle(fontSize = 22.sp, color = AppColors.muted)) { append("%") }
                },
                color = AppColors.ink, maxLines = 1,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, textDirection = TextDirection.Ltr),
            )
        }
    }
}

private const val RINGS_DP = 248
