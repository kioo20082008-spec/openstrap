package org.openstrap.app.ui.glow

import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.DayWords
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import org.openstrap.app.ui.theme.NumberFamily
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import org.openstrap.app.data.Phases
import org.openstrap.app.data.Route
import org.openstrap.app.data.Strength
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.presentation.EarnedLine
import org.openstrap.app.ui.workout.FamilySummary
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.familyColor
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.dashboard.LocalHeartZones

/*
 * The story cards a workout shares as (workouts spec 2.9, boards ShareRun to ShareGame): 9 by 16, one for each kind
 * of workout, lit from below in the kind's colour or laid over your photo with a dark fade so the text reads. Drawn at
 * [StoryWidth] by [StoryHeight] and scaled, so the preview and the saved 1080 by 1920 picture are the same card.
 */

/** The card's own width, in its own dp: the saved picture is three pixels to each. */
internal val StoryWidth = 360.dp

/** The card's own height, 16 to [StoryWidth]'s 9. */
internal val StoryHeight = 640.dp

/** A chip on a card: the best it set, else the weekly goal. */
internal class StoryChip(val text: String, val best: Boolean, val met: Boolean)

/** What a share card says, by kind of workout. Every text is ready to show. */
internal sealed interface StoryCard {
    val family: Workouts.Family

    /** A run, ride or walk: distance, time and pace (or what it has), a best, the sport and the date. */
    class Run(val stats: List<StoryStat>, val chip: StoryChip?, val footer: String) : StoryCard {
        override val family = Workouts.Family.Distance
    }

    /** Strength: kg lifted in dots, each exercise's sets and top weight, then the total. */
    class Lift(val whenLine: String, val sport: String, val value: String, val unit: String, val rows: List<Pair<String, String>>, val chip: StoryChip?) : StoryCard {
        override val family = Workouts.Family.Strength
    }

    /** Indoor cardio: the time in dots, the zone strip, average heart rate and kcal, and a chip. */
    class Indoor(val date: String, val sport: String, val time: String, val zones: List<Int>, val line: String?, val chip: StoryChip?) : StoryCard {
        override val family = Workouts.Family.IndoorCardio
    }

    /** Intervals: rounds in dots, a column for each round's peak, the peak and the work. */
    class Rounds(val whenLine: String, val sport: String, val done: String, val unit: String, val peaks: List<Int?>, val maxHeartRate: Int, val line: String?) : StoryCard {
        override val family = Workouts.Family.Intervals
    }

    /** Mind and body: minutes in dots and how the heart settled, over its line. */
    class Calm(val sport: String, val whenLine: String, val minutes: String, val unit: String, val line: String?, val heart: List<Float?>) : StoryCard {
        override val family = Workouts.Family.MindBody
    }

    /**
     * A week (the recap's Share week): the [range], the workouts [count] in dots with the [goalLine], its [days] as
     * the app's week dots, the [totals] and up to two chips, the streak and a best.
     */
    class Week(val range: String, val count: String, val goalLine: String, val days: List<Pair<String, Boolean>>, val totals: List<WeekFigure>, val chips: List<StoryChip>) : StoryCard {
        // Not a kind of workout: the card is lit in the goal green, as the recap is.
        override val family = Workouts.Family.Distance
    }

    /** The big number: the sport and the brand at the top, the minutes huge and thin at the foot over up to three figures. */
    class Big(val sport: String, val minutes: String, val line: String, val stats: List<StoryStat>, override val family: Workouts.Family) : StoryCard

    /** A game: minutes in dots, Load and the peak. */
    class Game(val sport: String, val minutes: String, val unit: String, val load: String?, val peak: String?) : StoryCard {
        override val family = Workouts.Family.Games
    }
}

/** A week's total on its card: its icon, value, the small unit after it, and its label. */
internal class WeekFigure(val icon: androidx.compose.ui.graphics.vector.ImageVector, val value: String, val unit: String?, val label: String)

/** A run's figure: its value, the small unit after it, and its label. */
internal class StoryStat(val value: String, val unit: String?, val label: String)

