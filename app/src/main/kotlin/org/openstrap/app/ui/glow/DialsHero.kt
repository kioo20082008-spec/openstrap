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
import androidx.compose.foundation.layout.heightIn
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
import org.openstrap.app.ui.theme.AppIcons
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
        val energyTitle = stringResource(R.string.glow_today_energy_title)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onEnergy).padding(top = 12.dp, bottom = 14.dp), contentAlignment = Alignment.TopCenter) {
            TripleRings(energy, recovery, sleep, word = energyTitle)
        }
        // The rings' three figures as cards, each with its own bar: Energy, Recovery, Sleep.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RingStatCard(energyTitle, energy.level, RingColors.Energy, onEnergy, Modifier.weight(1f), AppIcons.Lightning)
            RingStatCard(labels.second, recovery, RingColors.Recovery, onRecovery, Modifier.weight(1f), AppIcons.HeartFill)
            RingStatCard(labels.third, sleep, RingColors.Sleep, onSleep, Modifier.weight(1f), AppIcons.MoonFill)
        }
        Spacer(Modifier.height(10.dp))
        EnergyDayCard(energy, labels.first)
        Spacer(Modifier.height(10.dp))
        RingAdviceCard(planTitle, planNote, RingColors.Energy)
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
    // The advice card above already says [title] and [note]: the plan is a list, headed by its chip.
    Column(Modifier.fillMaxWidth().ringCard().padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = AppColors.ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            if (chip != null) StatusPill(chip, RingColors.Recovery)
        }
        items.forEach { item ->
            RingDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).let { m -> item.onClick?.let { m.clickable(onClick = it) } ?: m }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title, color = if (item.done) Muted else AppColors.ink,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textDecoration = if (item.done) TextDecoration.LineThrough else null),
                    )
                    Text(item.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Tick(item.done)
            }
        }
    }
}

@Composable
private fun Tick(done: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val r = size.minDimension / 2
        if (done) {
            drawCircle(RingColors.Energy, r)
            val p = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.66f)
                lineTo(size.width * 0.71f, size.height * 0.38f)
            }
            drawPath(p, OnHueInk, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        } else drawCircle(AppColors.raised, r)
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
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).ringCard().padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_title), Modifier.weight(1f), color = AppColors.ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            if (live) {
                val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
                val a by blink.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
                Box(Modifier.size(8.dp).clip(CircleShape).background(RingColors.Energy.copy(alpha = a)))
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_live), color = AppColors.readable(RingColors.Energy), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        if (tips.isEmpty()) {
            RingDivider()
            Text(androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.coach_all_good), Modifier.padding(vertical = 14.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        tips.take(3).forEach { t ->
            RingDivider()
            Spacer(Modifier.height(12.dp))
            // One hue a tone, from the rings palette: a warning in Energy's, calm in Sleep's, good in Recovery's.
            val tone = when (t.tone) {
                org.openstrap.app.presentation.Coach.Tone.Warn -> RingColors.Energy
                org.openstrap.app.presentation.Coach.Tone.Calm -> RingColors.Sleep
                org.openstrap.app.presentation.Coach.Tone.Good -> RingColors.Recovery
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = AppColors.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
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
                            Modifier.clip(CircleShape).background(tone).clickable(onClick = action.second).padding(horizontal = 16.dp, vertical = 8.dp),
                            color = OnHueInk, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
