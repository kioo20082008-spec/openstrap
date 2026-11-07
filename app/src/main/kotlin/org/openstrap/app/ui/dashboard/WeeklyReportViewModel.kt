package org.openstrap.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.WeeklyReport
import org.openstrap.app.data.catching
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.settings.UserProfile

/**
 * The week report ([WeeklyReport]) for the seven days ending yesterday, read once when the page opens. Loading while it is
 * worked out ([State.Loading]), and [State.Unavailable] when Recovery can't be (no report rather than a wrong one).
 */
// The report's week is the phone's own calendar.
@Suppress("ForbiddenMethodCall/uiClock")
class WeeklyReportViewModel(
    private val health: HealthRepository,
    private val profile: Flow<UserProfile>,
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data object Unavailable : State
        data class Ready(val report: WeeklyReport.Report) : State
    }

    val state: StateFlow<State> = flow { emit(catching { build() }.fold({ State.Ready(it) }, { State.Unavailable })) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), State.Loading)

    private suspend fun build(): WeeklyReport.Report {
        val zone = ZoneId.systemDefault()
        val last = LocalDate.now(zone).minusDays(1)
        val earlier = last.minusDays(WEEK)
        val from = earlier.minusDays(WEEK - 1)
        val p = profile.first()
        val now = health.recovery(last, p)
        // The week before has its own Readiness trend; a failure there only leaves that week's figure out.
        val before = catching { health.recovery(earlier, p) }.getOrNull()
        val readiness = (before?.readinessTrend.orEmpty() + now.readinessTrend).mapNotNull { (d, s) -> s?.let { d to it } }.toMap()
        val loads = (before?.dailyLoads.orEmpty() + now.dailyLoads).toMap() + (last to now.load)
        val sessions = health.sleepSessions(DayWindow(DayWindow.of(from, zone).from, DayWindow.of(last, zone).to)).first()
        return WeeklyReport.build(
            last = last,
            sleepNeed = now.sleepNeed,
            readiness = readiness,
            sleepScore = now.sleepScores,
            asleepMinutes = asleepByMorning(sessions, zone),
            restingHr = series(DailyMetric.RestingHr, from, last),
            hrv = series(DailyMetric.Hrv, from, last),
            steps = series(DailyMetric.Steps, from, last).mapValues { it.value.toInt() },
            load = loads,
        )
    }

    private suspend fun series(metric: DailyMetric, from: LocalDate, to: LocalDate): Map<LocalDate, Float> =
        health.daily(metric, from, to).first().associate { it.date to it.value }

    /** Minutes asleep in each morning's main sleep: the stages when the strap gave them, else the time less the awake. */
    private fun asleepByMorning(sessions: List<SleepSession>, zone: ZoneId): Map<LocalDate, Int> =
        sessions.filter { !it.nap }.groupBy { Instant.ofEpochSecond(it.end).atZone(zone).toLocalDate() }
            .mapValues { (_, day) -> day.maxOf { asleepMinutes(it) } }

    private fun asleepMinutes(s: SleepSession): Int {
        val stages = listOfNotNull(s.deepMinutes, s.lightMinutes, s.remMinutes)
        return if (stages.isNotEmpty()) stages.sum() else ((s.end - s.start) / SECONDS_A_MINUTE).toInt() - (s.awakeMinutes ?: 0)
    }

    private companion object {
        const val STOP_MS = 5_000L
        const val WEEK = 7L
        const val SECONDS_A_MINUTE = 60
    }
}