/**
 * The card for [summary]'s workout of [family], from the [hero] its summary shows, what it [earned] and your
 * [usualLoad] for a game's Load. [now] names the day.
 */
internal fun storyCard(
    family: Workouts.Family, summary: PhoneSummary, hero: SummaryHero, earned: List<EarnedLine>, usualLoad: Float?, now: Long,
    zone: ZoneId = ZoneId.systemDefault(), strings: Strings = Strings.app,
): StoryCard {
    val a = summary.analysis
    val active = a.activeSeconds
    val readings = FamilySummary.readings(summary)
    val whenLine = FamilySummary.whenLine(a.shownStart, now, zone)
    val date = DayWords.monthDay(Instant.ofEpochSecond(a.shownStart).atZone(zone).toLocalDate())
    val avg = a.workout.averageHeartRate
    val kcal = kcalFigure(a)
    val (minutes, minUnit) = minutesFigure(active, strings)
    fun join(parts: List<String>) = joinParts(parts, strings)
    val chip = earned.firstOrNull { it.kind == EarnedLine.Kind.Best }?.let { StoryChip(it.text, best = true, met = false) }
        ?: earned.firstOrNull { it.kind == EarnedLine.Kind.Goal }?.let {
            StoryChip(join(listOfNotNull(it.text, it.value)) ?: it.text, best = false, met = it.met)
        }
    val timeLabel = strings.get(R.string.glow_share_stat_time)
    val avgBpm = strings.get(R.string.glow_share_stat_avg_bpm)
    return when (family) {
        Workouts.Family.Distance -> {
            val m = summary.distanceMeters?.takeIf { it > 0f }
            val pace = hero.pace
            val stats = if (m != null) listOfNotNull(
                StoryStat(hero.value, hero.unit, strings.get(R.string.glow_share_stat_distance)),
                StoryStat(workoutClock(active), null, timeLabel),
                pace?.let { StoryStat(it, strings.get(R.string.glow_share_unit_per_km), strings.get(R.string.glow_share_stat_pace)) }
                    ?: avg?.let { StoryStat("$it", null, avgBpm) },
            ) else listOfNotNull(
                StoryStat(workoutClock(active), null, timeLabel),
                avg?.let { StoryStat("$it", null, avgBpm) },
                kcal?.let { StoryStat(it, null, strings.get(R.string.glow_share_stat_active_kcal)) },
            )
            StoryCard.Run(stats, chip, strings.format(R.string.glow_share_footer, a.sportName, date))
        }
        Workouts.Family.Strength -> {
            val lifted = Strength.kgLifted(summary.sets)
            val done = FamilySummary.setsDone(summary.sets)
            val length = strings.format(if (active >= 60) R.string.glow_share_total_minutes else R.string.glow_share_total_seconds, minutes)
            val total = join(listOfNotNull(length, strings.plural(R.plurals.glow_share_sets, done, done).takeIf { done > 0 })) ?: length
            StoryCard.Lift(
                whenLine, a.sportName, hero.value, if (lifted != null) strings.get(R.string.glow_share_unit_kg) else hero.unit,
                exerciseRows(summary.sets, strings).take(MAX_EXERCISES) + (strings.get(R.string.glow_share_total) to total), chip,
            )
        }
        Workouts.Family.IndoorCardio -> {
            val line = listOfNotNull(
                avg?.let { strings.format(R.string.glow_share_avg_bpm, it) }, kcal?.let { strings.format(R.string.glow_share_kcal, it) },
            )
            StoryCard.Indoor(date, a.sportName, dotClock(active), a.zoneSeconds, join(line), chip)
        }
        Workouts.Family.Intervals -> roundsCard(summary, readings, whenLine, minutes to minUnit, strings)
        Workouts.Family.MindBody -> {
            val settled = FamilySummary.settled(readings, a.shownStart, a.end)
            StoryCard.Calm(
                a.sportName, whenLine, minutes, minUnit,
                settled?.let { (f, t) -> FamilySummary.settledShort(f, t) }, heartLine(readings),
            )
        }
        Workouts.Family.Games -> {
            val load = a.workout.trainingLoad
            StoryCard.Game(
                a.sportName, minutes, minUnit,
                load?.let { FamilySummary.loadLine(it, usualLoad) },
                readings.maxOfOrNull { it.second }?.let { strings.format(R.string.glow_share_peak, it) },
            )
        }
    }
}

