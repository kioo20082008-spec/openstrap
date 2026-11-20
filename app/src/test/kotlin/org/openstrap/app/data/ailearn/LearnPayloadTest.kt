package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning

/** What the AI body learning sends once a day: whitelisted fields only, no personal data, held under the size cap. */
class LearnPayloadTest {
    private fun keysOf(v: Any?, into: MutableSet<String> = linkedSetOf()): Set<String> {
        when (v) {
            is JSONObject -> v.keys().forEach { k -> into += k; keysOf(v.get(k), into) }
            is JSONArray -> for (i in 0 until v.length()) keysOf(v.get(i), into)
        }
        return into
    }

    private fun json(input: LearnInput) = JSONObject(LearnPayload.build(input).json)

    /** A profile as stored, [bytes] or so long: statements that fill it. */
    private fun bigProfile(bytes: Int): String {
        val p = JSONObject().put("v", 1)
        var i = 0
        while (p.toString().length < bytes) {
            p.put("sleep_$i".take(12), "x".repeat(200))
            i++
        }
        return p.toString()
    }

    @Test fun `a two year history stays under the cap with every part in it`() {
        val built = LearnPayload.build(learnInput(days = 730, profile = BodyProfile.encode(sampleProfile())))
        assertTrue("${built.bytes} bytes", built.bytes <= LearnPayload.MAX_BYTES)
        assertEquals(built.json.toByteArray(Charsets.UTF_8).size, built.bytes)
        val j = JSONObject(built.json)
        for (k in listOf("profile", "history", "nights", "workouts", "insights", "incidents", "tuning", "user", "targets", "now")) assertTrue(k, j.has(k))
        assertEquals(built.daily, j.getJSONObject("history").getJSONObject("daily").getJSONArray("rows").length())
        assertEquals(LearnHistory.DAILY_DAYS, built.daily)
    }

    @Test fun `the history is daily for 90 days, weekly to a year and monthly beyond`() {
        val built = LearnPayload.build(learnInput(days = 730))
        assertEquals(90, built.daily)
        assertTrue("weeks ${built.weekly}", built.weekly in 38..40)
        assertTrue("months ${built.monthly}", built.monthly in 11..13)
        assertEquals(14, built.nights)
        assertEquals(14, built.workouts)
        val history = JSONObject(built.json).getJSONObject("history")
        assertEquals(listOf("d", "n") + LearnHistory.COLUMNS.map { it.key }, (0 until 14).map { history.getJSONObject("daily").getJSONArray("cols").optString(it) })
        assertTrue(history.has("weekly") && history.has("monthly"))
    }

    @Test fun `a short history is sent whole and without weeks or months`() {
        val built = LearnPayload.build(learnInput(days = 20))
        assertEquals(20, built.daily)
        assertEquals(0, built.weekly)
        assertEquals(0, built.monthly)
        assertFalse(built.trimmed)
        assertFalse(JSONObject(built.json).getJSONObject("history").let { it.has("weekly") || it.has("monthly") })
    }

    @Test fun `every key in the payload is on the whitelist, with and without a profile`() {
        for (profile in listOf(null, BodyProfile.encode(sampleProfile()))) {
            val unknown = keysOf(json(learnInput(profile = profile, incidents = listOf(incident(1, "ai.rest", changes = listOf(SelfLearning.Change(Knob.Rest, 0.8f))))))) - LearnPayload.KEYS
            assertEquals(emptySet<String>(), unknown)
        }
    }

    @Test fun `nothing personal or raw is sent, whatever a sport is called`() {
        val sport = "Run with bob@example.com at 51.5074,-0.1278 http://x.y/z"
        val text = LearnPayload.build(learnInput(sport = sport)).json
        for (bad in listOf("@", "://", "51.5074", "bob@", ".com")) assertFalse(bad, text.contains(bad))
        val keys = keysOf(JSONObject(text)).map { it.lowercase() }
        for (bad in listOf("name", "email", "location", "lat", "lon", "latitude", "longitude", "address", "device", "mac", "heartrate", "heart_rate", "samples", "route", "key", "token")) {
            assertFalse(bad, bad in keys)
        }
    }

    @Test fun `the oldest detail goes first when it does not fit`() {
        // A profile of this size leaves room for every day but not for all the weeks, and for no month.
        val tight = LearnPayload.build(learnInput(profile = bigProfile(9_000)))
        assertTrue(tight.trimmed)
        assertTrue("${tight.bytes} bytes", tight.bytes <= LearnPayload.MAX_BYTES)
        assertEquals(0, tight.monthly)
        assertTrue("weeks ${tight.weekly}", tight.weekly in 1..38)
        assertEquals(90, tight.daily)
        assertEquals(14, tight.nights)
        assertEquals(14, tight.workouts)
        // A little more and the days begin to go, but only after every week has.
        val tighter = LearnPayload.build(learnInput(profile = bigProfile(11_000)))
        assertEquals(0, tighter.weekly)
        assertTrue(tighter.daily < 90 && tighter.daily >= LearnPayload.MIN_DAILY)
        assertTrue(tighter.bytes <= LearnPayload.MAX_BYTES)
    }

    @Test fun `a profile so large nothing fits keeps only 14 days, the profile, the tuning and the insights`() {
        val built = LearnPayload.build(learnInput(profile = bigProfile(30_000)))
        assertTrue(built.trimmed)
        assertEquals(0, built.monthly)
        assertEquals(0, built.weekly)
        assertEquals(LearnPayload.MIN_DAILY, built.daily)
        assertEquals(0, built.workouts)
        assertEquals(0, built.nights)
        val j = JSONObject(built.json)
        for (k in listOf("profile", "tuning", "insights", "incidents")) assertTrue(k, j.has(k))
    }

    @Test fun `only the last days' incidents are sent, with what they changed`() {
        val recent = incident(2, "ai.rest", status = SelfLearning.Status.Applied, changes = listOf(SelfLearning.Change(Knob.Rest, 0.8f)), note = "free text the AI wrote")
        val old = incident(3, "energy.stuck_empty", last = LEARN_TODAY.minusDays(10))
        val j = json(learnInput(incidents = listOf(old, recent)))
        val list = j.getJSONArray("incidents")
        assertEquals(1, list.length())
        assertEquals("ai.rest", list.getJSONObject(0).getString("k"))
        assertEquals(0.8, list.getJSONObject(0).getJSONObject("ch").getDouble("rest"), 0.001)
        // The evidence the AI wrote is not echoed back.
        assertFalse(j.toString().contains("free text"))
    }

    @Test fun `the tuning in force is sent as every knob's value`() {
        val tuned = Knob.Rest.write(ChargeTuning.Default, 0.8f)
        val t = json(learnInput(tuning = tuned)).getJSONObject("tuning")
        assertEquals(KNOB_KEYS.toSet(), t.keys().asSequence().toSet())
        assertEquals(0.8, t.getDouble("rest"), 0.001)
        assertEquals(1.0, t.getDouble("alert"), 0.001)
    }

    @Test fun `the profile is sent as stored`() {
        val stored = BodyProfile.encode(sampleProfile())
        assertEquals(JSONObject(stored).toString(), json(learnInput(profile = stored)).getJSONObject("profile").toString())
        assertFalse(json(learnInput(profile = null)).has("profile"))
        assertFalse(json(learnInput(profile = "not json")).has("profile"))
    }

    @Test fun `figures are rounded so no float noise is sent`() {
        val text = LearnPayload.build(learnInput(days = 40)).json
        assertFalse(Regex("\\d\\.\\d{4,}").containsMatchIn(text))
    }
}
