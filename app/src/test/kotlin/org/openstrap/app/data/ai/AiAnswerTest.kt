package org.openstrap.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the model's reply: tolerant of fences, cut-off answers and fields it didn't ask for, and never throwing. */
class AiAnswerTest {
    private val whole = """{"summary":"نومك أقل من حاجتك","patterns":["النوم 6 ساعات","الإجهاد مرتفع"],"doThis":["نم مبكراً"],"adjust":["خفف التمرين"],"outlook":"التعافي سيتحسن"}"""

    @Test fun `a whole answer is read in its five parts`() {
        val a = AiAnswer.parse(whole)!!
        assertEquals("نومك أقل من حاجتك", a.summary)
        assertEquals(listOf("النوم 6 ساعات", "الإجهاد مرتفع"), a.patterns)
        assertEquals(listOf("نم مبكراً"), a.doThis)
        assertEquals(listOf("خفف التمرين"), a.adjust)
        assertEquals("التعافي سيتحسن", a.outlook)
        assertNull(a.plain)
        assertFalse(a.partial)
        assertTrue(a.structured)
    }

    @Test fun `code fences, with or without a language, are stripped`() {
        assertEquals(AiAnswer.parse(whole), AiAnswer.parse("```json\n$whole\n```"))
        assertEquals(AiAnswer.parse(whole), AiAnswer.parse("```\n$whole\n```"))
        assertEquals(AiAnswer.parse(whole), AiAnswer.parse("هذا هو الجواب:\n```json\n$whole\n```\nأتمنى أن يفيدك"))
    }

    @Test fun `text around bare JSON is ignored`() {
        assertEquals(AiAnswer.parse(whole), AiAnswer.parse("تفضل $whole شكراً"))
    }

    @Test fun `an unclosed fence loses only its opening line`() {
        assertEquals(AiAnswer.parse(whole), AiAnswer.parse("```json\n$whole"))
    }

    @Test fun `fields it doesn't know are ignored`() {
        val a = AiAnswer.parse("""{"summary":"س","confidence":0.9,"extra":{"a":[1,2]},"patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals("س", a.summary)
        assertFalse(a.partial)
    }

    @Test fun `an answer cut off in a string is closed and marked partial`() {
        val a = AiAnswer.parse("""{"summary":"ملخص قصير","patterns":["نمط أول","نمط ثا""")!!
        assertEquals("ملخص قصير", a.summary)
        assertEquals("نمط أول", a.patterns.first())
        assertTrue(a.patterns.size in 1..2)
        assertTrue(a.doThis.isEmpty())
        assertTrue(a.partial)
        assertNull(a.plain)
    }

    @Test fun `an answer cut off inside a key or after a colon or comma is still read`() {
        for (cut in listOf("""{"summary":"س","doThi""", """{"summary":"س","doThis":""", """{"summary":"س",""", """{"summary":"س","patterns":["أ",""")) {
            val a = AiAnswer.parse(cut)
            assertNotNull(cut, a)
            assertEquals(cut, "س", a!!.summary)
            assertTrue(cut, a.partial)
        }
    }

    @Test fun `an answer missing some parts is partial`() {
        val a = AiAnswer.parse("""{"summary":"س","patterns":["ن"]}""")!!
        assertTrue(a.partial)
        assertTrue(a.doThis.isEmpty())
        assertNull(a.plain)
    }

    @Test fun `a reply with no JSON is shown as plain text`() {
        val a = AiAnswer.parse("نومك قليل هذا الأسبوع، حاول أن تنام مبكراً.")!!
        assertEquals("نومك قليل هذا الأسبوع، حاول أن تنام مبكراً.", a.plain)
        assertFalse(a.structured)
        assertTrue(a.partial)
    }

    @Test fun `JSON with none of the five parts is shown as plain text`() {
        val a = AiAnswer.parse("""{"hello":"world"}""")!!
        assertNotNull(a.plain)
        assertFalse(a.structured)
    }

    @Test fun `braces that never form an object fall back to plain text`() {
        assertNotNull(AiAnswer.parse("{{{ ]]] not json")!!.plain)
    }

    @Test fun `blank or missing replies are nothing`() {
        assertNull(AiAnswer.parse(null))
        assertNull(AiAnswer.parse(""))
        assertNull(AiAnswer.parse("  \n "))
        assertNull(AiAnswer.parse("```json\n```"))
    }

    @Test fun `Eastern Arabic digits become Western ones`() {
        val a = AiAnswer.parse("""{"summary":"نمت ٦ ساعات و۳۰ دقيقة","patterns":["١٠٠٠٠ خطوة"],"doThis":["أ"],"adjust":["ب"],"outlook":"ج"}""")!!
        assertEquals("نمت 6 ساعات و30 دقيقة", a.summary)
        assertEquals("10000 خطوة", a.patterns.single())
    }

    @Test fun `bullets and numbering are taken off the items`() {
        val a = AiAnswer.parse("""{"summary":"س","patterns":["- أول","* ثان","1. ثالث","٢) رابع","• خامس"],"doThis":["أ"],"adjust":["ب"],"outlook":"ج"}""")!!
        assertEquals(listOf("أول", "ثان", "ثالث", "رابع", "خامس"), a.patterns)
    }

    @Test fun `lists are capped and objects read by their text`() {
        val many = (1..20).joinToString(",") { "\"نقطة $it\"" }
        val a = AiAnswer.parse("""{"summary":"س","patterns":[$many],"doThis":[{"title":"عنوان","detail":"تفصيل"}],"adjust":"سطر أول\nسطر ثان","outlook":"ج"}""")!!
        assertEquals(AiAnswer.MAX_ITEMS, a.patterns.size)
        assertEquals("عنوان. تفصيل", a.doThis.single())
        assertEquals(listOf("سطر أول", "سطر ثان"), a.adjust)
    }

    @Test fun `a long item is cut`() {
        val a = AiAnswer.parse("""{"summary":"س","patterns":["${"ا".repeat(2_000)}"],"doThis":["أ"],"adjust":["ب"],"outlook":"ج"}""")!!
        assertEquals(AiAnswer.MAX_ITEM_CHARS, a.patterns.single().length)
    }

    @Test fun `a few other names for the parts are understood`() {
        val a = AiAnswer.parse("""{"overview":"س","observations":["ن"],"do_this":["ع"],"adjustments":["ت"],"forecast":"م"}""")!!
        assertEquals("س", a.summary)
        assertEquals(listOf("ع"), a.doThis)
        assertEquals("م", a.outlook)
        assertFalse(a.partial)
    }

    @Test fun `an answer survives the cache`() {
        val a = AiAnswer.parse(whole)!!
        assertEquals(a, AiAnswer.fromJson(a.toJson()))
        val plain = AiAnswer.parse("نص بلا هيكل")!!
        assertEquals(plain, AiAnswer.fromJson(plain.toJson()))
        val partial = AiAnswer.parse("""{"summary":"س","patterns":["ن""")!!
        assertEquals(partial, AiAnswer.fromJson(partial.toJson()))
    }

    @Test fun `a cached answer that is not JSON reads as nothing`() {
        assertNull(AiAnswer.fromJson("not json"))
    }

    @Test fun `digits are converted alone`() {
        assertEquals("0123456789 abc", AiAnswer.digits("٠١٢٣٤٥٦٧٨٩ abc"))
        assertEquals("0123456789", AiAnswer.digits("۰۱۲۳۴۵۶۷۸۹"))
    }
}
