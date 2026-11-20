package org.openstrap.app.data.ailearn

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Days
import org.openstrap.app.data.ai.AiClient
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ai.AiInputSource
import org.openstrap.app.data.ai.AiKeyStore
import org.openstrap.app.data.ai.AiResult
import org.openstrap.app.data.catching
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.ReplayPlausibility
import org.openstrap.app.data.selfcheck.SelfCheckRunner
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.settings.SettingsStore
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs the AI body learning (docs/algorithms/self-check.md): once a day, after the strap's first sync, it sends the history
 * and yesterday's profile to the person's own DeepSeek key ([LearnPayload]), reads the answer ([LearnAnswer]) and has
 * [AiLearning] and [ProposalGate] decide what of it is kept. It never changes stored data, sleep scores or Readiness weights; a
 * knob is only ever moved through a self-check incident that Undo takes back.
 *
 * [tick] is the background worker's look (hourly, only with a network): it asks only when the rules say a run is due
 * ([AiLearning.gate]), and tells the day's one notification when there is one. [learnNow] is the person's tap and ignores the
 * day's limits. Both wait while [busy] (a workout is recording), and nothing here logs the key or what is sent: a failure is
 * logged by its name alone.
 */
class AiLearningKeeper(
    private val settings: SettingsStore,
    private val keys: AiKeyStore,
    private val client: AiClient,
    private val source: AiInputSource,
    private val runner: SelfCheckRunner,
    /** True while a workout is recording or the strap's firmware is updating: nothing is asked then. */
    private val busy: () -> Boolean = { false },
    /** True during quiet hours, Do Not Disturb or the night: the notification waits (and goes out the same day or not at all). */
    private val quiet: suspend () -> Boolean = { false },
    /** Tells the person what was learned today. */
    private val notify: (AiLearning.Notice, LocalDate) -> Unit = { _, _ -> },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MILLIS },
) {
    /** What a look came to. */
    sealed interface Outcome {
        /** The switch is off, or the assistant is not on. */
        data object Off : Outcome

        /** No key is saved. */
        data object NoKey : Outcome

        /** A workout is recording (or the firmware is updating): not now. */
        data object Busy : Outcome

        /** Another run is going on. */
        data object Running : Outcome

        /** A run was not due, for [gate]'s reason. */
        data class Skipped(val gate: AiLearning.Gate) : Outcome

        /** It learned: the lines it said, how many knob changes it applied and how many proposals it refused. */
        data class Learned(val lines: List<String>, val changed: Int, val refused: Int) : Outcome

        /** It could not: [failure], and the HTTP status when there was one. */
        data class Failed(val failure: AiFailure, val code: Int?) : Outcome
    }

    private val lock = Mutex()
    private val working = MutableStateFlow(false)

    /** True while a request is being made, for the page's button. */
    val running: StateFlow<Boolean> = working.asStateFlow()

    /** Whether the learning runs: the assistant is on with a key, and the learning's own switch has not been turned off. */
    val active: Flow<Boolean> = combine(keys.state, settings.aiLearnOn) { key, on -> key.ready && (on ?: true) }.distinctUntilChanged()

    /** The background worker's look: a run if one is due, then the day's notification. */
    suspend fun tick(): Outcome {
        val outcome = run(manual = false)
        if (outcome !is Outcome.Off && outcome !is Outcome.NoKey) catching { tell() }.onFailure { Log.w(TAG, "ai learning: telling failed", it) }
        return outcome
    }

    /** "Learn now": a run whatever the day's limits say, still not while a workout records. Not told afterwards: the person is looking. */
    suspend fun learnNow(): Outcome = run(manual = true)

    /** "Clear what it learned": the profile, what the AI proposed and its notes are gone, and it learns again from tomorrow. Waits for a run that is going on. */
    suspend fun clear() {
        lock.lock()
        try {
            val today = Days.date(nowSeconds(), zone())
            settings.updateSelfCheck { AiLearning.clear(it) }
            settings.saveAiLearn(null, AiLearning.cleared(today))
        } finally {
            lock.unlock()
        }
    }

    // The request, the answer and what is kept; any failure of the gathering is "no data", never a crash.
    @Suppress("LongMethod", "ReturnCount")
    private suspend fun run(manual: Boolean): Outcome {
        if (!isOn()) return Outcome.Off
        val key = keys.load() ?: return Outcome.NoKey
        if (busy()) return Outcome.Busy
        if (!lock.tryLock()) return Outcome.Running
        working.value = true
        try {
            val now = nowSeconds()
            val z = zone()
            val today = Days.date(now, z)
            val state = settings.aiLearnState.first()
            if (!manual) {
                val synced = settings.strapSnapshot.first().lastSyncDone?.let { Days.date(it, z) == today } == true
                val gate = AiLearning.gate(state, now, today, synced)
                if (gate != AiLearning.Gate.Go) return Outcome.Skipped(gate)
            }
            suspend fun fail(failure: AiFailure, code: Int? = null): Outcome {
                settings.updateAiLearnState { AiLearning.failed(it, failure, code, now, today, manual) }
                Log.w(TAG, "ai learning: ${failure.name}")
                return Outcome.Failed(failure, code)
            }

            val input = catching { source.gather(AiInputSource.LEARN_DAYS) }.getOrElse { return fail(AiFailure.NoData) }
            if (input.days.count { it.sleepMinutes != null } < MIN_NIGHTS) return fail(AiFailure.NoData)
            val stored = settings.aiLearnProfile.first()
            val old = BodyProfile.decode(stored)
            val self = settings.selfCheck.first()
            val built = LearnPayload.build(LearnInput(input, stored?.takeIf { old != null }, self.incidents, SelfLearning.learned(self, today)))
            val reply = client.learn(key, LearnPrompt.system, LearnPrompt.userMessage(built.json), keys.deeper.first())
            val text = when (reply) {
                is AiResult.Failed -> return fail(reply.failure, reply.code)
                is AiResult.Ok -> reply.value
            }
            val answer = LearnAnswer.parse(text) ?: return fail(AiFailure.Malformed)

            val knobs = settings.selfCheckOn.first()
            val plausibility = if (knobs && answer.proposals.any { Knob.of(it.key) != null }) plausibility(today, z, now) else null
            val dataFrom = input.days.firstOrNull { it.sleepMinutes != null || it.steps != null || it.restingHr != null }?.date
            var finished: AiLearning.Finished? = null
            // Judged inside the update, on the self-check's state as it is then, so an Undo made during the request is not lost.
            settings.updateSelfCheck { current ->
                AiLearning.finish(answer, old, dataFrom, today, now, state, current, plausibility, knobs, manual, built.bytes, built.rows)
                    .also { finished = it }.selfCheck
            }
            val done = checkNotNull(finished)
            settings.saveAiLearn(BodyProfile.encode(done.profile).takeUnless { done.profile.isEmpty }, done.state)
            return Outcome.Learned(done.state.learned, done.judged.accepted.size, done.judged.refused.size)
        } finally {
            working.value = false
            lock.unlock()
        }
    }

    /** How the last days replay under a tuning, or null when they cannot be replayed (then no knob is moved). */
    private suspend fun plausibility(today: LocalDate, zone: ZoneId, now: Long): Plausibility? {
        val replays = catching { runner.replays(today, settings.profile.first(), zone, now) }.getOrNull()?.takeIf { it.days > 0 } ?: return null
        val base = settings.calibration.first()?.tuning ?: ChargeTuning.Default
        return ReplayPlausibility(replays, base)
    }

    /** The day's one notification, when [AiLearning.notice] says so. Marked told before it goes out, so it can never go twice. */
    private suspend fun tell() {
        if (busy()) return
        val today = Days.date(nowSeconds(), zone())
        val notice = AiLearning.notice(settings.aiLearnState.first(), settings.selfCheck.first(), today, quiet()) ?: return
        settings.updateAiLearnState { AiLearning.told(it, today) }
        notify(notice, today)
    }

    private suspend fun isOn(): Boolean = keys.state.first().ready && (settings.aiLearnOn.first() ?: true)

    companion object {
        private const val TAG = "OpenStrap"
        private const val MILLIS = 1000L

        /** Nights with sleep in the history before there is anything to learn from. */
        const val MIN_NIGHTS = 5
    }
}