/** Two or more parts of a card's line as one, "141 avg bpm, 320 kcal", null when there are none. */
private fun joinParts(parts: List<String>, strings: Strings): String? =
    parts.reduceOrNull { x, y -> strings.format(R.string.glow_share_join, x, y) }

/** An intervals card: the reps or rounds done of those planned, each one's peak, and the peak and the work under them. */
private fun roundsCard(
    summary: PhoneSummary, readings: List<Pair<Long, Int>>, whenLine: String, minutes: Pair<String, String>, strings: Strings,
): StoryCard.Rounds {
    val a = summary.analysis
    val done = Phases.roundsDone(summary.rounds)
    val reps = summary.structure is Structure.Reps
    val planned = (summary.structure as? Structure.Timed)?.rounds ?: (summary.structure as? Structure.Reps)?.rounds
    val lines = FamilySummary.rounds(summary.rounds, readings)
    val peak = lines.mapNotNull { it.peak }.maxOrNull() ?: readings.maxOfOrNull { it.second }
    val work = FamilySummary.workSeconds(summary.rounds)
    val workText = if (work >= 60) {
        strings.format(R.string.glow_share_work_minutes, work / 60)
    } else {
        strings.format(R.string.glow_share_work_seconds, work)
    }
    val peakText = peak?.let { strings.format(R.string.glow_share_peak_bpm, it) }
    if (done == 0) return StoryCard.Rounds(whenLine, a.sportName, minutes.first, minutes.second, emptyList(), a.maxHeartRate, peakText)
    val unit = when {
        planned != null && planned >= done ->
            strings.format(if (reps) R.string.glow_share_unit_of_reps else R.string.glow_share_unit_of_rounds, planned)
        else -> strings.get(if (reps) R.string.glow_share_unit_reps else R.string.glow_share_unit_rounds)
    }
    return StoryCard.Rounds(
        whenLine, a.sportName, "$done", unit,
        lines.map { it.peak }, a.maxHeartRate, joinParts(listOfNotNull(peakText, workText), strings),
    )
}

/** Exercises a strength card names; the total follows them. */
private const val MAX_EXERCISES = 4

/**
 * Each named exercise in the order first done: "5 × 10, top 65 kg" when every set had the same reps, else "4 sets,
 * top 65 kg", without the top when nothing was weighed. Sets without a name aren't listed.
 */
internal fun exerciseRows(sets: List<org.openstrap.app.data.db.AppWorkoutSet>, strings: Strings = Strings.app): List<Pair<String, String>> {
    val done = sets.filter { it.end != null && !it.exercise.isNullOrBlank() }
    return done.groupBy { it.exercise!!.trim().lowercase() }.values.map { mine ->
        val n = mine.size
        val reps = mine.map { it.reps }.distinct().singleOrNull()
        val top = mine.mapNotNull { it.weightKg }.maxOrNull()
        val count = if (reps != null) {
            strings.format(R.string.glow_share_row_reps, n, reps)
        } else {
            strings.plural(R.plurals.glow_share_sets, n, n)
        }
        mine.first().exercise!!.trim() to (top?.let { strings.format(R.string.glow_share_row_top, count, Strength.kgText(it)) } ?: count)
    }
}

/** [readings] as 40 averages, for a calm card's heart line; empty under ten readings. */
private fun heartLine(readings: List<Pair<Long, Int>>): List<Float?> {
    if (readings.size < 10) return emptyList()
    return FamilySummary.heartLine(readings, 40)
}

/** How a run's route shows on its card. Map, the route over a map, is to come. */
internal enum class StoryRoute { Off, Line }

/** The route's geometry on a card: its ends cut for privacy, and fitted into a box. Pure, so it's tested. */
internal object StoryRouteShape {
    /** What "Hide start and end" cuts from each end of the line, in metres. */
    const val HIDE_M = 200.0

