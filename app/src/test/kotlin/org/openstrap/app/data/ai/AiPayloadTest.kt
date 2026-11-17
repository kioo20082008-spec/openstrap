package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the smart assistant sends: only the whitelisted fields, no personal data, held under the size cap. */
class AiPayloadTest {
    private fun keysOf(v: Any?, into: MutableSet<String> = linkedSetOf()): Set<String> {
        when (v) {
            is JSONObject -> v.keys().forEach { k -> into += k; keysOf(v.get(k), into) }
            is JSONArray -> for (i in 0 until v.length()) keysOf(v.get(i), into)
        }
        return into
    }

    private fun json(input: AiInput = aiInput()) = JSONObject(AiPayload.build(input).json)

    @Test fun `every key in the payload is on the whitelist`() {
        val unknown = keysOf(json()) - AiPayload.KEYS
        assertEquals(emptySet<String>(), unknown)
    }

    @Test fun `a full input keeps 30 days, 14 nights and 14 workouts, oldest first`() {
        val built = AiPayload.build(aiInput())
        assertEquals(30, built.days)
        assertEquals(14, built.nights)
        assertEquals(14, built.workouts)
        assertFalse(built.trimmed)
        val j = JSONObject(built.json)
        assertEquals(30, j.getJSONArray("days").length())
        assertEquals(14, j.getJSONArray("nights").length())
        assertEquals(14, j.getJSONArray("workouts").length())
        assertEquals(AiPayload.shortDay(AI_TODAY.minusDays(30)), j.getJSONArray("days").getJSONObject(0).getString("d"))
        assertEquals(AiPayload.shortDay(AI_TODAY.minusDays(1)), j.getJSONArray("days").getJSONObject(29).getString("d"))
    }

    @Test fun `the fields the assistant needs are there`() {
        val j = json()
        assertEquals(1, j.getInt("v"))
        assertEquals("2026-11-17", j.getString("today"))
        assertEquals(480, j.getJSONObject("user").getInt("sleep_need_min"))
        assertEquals(10_000, j.getJSONObject("targets").getInt("steps"))
        assertEquals("yellow", j.getJSONObject("now").getString("plan"))
        assertEquals(40.0, j.getJSONObject("load_model").getDouble("fitness"), 0.001)
        assertEquals(30, j.getJSONObject("baselines").getInt("n"))
        val f = j.getJSONObject("forecast")
        assertEquals("rule_of_thumb", f.getString("basis"))
        assertTrue(f.has("expected_recovery_tomorrow"))
        assertTrue(f.has("load_trend"))
        assertEquals(150, f.getInt("sleep_debt_min"))
        val day = j.getJSONArray("days").getJSONObject(0)
        for (k in listOf("sleep", "sscore", "rec", "hrv", "rhr", "load", "strain", "steps", "energy_wake")) assertTrue(k, day.has(k))
        val night = j.getJSONArray("nights").getJSONObject(0)
        for (k in listOf("bed", "wake", "asleep", "deep", "rem", "light", "awake", "wakeups", "hrv", "score")) assertTrue(k, night.has(k))
        val w = j.getJSONArray("workouts").getJSONObject(0)
        for (k in listOf("sport", "min", "avg_hr", "max_hr", "load", "hr_drop_1m")) assertTrue(k, w.has(k))
    }

    @Test fun `no personal data and no raw streams can be in it`() {
        val text = AiPayload.build(aiInput(sport = "Run @ home https://x.co/a?b=1 me@mail.com 12.5,41.3")).json.lowercase()
        for (word in listOf("email", "name", "latitude", "longitude", "gps", "location", "address", "device", "serial", "token", "samples", "stream", "mac")) {
            assertFalse("\"$word\" in the payload", text.contains("\"$word"))
        }
        assertFalse(text.contains('@'))
        assertFalse(text.contains("://"))
        assertFalse(text.contains("mail.com"))
    }

    @Test fun `a sport name is cut down to letters, digits and spaces`() {
        val cleaned = AiPayload.cleanText("Run @ home https://x.co/a?b=1")
        assertTrue(cleaned, cleaned.startsWith("Run home https x co"))
        assertTrue(cleaned.all { it.isLetterOrDigit() || it == ' ' })
        assertTrue(cleaned.length <= 24)
        assertEquals("workout", AiPayload.cleanText("@@@"))
        assertTrue(AiPayload.cleanText("x".repeat(100)).length <= 24)
    }

    @Test fun `it stays under 12 kB, dropping the oldest history first when the nights are long`() {
        val big = AiPayload.build(aiInput(bedtime = "z".repeat(450)))
        assertTrue("${big.bytes} bytes", big.bytes <= AiPayload.MAX_BYTES)
        assertEquals(big.json.toByteArray().size, big.bytes)
        assertTrue(big.trimmed)
        assertTrue(big.nights < 14)
        // The baselines, targets and the forecast stay when history goes.
        val j = JSONObject(big.json)
        assertTrue(j.has("baselines") && j.has("targets") && j.has("forecast"))
    }

    @Test fun `a normal payload is well under the cap`() {
        val built = AiPayload.build(aiInput())
        assertTrue("${built.bytes} bytes", built.bytes < AiPayload.MAX_BYTES)
    }

    @Test fun `missing values are left out, not sent as null`() {
        val input = aiInput(days = 5, nights = 0, workouts = 0, fitness = null, debt = null)
            .let { it.copy(days = it.days.map { d -> d.copy(hrv = null, steps = null) }, ageYears = null, sex = "X") }
        val j = json(input)
        assertFalse(j.has("load_model"))
        assertFalse(j.getJSONObject("user").has("age"))
        assertFalse(j.getJSONObject("user").has("sex"))
        assertFalse(j.getJSONArray("days").getJSONObject(0).has("hrv"))
        assertFalse(j.getJSONArray("days").getJSONObject(0).has("steps"))
        assertEquals(0, j.getJSONArray("nights").length())
        assertFalse(AiPayload.build(input).json.contains("null"))
    }

    @Test fun `figures are rounded to one decimal`() {
        val text = AiPayload.build(aiInput()).json
        assertTrue(text.contains("\"hrv\":56.2"))
        assertFalse(text.contains("56.2345"))
    }

    @Test fun `the plan zone follows Today's cuts`() {
        assertNull(AiPlan.zone(null))
        assertEquals("red", AiPlan.zone(33))
        assertEquals("yellow", AiPlan.zone(34))
        assertEquals("yellow", AiPlan.zone(66))
        assertEquals("green", AiPlan.zone(67))
    }

    @Test fun `the prompt and the request carry the payload and the answer's keys`() {
        val payload = AiPayload.build(aiInput()).json
        val body = AiClient.requestBody(AiPrompt.system, AiPrompt.userMessage(payload))
        val user = body.getJSONArray("messages").getJSONObject(1).getString("content")
        assertTrue(user.endsWith(payload))
        for (k in listOf(AiAnswer.SUMMARY, AiAnswer.PATTERNS, AiAnswer.DO_THIS, AiAnswer.ADJUST, AiAnswer.OUTLOOK)) assertTrue(k, AiPrompt.system.contains(k))
        assertNotNull(AiPrompt.system)
    }
}
