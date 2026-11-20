package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI's answer to the daily request, read tolerantly: fenced, cut off, in prose, or odd in its details. */
class LearnAnswerTest {
    private val good = """
        {"learned":["تعلّمت أن نومك يقصر بعد التمرين المسائي","تعلّمت أن HRV عندك أعلى في الأحد"],
         "profile":{"sleep":[{"t":"ينام نحو سبع ساعات ونصف","c":80,"n":60}],"facts":{"need":455,"bed":"23:30"}},
         "proposals":[{"knob":"rest","to":0.85,"evidence":"طاقتك عند الاستيقاظ أقل من المتوقع 5 أيام","confidence":75}]}
    """.trimIndent()

    @Test fun `a clean answer is read whole`() {
        val a = LearnAnswer.parse(good)!!
        assertEquals(2, a.learned.size)
        assertEquals(1, a.profile!!.sections.getValue(ProfileSection.Sleep).size)
        assertEquals(455, a.profile!!.facts.needMin)
        val p = a.proposals.single()
        assertEquals("rest", p.key)
        assertEquals(0.85, p.to!!, 0.0001)
        assertEquals(75, p.confidence)
        assertTrue(p.evidence.startsWith("طاقتك"))
    }

    @Test fun `code fences around the JSON are stripped`() {
        assertNotNull(LearnAnswer.parse("```json\n$good\n```"))
        assertNotNull(LearnAnswer.parse("```\n$good\n```"))
        assertEquals(2, LearnAnswer.parse("```JSON\n$good\n```")!!.learned.size)
    }

    @Test fun `JSON after a few words of prose is found`() {
        val a = LearnAnswer.parse("Here is the update:\n$good\nHope that helps.")
        assertEquals(2, a!!.learned.size)
    }

    @Test fun `an answer cut off part way is read as far as it goes`() {
        val cut = "{\"learned\":[\"تعلّمت أن نومك يقصر بعد السهر\",\"تعلّمت أن HRV عندك أعلى يوم الأحد\"],\"profile\":{\"sleep\":[{\"t\":\"ينام سبع ساعات تقريباً\",\"c\":70}]},\"proposals\":[{\"knob\":\"cap\",\"to\":6"
        val a = LearnAnswer.parse(cut)!!
        assertEquals(2, a.learned.size)
        assertNotNull(a.profile)
    }

    @Test fun `text with no JSON, or nothing usable in it, is no answer`() {
        assertNull(LearnAnswer.parse(null))
        assertNull(LearnAnswer.parse(""))
        assertNull(LearnAnswer.parse("I cannot help with that."))
        assertNull(LearnAnswer.parse("{ this is : not json"))
        assertNull(LearnAnswer.parse("{}"))
        assertNull(LearnAnswer.parse("[1,2,3]"))
        assertNull(LearnAnswer.parse("{\"unrelated\":1,\"other\":[1]}"))
        assertNull(LearnAnswer.parse("{\"learned\":[],\"profile\":{},\"proposals\":[]}"))
    }

    @Test fun `at most three lines of what it learned, each short and plain`() {
        val many = (1..6).joinToString(",") { "\"سطر رقم $it تعلّمته اليوم عن نومك\"" }
        val a = LearnAnswer.parse("{\"learned\":[$many,\"" + "ط".repeat(500) + "\"]}")!!
        assertEquals(LearnAnswer.MAX_LINES, a.learned.size)
        assertTrue(a.learned.all { it.length <= LearnAnswer.MAX_LINE })
        val long = LearnAnswer.parse("{\"learned\":[\"" + "ط".repeat(500) + "\"]}")!!
        assertEquals(LearnAnswer.MAX_LINE, long.learned.single().length)
    }

    @Test fun `lines that are markup or too short are dropped, and text is flattened`() {
        val a = LearnAnswer.parse("{\"learned\":[\"ok\",\"<script>alert(1)</script> تعلّمت أن نومك يتحسن\",\"سطر\\nواحد  طويل بما يكفي\"]}")!!
        assertEquals(2, a.learned.size)
        assertFalse(a.learned.any { '<' in it || '>' in it || '\n' in it })
    }

    @Test fun `the lines may be one string, and other names for the keys are accepted`() {
        val a = LearnAnswer.parse("{\"today\":\"تعلّمت أن نومك ثابت\\nوتعلّمت أن نبضك ينخفض\",\"body_profile\":{\"rhr\":[\"نبض راحته يرتفع بعد السهر\"]},\"changes\":{\"rest\":0.9}}")!!
        assertEquals(2, a.learned.size)
        assertEquals(1, a.profile!!.sections.getValue(ProfileSection.RestingHr).size)
        assertEquals("rest", a.proposals.single().key)
        assertEquals(0.9, a.proposals.single().to!!, 0.0001)
    }

    @Test fun `the profile may arrive as a string of JSON`() {
        val a = LearnAnswer.parse("{\"profile\":\"{\\\"sleep\\\":[{\\\"t\\\":\\\"ينام سبع ساعات تقريباً\\\",\\\"c\\\":70}]}\"}")!!
        assertEquals(1, a.profile!!.sections.getValue(ProfileSection.Sleep).size)
    }

    @Test fun `numbers written as text, in Arabic digits or as a fraction, are read`() {
        val a = LearnAnswer.parse("{\"proposals\":[{\"knob\":\"Cap\",\"to\":\"٤٥\",\"evidence\":\"دليل كافٍ هنا\",\"confidence\":\"0.9\"},{\"knob\":\"rhr\",\"value\":\"2,5\",\"confidence\":\"80%\"}]}")!!
        assertEquals(listOf("cap", "rhr"), a.proposals.map { it.key })
        assertEquals(45.0, a.proposals[0].to!!, 0.0001)
        assertEquals(90, a.proposals[0].confidence)
        assertEquals(2.5, a.proposals[1].to!!, 0.0001)
        assertEquals(80, a.proposals[1].confidence)
    }

    @Test fun `a proposal with a value that is not a number keeps its key and no value`() {
        val p = LearnAnswer.parse("{\"proposals\":[{\"knob\":\"rest\",\"to\":\"a lot\",\"evidence\":\"x\"}]}")!!.proposals.single()
        assertEquals("rest", p.key)
        assertNull(p.to)
        assertNull(p.confidence)
    }

    @Test fun `unknown keys and kinds of value are ignored`() {
        val a = LearnAnswer.parse("{\"learned\":[\"تعلّمت أن نومك ثابت\",5,null,{\"x\":1}],\"proposals\":[1,\"x\",null,{\"knob\":\"rest\",\"to\":0.9}],\"extra\":{\"deep\":[1]}}")!!
        assertEquals(1, a.learned.size)
        assertEquals(1, a.proposals.size)
    }

    @Test fun `at most a dozen proposals are read and the evidence is kept short`() {
        val many = (1..20).joinToString(",") { "{\"knob\":\"rest\",\"to\":0.9,\"evidence\":\"" + "د".repeat(500) + "\"}" }
        val a = LearnAnswer.parse("{\"proposals\":[$many]}")!!
        assertEquals(LearnAnswer.MAX_READ, a.proposals.size)
        assertTrue(a.proposals.all { it.evidence.length <= LearnAnswer.MAX_EVIDENCE })
    }
}
