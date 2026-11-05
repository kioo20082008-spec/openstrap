package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.AppPalette
import org.openstrap.app.ui.theme.NumberFamily

private val Amber = Color(0xFFF5B657)
private val Mint = Color(0xFF5FD3A6)
private val Muted = Color(0xFFA9ADB9)

/** The zone colour for a Recovery score: rest, steady, ready. */
internal fun zoneColor(score: Int) = when {
    score < 34 -> AppPalette.Coral
    score < 67 -> Amber
    else -> Mint
}

/**
 * The redesign's Recovery hero, "Pulse": a heart beating at your resting rate with ripples, a heart trace running behind
 * the score as it counts up, the score's word, then the three zones with a marker sliding to where you are.
 */
@Composable
internal fun PulseHero(
    score: Int, word: String, line: String, sentence: String?, zones: Triple<String, String, String>,
    restingHr: Int?, onOpen: () -> Unit, info: @Composable () -> Unit,
) {
    val beatMs = (60_000 / (restingHr ?: 60).coerceIn(35, 120))
    val pulse = rememberInfiniteTransition(label = "pulse")
    val beat by pulse.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = beatMs
            1f at 0; 1.25f at (beatMs * 0.15f).toInt(); 1f at (beatMs * 0.3f).toInt(); 1.12f at (beatMs * 0.45f).toInt(); 1f at beatMs
        }),
        label = "beat",
    )
    val ripple1 = pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(beatMs, easing = LinearEasing)), label = "r1")
    val ripple2 = pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(beatMs, easing = LinearEasing), RepeatMode.Restart, StartOffset(beatMs / 4)), label = "r2",
    )
    val trace by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "trace")
    val shown = remember(score) { Animatable(0f) }
    LaunchedEffect(score) { shown.animateTo(score.toFloat(), tween(1400, easing = FastOutSlowInEasing)) }
    val marker = remember(score) { Animatable(0f) }
    LaunchedEffect(score) { marker.animateTo(score / 100f, tween(1600, easing = FastOutSlowInEasing)) }
    val color = zoneColor(score)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The heart trace, left to right in every language, behind the heart and the number.
            Ltr {
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 24.dp)) {
                    val w = size.width
                    val h = size.height
                    val base = h * 0.58f
                    val path = Path().apply {
                        moveTo(0f, base)
                        listOf(0.2f, 0.62f).forEach { at ->
                            val x = w * at
                            lineTo(x, base); lineTo(x + w * 0.025f, base - h * 0.17f); lineTo(x + w * 0.045f, base + h * 0.25f)
                            lineTo(x + w * 0.075f, base - h * 0.42f); lineTo(x + w * 0.105f, base + h * 0.35f); lineTo(x + w * 0.125f, base)
                        }
                        lineTo(w, base)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val len = measure.length
                    val head = len * 1.4f * trace
                    val seg = Path()
                    measure.getSegment((head - len * 0.4f).coerceAtLeast(0f), head.coerceAtMost(len), seg, true)
                    drawPath(seg, Color(0xFFFFB59A).copy(alpha = 0.3f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) { info() }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(64.dp), contentAlignment = Alignment.Center) {
                listOf(ripple1, ripple2).forEach { state ->
                    Box(
                        Modifier.size(64.dp).graphicsLayer { val r = state.value; scaleX = 0.6f + 1.4f * r; scaleY = 0.6f + 1.4f * r; alpha = 0.45f * (1f - r) }
                            .border(1.dp, Color(0xFFFFB59A), CircleShape),
                    )
                }
                Icon(AppIcons.Heart, null, tint = Color(0xFFFFB59A), modifier = Modifier.size(30.dp).graphicsLayer { scaleX = beat; scaleY = beat })
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                shown.value.toInt().toString(), color = AppPalette.Cream,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraLight, fontSize = 108.sp, lineHeight = 108.sp, letterSpacing = (-5).sp, textDirection = TextDirection.Ltr),
            )
            Text("%", Modifier.padding(bottom = 64.dp), color = Muted, style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Light, fontSize = 24.sp))
        }
        Text(word, color = AppPalette.Cream, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(6.dp))
        Text(line, color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        // The three zones, read left to right like a scale, the marker sliding to the score.
        Ltr {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                val full = maxWidth
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 until 34 to AppPalette.Coral, 34 until 67 to Amber, 67..100 to Mint).forEach { (range, c) ->
                        Box(Modifier.weight(1f).padding(top = 4.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (score in range) c else Color.White.copy(alpha = 0.16f)))
                    }
                }
                Box(
                    Modifier.offset(x = (full - 10.dp) * marker.value, y = 2.dp).size(10.dp).clip(CircleShape).background(Color(0xFFFFF3E2)),
                )
            }
        }
        Ltr {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp, end = 6.dp)) {
                listOf(zones.first, zones.second, zones.third).forEach {
                    Text(it, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
        if (sentence != null) {
            Spacer(Modifier.height(16.dp))
            Text(sentence, color = Color(0xFFC9CCD6), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}
