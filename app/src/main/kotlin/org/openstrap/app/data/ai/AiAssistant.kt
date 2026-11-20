package org.openstrap.app.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.openstrap.app.data.Days
import java.time.ZoneId

/**
 * The smart assistant, from the settings to the screen: the switch and key ([keys]), what to send ([source]), the call
 * ([client]) and the last answers ([results]). It never starts by itself: [analyse] runs only when the user asks, from
 * the Analysis screen's button, and [check] only from the settings page's Test.
 */
class AiAssistant(
    val keys: AiKeyStore,
    private val client: AiClient,
    private val source: AiInputSource,
    val results: AiResultStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MILLIS },
    /** The body profile the AI body learning keeps (its JSON), sent with the analysis so it is made for this body; null when there is none. */
    private val profile: suspend () -> String? = { null },
) {
    /**
     * One analysis: switched on and a key saved, the numbers gathered, sent, the answer read and kept for today. A
     * failure is an [AiResult.Failed] with its reason; nothing is kept then. Cancellation passes through.
     */
    // Gathering reads the whole model: whatever it throws is "could not read the data", not a crash.
    @Suppress("TooGenericExceptionCaught")
    suspend fun analyse(): AiResult<AiCached> {
        if (!keys.enabled.first()) return AiResult.Failed(AiFailure.Disabled)
        val key = keys.load() ?: return AiResult.Failed(AiFailure.NoKey)
        val input = try {
            source.gather()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AiResult.Failed(AiFailure.NoData)
        }
        if (!enough(input)) return AiResult.Failed(AiFailure.NoData)
        // A profile that can't be read is no profile: the analysis goes ahead without it.
        val body = try {
            profile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val payload = AiPayload.build(input, body)
        return when (val reply = client.analyse(key, payload.json, deeper = keys.deeper.first())) {
            is AiResult.Failed -> reply
            is AiResult.Ok -> {
                val answer = AiAnswer.parse(reply.value) ?: return AiResult.Failed(AiFailure.Malformed)
                val now = nowSeconds()
                val cached = AiCached(Days.date(now, zone()), now, answer, payload.days, payload.nights, payload.workouts)
                // Not being able to keep it doesn't lose it: it is shown this once.
                try {
                    results.save(cached)
                } catch (e: java.io.IOException) {
                    Unit
                }
                AiResult.Ok(cached)
            }
        }
    }

    /** Whether [key] is accepted by DeepSeek (the settings page's Test). */
    suspend fun check(key: String): AiResult<Unit> = client.check(key)

    private companion object {
        const val MILLIS = 1000L
    }

    internal fun enough(input: AiInput): Boolean = input.days.count { it.sleepMinutes != null || it.steps != null } >= MIN_DAYS
}

/** Days with any data before there is anything to ask about. */
private const val MIN_DAYS = 3
