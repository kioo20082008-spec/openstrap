package org.openstrap.app.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Why a request to the assistant didn't give an answer. Each has its own words on the screen. */
enum class AiFailure {
    /** The assistant is switched off. */
    Disabled,

    /** No key is saved. */
    NoKey,

    /** No connection to DeepSeek (no network, no DNS, refused). */
    Offline,

    /** 401: DeepSeek doesn't accept the key. */
    BadKey,

    /** 402: the account has no balance. */
    NoBalance,

    /** 429: too many requests, try again in a while. */
    RateLimit,

    /** No answer in time: 150 seconds, or 240 for the deeper thinking. */
    Timeout,

    /** An answer that has nothing in it to show. */
    Malformed,

    /** Too few days of data to ask: nothing is sent. */
    NoData,

    /** 5xx: DeepSeek is busy or down. */
    Server,

    /** Another refusal (400, 422, ...). */
    Rejected,

    /** Anything else. */
    Unknown,
}

/** The outcome of one request: the answer, or why there isn't one. [code] is the HTTP status when there was one. */
sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>

    data class Failed(val failure: AiFailure, val code: Int? = null) : AiResult<Nothing>
}

/** What came back: the HTTP status and the body as text. */
data class AiReply(val code: Int, val body: String)

/** One HTTPS call, so the client's rules (what to send, what a status means) are tested without a network. */
interface AiHttp {
    /** Sends [body] (null for a GET) with the bearer [key] to [url] and gives the reply; throws [IOException] when there is none. */
    suspend fun call(url: String, key: String, body: String?, timeoutMillis: Int): AiReply
}

/**
 * The HTTPS call over `HttpURLConnection`, with no new dependency. It runs off the main thread, gives up after
 * [timeoutMillis] and closes the connection when the caller is cancelled, which ends a read that is waiting. The key goes
 * in the Authorization header only; nothing is logged.
 */
