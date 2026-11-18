package org.openstrap.app.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** The request's shape and what each reply, status and failure means, with a fake in place of the network. */
class AiClientTest {
    private class Call(val url: String, val key: String, val body: String?)

    private class Fake(private vararg val replies: Any) : AiHttp {
        val calls = mutableListOf<Call>()

        override suspend fun call(url: String, key: String, body: String?, timeoutMillis: Int): AiReply {
            calls += Call(url, key, body)
            return when (val r = replies[minOf(calls.size - 1, replies.size - 1)]) {
                is AiReply -> r
                is Throwable -> throw r
                else -> error("bad fake")
            }
        }
    }

    private fun completion(content: String) =
        AiReply(200, JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString())

    private val key = "sk-test1234567890abcdef"

    @Test fun `the request goes to the chat endpoint with the key in hand and the asked-for shape`() = runTest {
        val http = Fake(completion("{}"))
        val result = AiClient(http).analyse(key, """{"v":1}""")
        assertEquals(AiResult.Ok("{}"), result)
        val call = http.calls.single()
        assertEquals("https://api.deepseek.com/chat/completions", call.url)
        assertEquals(key, call.key)
        val body = JSONObject(call.body!!)
        assertEquals("deepseek-chat", body.getString("model"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals(0.3, body.getDouble("temperature"), 0.0001)
        assertFalse(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertTrue(messages.getJSONObject(1).getString("content").endsWith("""{"v":1}"""))
    }

    @Test fun `the key is never in the body`() = runTest {
        val http = Fake(completion("{}"))
        AiClient(http).analyse(key, """{"v":1}""")
        assertFalse(http.calls.single().body!!.contains(key))
    }

    @Test fun `the timeout is two and a half minutes, four for the deeper thinking, one for a key check`() {
        assertEquals(150_000, AiClient.TIMEOUT_MS)
        assertEquals(240_000, AiClient.DEEP_TIMEOUT_MS)
        assertEquals(60_000, AiClient.CHECK_TIMEOUT_MS)
        assertEquals("api.deepseek.com", AiClient.HOST)
    }

    @Test fun `statuses mean what the screen says`() {
        assertEquals(AiFailure.BadKey, AiClient.failureOf(401))
        assertEquals(AiFailure.NoBalance, AiClient.failureOf(402))
        assertEquals(AiFailure.RateLimit, AiClient.failureOf(429))
        assertEquals(AiFailure.Timeout, AiClient.failureOf(408))
        assertEquals(AiFailure.Server, AiClient.failureOf(500))
        assertEquals(AiFailure.Server, AiClient.failureOf(503))
        assertEquals(AiFailure.Rejected, AiClient.failureOf(400))
        assertEquals(AiFailure.Rejected, AiClient.failureOf(422))
        assertEquals(AiFailure.Unknown, AiClient.failureOf(302))
    }

    @Test fun `a refused request gives its failure and status`() = runTest {
        for ((code, failure) in listOf(401 to AiFailure.BadKey, 402 to AiFailure.NoBalance, 429 to AiFailure.RateLimit, 503 to AiFailure.Server, 422 to AiFailure.Rejected)) {
            val result = AiClient(Fake(AiReply(code, "{}"))).analyse(key, "{}")
            assertEquals(AiResult.Failed(failure, code), result)
        }
    }

    @Test fun `no connection is offline, a slow socket is a timeout, anything else is unknown`() = runTest {
        assertEquals(AiResult.Failed(AiFailure.Offline), AiClient(Fake(IOException("no route"))).analyse(key, "{}"))
        assertEquals(AiResult.Failed(AiFailure.Timeout), AiClient(Fake(SocketTimeoutException())).analyse(key, "{}"))
        assertEquals(AiResult.Failed(AiFailure.Unknown), AiClient(Fake(IllegalStateException("boom"))).analyse(key, "{}"))
    }

    private class Slow(private val millis: Long, private val reply: AiReply) : AiHttp {
        val timeouts = mutableListOf<Int>()

        override suspend fun call(url: String, key: String, body: String?, timeoutMillis: Int): AiReply {
            timeouts += timeoutMillis
            delay(millis)
            return reply
        }
    }

    @Test fun `an answer that takes over two and a half minutes is a timeout`() = runTest {
        assertEquals(AiResult.Failed(AiFailure.Timeout), AiClient(Slow(160_000, AiReply(200, ""))).analyse(key, "{}"))
        // A slow but in-time answer is fine, and the socket is told the same limit.
        val fine = Slow(120_000, completion("{}"))
        assertEquals(AiResult.Ok("{}"), AiClient(fine).analyse(key, "{}"))
        assertEquals(listOf(150_000), fine.timeouts)
    }

    @Test fun `the deeper thinking is given four minutes`() = runTest {
        val slow = Slow(200_000, completion("{}"))
        assertEquals(AiResult.Ok("{}"), AiClient(slow).analyse(key, "{}", deeper = true))
        assertEquals(listOf(240_000), slow.timeouts)
        assertEquals(AiResult.Failed(AiFailure.Timeout), AiClient(Slow(250_000, completion("{}"))).analyse(key, "{}", deeper = true))
        // The same wait on the chat model is too long.
        assertEquals(AiResult.Failed(AiFailure.Timeout), AiClient(Slow(200_000, completion("{}"))).analyse(key, "{}"))
    }

    @Test fun `the deeper thinking asks the reasoning model, and falls back to the chat model when refused`() = runTest {
        val ok = Fake(completion("{}"))
        AiClient(ok).analyse(key, "{}", deeper = true)
        assertEquals("deepseek-reasoner", JSONObject(ok.calls.single().body!!).getString("model"))
        assertFalse(JSONObject(ok.calls.single().body!!).has("response_format"))
        val refused = Fake(AiReply(400, "{}"), completion("{}"))
        assertEquals(AiResult.Ok("{}"), AiClient(refused).analyse(key, "{}", deeper = true))
        assertEquals(listOf("deepseek-reasoner", "deepseek-chat"), refused.calls.map { JSONObject(it.body!!).getString("model") })
        // A wrong key or no balance is not retried, and the chat model's own refusal is not either.
        val wrong = Fake(AiReply(401, "{}"))
        assertEquals(AiResult.Failed(AiFailure.BadKey, 401), AiClient(wrong).analyse(key, "{}", deeper = true))
        assertEquals(1, wrong.calls.size)
        val chat = Fake(AiReply(400, "{}"))
        assertEquals(AiResult.Failed(AiFailure.Rejected, 400), AiClient(chat).analyse(key, "{}"))
        assertEquals(1, chat.calls.size)
    }

    @Test fun `the answer is the reply's content, not the reasoning before it`() {
        val reply = JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("reasoning_content", "تفكير طويل").put("content", """{"summary":"س"}"""))),
        ).toString()
        assertEquals("""{"summary":"س"}""", AiClient.content(reply))
    }

