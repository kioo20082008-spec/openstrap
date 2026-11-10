package org.openstrap.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.Insights
import org.openstrap.app.data.Journal
import org.openstrap.app.data.catching
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate

/**
 * The evening journal and what it shows: the habits tapped per day, and, from them and the mornings after
 * ([Insights]), how each one moves HRV, resting heart rate and the sleep score.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// The journal's days are the phone's own calendar days.
@Suppress("ForbiddenMethodCall/uiClock")
class JournalViewModel(
    private val settings: SettingsStore,
    private val health: HealthRepository,
    private val profile: Flow<UserProfile>,
) : ViewModel() {
    val tags: StateFlow<Map<LocalDate, Set<Journal.Tag>>> = settings.journal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyMap())

    val checkIns: StateFlow<Map<LocalDate, org.openstrap.app.data.MorningFeel.Entry>> = settings.checkIns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyMap())

    val calibration: StateFlow<org.openstrap.app.data.CalibrationResult?> = settings.calibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    fun checkIn(day: LocalDate, entry: org.openstrap.app.data.MorningFeel.Entry) {
        viewModelScope.launch { settings.setCheckIn(day, entry, LocalDate.now()) }
    }

    fun toggle(day: LocalDate, tag: Journal.Tag) {
        viewModelScope.launch { settings.toggleJournal(day, tag, LocalDate.now()) }
    }

    val insights: StateFlow<Insights.Result> = settings.journal
        .flatMapLatest { logged -> if (logged.isEmpty()) flowOf(Insights.Result.EMPTY) else analysis(logged) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), Insights.Result.EMPTY)

    private fun analysis(logged: Map<LocalDate, Set<Journal.Tag>>): Flow<Insights.Result> {
        val today = LocalDate.now()
        val from = today.minusDays(Journal.KEEP_DAYS.toLong())
        return combine(health.daily(DailyMetric.Hrv, from, today), health.daily(DailyMetric.RestingHr, from, today)) { hrv, rhr -> hrv to rhr }
            .mapLatest { (hrv, rhr) ->
                val scores = catching { health.recovery(today, profile.first()).sleepScores }.getOrDefault(emptyMap())
                Insights.analyze(
                    logged,
                    mapOf(
                        Insights.Metric.Hrv to hrv.associate { it.date to it.value },
                        Insights.Metric.RestingHr to rhr.associate { it.date to it.value },
                        Insights.Metric.SleepScore to scores.mapValues { it.value.toFloat() },
                    ),
                    today,
                )
            }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