// The IO dispatcher is chosen here, where the blocking call is.
@Suppress("InjectDispatcher")
class UrlConnectionHttp : AiHttp {
    override suspend fun call(url: String, key: String, body: String?, timeoutMillis: Int): AiReply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            coroutineScope {
                // Cancelled with the caller: closing the connection makes the blocked read throw instead of waiting.
                val guard = launch { try { awaitCancellation() } finally { conn.disconnect() } }
                try {
                    conn.connectTimeout = CONNECT_MS
                    conn.readTimeout = timeoutMillis
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    conn.setRequestProperty("Accept", "application/json")
                    if (body == null) {
                        conn.requestMethod = "GET"
                    } else {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        conn.setFixedLengthStreamingMode(bytes.size)
                        conn.outputStream.use { it.write(bytes) }
                    }
                    val code = conn.responseCode
                    val stream = if (code in OK_RANGE) conn.inputStream else conn.errorStream
                    AiReply(code, stream?.let { readCapped(it) }.orEmpty())
                } finally {
                    guard.cancel()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** The body as text, at most [MAX_REPLY_BYTES]: a reply is some tens of KB of JSON (more with the reasoning), so more is not one. */
    private fun readCapped(stream: InputStream): String = stream.use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (out.size() < MAX_REPLY_BYTES) {
            val n = it.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_MS = 15_000
        const val BUFFER = 8 * 1024
        const val MAX_REPLY_BYTES = 1024 * 1024
        val OK_RANGE = 200..299
    }
}

/**
 * The DeepSeek calls, OpenAI-compatible: [analyse] posts the payload to `/chat/completions` (model deepseek-chat, JSON
 * answer, temperature 0.3, 150 seconds; or, for the deeper thinking, deepseek-reasoner with 240 seconds and no JSON
 * mode, which that model may refuse: the answer is read tolerantly anyway, and a refusal is retried once on the chat
 * model), [check] asks the account endpoint whether the key works without spending a token. Every outcome is an [AiResult]: no exception leaves, and neither the key nor what was sent is ever logged or
 * kept in an error.
 */
class AiClient(private val http: AiHttp = UrlConnectionHttp(), private val base: String = BASE) {
    /**
     * Sends [payload] (an [AiPayload] JSON) with [key] and gives the reply's text, to be read by [AiAnswer.parse]. With
     * [deeper] the reasoning model is asked, and if DeepSeek refuses that request (a 4xx) it is asked again on the chat model.
     */
    suspend fun analyse(key: String, payload: String, deeper: Boolean = false): AiResult<String> =
        askWithFallback(key, AiPrompt.system, AiPrompt.userMessage(payload), deeper)

    /**
     * The AI body learning's daily request (data/ailearn): [system] and [user] are its prompt ([org.openstrap.app.data.ailearn.LearnPrompt]),
     * sent and answered exactly as [analyse] is, on the chat model, or on the reasoning model with [deeper]. The reply's text
     * is read by [org.openstrap.app.data.ailearn.LearnAnswer.parse].
     */
    suspend fun learn(key: String, system: String, user: String, deeper: Boolean = false): AiResult<String> =
        askWithFallback(key, system, user, deeper)

    private suspend fun askWithFallback(key: String, system: String, user: String, deeper: Boolean): AiResult<String> {
        val first = ask(key, system, user, deeper)
        if (deeper && first is AiResult.Failed && first.failure == AiFailure.Rejected) return ask(key, system, user, false)
        return first
    }

    private suspend fun ask(key: String, system: String, user: String, deeper: Boolean): AiResult<String> {
        val body = requestBody(system, user, deeper).toString()
        return when (val r = send(base + CHAT, key, body, if (deeper) DEEP_TIMEOUT_MS else TIMEOUT_MS)) {
            is AiResult.Failed -> r
            is AiResult.Ok -> content(r.value)?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiFailure.Malformed)
        }
    }

    /**
     * Whether [key] works: the account's balance endpoint answers 200 with `is_available`. A server that doesn't know
     * that endpoint (404, 403, 405) is asked for a one-token answer instead.
     */
    suspend fun check(key: String): AiResult<Unit> {
        when (val r = send(base + BALANCE, key, null, CHECK_TIMEOUT_MS)) {
            is AiResult.Ok -> return if (available(r.value)) AiResult.Ok(Unit) else AiResult.Failed(AiFailure.NoBalance, CODE_PAYMENT)
            is AiResult.Failed -> if (r.code == null || r.code !in FALLBACK_CODES) return r
        }
        val tiny = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "1")))
            put("max_tokens", 1)
            put("stream", false)
        }.toString()
        return when (val r = send(base + CHAT, key, tiny, CHECK_TIMEOUT_MS)) {
            is AiResult.Ok -> AiResult.Ok(Unit)
            is AiResult.Failed -> r
        }
    }

    // A call that fails any other way is an outcome, not a crash; only cancellation passes through.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(url: String, key: String, body: String?, timeoutMs: Int): AiResult<String> = try {
        val reply = withTimeout(timeoutMs.toLong()) { http.call(url, key, body, timeoutMs) }
        if (reply.code in OK_RANGE) AiResult.Ok(reply.body) else AiResult.Failed(failureOf(reply.code), reply.code)
    } catch (e: TimeoutCancellationException) {
        AiResult.Failed(AiFailure.Timeout)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocketTimeoutException) {
        AiResult.Failed(AiFailure.Timeout)
    } catch (e: IOException) {
        AiResult.Failed(AiFailure.Offline)
    } catch (e: Exception) {
        AiResult.Failed(AiFailure.Unknown)
    }

    companion object {
        /** Where the data goes, named on the consent text. */
        const val HOST = "api.deepseek.com"
        const val BASE = "https://$HOST"
        const val CHAT = "/chat/completions"
        const val BALANCE = "/user/balance"
        const val MODEL = "deepseek-chat"

        /** The reasoning model the "deeper thinking" setting asks for. */
        const val MODEL_DEEP = "deepseek-reasoner"
        const val TEMPERATURE = 0.3

        /** How long an analysis may take on the chat model, on the reasoning model, and a key check. */
        const val TIMEOUT_MS = 150_000
        const val DEEP_TIMEOUT_MS = 240_000
        const val CHECK_TIMEOUT_MS = 60_000

        /** The longest answer asked for: the chat model's limit is 8192 tokens; the reasoning model's count includes its reasoning. */
        const val MAX_TOKENS = 8_000
        const val MAX_TOKENS_DEEP = 16_000
        private const val CODE_PAYMENT = 402
        private val OK_RANGE = 200..299
        private val FALLBACK_CODES = setOf(403, 404, 405)

        /** What an HTTP status means for the user. */
        fun failureOf(code: Int): AiFailure = when (code) {
            401 -> AiFailure.BadKey
            402 -> AiFailure.NoBalance
            429 -> AiFailure.RateLimit
            408 -> AiFailure.Timeout
            in 500..599 -> AiFailure.Server
            in 400..499 -> AiFailure.Rejected
            else -> AiFailure.Unknown
        }

        /**
         * The request body: model, the two messages, a JSON answer, temperature 0.3 and a ceiling on the answer's length.
         * For the [deeper] model there is no JSON mode and no temperature (it takes neither), and the ceiling is higher.
         */
        fun requestBody(system: String, user: String, deeper: Boolean = false): JSONObject = JSONObject().apply {
            put("model", if (deeper) MODEL_DEEP else MODEL)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
            if (!deeper) {
                put("response_format", JSONObject().put("type", "json_object"))
                put("temperature", TEMPERATURE)
            }
            put("max_tokens", if (deeper) MAX_TOKENS_DEEP else MAX_TOKENS)
            put("stream", false)
        }

        /** The model's text in a chat completion, or null when the reply has none. */
        fun content(reply: String): String? = try {
            JSONObject(reply).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null
        }

        /** Whether the balance reply says the account can be used. */
        fun available(reply: String): Boolean = try {
            JSONObject(reply).optBoolean("is_available", true)
        } catch (e: JSONException) {
            true
        }
    }
}