    @Test fun `a reply with no text in it is malformed`() = runTest {
        assertEquals(AiResult.Failed(AiFailure.Malformed), AiClient(Fake(AiReply(200, "not json"))).analyse(key, "{}"))
        assertEquals(AiResult.Failed(AiFailure.Malformed), AiClient(Fake(AiReply(200, """{"choices":[]}"""))).analyse(key, "{}"))
        assertEquals(AiResult.Failed(AiFailure.Malformed), AiClient(Fake(completion("  "))).analyse(key, "{}"))
    }

    @Test fun `the text of a completion is read`() {
        assertEquals("hello", AiClient.content(completion("hello").body))
        assertNull(AiClient.content("[]"))
    }

    @Test fun `a working key answers the balance check without a chat`() = runTest {
        val http = Fake(AiReply(200, """{"is_available":true,"balance_infos":[]}"""))
        assertEquals(AiResult.Ok(Unit), AiClient(http).check(key))
        assertEquals("https://api.deepseek.com/user/balance", http.calls.single().url)
        assertNull(http.calls.single().body)
    }

    @Test fun `an empty account is no balance`() = runTest {
        assertEquals(AiResult.Failed(AiFailure.NoBalance, 402), AiClient(Fake(AiReply(200, """{"is_available":false}"""))).check(key))
    }

    @Test fun `a wrong key stops at the first call`() = runTest {
        val http = Fake(AiReply(401, "{}"))
        assertEquals(AiResult.Failed(AiFailure.BadKey, 401), AiClient(http).check(key))
        assertEquals(1, http.calls.size)
    }

    @Test fun `a server without the balance endpoint is asked for one token instead`() = runTest {
        val http = Fake(AiReply(404, ""), completion("1"))
        assertEquals(AiResult.Ok(Unit), AiClient(http).check(key))
        assertEquals(2, http.calls.size)
        val tiny = JSONObject(http.calls[1].body!!)
        assertEquals(1, tiny.getInt("max_tokens"))
        assertTrue(http.calls[1].url.endsWith("/chat/completions"))
    }
}