    /**
     * [points] less the first and last [meters] along the way, cut between fixes so the line stops where the distance
     * runs out. Empty when nothing is left between the two.
     */
    fun trimEnds(points: List<Route.Point>, meters: Double = HIDE_M): List<Route.Point> {
        if (points.size < 2) return emptyList()
        val along = DoubleArray(points.size)
        for (i in 1 until points.size) along[i] = along[i - 1] + Route.meters(points[i - 1], points[i])
        val from = meters
        val to = along.last() - meters
        if (to <= from) return emptyList()
        fun at(d: Double): Route.Point {
            val i = (1 until points.size).first { along[it] >= d }
            val a = points[i - 1]
            val b = points[i]
            val span = along[i] - along[i - 1]
            val f = if (span <= 0) 0.0 else (d - along[i - 1]) / span
            return a.copy(t = a.t + ((b.t - a.t) * f).toLong(), lat = a.lat + (b.lat - a.lat) * f, lon = a.lon + (b.lon - a.lon) * f)
        }
        return listOf(at(from)) + points.indices.filter { along[it] > from && along[it] < to }.map { points[it] } + at(to)
    }

    /**
     * [points] in a [width] by [height] box, north up, to scale (longitude narrowed by the latitude) and centred.
     * Empty without two points apart.
     */
    fun fit(points: List<Route.Point>, width: Float, height: Float): List<Offset> {
        if (points.size < 2) return emptyList()
        val narrow = cos(Math.toRadians(points.sumOf { it.lat } / points.size))
        val xs = points.map { it.lon * narrow }
        val ys = points.map { -it.lat }
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()
        if (spanX <= 0 && spanY <= 0) return emptyList()
        val scale = minOf(if (spanX > 0) width / spanX else Double.MAX_VALUE, if (spanY > 0) height / spanY else Double.MAX_VALUE)
        val left = (width - spanX * scale) / 2
        val top = (height - spanY * scale) / 2
        return points.indices.map { i -> Offset((left + (xs[i] - xs.min()) * scale).toFloat(), (top + (ys[i] - ys.min()) * scale).toFloat()) }
    }
}

/**
 * The card for [card], at [StoryWidth] by [StoryHeight] in whatever density it's given, in the rings theme: over
 * [photo] with the dark fade, else flat near-black with no light, the card's figures bold in its one hue, and a run's
 * [route] as a solid line in that hue when there is one.
 */
@Composable
internal fun StoryCardView(card: StoryCard, photo: ImageBitmap?, route: List<Route.Point>, modifier: Modifier = Modifier) {
    Box(modifier.size(StoryWidth, StoryHeight).background(StoryPage)) {
        if (photo != null) {
            Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (card is StoryCard.Big) BigFade() else PhotoFade(top = card !is StoryCard.Run && card !is StoryCard.Game)
        }
        if (route.size >= 2) RouteLine(route, storyColor(card), Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(top = 110.dp).height(240.dp))
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (card) {
                is StoryCard.Run -> RunStory(card)
                is StoryCard.Lift -> LiftStory(card)
                is StoryCard.Indoor -> IndoorStory(card)
                is StoryCard.Rounds -> RoundsStory(card)
                is StoryCard.Calm -> CalmStory(card)
                is StoryCard.Game -> GameStory(card)
                is StoryCard.Big -> BigStory(card)
                is StoryCard.Week -> WeekStory(card)
            }
        }
    }
}

/** A card's light: its kind's colour, except indoor cardio's, which is heart red like the board. */
private fun storyColor(card: StoryCard): Color = when (card) {
    is StoryCard.Indoor -> MetricColors.heart
    is StoryCard.Week -> MetricColors.good
    else -> familyColor(card.family)
}

/** The rings theme's share card: flat near-black, its chips on the card grey, its dimmer text muted grey. */
private val StoryPage = Color(0xFF0A0A0C)
private val StoryCardGrey = Color(0xFF17171B)
private val StoryMuted = Color(0xFFA7A7B0)

