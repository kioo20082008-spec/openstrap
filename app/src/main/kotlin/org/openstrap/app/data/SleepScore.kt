package org.openstrap.app.data

import org.openstrap.app.data.db.SleepStage
import kotlin.math.max
import kotlin.math.min

/**
 * OpenStrap's own nightly sleep score, 0-100, from published sleep guidance rather than the strap's closed formula
 * (docs/algorithms/recovery.md, "Sleep score"). It's the weighted mean of the parts a night has, renormalised over
 * those present: [DURATION_WEIGHT] duration, [CONTINUITY_WEIGHT] continuity and [REGULARITY_WEIGHT] regularity.
 *
 * - **Duration**: total sleep of the main sleep (naps not added) against the user's need ([Recovery.sleepNeed]), with
 *   a full band of need ± [DURATION_BAND_MINUTES]. The National Sleep Foundation recommends 7-9 h for adults (7-8 h
 *   from 65), calls 6 h and 10 h "may be appropriate" and less or more not recommended (Hirshkowitz et al. 2015).
 * - **Continuity**: wake after sleep onset and awakenings of [AWAKENING_MIN_MINUTES] or more, at the NSF sleep quality
 *   cuts: WASO ≤ 20 min good, 41-51 min poor by age; at most one awakening over 5 min good, four or more poor (Ohayon
 *   et al. 2017). Needs stages. Stages themselves are never scored: four-stage agreement across six wearables was
 *   kappa 0.21-0.53 (Schyvens et al. 2025).
 * - **Regularity**: how far tonight's sleep midpoint sits from the usual one, the circular mean of the previous up to
 *   [REGULARITY_NIGHTS] main sleeps. Sleep regularity predicted mortality better than duration (Windred et al. 2024,
 *   with the Sleep Regularity Index of Phillips et al. 2017); a midpoint shifted from the usual is social jetlag
 *   (Wittmann et al. 2006). Needs [REGULARITY_MIN_NIGHTS] earlier main sleeps.
 */
object SleepScore {
    const val DURATION_WEIGHT = 0.45f
    const val CONTINUITY_WEIGHT = 0.30f
    const val REGULARITY_WEIGHT = 0.25f

    /** Duration scores 100 from need − this to need + this: 7-9 h at the default 8 h need, the NSF's recommended range. */
    const val DURATION_BAND_MINUTES = 60

    /** An hour short of the band (6 h at the default need, "may be appropriate") scores this; three hours short, 0. */
    const val SHORT_HOUR_SCORE = 60f
    const val SHORT_ZERO_MINUTES = 180

    /**
     * An hour over the band (10 h at the default need, "may be appropriate") scores this, three hours over this
     * ([LONG_THREE_HOURS_SCORE]), and the score keeps falling at the same rate to [LONG_FLOOR] beyond: long sleep is
     * not recommended, but a long night after a short week is often recovery, so it never scores as badly as a short one.
     */
    const val LONG_HOUR_SCORE = 85f
    const val LONG_THREE_HOURS_SCORE = 55f
    const val LONG_FLOOR = 40f
    /** From an hour over the band to three hours over. */
    private const val LONG_SLOPE_MINUTES = 120f

    /** WASO up to this is good (Ohayon 2017: ≤ 20 min at every age) and scores 100; it falls to 0 at [WASO_ZERO_MINUTES]. */
    const val WASO_GOOD_MINUTES = 20
    /** Where the NSF's "poor" WASO starts (41-51 min, by age): the top of that range. */
    const val WASO_ZERO_MINUTES = 50

    /** An awake stretch counts as an awakening from this long (Ohayon 2017 counts awakenings over 5 min). */
    const val AWAKENING_MIN_MINUTES = 5
    /** Awakenings up to this are good and score 100; from [AWAKENINGS_ZERO] they score 0 (Ohayon 2017: ≥ 4 poor). */
    const val AWAKENINGS_GOOD = 1
    const val AWAKENINGS_ZERO = 4

