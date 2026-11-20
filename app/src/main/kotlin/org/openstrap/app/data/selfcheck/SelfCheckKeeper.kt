package org.openstrap.app.data.selfcheck

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.catching
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps the self-check going (docs/algorithms/self-check.md): hands what it learned to [HealthRepository] as the state, the
 * switch or the day changes, runs a pass ([SelfCheckRunner]) when [SelfLearning.due], and tells about what it found,
 * through [notify], when [SelfLearning.nextNotice] says so. [tick] is cheap when nothing is due, so it is called from the
 * background worker every 15 minutes and from a short delay after new data lands; the heavy pass is gated to once per
 * [SelfLearning.RUN_EVERY_SECONDS], never overlaps itself, and waits while [busy] (a workout is recording). Off, it does
 * nothing and Energy is as published.
 */
class SelfCheckKeeper(
    dao: HealthDao,
    private val settings: SettingsStore,
    private val health: HealthRepository,
    private val scope: CoroutineScope,
    /** True while a workout is recording or the strap's firmware is updating: nothing heavy runs then. */
    private val busy: () -> Boolean = { false },
    /** True during quiet hours, Do Not Disturb or the night: nothing is told then, and it is told when the hours are over. */
    private val quiet: suspend () -> Boolean = { false },
    /** Tells the user about [SelfLearning.Incident]. */
    private val notify: (SelfLearning.Incident) -> Unit = {},
    private val runner: SelfCheckRunner = SelfCheckRunner(health, dao),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val lock = Mutex()

    fun start() {
        // What was learned applies as it changes, and as the days pass (the correction fades, so the day matters).
        scope.launch {
            combine(settings.selfCheckOn, settings.selfCheck, hourly()) { on, state, _ ->
                if (on) SelfLearning.learned(state, today()) else ChargeTuning.Default
            }.distinctUntilChanged().collect { health.applyLearned(it) }
        }
        // A pass a little after new data lands (a sync's end), and a first one soon after the app starts.
        scope.launch {
            delay(START_DELAY_MS)
            catching { tick() }.onFailure { Log.w(TAG, "self-check failed", it) }
            health.changes().collectLatest {
                delay(AFTER_DATA_MS)
                catching { tick() }.onFailure { Log.w(TAG, "self-check failed", it) }
            }
        }
    }

    /** One look: a pass when it is due, then at most one notice. Does nothing while off or [busy]. */
    suspend fun tick() = lock.withLock {
        if (!settings.selfCheckOn.first() || busy()) return@withLock
        val zone = ZoneId.systemDefault()
        val now = nowSeconds()
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        if (SelfLearning.due(settings.selfCheck.first(), now, today)) {
            val strap = settings.strapSnapshot.first()
            val calibration = settings.calibration.first()?.tuning ?: ChargeTuning.Default
            val decisions = catching { runner.run(today, settings.profile.first(), zone, now, calibration, strap.battery, strap.charging) }
                .onFailure { Log.w(TAG, "self-check pass failed", it) }
                .getOrDefault(emptyList())
            // A failed pass still counts as a run, so it is not tried again every quarter hour.
            settings.updateSelfCheck { SelfLearning.settle(it, decisions, now, today) }
        }
        if (busy()) return@withLock
        val next = SelfLearning.nextNotice(settings.selfCheck.first(), today, quiet()) ?: return@withLock
        settings.updateSelfCheck { SelfLearning.told(it, next.id, today) }
        notify(next)
    }

    private fun today(): LocalDate = Instant.ofEpochSecond(nowSeconds()).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun hourly() = flow {
        while (true) {
            emit(Unit)
            delay(HOUR_MS)
        }
    }

    private companion object {
        const val TAG = "OpenStrap"
        const val START_DELAY_MS = 90_000L
        const val AFTER_DATA_MS = 2 * 60_000L
        const val HOUR_MS = 60 * 60_000L
    }
}
