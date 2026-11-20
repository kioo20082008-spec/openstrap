package org.openstrap.app.data.selfcheck

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.openstrap.app.data.ChargeInputs
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.catching
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate
import java.time.ZoneId

/**
 * One pass of the self-check: replays the last days' Energy under the published model (the calibration's, without what was
 * learned, so a correction that works keeps being seen as needed until it fades), builds the [Facts], runs every
 * [SelfChecks], and has [SelfPlanner] turn what was found into [Decision]s. Heavy (a replay per day, a few hundred
 * milliseconds each, and up to [SelfPlanner.BUDGET] in-memory Energy runs for a day with something to solve), so the
 * keeper runs it off the main thread, at most every few hours, and never while a workout is recording.
 */
class SelfCheckRunner(
    private val health: HealthRepository,
    dao: HealthDao,
    private val engine: RecoveryEngine = RecoveryEngine(dao),
) {
    /**
     * [Decision]s for the last [TRACE_DAYS] days to [today]. [base] is the tuning from the morning check-ins; [battery] and
     * [charging] are the strap as last seen.
     */
    suspend fun run(
        today: LocalDate, profile: UserProfile, zone: ZoneId, now: Long, base: ChargeTuning, battery: Int?, charging: Boolean,
    ): List<Decision> {
        val published = ((RESULT_DAYS - 1) downTo 0).mapNotNull { back ->
            currentCoroutineContext().ensureActive()
            val date = today.minusDays(back.toLong())
            catching { health.recovery(date, profile) }.getOrNull()
        }
        val byDate = published.associateBy { it.date }

        // The published model's Energy for each of the last days, kept with the inputs that made it for the planner.
        val replays = LinkedHashMap<LocalDate, ChargeInputs>()
        val traces = ArrayList<DayTrace>()
        for (back in (TRACE_DAYS - 1) downTo 0) {
            currentCoroutineContext().ensureActive()
            val date = today.minusDays(back.toLong())
            val inputs = catching { engine.replay(date, profile, zone, now) }.getOrNull()?.charge ?: continue
            replays[date] = inputs
            traces += SelfFacts.trace(inputs, base, date, falseStepsOf(byDate[date]), partial = date == today)
        }

        val facts = Facts(
            today = today,
            days = traces,
            nights = SelfFacts.nights(published),
            readiness = SelfFacts.readiness(published),
            readinessWeek = byDate[today]?.readinessTrend?.map { it.second }.orEmpty(),
            calories = SelfFacts.calories(published),
            strap = StrapFacts(battery, charging),
        )
        val found = SelfChecks.inspectAll(facts)
        val out = ArrayList<Decision>()
        for ((date, group) in found.groupBy { it.day }) {
            currentCoroutineContext().ensureActive()
            val inputs = replays[date]
            val evaluate = inputs?.let {
                SelfPlanner.Evaluate { values ->
                    val tuning = values.entries.fold(base) { t, (knob, v) -> knob.write(t, v) }
                    SelfFacts.trace(it, tuning, date, falseStepsOf(byDate[date]), partial = date == today)
                }
            }
            out += SelfPlanner.decide(group, evaluate)
        }
        return out
    }

    /**
     * The last [TRACE_DAYS] days' Energy inputs to [today], to be replayed under other tunings ([Replays]): what the AI body
     * learning tries a proposal on. The replays are the same ones [run] makes; nothing is detected or changed here.
     */
    suspend fun replays(today: LocalDate, profile: UserProfile, zone: ZoneId, now: Long): Replays {
        val inputs = LinkedHashMap<LocalDate, ChargeInputs>()
        val falseSteps = HashMap<LocalDate, Int>()
        for (back in (TRACE_DAYS - 1) downTo 0) {
            currentCoroutineContext().ensureActive()
            val date = today.minusDays(back.toLong())
            val charge = catching { engine.replay(date, profile, zone, now) }.getOrNull()?.charge ?: continue
            inputs[date] = charge
            falseSteps[date] = falseStepsOf(catching { health.recovery(date, profile) }.getOrNull())
        }
        return Replays(inputs, falseSteps, today)
    }

    private fun falseStepsOf(r: RecoveryResult?): Int = r?.activity?.falseSteps?.values?.sum() ?: 0

    companion object {
        /** Nights, Readiness and calories are read over this many days to today. */
        const val RESULT_DAYS = 10

        /** Energy is replayed for this many days to today: yesterday's evening can still be settled once the day is over. */
        const val TRACE_DAYS = 4
    }
}