    /** Previous main sleeps the usual midpoint is the circular mean of, and how many it needs. */
    const val REGULARITY_NIGHTS = 14
    const val REGULARITY_MIN_NIGHTS = 5
    /** A midpoint within this of the usual scores 100; [REGULARITY_ZERO_MINUTES] or more away (3 h of social jetlag), 0. */
    const val REGULARITY_GOOD_MINUTES = 30
    const val REGULARITY_ZERO_MINUTES = 180

    private const val DAY_MINUTES = 1440
    private const val HOUR = 60
    /** A part's best. */
    private const val FULL = 100f

    /** A night's score and its parts (each 0-100; null where the night didn't have what the part needs). */
    data class Result(
        val total: Int,
        val duration: Float,
        val continuity: Float?,
        val regularity: Float?,
        /** What the parts were worked out from. */
        val asleepMinutes: Int,
        val needMinutes: Int,
        val wasoMinutes: Int?,
        val awakenings: Int?,
        /** How far tonight's midpoint sat from the usual, minutes (0-720). */
        val midpointShiftMinutes: Int?,
    )

    /**
     * [tonight]'s score against [earlier] main sleeps (any order; those on or after [tonight]'s day are ignored) for
     * the usual midpoint, with sleep need [need].
     */
    fun of(tonight: Recovery.Night, earlier: List<Recovery.Night>, need: Int = Recovery.SLEEP_NEED_MINUTES): Result {
        val usual = tonight.midpointMinutes?.let {
            usualMidpoint(earlier.filter { it.day < tonight.day }.sortedBy { it.day }.mapNotNull { it.midpointMinutes })
        }
        val shift = tonight.midpointMinutes?.let { m -> usual?.let { midpointShift(m, it) } }
        return of(tonight.asleepMinutes, need, tonight.wasoMinutes, tonight.awakenings, shift)
    }

    /** The score from its inputs: time asleep, need, WASO and awakenings (null without stages), and the midpoint shift. */
    fun of(asleepMinutes: Int, need: Int, wasoMinutes: Int?, awakenings: Int?, midpointShiftMinutes: Int?): Result {
        val d = duration(asleepMinutes, need)
        val c = wasoMinutes?.let { continuity(it, awakenings) }
        val r = midpointShiftMinutes?.let(::regularity)
        val parts = listOfNotNull(DURATION_WEIGHT to d, c?.let { CONTINUITY_WEIGHT to it }, r?.let { REGULARITY_WEIGHT to it })
        val mean = parts.sumOf { (w, p) -> w.toDouble() * p } / parts.sumOf { it.first.toDouble() }
        val total = Math.round(mean).toInt().coerceIn(0, FULL.toInt())
        return Result(total, d, c, r, asleepMinutes, need, wasoMinutes, awakenings, midpointShiftMinutes)
    }

    /**
     * Duration, piecewise linear in total sleep against the band need ± [DURATION_BAND_MINUTES] (L to U): 100 inside,
     * [SHORT_HOUR_SCORE] at L − 60, 0 from L − [SHORT_ZERO_MINUTES]; [LONG_HOUR_SCORE] at U + 60,
     * [LONG_THREE_HOURS_SCORE] at U + 180, then on down to [LONG_FLOOR]. At 8 h need: 7-9 h 100, 6 h 60, 4 h 0, 10 h 85, 12 h 55.
     */
    fun duration(asleepMinutes: Int, need: Int = Recovery.SLEEP_NEED_MINUTES): Float {
        val low = need - DURATION_BAND_MINUTES
        val high = need + DURATION_BAND_MINUTES
        val t = asleepMinutes.toFloat()
        return when {
            t < low - SHORT_ZERO_MINUTES -> 0f
            t < low - HOUR -> SHORT_HOUR_SCORE * (t - (low - SHORT_ZERO_MINUTES)) / (SHORT_ZERO_MINUTES - HOUR)
            t < low -> FULL - (FULL - SHORT_HOUR_SCORE) * (low - t) / HOUR
            t <= high -> FULL
            t <= high + HOUR -> FULL - (FULL - LONG_HOUR_SCORE) * (t - high) / HOUR
            else -> {
                val slope = (LONG_HOUR_SCORE - LONG_THREE_HOURS_SCORE) / LONG_SLOPE_MINUTES
                max(LONG_FLOOR, LONG_HOUR_SCORE - slope * (t - high - HOUR))
            }
        }
    }

