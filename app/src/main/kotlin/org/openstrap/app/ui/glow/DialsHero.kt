package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import org.openstrap.app.ui.theme.AppColors
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
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
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

// The theme's colours ([AppColors]), read on each use so a theme switch shows at once.
private val Track: Color get() = AppColors.ink.copy(alpha = 0.15f)
private val Muted: Color get() = AppColors.muted
private val PlanSurface: Color get() = AppColors.glass
private val PlanEdge: Color get() = AppColors.hairline

/**
 * The redesign's Today hero: Energy as a round flask ([EnergyFlask]) with its name and level word, the day's plan,
 * then Energy at waking, Recovery and Sleep in one pane of glass, and the plan's card. Tapping the flask opens the
 * Energy detail; Recovery opens from its figure. The figures are laid out start to end, so in Arabic Energy at waking
 * sits on the right and Sleep on the left.
 */
@Composable
internal fun DialsHero(
    energy: FlaskModel, recovery: Int?, sleep: Int?,
    labels: Triple<String, String, String>,
    planTitle: String, planNote: String, planChip: String?, plan: List<PlanItem>,
    onEnergy: () -> Unit, onRecovery: () -> Unit, onSleep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Room above the glass for its glow, inside the ripple's corners.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onEnergy).padding(top = 24.dp, bottom = 12.dp), contentAlignment = Alignment.TopCenter) {
            EnergyFlask(energy)
        }
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.glow_today_energy_title))
                energy.word?.let { withStyle(SpanStyle(color = AppColors.ink)) { append(" · "); append(it) } }
            },
            color = Muted, style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(planTitle, color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(14.dp))
        Text(planNote, color = AppColors.ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(20.dp))
        // Three figures in one pane of glass, hairlines between: Energy at waking, Recovery, Sleep.
        val shape = RoundedCornerShape(24.dp)
        Row(Modifier.fillMaxWidth().clip(shape).background(PlanSurface).border(1.dp, PlanEdge, shape).padding(vertical = 14.dp)) {
            listOf(
                Triple(labels.first, energy.atWake?.let { "$it" }, onEnergy),
                Triple(labels.second, recovery?.let { "$it" }, onRecovery),
                Triple(labels.third, sleep?.let { "$it" }, onSleep),
            ).forEachIndexed { i, (k, v, open) ->
                if (i > 0) Box(Modifier.width(1.dp).height(52.dp).background(PlanEdge))
                Column(Modifier.weight(1f).clickable(onClick = open).padding(horizontal = 16.dp)) {
                    Text(k, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(v ?: "–", color = AppColors.ink, style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Light, fontSize = 28.sp, textDirection = TextDirection.Ltr))
                }
            }
        }
        if (plan.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PlanCard(planTitle, planNote, planChip, plan)
        }
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
                if (sweep > 0f) drawArc(AppColors.readable(color), -90f, sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(
                buildAnnotatedString {
                    append(value ?: "–")
                    if (value != null && unit != null) withStyle(SpanStyle(fontSize = number * 0.4f, color = Muted, fontWeight = FontWeight.Bold)) { append(unit) }
                },
                color = AppColors.ink, maxLines = 1,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = number, textDirection = TextDirection.Ltr),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PlanCard(title: String, note: String, chip: String?, items: List<PlanItem>) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(PlanSurface).border(1.dp, PlanEdge, shape).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            if (chip != null) Row(
                Modifier.clip(CircleShape).background(AppColors.raised).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.ink))
                Spacer(Modifier.width(8.dp))
                Text(chip, color = AppColors.ink, style = MaterialTheme.typography.labelLarge)
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
                        item.title, color = if (item.done) Muted else AppColors.ink,
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
        } else drawCircle(AppColors.ink.copy(alpha = 0.4f), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
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
            Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_title), Modifier.weight(1f), color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
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
                    Text(t.title, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
