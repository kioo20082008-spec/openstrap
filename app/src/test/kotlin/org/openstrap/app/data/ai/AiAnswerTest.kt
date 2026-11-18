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

    // ---- the detailed analysis (deep)

    private val deepPart = "\"deep\":" + """[{"title":"النوم","text":"نمت هذا الأسبوع 6 ساعات و50 دقيقة، أقل من الأسبوع الماضي.","evidence":["6 ساعات و50 دقيقة","7 ساعات و20 دقيقة قبلها"]},{"title":"التعافي","text":"التعافي ثابت تقريباً.","evidence":["62 مقابل 61"]}]"""
    private val withDeep = """{$deepPart,"summary":"نومك أقل من حاجتك","patterns":["النوم 6 ساعات"],"doThis":["نم مبكراً"],"adjust":["خفف التمرين"],"outlook":"التعافي سيتحسن"}"""

    @Test fun `a deep part is read with its sections, texts and evidence beside the five parts`() {
        val a = AiAnswer.parse(withDeep)!!
        assertEquals(2, a.deep.size)
        assertEquals("النوم", a.deep[0].title)
        assertEquals("نمت هذا الأسبوع 6 ساعات و50 دقيقة، أقل من الأسبوع الماضي.", a.deep[0].text)
        assertEquals(listOf("6 ساعات و50 دقيقة", "7 ساعات و20 دقيقة قبلها"), a.deep[0].evidence)
        assertEquals("نومك أقل من حاجتك", a.summary)
        assertEquals(listOf("نم مبكراً"), a.doThis)
        assertFalse(a.partial)
        assertTrue(a.structured)
    }

    @Test fun `an answer with no deep is the same as before, not partial`() {
        val a = AiAnswer.parse(whole)!!
        assertTrue(a.deep.isEmpty())
        assertFalse(a.partial)
    }

    @Test fun `a malformed deep is dropped and the rest still shows`() {
        for (bad in listOf("\"deep\":\"نص\"", "\"deep\":42", "\"deep\":null", "\"deep\":[1,2,null]", "\"deep\":[{}]", "\"deep\":[{\"title\":\"فقط عنوان\"}]", "\"deep\":{}")) {
            val a = AiAnswer.parse("""{$bad,"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
            assertTrue(bad, a.deep.isEmpty())
            assertEquals(bad, "س", a.summary)
            assertFalse(bad, a.partial)
            assertNull(bad, a.plain)
        }
    }

    @Test fun `deep sections may be bare strings, an object of title to text, or use other names`() {
        val strings = AiAnswer.parse("""{"deep":["فقرة أولى","فقرة ثانية"],"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals(listOf("فقرة أولى", "فقرة ثانية"), strings.deep.map { it.text })
        assertEquals(listOf("", ""), strings.deep.map { it.title })
        val mapped = AiAnswer.parse("""{"deep":{"النوم":"نص النوم","التعافي":{"text":"نص التعافي","evidence":["62"]}},"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals(listOf("النوم", "التعافي"), mapped.deep.map { it.title })
        assertEquals(listOf("62"), mapped.deep[1].evidence)
        val named = AiAnswer.parse("""{"detailed":[{"name":"الحمل","analysis":"نص","numbers":"رقم أ\nرقم ب"}],"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals("الحمل", named.deep.single().title)
        assertEquals("نص", named.deep.single().text)
        assertEquals(listOf("رقم أ", "رقم ب"), named.deep.single().evidence)
    }

    @Test fun `deep digits are Western, and sections, text and evidence are capped`() {
        val many = (1..12).joinToString(",") { """{"title":"قسم $it","text":"${"ا".repeat(4_000)}","evidence":[${(1..12).joinToString(",") { e -> "\"${"ب".repeat(300)}$e\"" }}]}""" }
        val a = AiAnswer.parse("""{"deep":[$many,{"title":"٢٠٢٦","text":"نمت ٦ ساعات"}],"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals(AiAnswer.MAX_SECTIONS, a.deep.size)
        assertTrue(a.deep.all { it.text.length == AiAnswer.MAX_SECTION_CHARS })
        assertTrue(a.deep.all { it.evidence.size == AiAnswer.MAX_EVIDENCE && it.evidence.all { e -> e.length == AiAnswer.MAX_EVIDENCE_CHARS } })
        val digits = AiAnswer.parse("""{"deep":[{"title":"٢٠٢٦","text":"نمت ٦ ساعات"}],"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م"}""")!!
        assertEquals("2026", digits.deep.single().title)
        assertEquals("نمت 6 ساعات", digits.deep.single().text)
    }

    @Test fun `an answer cut off inside the deep part is closed, keeps what was written, and is partial`() {
        val a = AiAnswer.parse("""{"deep":[{"title":"النوم","text":"نص كامل","evidence":["رقم"]},{"title":"التعافي","text":"نص مقطو""")!!
        assertEquals(2, a.deep.size)
        assertEquals("نص كامل", a.deep[0].text)
        assertTrue(a.deep[1].text.startsWith("نص مقطو"))
        assertTrue(a.partial)
        assertNull(a.plain)
        assertTrue(a.summary.isEmpty())
    }

    @Test fun `a deep part alone is an answer, JSON with neither is plain text`() {
        val a = AiAnswer.parse("""{"deep":[{"title":"ن","text":"نص"}]}""")!!
        assertNull(a.plain)
        assertEquals(1, a.deep.size)
        assertTrue(a.partial)
        assertNotNull(AiAnswer.parse("""{"deep":[{}],"hello":"world"}""")!!.plain)
    }

    @Test fun `the deep part survives the cache, and an older cached answer without it still loads`() {
        val a = AiAnswer.parse(withDeep)!!
        assertEquals(a, AiAnswer.fromJson(a.toJson()))
        val old = """{"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م","partial":false}"""
        val read = AiAnswer.fromJson(old)!!
        assertEquals("س", read.summary)
        assertTrue(read.deep.isEmpty())
        assertEquals(AiAnswer("س", listOf("ن"), listOf("ع"), listOf("ت"), "م"), read)
        // A cached deep that is damaged loses only itself.
        val damaged = """{"summary":"س","patterns":["ن"],"doThis":["ع"],"adjust":["ت"],"outlook":"م","deep":"not a list"}"""
        assertTrue(AiAnswer.fromJson(damaged)!!.deep.isEmpty())
    }
}
