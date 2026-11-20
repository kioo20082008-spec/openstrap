package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The body profile: read tolerantly, bounded, merged day to day and held to 4 KB. */
class BodyProfileTest {
    private val today = LEARN_TODAY

    private fun profileJson(vararg sections: Pair<String, List<JSONObject>>, facts: JSONObject? = null): JSONObject =
        JSONObject().apply {
            sections.forEach { (k, v) -> put(k, JSONArray().apply { v.forEach { put(it) } }) }
            facts?.let { put("facts", it) }
        }

    private fun st(text: String, c: Int, n: Int = 10) = JSONObject().put("t", text).put("c", c).put("n", n)

    @Test fun `a profile survives being written and read`() {
        val p = sampleProfile()
        assertEquals(p, BodyProfile.decode(BodyProfile.encode(p)))
    }

    @Test fun `text that is not a profile reads as none`() {
        assertNull(BodyProfile.decode(null))
        assertNull(BodyProfile.decode("  "))
        assertNull(BodyProfile.decode("not json at all"))
        assertNull(BodyProfile.decode("[1,2]"))
    }

    @Test fun `unknown sections and keys are ignored`() {
        val o = profileJson("sleep" to listOf(st("ينام سبع ساعات تقريباً", 70)), "secrets" to listOf(st("ignored", 99)))
        o.put("token", "abc")
        val p = BodyProfile.fromJson(o)!!
        assertEquals(setOf(ProfileSection.Sleep), p.sections.keys)
        assertEquals(1, p.statements)
    }

    @Test fun `text is cut down to plain letters, digits and a little punctuation`() {
        assertEquals("see now", BodyProfile.cleanText("see https://evil.example/x?y=1 now"))
        assertEquals("a b c", BodyProfile.cleanText("a <b> \"c\""))
        assertEquals("room 5 at 12:30", BodyProfile.cleanText("room ٥ at ١٢:٣٠"))
        assertEquals("", BodyProfile.cleanText(null))
        assertEquals(120, BodyProfile.cleanText("x".repeat(500)).length)
        assertFalse(BodyProfile.cleanText("mail me@x.com {json} [list] #tag").any { it in "@{}[]#" })
    }

    @Test fun `Arabic letters keep their marks`() {
        val text = "تعلّمت أنّ نومك ثابتٌ تقريباً"
        assertEquals(text, BodyProfile.cleanText(text))
        assertEquals("نومه أقل من 6 ساعات (تقريباً)", BodyProfile.cleanText("نومه أقل من ٦ ساعات (تقريباً)"))
    }

    @Test fun `a statement is bounded, a short one is dropped, confidence goes to 0 to 100, at most three a section`() {
        val o = profileJson(
            "sleep" to listOf(st("short", 90), st("ينام سبع ساعات تقريباً", 250, 99_999), st("حاجته للنوم أكثر بعد الجري", 0.7.toInt()), st("يستيقظ مرتين كل ليلة تقريباً", 40), st("يتأخر نومه في عطلة الأسبوع", 30)),
        )
        val p = BodyProfile.fromJson(o)!!
        val list = p.sections.getValue(ProfileSection.Sleep)
        assertEquals(3, list.size)
        assertTrue(list.all { it.confidence in 0..100 && it.n in 0..9_999 && it.text.length >= 6 })
        assertEquals(100, list.first().confidence)
        assertEquals(list.map { it.confidence }.sortedDescending(), list.map { it.confidence })
    }

    @Test fun `a confidence given as a fraction reads as a percent`() {
        val o = profileJson("hrv" to listOf(JSONObject().put("t", "ينخفض بعد التمرين الشاق").put("c", 0.8)))
        assertEquals(80, BodyProfile.fromJson(o)!!.sections.getValue(ProfileSection.Hrv).single().confidence)
    }

    @Test fun `the facts are checked against their bounds`() {
        val f = JSONObject().put("need", 200).put("bed", "25:99").put("wake", "06:45").put("rec_days", 40).put("hrv_ms", 55.4).put("rhr_bpm", 400)
        val facts = BodyProfile.fromJson(profileJson(facts = f))!!.facts
        assertNull(facts.needMin)
        assertNull(facts.bed)
        assertEquals("06:45", facts.wake)
        assertNull(facts.recoveryDays)
        assertEquals(55, facts.hrvMs)
        assertNull(facts.rhrBpm)
        val good = BodyProfile.fromJson(profileJson(facts = JSONObject().put("need", 452).put("rec_days", 1.46)))!!.facts
        assertEquals(452, good.needMin)
        assertEquals(1.5f, good.recoveryDays)
    }

    @Test fun `a section the answer names replaces the old one, an empty one clears it, one it leaves out stays`() {
        val old = sampleProfile()
        val incoming = BodyProfile.fromJson(
            profileJson(
                "sleep" to listOf(st("ينام الآن ثماني ساعات", 75)),
                "hrv" to emptyList(),
                "rhr" to listOf(st("يرتفع نبضه بعد السهر", 60)),
            ),
        )
        val merged = BodyProfile.merge(old, incoming, today, dataFrom = null)
        assertEquals(listOf("ينام الآن ثماني ساعات"), merged.sections.getValue(ProfileSection.Sleep).map { it.text })
        assertFalse(ProfileSection.Hrv in merged.sections)
        assertEquals(1, merged.sections.getValue(ProfileSection.RestingHr).size)
        // The facts the answer didn't mention stay.
        assertEquals(sampleProfile().facts, merged.facts)
    }