    /** Continuity: the mean of the WASO part and, when known, the awakenings part. */
    fun continuity(wasoMinutes: Int, awakenings: Int?): Float {
        val waso = wasoPart(wasoMinutes)
        return awakenings?.let { (waso + awakeningsPart(it)) / 2f } ?: waso
    }

    /** 100 at ≤ [WASO_GOOD_MINUTES], 0 at ≥ [WASO_ZERO_MINUTES], linear between. */
    fun wasoPart(wasoMinutes: Int): Float =
        (FULL * (WASO_ZERO_MINUTES - wasoMinutes) / (WASO_ZERO_MINUTES - WASO_GOOD_MINUTES)).coerceIn(0f, FULL)

    /** 100 at ≤ [AWAKENINGS_GOOD], 0 at ≥ [AWAKENINGS_ZERO], in even steps between: 2 → 67, 3 → 33. */
    fun awakeningsPart(awakenings: Int): Float =
        (FULL * (AWAKENINGS_ZERO - awakenings) / (AWAKENINGS_ZERO - AWAKENINGS_GOOD)).coerceIn(0f, FULL)

    /** 100 within [REGULARITY_GOOD_MINUTES] of the usual midpoint, 0 from [REGULARITY_ZERO_MINUTES], linear between. */
    fun regularity(midpointShiftMinutes: Int): Float {
        val span = REGULARITY_ZERO_MINUTES - REGULARITY_GOOD_MINUTES
        return (FULL * (REGULARITY_ZERO_MINUTES - midpointShiftMinutes) / span).coerceIn(0f, FULL)
    }

    /**
     * The usual midpoint (minutes after local midnight) of the last [REGULARITY_NIGHTS] of [midpoints] (oldest first),
     * by their circular mean ([RecoveryEngine.circularMean]) so 23:50 and 00:10 average to midnight. Null under
     * [REGULARITY_MIN_NIGHTS], or when they spread evenly round the clock.
     */
    fun usualMidpoint(midpoints: List<Int>): Int? {
        val recent = midpoints.takeLast(REGULARITY_NIGHTS).takeIf { it.size >= REGULARITY_MIN_NIGHTS } ?: return null
        val seconds = RecoveryEngine.circularMean(recent.map { it * HOUR })
        return seconds?.let { Math.floorMod(Math.round(it / HOUR.toFloat()), DAY_MINUTES) }
    }

    /** How far apart two clock times (minutes after midnight) are, the short way round: 0-720. */
    fun midpointShift(a: Int, b: Int): Int {
        val d = Math.floorMod(a - b, DAY_MINUTES)
        return min(d, DAY_MINUTES - d)
    }

    /**
     * Awakenings in [stages] (start, end, stage): awake stretches of at least [AWAKENING_MIN_MINUTES] between the first
     * sleep stage's start and the last one's end, touching awake rows merged into one. Null without any sleep stage.
     */
    fun awakenings(stages: List<Triple<Long, Long, Int>>): Int? {
        val sleep = stages.filter { it.third != SleepStage.AWAKE }
        if (sleep.isEmpty()) return null
        val onset = sleep.minOf { it.first }
        val wake = sleep.maxOf { it.second }
        val awake = stages.filter { it.third == SleepStage.AWAKE }
            .map { max(it.first, onset) to min(it.second, wake) }.filter { it.second > it.first }.sortedBy { it.first }
        var count = 0
        var from = 0L
        var to = Long.MIN_VALUE
        fun close() { if (to != Long.MIN_VALUE && to - from >= AWAKENING_MIN_MINUTES * HOUR) count++ }
        for ((a, b) in awake) {
            // A gap under a minute is the same stretch, split across two rows.
            if (to != Long.MIN_VALUE && a <= to + MERGE_GAP_SECONDS) { to = max(to, b); continue }
            close()
            from = a
            to = b
        }
        close()
        return count
    }

    /** Awake rows this close count as one stretch. */
    private const val MERGE_GAP_SECONDS = 60L
}
