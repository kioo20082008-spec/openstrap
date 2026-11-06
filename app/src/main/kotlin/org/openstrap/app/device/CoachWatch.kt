package org.openstrap.app.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.openstrap.app.MainActivity
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.R
import org.openstrap.app.presentation.ChartPoint
import org.openstrap.app.presentation.Coach
import org.openstrap.app.i18n.Strings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The coach while the app is closed: every 15 minutes it reads the day's stress minutes, the strap's battery and
 * tonight's bedtime, and when one of them calls for it says so once that day, as a notification and a buzz on the strap.
 */
class CoachWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OpenStrapApp
        val c = app.container
        val zone = ZoneId.systemDefault()
        val now = Instant.now().epochSecond
        val today = LocalDate.now(zone)
        val day = runCatching { c.days.day(today, true).first() }.getOrNull() ?: return Result.success()
        val connected = c.strap.connection.value as? ConnectionState.Connected
        val need = c.settings.goals.first().sleepMinutes
        // After midnight today has no night yet: the wake is yesterday's.
        val wake = (day.sleep.main ?: runCatching { c.days.day(today.minusDays(1), false).first().sleep.main }.getOrNull())
            ?.end?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalTime() }
        val tips = Coach.tips(
            Coach.Input(
                now = now, zone = zone, isToday = true, readiness = null, loadTrend = null, sleepNeed = need,
                steps = day.steps, usualSteps = null, stepGoal = Int.MAX_VALUE,
                stress = day.stress.map { ChartPoint(it.timestamp, it.value.toFloat()) },
                restingHr = null, liveHr = null, recording = c.workout.state.value is org.openstrap.app.device.WorkoutState.Recording,
                usualBedtime = null, wake = wake, restingTrend = emptyList(),
                battery = connected?.batteryPercent, charging = connected?.charging == true,
            ),
        )
        val prefs = app.getSharedPreferences("coach", Context.MODE_PRIVATE)
        tips.filter { it.kind in Watched }.forEach { tip ->
            // The bedtime only in the half hour before it, so it's a reminder rather than news.
            if (tip.kind == Coach.Kind.Bedtime && wake != null) {
                val bed = wake.minusMinutes(need.toLong())
                val until = (bed.toSecondOfDay() - Instant.ofEpochSecond(now).atZone(zone).toLocalTime().toSecondOfDay()).let { if (it < 0) it + 86_400 else it }
                if (until > 30 * 60) return@forEach
            }
            // Once in 20 hours a kind, so 23:50 and 00:05 aren't two warnings.
            val key = "${tip.kind}-at"
            if (now - prefs.getLong(key, 0L) < 20 * 3600) return@forEach
            prefs.edit().putLong(key, now).apply()
            val manager = app.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return@forEach
            notify(app, tip)
            // The buzz only by day, and never in Do Not Disturb: a tip is never worth waking you.
            val hour = Instant.ofEpochSecond(now).atZone(zone).hour
            val quiet = manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL || hour < 8 || hour >= 22
            if (connected != null && !quiet) runCatching { c.strap.buzz(BUZZ) }
        }
        return Result.success()
    }

    private fun notify(context: Context, tip: Coach.Tip) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, Strings.app.get(R.string.coach_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, tip.kind.ordinal, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            ID + tip.kind.ordinal,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_openstrap)
                .setContentTitle(tip.title)
                .setContentText(tip.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(tip.body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL = "coach"
        private const val ID = 7400
        /** A new name since the worker moved package: the old request names a class that's gone, so it's cancelled. */
        private const val WORK = "coach-watch-device"
        private const val OLD_WORK = "coach-watch"
        /** Two short pulses: gentle enough for a tip, distinct from a call. */
        private val BUZZ = listOf(180, 160, 180)
        private val Watched = setOf(Coach.Kind.Stress, Coach.Kind.Charge, Coach.Kind.Bedtime)

        fun schedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(OLD_WORK)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CoachWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