/** The rings theme's figures: bold in the numbers' face, Western digits left to right. */
private val StoryStatStyle get() = androidx.compose.ui.text.TextStyle(
    textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr, fontFamily = NumberFamily,
    fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp,
)
private val StoryTitleStyle get() = StoryTitleStyle.copy(fontWeight = FontWeight.ExtraBold)

/**
 * A card's one big figure, [value] bold in [color] with [unit] after it on its baseline in muted grey, shrunk to
 * [maxWidth] when long. Replaces the dotted number of the old look.
 */
@Composable
private fun StoryHero(value: String, unit: String, color: Color, maxWidth: androidx.compose.ui.unit.Dp, size: androidx.compose.ui.unit.TextUnit = 64.sp) {
    Row(Modifier.widthIn(max = maxWidth), verticalAlignment = Alignment.Bottom) {
        Text(
            value, color = color, maxLines = 1, softWrap = false,
            style = StoryStatStyle.copy(fontSize = size, lineHeight = size),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (unit.isNotBlank()) Text(unit, color = StoryMuted, style = GlowType.body, maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
    }
}

/**
 * The week's ring: one thick ring in [color], [share] of it from the top, round capped, with [count] bold in the
 * middle (the rings theme's hero, as [RingStatCard]'s).
 */
@Composable
private fun StoryRing(count: String, share: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val w = 26.dp.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arc = androidx.compose.ui.geometry.Size(d, d)
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, tl, arc, style = Stroke(w))
            val sweep = 360f * share.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, -90f, sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(count, color = Color.White, maxLines = 1, style = StoryStatStyle.copy(fontSize = 72.sp, lineHeight = 72.sp))
    }
}

/** The dark fade over a photo: up from the foot so the figures read, and down from the top when the card has text there. */
@Composable
private fun PhotoFade(top: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (top) Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.3f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(if (top) 0.62f else 0.32f).background(
                Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
    }
}

/** The route as one solid line in [color], round capped, fitted into the space it's given: no glow in the rings theme. */
@Composable
private fun RouteLine(route: List<Route.Point>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val points = StoryRouteShape.fit(route, size.width, size.height)
        if (points.size < 2) return@Canvas
        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, color, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** "OpenStrap", with the ring before it on the calm and game cards. */
@Composable
private fun Brand(ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ring) {
            Canvas(Modifier.size(14.dp)) { drawCircle(Color.White.copy(alpha = 0.8f), radius = size.minDimension * 0.34f, style = Stroke(2.4.dp.toPx())) }
            Spacer(Modifier.width(6.dp))
        }
        Text("OpenStrap", color = if (ring) Color.White.copy(alpha = 0.8f) else Color.White, style = GlowType.storyBrand, maxLines = 1)
    }
}

/** A best or the weekly goal as a status pill on the card grey, 12 dp round, its icon in its colour. */
@Composable
private fun StoryChipView(chip: StoryChip) {
    val (icon, tint) = if (chip.best) AppIcons.FlagCheckered to MetricColors.calories else AppIcons.Target to if (chip.met) MetricColors.good else Color.White
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(12.dp)).background(StoryCardGrey).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text(chip.text, color = Color.White, style = ChipText, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
    }
}

private val ChipText = GlowType.secondary.copy(fontWeight = FontWeight.Medium)

/** White for a card's dimmer lines, as on the boards. */
private const val StoryDim = 0.7f

/** Board ShareRun: everything at the foot, the best over the three figures, the sport and date beside the brand. */
@Composable
private fun RunStory(card: StoryCard.Run) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom)) {
        card.chip?.let { StoryChipView(it) }
        Row(Modifier.fillMaxWidth()) {
            card.stats.forEach { s ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.value, color = Color.White, style = StoryStatStyle, maxLines = 1)
                        s.unit?.let { Text(" $it", color = Color.White, style = GlowType.body, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp)) }
                    }
                    Text(s.label, color = StoryMuted, style = GlowType.secondary, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(card.footer, color = StoryMuted, style = GlowType.secondary, maxLines = 1, modifier = Modifier.weight(1f))
            Brand()
        }
    }
}

