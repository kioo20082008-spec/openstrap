package org.openstrap.app.data.selfcheck

/*
 * The checks on what the strap measures: the heart-rate baselines, steps, calories and the strap's own data. Only the
 * first can change anything (through Energy's knobs); the rest are recorded, and told when they are certain.
 */

/**
 * Hours of "effort" without a step: still, awake minutes that Energy counted as Activity or Exercise. Sitting does not
 * drain a battery like running, so a large share means the resting heart rate Energy measures against is too low for
 * today (illness, heat, caffeine, a stressful afternoon) or a raised heart rate was read as sustained exercise. Medium
 * from [MEDIUM_MINUTES], and only Medium may change anything. nums: minutes counted, still awake minutes, share in percent.
 */
class SittingAsEffort : DayCheck {
    override val id = "heart.sitting_as_effort"
    override val domain = Domain.HeartRate

    override fun inspectDay(day: DayTrace): Finding? {
        val still = day.minutes.filter { !it.asleep && !it.workout && it.steps == 0 }
        if (still.size < MIN_STILL_MINUTES) return null
        val effort = still.count { Traces.active(it.state) }
        val share = effort.toFloat() / still.size
        if (effort < LOW_MINUTES || share < MIN_SHARE) return null
        return Finding(
            id, domain, if (effort >= MEDIUM_MINUTES) Severity.Medium else Severity.Low, day.date,
            listOf(effort.toFloat(), still.size.toFloat(), Math.round(share * 100f).toFloat()),
            Plan.Search(listOf(Knob.Resting, Knob.Cap, Knob.Active), Direction.Soften),
        )
    }

    companion object {
        const val MIN_STILL_MINUTES = 240
        const val LOW_MINUTES = 90
        const val MEDIUM_MINUTES = 150
        const val MIN_SHARE = 0.12f
    }
}

/**
 * Minutes with a heart rate well over the maximum the app holds: the maximum is learned from real peaks and follows
 * them within a day, so this only tells that it lagged. nums: minutes, the highest heart rate, the maximum held.
 */
class PeakOverMax : DayCheck {
    override val id = "heart.peak_over_max"
    override val domain = Domain.HeartRate

    override fun inspectDay(day: DayTrace): Finding? {
        val peak = day.peakHr ?: return null
        if (day.peakMinutes < MIN_MINUTES) return null
        return Finding(
            id, domain, if (day.peakMinutes >= MEDIUM_MINUTES) Severity.Medium else Severity.Low, day.date,
            listOf(day.peakMinutes.toFloat(), peak.toFloat(), day.maxHr), Plan.Inform,
        )
    }

    companion object {
        const val MIN_MINUTES = 3
        const val MEDIUM_MINUTES = 10
    }
}

/**
 * Steps counted in minutes where the heart did not respond at all ([MAX_HRR] of reserve or less): the arm moving at a desk
 * the false-step filter missed. nums: those steps, the day's steps, share in percent.
 */
class StepsWithoutResponse : DayCheck {
    override val id = "steps.no_response"
    override val domain = Domain.Steps

    override fun inspectDay(day: DayTrace): Finding? {
        if (day.partial) return null
        val total = day.minutes.sumOf { it.steps }
        val quiet = day.minutes.filter { !it.asleep && it.steps >= MIN_MINUTE_STEPS && (it.hrr ?: 1f) <= MAX_HRR }.sumOf { it.steps }
        if (quiet < MIN_STEPS || total <= 0 || quiet.toFloat() / total < MIN_SHARE) return null
        return Finding(id, domain, Severity.Low, day.date, listOf(quiet.toFloat(), total.toFloat(), Math.round(quiet * 100f / total).toFloat()), Plan.Inform)
    }

    companion object {
        const val MIN_MINUTE_STEPS = 30
        const val MAX_HRR = 0.04f
        const val MIN_STEPS = 1_500
        const val MIN_SHARE = 0.35f
    }
}

/**
 * Most of a day's steps taken out as false steps ([DayTrace.falseSteps]): either the filter is too eager or the strap
 * moved on the wrist all day. nums: false steps, steps kept, share removed in percent.
 */
class StepsMostlyFalse : DayCheck {
    override val id = "steps.mostly_false"
    override val domain = Domain.Steps

    override fun inspectDay(day: DayTrace): Finding? {
        if (day.partial) return null
        val all = day.steps + day.falseSteps
        if (day.falseSteps < MIN_STEPS || all <= 0 || day.falseSteps.toFloat() / all < MIN_SHARE) return null
        return Finding(
            id, domain, Severity.Low, day.date,
            listOf(day.falseSteps.toFloat(), day.steps.toFloat(), Math.round(day.falseSteps * 100f / all).toFloat()), Plan.Inform,
        )
    }

    companion object {
        const val MIN_STEPS = 3_000
        const val MIN_SHARE = 0.6f
    }
}

/** A whole day's energy expenditure whose activity level (total over resting) is outside 1.2 to 2.5. nums: total kcal, steps. */
class CaloriesImplausible : Check {
    override val id = "calories.pal"
    override val domain = Domain.Calories

    override fun inspect(facts: Facts): List<Finding> =
        facts.calories.filter { it.palImplausible && !it.partial }.takeLast(RECENT_DAYS).map {
            Finding(id, domain, Severity.Medium, it.day, listOf(it.totalKcal, it.steps.toFloat()), Plan.Inform)
        }

    companion object {
        const val RECENT_DAYS = 3
    }
}

/**
 * Hours with the strap on but no heart rate, with nothing (off the wrist, off overnight) to explain it, on [MIN_DAYS] of the
 * last days: the strap sits loose, or its connection drops. nums: unexplained minutes on the worst day, days affected.
 */
class StrapGaps : Check {
    override val id = "strap.gaps"
    override val domain = Domain.Strap

    override fun inspect(facts: Facts): List<Finding> {
        val days = facts.days.takeLast(WINDOW_DAYS)
        val lost = days.map { d -> d.gaps.filter { !it.explained }.sumOf { it.minutes } }
        val bad = lost.count { it >= MIN_MINUTES }
        val last = days.lastOrNull() ?: return emptyList()
        if (bad < MIN_DAYS) return emptyList()
        return listOf(Finding(id, domain, Severity.Medium, last.date, listOf((lost.maxOrNull() ?: 0).toFloat(), bad.toFloat()), Plan.Inform))
    }

    companion object {
        const val WINDOW_DAYS = 3
        const val MIN_MINUTES = 120
        const val MIN_DAYS = 2
    }
}

/** The strap nearly flat and off the charger: told once, so the history doesn't get a hole. nums: percent. */
class StrapBatteryLow : Check {
    override val id = "strap.battery"
    override val domain = Domain.Strap

    override fun inspect(facts: Facts): List<Finding> {
        val s = facts.strap ?: return emptyList()
        val b = s.battery ?: return emptyList()
        if (s.charging || b > MAX_PERCENT) return emptyList()
        return listOf(Finding(id, domain, Severity.Low, facts.today, listOf(b.toFloat()), Plan.Inform))
    }

    companion object {
        const val MAX_PERCENT = 5
    }
}
