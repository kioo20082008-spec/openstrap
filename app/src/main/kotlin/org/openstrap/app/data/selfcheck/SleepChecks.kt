package org.openstrap.app.data.selfcheck

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * The sleep checks: night values that cannot be, or that disagree with the strap's own. Nothing about sleep is learned
 * (stages are displayed, never scored): they are recorded, and told when they are certain.
 */

/**
 * A night too short, too long, or with stages that add up to more than the night. nums: minutes asleep, deep plus REM minutes,
 * minutes awake (-1 unknown).
 */
class SleepImplausible : Check {
    override val id = "sleep.implausible"
    override val domain = Domain.Sleep

    override fun inspect(facts: Facts): List<Finding> = facts.nights.takeLast(RECENT_NIGHTS).mapNotNull { n ->
        val stages = (n.deepMinutes ?: 0) + (n.remMinutes ?: 0)
        val bad = n.asleepMinutes < MIN_MINUTES || n.asleepMinutes > MAX_MINUTES || stages > n.asleepMinutes + STAGE_SLACK
        if (!bad) return@mapNotNull null
        Finding(id, domain, Severity.Medium, n.day, listOf(n.asleepMinutes.toFloat(), stages.toFloat(), (n.awakeMinutes ?: -1).toFloat()), Plan.Inform)
    }

    companion object {
        const val RECENT_NIGHTS = 3
        const val MIN_MINUTES = 120
        const val MAX_MINUTES = 14 * 60
        const val STAGE_SLACK = 15
    }
}

/**
 * OpenStrap's sleep score [MIN_GAP] points or more from the strap's own on [MIN_NIGHTS] of the last [WINDOW] nights, the same
 * way each time: the two scores weigh the night differently, but not by that much for long. nums: nights, the mean gap
 * (own minus strap's, whole points).
 */
class SleepScoreGap : Check {
    override val id = "sleep.score_gap"
    override val domain = Domain.Sleep

    override fun inspect(facts: Facts): List<Finding> {
        val rows = facts.nights.takeLast(WINDOW).filter { it.score != null && it.strapScore != null }
        if (rows.size < MIN_NIGHTS) return emptyList()
        val gaps = rows.map { it.score!! - it.strapScore!! }
        val wide = gaps.filter { abs(it) >= MIN_GAP }
        if (wide.size < MIN_NIGHTS || !(wide.all { it > 0 } || wide.all { it < 0 })) return emptyList()
        return listOf(Finding(id, domain, Severity.Low, rows.last().day, listOf(wide.size.toFloat(), wide.average().toFloat()), Plan.Inform))
    }

    companion object {
        const val WINDOW = 5
        const val MIN_NIGHTS = 3
        const val MIN_GAP = 30
    }
}

/**
 * The latest night's midpoint more than [MIN_JUMP] minutes from the circular mean of the nights before it (seven or more of
 * them): a time-zone change, a shift, or a night cut in two. nums: the jump in minutes, the latest midpoint, the usual one.
 */
class SleepMidpointJump : Check {
    override val id = "sleep.midpoint_jump"
    override val domain = Domain.Sleep

    override fun inspect(facts: Facts): List<Finding> {
        val rows = facts.nights.filter { it.midpointMinutes != null }
        val latest = rows.lastOrNull() ?: return emptyList()
        val before = rows.dropLast(1).takeLast(HISTORY).map { it.midpointMinutes!! }
        if (before.size < MIN_HISTORY) return emptyList()
        val usual = circularMean(before)
        val jump = circularDistance(latest.midpointMinutes!!, usual)
        if (jump < MIN_JUMP) return emptyList()
        return listOf(Finding(id, domain, Severity.Low, latest.day, listOf(jump.toFloat(), latest.midpointMinutes.toFloat(), usual.toFloat()), Plan.Inform))
    }

    companion object {
        const val HISTORY = 14
        const val MIN_HISTORY = 7
        const val MIN_JUMP = 240
        private const val DAY_MINUTES = 1440

        /** The mean of clock times in minutes after midnight, around the clock: 23:30 and 00:30 average to midnight. */
        fun circularMean(minutes: List<Int>): Int {
            val a = minutes.map { it * 2 * Math.PI / DAY_MINUTES }
            val mean = atan2(a.sumOf { sin(it) }, a.sumOf { cos(it) })
            return Math.floorMod(Math.round(mean * DAY_MINUTES / (2 * Math.PI)).toInt(), DAY_MINUTES)
        }

        fun circularDistance(a: Int, b: Int): Int = Math.floorMod(a - b, DAY_MINUTES).let { minOf(it, DAY_MINUTES - it) }
    }
}
