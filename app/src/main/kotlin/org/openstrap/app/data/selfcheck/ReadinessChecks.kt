package org.openstrap.app.data.selfcheck

import kotlin.math.abs

/*
 * The Recovery (Readiness) checks: a score that moves a lot with nothing behind it, or that does not move at all.
 * Recorded, and not changed: the weights are the published ones, or the user's own from the morning check-ins.
 */

/**
 * A jump of [MIN_JUMP] points or more from one day to the next while none of the three contributors that carry most of the
 * weight (HRV, resting heart rate, sleep) moved by [CONTRIBUTOR_MOVE] or more. nums: the jump (signed), the score before,
 * the score now.
 */
class ReadinessJump : Check {
    override val id = "readiness.jump"
    override val domain = Domain.Readiness

    override fun inspect(facts: Facts): List<Finding> {
        val rows = facts.readiness.sortedBy { it.day }
        val out = ArrayList<Finding>()
        for (i in 1 until rows.size) {
            val a = rows[i - 1]
            val b = rows[i]
            if (b.day.toEpochDay() - a.day.toEpochDay() != 1L || a.score == null || b.score == null) continue
            val jump = b.score - a.score
            if (abs(jump) < MIN_JUMP) continue
            val moves = listOf(a.hrv to b.hrv, a.restingHr to b.restingHr, a.sleep to b.sleep)
                .mapNotNull { (x, y) -> if (x != null && y != null) abs(y - x) else null }
            if (moves.size < 2 || moves.any { it >= CONTRIBUTOR_MOVE }) continue
            out += Finding(id, domain, Severity.Medium, b.day, listOf(jump.toFloat(), a.score.toFloat(), b.score.toFloat()), Plan.Inform)
        }
        return out.takeLast(RECENT_DAYS)
    }

    companion object {
        const val MIN_JUMP = 36
        const val CONTRIBUTOR_MOVE = 15
        const val RECENT_DAYS = 2
    }
}

/** The same score (give or take a point) on every one of the last week's days, six or more of them scored. nums: scored days, the score. */
class ReadinessStuck : Check {
    override val id = "readiness.stuck"
    override val domain = Domain.Readiness

    override fun inspect(facts: Facts): List<Finding> {
        val scores = facts.readinessWeek.filterNotNull()
        if (scores.size < MIN_DAYS || facts.readinessWeek.size < WEEK) return emptyList()
        if ((scores.maxOrNull() ?: 0) - (scores.minOrNull() ?: 0) > MAX_RANGE) return emptyList()
        return listOf(Finding(id, domain, Severity.Medium, facts.today, listOf(scores.size.toFloat(), scores.average().toFloat()), Plan.Inform))
    }

    companion object {
        const val WEEK = 7
        const val MIN_DAYS = 6
        const val MAX_RANGE = 1
    }
}
