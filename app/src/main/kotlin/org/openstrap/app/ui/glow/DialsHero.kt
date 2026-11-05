package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppPalette
import org.openstrap.app.ui.theme.NumberFamily

/** One line of the daily plan: done lines are ticked and struck through. */
@androidx.compose.runtime.Immutable
internal data class PlanItem(val title: String, val note: String, val done: Boolean, val onClick: (() -> Unit)? = null)

private val Track = Color(0xFF2E3B5C)
private val Muted = Color(0xFFA9ADB9)
private val PlanSurface = Color(0xFF1C2C50)
private val PlanEdge = Color(0xFF2E4069)

/**
 * The redesign's Today hero: three dials (Sleep, Recovery, Load) with Recovery largest in the middle, then the day's
 * plan. Laid out start to end, so in Arabic Sleep sits on the right and Load on the left.
 */
@Composable
internal fun DialsHero(
    recovery: Int?, sleep: Int?, strain: Float?,
    labels: Triple<String, String, String>,
    planTitle: String, planNote: String, planChip: String?, plan: List<PlanItem>,
    onSleep: () -> Unit, onRecovery: () -> Unit, onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            Dial(labels.first, sleep?.toString(), "%", (sleep ?: 0) / 100f, AppPalette.Cream, 100.dp, 10.dp, 28.sp, onSleep)
            Dial(labels.second, recovery?.toString(), "%", (recovery ?: 0) / 100f, AppPalette.Coral, 132.dp, 13.dp, 40.sp, onRecovery)
            Dial(labels.third, strain?.let { String.format(java.util.Locale.US, "%.1f", it) }, null, (strain ?: 0f) / 21f, AppPalette.Cream, 100.dp, 10.dp, 28.sp, onLoad)
        }
        Spacer(Modifier.height(20.dp))
        PlanCard(planTitle, planNote, planChip, plan)
    }
}

@Composable
private fun Dial(label: String, value: String?, unit: String?, share: Float, color: Color, size: Dp, stroke: Dp, number: TextUnit, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = stroke.toPx()
                val arc = Size(this.size.width - w, this.size.height - w)
                val tl = Offset(w / 2, w / 2)
                drawArc(Track, 0f, 360f, false, tl, arc, style = Stroke(w))
                val sweep = 360f * share.coerceIn(0f, 1f)
                if (sweep > 0f) drawArc(color, -90f, sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(
                buildAnnotatedString {
                    append(value ?: "–")
                    if (value != null && unit != null) withStyle(SpanStyle(fontSize = number * 0.4f, color = Muted, fontWeight = FontWeight.Bold)) { append(unit) }
                },
                color = AppPalette.Cream, maxLines = 1,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = number, textDirection = TextDirection.Ltr),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = AppPalette.Cream, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PlanCard(title: String, note: String, chip: String?, items: List<PlanItem>) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(PlanSurface).border(1.dp, PlanEdge, shape).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = AppPalette.Cream, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            if (chip != null) Row(
                Modifier.clip(CircleShape).background(AppPalette.NavyHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AppPalette.Cream))
                Spacer(Modifier.width(8.dp))
                Text(chip, color = AppPalette.Cream, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(note, color = Muted, style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PlanEdge))
            Row(
                Modifier.fillMaxWidth().let { m -> item.onClick?.let { m.clickable(onClick = it) } ?: m }.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title, color = if (item.done) Muted else AppPalette.Cream,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDecoration = if (item.done) TextDecoration.LineThrough else null),
                    )
                    Text(item.note, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(12.dp))
                Tick(item.done)
            }
        }
    }
}

@Composable
private fun Tick(done: Boolean) {
    Canvas(Modifier.size(30.dp)) {
        val r = size.minDimension / 2
        if (done) {
            drawCircle(AppPalette.Coral, r)
            val p = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.66f)
                lineTo(size.width * 0.71f, size.height * 0.38f)
            }
            drawPath(p, AppPalette.Navy, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        } else drawCircle(Color(0xFF6B7591), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

/** What the coach card can open: a breathing session, and Start a workout. Provided by the activity. */
val LocalOpenBreathe = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** The strap's battery and whether it's charging, for the coach's charging tip. */
val LocalStrapBattery = androidx.compose.runtime.compositionLocalOf<Pair<Int?, Boolean>> { null to false }

/**
 * The coach ([org.openstrap.app.presentation.Coach]): the one or two things your readings say right now, each with
 * the numbers behind it, rewritten as the readings change. Live while heart rate streams from the strap.
 */
@Composable
internal fun CoachCard(tips: List<org.openstrap.app.presentation.Coach.Tip>, live: Boolean, onWorkout: () -> Unit) {
    val breathe = LocalOpenBreathe.current
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(PlanSurface).border(1.dp, PlanEdge, shape).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_title), Modifier.weight(1f), color = AppPalette.Cream, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            if (live) {
                val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
                val a by blink.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
                Box(Modifier.size(8.dp).clip(CircleShape).background(AppPalette.Coral.copy(alpha = a)))
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_live), color = AppPalette.Coral, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (tips.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_all_good), color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        tips.take(3).forEach { t ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PlanEdge))
            Spacer(Modifier.height(12.dp))
            val tone = when (t.tone) {
                org.openstrap.app.presentation.Coach.Tone.Warn -> AppPalette.Coral
                org.openstrap.app.presentation.Coach.Tone.Calm -> Color(0xFFF5B657)
                org.openstrap.app.presentation.Coach.Tone.Good -> Color(0xFF5FD3A6)
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = AppPalette.Cream, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(3.dp))
                    Text(t.body, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    val action = when (t.action) {
                        org.openstrap.app.presentation.Coach.Action.Breathe -> org.openstrap.app.R.string.coach_breathe_action to breathe
                        org.openstrap.app.presentation.Coach.Action.Workout -> org.openstrap.app.R.string.coach_workout_action to onWorkout
                        org.openstrap.app.presentation.Coach.Action.None -> null
                    }
                    if (action != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            androidx.compose.ui.res.stringResource(action.first),
                            Modifier.clip(CircleShape).background(tone.copy(alpha = 0.16f)).clickable(onClick = action.second).padding(horizontal = 14.dp, vertical = 7.dp),
                            color = tone, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}
