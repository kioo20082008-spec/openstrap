package org.openstrap.app.device

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.catching
import java.util.concurrent.TimeUnit

/**
 * The AI body learning's daily look (docs/algorithms/self-check.md): once an hour, and only with a network and a battery that
 * is not low, it asks the keeper whether a run is due. The keeper decides: at most one successful run a day, after the strap's
 * first sync that day, a wait after each failure, three automatic tries a day at most, and never while a workout records. So
 * most looks end at once, and a failed one is simply tried again at a later look. It is scheduled only while the learning is
 * on ([follow]).
 */
class AiLearnWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as OpenStrapApp).container
        catching { container.aiLearning.tick() }
        // A look that found nothing to do, or failed, is not a failed job: the next hour's look is the retry.
        return Result.success()
    }

    companion object {
        private const val WORK = "ai-learn"

        /** Starts the hourly look; a no-op when it is already scheduled. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AiLearnWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        /** Keeps the schedule with [active]: set while the learning is on (the assistant on with a key, and its own switch), gone while it is off. */
        suspend fun follow(context: Context, active: Flow<Boolean>) {
            active.distinctUntilChanged().collect { if (it) schedule(context) else cancel(context) }
        }
    }
}