/** When, then the sport large, as the strength and HIIT cards open. */
@Composable
private fun StoryHeading(whenLine: String, sport: String) {
    Text(whenLine, color = StoryMuted, style = GlowType.secondary, maxLines = 1)
    Text(sport, color = Color.White, style = StoryTitleStyle, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
}

/** Board ShareStrength: kg lifted in dots, each exercise and the total as rows, the best and the brand at the foot. */
@Composable
private fun LiftStory(card: StoryCard.Lift) {
    Column(Modifier.fillMaxSize()) {
        StoryHeading(card.whenLine, card.sport)
        Box(Modifier.padding(top = 30.dp)) { StoryHero(card.value, card.unit, storyColor(card), 250.dp) }
        Column(Modifier.padding(top = 26.dp)) {
            card.rows.forEachIndexed { i, (name, text) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, style = GlowType.body, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Text(text, color = Color.White.copy(alpha = 0.8f), style = GlowType.body, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        FootRow(card.chip)
    }
}

/** A chip, if any, on the left and the brand on the right. */
@Composable
private fun FootRow(chip: StoryChip?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { chip?.let { StoryChipView(it) } }
        Brand()
    }
}

/** Board ShareIndoor: the brand and date, the time in dots in the middle, then the zones, the figures and a chip. */
@Composable
private fun IndoorStory(card: StoryCard.Indoor) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Brand() }
            Text(card.date, color = StoryMuted, style = GlowType.secondary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(card.sport, color = Color.White, style = GlowType.cardTitle, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            StoryHero(card.time, "", storyColor(card), 310.dp, 80.sp)
        }
        Text(
            stringResource(R.string.glow_share_workout_time), color = StoryMuted, style = GlowType.secondary,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        // Time in each zone as one strip, its ends rounded.
        ZoneBar(card.zones, ZONE_COLORS, height = 10.dp, gap = 3.dp, corner = 2.dp, ends = 5.dp)
        card.line?.let { Text(it, color = Color.White, style = GlowType.sentence, maxLines = 1, modifier = Modifier.padding(top = 14.dp)) }
        card.chip?.let { Box(Modifier.padding(top = 14.dp)) { StoryChipView(it) } }
    }
}

/**
 * The week (mockups/share-week-days.html, option A): the brand and the dates, "My week" and the count in dots in the
 * middle with the goal under it, the days as the app's week dots, then the totals and the chips.
 */
@Composable
private fun WeekStory(card: StoryCard.Week) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Brand() }
            Text(card.range, color = StoryMuted, style = GlowType.secondary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.glow_share_my_week), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
            val share = if (card.days.isEmpty()) 0f else card.days.count { it.second } / card.days.size.toFloat()
            StoryRing(card.count, share, storyColor(card))
        }
        Text(card.goalLine, color = StoryMuted, style = GlowType.secondary, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        WeekDots(card.days, Color.White, Modifier.padding(top = 28.dp), dot = 34.dp)
        Spacer(Modifier.weight(1f))
        if (card.totals.isNotEmpty()) WeekFigures(card.totals)
        if (card.chips.isNotEmpty()) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            card.chips.take(2).forEach { StoryChipView(it) }
        }
    }
}

/**
 * The week's totals (mockups/share-week-totals.html, option A with icons): each its icon over the value large with its
 * unit, the label under, thin rules between them.
 */
@Composable
private fun WeekFigures(figures: List<WeekFigure>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        figures.forEachIndexed { i, f ->
            if (i > 0) Box(Modifier.padding(end = 14.dp).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
            Column(Modifier.weight(1f)) {
                Icon(f.icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(f.value, color = Color.White, style = StoryStatStyle, maxLines = 1)
                    f.unit?.let { Text(" $it", color = Color.White, style = GlowType.body, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp)) }
                }
                Text(f.label, color = StoryMuted, style = GlowType.secondary, maxLines = 1)
            }
        }
    }
}