    @Test fun `a fact the answer gives replaces the old one and the rest stay`() {
        val incoming = BodyProfile.fromJson(profileJson(facts = JSONObject().put("need", 480).put("rhr_bpm", 57)))
        val merged = BodyProfile.merge(sampleProfile(), incoming, today, null)
        assertEquals(480, merged.facts.needMin)
        assertEquals(57, merged.facts.rhrBpm)
        assertEquals("23:30", merged.facts.bed)
        assertEquals(55, merged.facts.hrvMs)
    }

    @Test fun `the span is the app's own, from the earliest data seen to today`() {
        val first = BodyProfile.merge(null, BodyProfile.fromJson(profileJson("sleep" to listOf(st("ينام سبع ساعات تقريباً", 70)))), today, dataFrom = today.minusDays(300))
        assertEquals(today.minusDays(300), first.from)
        assertEquals(today, first.to)
        assertEquals(today, first.updated)
        val later = BodyProfile.merge(first, null, today.plusDays(1), dataFrom = today.minusDays(100))
        assertEquals(today.minusDays(300), later.from)
        assertEquals(today.plusDays(1), later.to)
        // An answer with no profile leaves the day it was last rewritten.
        assertEquals(today, later.updated)
        assertEquals(first.sections, later.sections)
    }

    @Test fun `a statement's own first day only stays when it lies inside the span`() {
        val o = profileJson(
            "sleep" to listOf(
                JSONObject().put("t", "ينام سبع ساعات تقريباً").put("c", 70).put("f", today.minusDays(10).toString()),
                JSONObject().put("t", "ينام أقل في العطلة").put("c", 60).put("f", today.minusDays(900).toString()),
                JSONObject().put("t", "يتقطع نومه بعد القهوة").put("c", 50).put("f", "garbage"),
            ),
        )
        val merged = BodyProfile.merge(null, BodyProfile.fromJson(o), today, dataFrom = today.minusDays(100))
        val byText = merged.sections.getValue(ProfileSection.Sleep).associate { it.text to it.from }
        assertEquals(today.minusDays(10), byText["ينام سبع ساعات تقريباً"])
        assertNull(byText["ينام أقل في العطلة"])
        assertNull(byText["يتقطع نومه بعد القهوة"])
    }

    @Test fun `the profile is held to 4 KB by dropping the least certain statements first`() {
        val long = "ينام الشخص عادة سبع ساعات ونصف ويتقطع نومه بعد السهر والقهوة والتمرين المتأخر وعودته متأخراً "
        val sections = ProfileSection.entries.associateWith { s ->
            (1..3).map { i -> Statement((long + s.key + i).take(BodyProfile.MAX_TEXT), 20 + i * 10 + s.ordinal, 30) }
        }
        val big = Profile(updated = today, from = today.minusDays(100), to = today, sections = sections, facts = sampleProfile().facts)
        assertTrue(BodyProfile.bytes(big) > BodyProfile.MAX_BYTES)
        val fit = BodyProfile.fit(big)
        assertTrue("${BodyProfile.bytes(fit)}", BodyProfile.bytes(fit) <= BodyProfile.MAX_BYTES)
        val kept = fit.sections.values.flatten()
        val dropped = big.sections.values.flatten() - kept.toSet()
        assertTrue(dropped.isNotEmpty())
        assertTrue("the surest were kept", dropped.maxOf { it.confidence } <= kept.minOf { it.confidence })
        // merge() applies it too.
        assertTrue(BodyProfile.bytes(BodyProfile.merge(null, big, today, null)) <= BodyProfile.MAX_BYTES)
    }

    @Test fun `what is stored never holds a key the profile does not define`() {
        val stored = JSONObject(BodyProfile.encode(sampleProfile()))
        fun keys(v: Any?, into: MutableSet<String> = linkedSetOf()): Set<String> {
            when (v) {
                is JSONObject -> v.keys().forEach { k -> into += k; keys(v.get(k), into) }
                is JSONArray -> for (i in 0 until v.length()) keys(v.get(i), into)
            }
            return into
        }
        assertEquals(emptySet<String>(), keys(stored) - BodyProfile.KEYS)
        assertNotNull(stored.optJSONObject("facts"))
    }

    @Test fun `a date that does not parse is left out`() {
        val o = profileJson("sleep" to listOf(st("ينام سبع ساعات تقريباً", 70))).put("u", "yesterday").put("from", "2026-02-30")
        val p = BodyProfile.fromJson(o)!!
        assertNull(p.updated)
        assertNull(p.from)
        assertEquals(LocalDate.of(2026, 11, 17), BodyProfile.fromJson(JSONObject().put("u", "2026-11-17"))!!.updated)
    }
}
