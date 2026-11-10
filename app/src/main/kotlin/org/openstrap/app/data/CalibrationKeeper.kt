package org.openstrap.app.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.SettingsStore
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps the personal calibrations ([CalibrationResult]) in step: hands the stored one to [HealthRepository] as it
 * changes, and works it out again in the background when it is [CalibrationResult.due]. The replay is heavy, so it starts a
 * minute after the app does and then looks again every [CHECK_EVERY_MS].
 */
class CalibrationKeeper(
    private val dao: HealthDao,
    private val settings: SettingsStore,
    private val health: HealthRepository,
    private val scope: CoroutineScope,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    fun start() {
        scope.launch { settings.calibration.collect { health.applyCalibration(it) } }
        scope.launch {
            delay(START_DELAY_MS)
            while (true) {
                catching { refreshIfDue() }.onFailure { Log.w(TAG, "calibration failed", it) }
                delay(CHECK_EVERY_MS)
            }
        }
    }

    /** Works the calibration out and stores it when the stored one is missing or old. */
    suspend fun refreshIfDue() {
        val zone = ZoneId.systemDefault()
        val now = nowSeconds()
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        if (!CalibrationResult.due(settings.calibration.first(), today)) return
        settings.setCalibration(CalibrationRunner(dao).run(today, settings.profile.first(), zone, now))
    }

    private companion object {
        const val TAG = "OpenStrap"
        const val START_DELAY_MS = 60_000L
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
