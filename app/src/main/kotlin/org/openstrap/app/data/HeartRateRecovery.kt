package org.openstrap.app.data

import kotlin.math.abs

/**
 * Heart rate recovery after a workout (HRR): how far the heart rate fell from the peak of the last minute of effort
 * to one minute ([Result.hrr1]) and two minutes ([Result.hrr2]) after the effort stopped. It is worked out from the
 * heart rate the workout already holds ([WorkoutAnalysis.heartRate], with the strap's three minutes after the end);
 * the strap's separate recovery recording (`recovery_hr`, type 70) is not read, as where its first byte falls in time is
 * not confirmed. Pure.
 *
 * It says nothing when the readings can't support it: samples outside [HeartRate.VALID] are dropped, a jump of more than
 * [MAX_JUMP_BPM_PER_S] a second is an artefact and dropped ([clean]), the last minute needs [MIN_EFFORT_SAMPLES]
 * readings, the peak must be a real effort ([MIN_PEAK_PCT] of max), the minute after must have readings and not keep
 * climbing, and the heart rate must really have fallen (by more than 0 and not past [MAX_DROP_1]). Otherwise null.
 *
 * Cole et al. (1999, N Engl J Med) found a fall of 12 bpm or less in the first minute after a maximal test to go with
 * a higher risk. That was after a treadmill test on a clinic protocol, not a wrist reading after any workout, so
 * [isLow] and [trend] are only a trend against your own 30 days, never a diagnosis.
 */
object HeartRateRecovery {
    /** The effort that counts: the last minute before the stop. */
    const val EFFORT_WINDOW_S = 60L

    /** A faster change than this between two readings is the sensor slipping, not a heart. */
    const val MAX_JUMP_BPM_PER_S = 25

    /** After this many readings in a row dropped, the next stands as the new level (a real step, not a spike). */
    const val RESET_AFTER_REJECTS = 3

    /** Readings needed in the last minute of effort, of the 60 a second-by-second recording holds. */
    const val MIN_EFFORT_SAMPLES = 20

    /** Readings needed in the minute after the stop. */
    const val MIN_TAIL_SAMPLES = 10

    /** The peak has to be this share of max HR or more, as for the Load card's drop ([WorkoutAnalysis.recoveryIsMeaningful]). */
    const val MIN_PEAK_PCT = 65

    /** A level is the median of the readings within this many seconds of the moment, so one stray reading can't set it. */
    const val SMOOTH_S = 2L

    /** A fall past these in one or two minutes is not a heart rate recovery but a bad reading. */
    const val MAX_DROP_1 = 80
    const val MAX_DROP_2 = 100

    /** After the stop the smoothed heart rate may rise this far above the peak (the first seconds) before it is still effort. */
    const val STILL_CLIMBING_BPM = 5

    /** Cole's 12 bpm or less in the first minute. */
    const val COLE_LOW_BPM = 12

    /** Your usual needs this many recoveries, from the last [LOOKBACK_DAYS] days. */
    const val USUAL_MIN = 3
    const val LOOKBACK_DAYS = 30

    /** A recovery within this many bpm of your usual is usual. */
    const val BAND = 4

    /** [peak] is the last minute's highest level; the falls are from it. [hrr2] is null without a clean second minute. */
    data class Result(val peak: Int, val hrr1: Int, val hrr2: Int?)

    enum class Trend { Faster, Usual, Slower }

    /** [samples] (second to bpm) in time order with the invalid and the jumps over [MAX_JUMP_BPM_PER_S] taken out. */
    fun clean(samples: List<Pair<Long, Int>>): List<Pair<Long, Int>> {
        val sorted = samples.filter { it.second in HeartRate.VALID }.sortedBy { it.first }.distinctBy { it.first }
        val out = ArrayList<Pair<Long, Int>>(sorted.size)
        var rejected = 0
        for (s in sorted) {
            val last = out.lastOrNull()
            val seconds = if (last == null) 1L else (s.first - last.first).coerceAtLeast(1L)
            if (last == null || abs(s.second - last.second) <= MAX_JUMP_BPM_PER_S * seconds || rejected >= RESET_AFTER_REJECTS) {
                out += s
                rejected = 0
            } else {
                rejected++
            }
        }
        return out
    }

    /** The heart rate level at [t]: the middle of the readings within [SMOOTH_S] of it; null with fewer than two. */
    private fun level(cleaned: List<Pair<Long, Int>>, t: Long): Int? {
        val near = cleaned.filter { it.first in t - SMOOTH_S..t + SMOOTH_S }.map { it.second }
        return if (near.size < 2) null else Stats.upperMiddle(near)
    }

    /**
     * The recovery from [samples] (second to bpm) for effort that stopped at [end], on a [maxHr]; null when it can't be
     * told (see above).
     */
    fun compute(samples: List<Pair<Long, Int>>, end: Long, maxHr: Int): Result? {
        if (maxHr <= 0) return null
        val s = clean(samples.filter { it.first in end - EFFORT_WINDOW_S - SMOOTH_S..end + 2 * 60L + SMOOTH_S })
        val effort = s.filter { it.first in end - EFFORT_WINDOW_S + 1..end }
        if (effort.size < MIN_EFFORT_SAMPLES) return null
        val peak = effort.mapNotNull { level(s, it.first) }.maxOrNull() ?: return null
        if (peak * 100 < maxHr * MIN_PEAK_PCT) return null
        val tail = s.filter { it.first in end + 1..end + 60L }
        if (tail.size < MIN_TAIL_SAMPLES) return null
        // The effort has to stop: a smoothed heart rate well over the peak after the stop means it carried on.
        if (tail.any { (level(s, it.first) ?: 0) > peak + STILL_CLIMBING_BPM }) return null
        val after1 = level(s, end + 60L) ?: return null
        val hrr1 = peak - after1
        if (hrr1 <= 0 || hrr1 > MAX_DROP_1) return null
        // A second minute that ends higher than the first (you moved again) is left out, not corrected.
        val hrr2 = level(s, end + 120L)?.let { peak - it }?.takeIf { it >= hrr1 && it <= MAX_DROP_2 }
        return Result(peak, hrr1, hrr2)
    }

    /** [a]'s recovery from its own heart rate and end; null for a workout with no recording after its end, among the rest. */
    fun of(a: WorkoutAnalysis): Result? = compute(a.heartRate.map { it.timestamp to it.bpm }, a.end, a.maxHeartRate)

    /** Your usual first-minute recovery: the median of [hrr1s] (the last [LOOKBACK_DAYS] days), null with fewer than [USUAL_MIN]. */
    fun usual(hrr1s: List<Int>): Int? = if (hrr1s.size < USUAL_MIN) null else Stats.median(hrr1s)?.let { Math.round(it) }

    /** Whether [hrr1] is faster, usual or slower than [usual] by [BAND]; null without a usual. */
    fun trend(hrr1: Int, usual: Int?): Trend? = when {
        usual == null -> null
        hrr1 >= usual + BAND -> Trend.Faster
        hrr1 <= usual - BAND -> Trend.Slower
        else -> Trend.Usual
    }

    /** Whether [hrr1] is at or under Cole's 12 bpm: to be shown as a trend to watch, never as a finding. */
    fun isLow(hrr1: Int): Boolean = hrr1 <= COLE_LOW_BPM
}
