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

    @Test fun `the timeout is a minute`() {
        assertEquals(60_000, AiClient.TIMEOUT_MS)
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

    @Test fun `an answer that takes over a minute is a timeout`() = runTest {
        val slow = object : AiHttp {
            override suspend fun call(url: String, key: String, body: String?, timeoutMillis: Int): AiReply {
                delay(120_000)
                return AiReply(200, "")
            }
        }
        assertEquals(AiResult.Failed(AiFailure.Timeout), AiClient(slow).analyse(key, "{}"))
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