/** Board ShareHiit: rounds in dots, a column for each round's peak, the line under them and the brand. */
@Composable
private fun RoundsStory(card: StoryCard.Rounds) {
    Column(Modifier.fillMaxSize()) {
        StoryHeading(card.whenLine, card.sport)
        Box(Modifier.padding(top = 30.dp)) { StoryHero(card.done, card.unit, storyColor(card), 200.dp) }
        if (card.peaks.isNotEmpty()) {
            val heartZones = LocalHeartZones.current
            // Each round's peak in its zone's colour, white once it reached the top zone.
            fun colorOf(bpm: Int) = WorkoutRecorder.liveZone(bpm, card.maxHeartRate, heartZones)?.takeIf { it < ZONE_COLORS.lastIndex }
            PeakBars(card.peaks, { bpm -> colorOf(bpm)?.let { ZONE_COLORS[it] } ?: Color.White }, Modifier.padding(top = 30.dp))
        }
        card.line?.let { Text(it, color = Color.White, style = GlowType.sentence, maxLines = 2, modifier = Modifier.padding(top = 16.dp)) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Brand() }
    }
}

/** Board ShareMind: the sport and when, minutes in dots low in the middle, how the heart settled over its line. */
@Composable
private fun CalmStory(card: StoryCard.Calm) {
    Column(Modifier.fillMaxSize()) {
        Text(card.sport, color = Color.White.copy(alpha = 0.85f), style = GlowType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        Text(card.whenLine, color = StoryMuted, style = GlowType.secondary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.weight(1f))
        StoryHero(card.minutes, card.unit, storyColor(card), 250.dp)
        card.line?.let { Text(it, color = Color.White, style = GlowType.sentence, maxLines = 1, modifier = Modifier.padding(top = 16.dp)) }
        // The heart rate through the session as one soft white line.
        PlainLine(card.heart, Color.White.copy(alpha = 0.55f), 40.dp, Modifier.padding(top = 16.dp), smooth = false, stroke = 2.dp)
        Spacer(Modifier.weight(1f))
        Brand(ring = true)
    }
}

/** Board ShareGame: everything at the foot over the photo, the sport, minutes in dots, Load and the peak, the brand. */
@Composable
private fun GameStory(card: StoryCard.Game) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Text(card.sport, color = Color.White, style = GlowType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        Box(Modifier.padding(top = 12.dp)) { StoryHero(card.minutes, card.unit, Color.White, 250.dp, 80.sp) }
        card.load?.let { GameLine(it, Modifier.padding(top = 14.dp)) }
        card.peak?.let { GameLine(it, Modifier.padding(top = 2.dp)) }
        Box(Modifier.padding(top = 16.dp)) { Brand(ring = true) }
    }
}

/** One of a game card's lines under the minutes. */
@Composable
private fun GameLine(text: String, modifier: Modifier) =
    Text(text, color = Color.White, style = GlowType.sentence, maxLines = 1, modifier = modifier)

/** The big number's fade over a photo: light, since its text carries its own shadow. */
@Composable
private fun BigFade() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.2f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))),
        )
    }
}

/** The big number card: the sport and the brand at the top; at the foot the minutes, their line, a hairline and the figures. */
@Composable
private fun BigStory(card: StoryCard.Big) {
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() })
    val number = GlowType.flaskNumber.copy(fontWeight = FontWeight.ExtraBold, shadow = shadow)
    Column(Modifier.fillMaxSize().padding(vertical = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(card.sport, color = Color.White, style = GlowType.body.copy(fontWeight = FontWeight.Bold, shadow = shadow), maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                "OPENSTRAP", color = Color.White, maxLines = 1,
                style = number.copy(fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em),
            )
        }
        Column {
            Text(card.minutes, color = Color.White, style = number.copy(fontSize = 128.sp, lineHeight = 132.sp, letterSpacing = (-4).sp), maxLines = 1, softWrap = false)
            Text(card.line, color = Color.White, style = GlowType.cardTitle.copy(fontWeight = FontWeight.Bold, shadow = shadow), maxLines = 1)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.5f)))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                card.stats.forEach { s ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.value, color = Color.White, style = number.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp), maxLines = 1)
                        Text(s.label, color = Color.White, style = GlowType.secondary.copy(shadow = shadow), maxLines = 1, modifier = Modifier.padding(start = 5.dp, bottom = 2.dp))
                    }
                }
            }
        }
    }
}
