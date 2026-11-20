package org.openstrap.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.testing.SourceTree
import java.io.File

/** The AI learning's strings in both languages: the same names, the same placeholders, Arabic placeholders isolated, apostrophes escaped. */
class AiLearnStringsTest {
    private val placeholder = Regex("%\\d\\$[sd]")
    private val entry = Regex("<string name=\"([^\"]+)\">(.*)</string>")

    private fun read(folder: String): Map<String, String> =
        File(SourceTree.root, "res/$folder/strings_ailearn.xml").readLines(Charsets.UTF_8).mapNotNull { entry.find(it) }.associate { it.groupValues[1] to it.groupValues[2] }

    private val en by lazy { read("values") }
    private val ar by lazy { read("values-ar") }

    @Test fun `both languages have the same strings, each with the same placeholders`() {
        assertTrue(en.size > 50)
        assertEquals(en.keys, ar.keys)
        for ((name, text) in en) {
            assertEquals(name, placeholder.findAll(text).map { it.value }.toList().sorted(), placeholder.findAll(ar.getValue(name)).map { it.value }.toList().sorted())
        }
    }

    @Test fun `every Arabic placeholder sits between direction isolates`() {
        for ((name, text) in ar) {
            for (m in placeholder.findAll(text)) {
                assertEquals("$name before ${m.value}", '⁨', text.getOrNull(m.range.first - 1))
                assertEquals("$name after ${m.value}", '⁩', text.getOrNull(m.range.last + 1))
            }
        }
    }

    @Test fun `an apostrophe or a quote in a value is escaped, as aapt needs`() {
        for ((name, text) in en + ar) {
            assertFalse("$name has a bare apostrophe", Regex("(?<!\\\\)'").containsMatchIn(text))
            assertFalse("$name has a bare quote", Regex("(?<!\\\\)\"").containsMatchIn(text))
        }
    }

    @Test fun `the Arabic text has no copy the app bans, and none of it is empty`() {
        for ((name, text) in ar + en) {
            assertTrue(name, text.isNotBlank())
            assertFalse("$name uses banned punctuation", text.any { it in "·;—–" })
        }
    }

    @Test fun `the consent strings name the size cap the payload really has`() {
        assertEquals(24_000, org.openstrap.app.data.ailearn.LearnPayload.MAX_BYTES)
        assertEquals(4_096, org.openstrap.app.data.ailearn.BodyProfile.MAX_BYTES)
        assertTrue(en.getValue("ailearn_consent_size").contains("%1\$s KB") && en.getValue("ailearn_consent_size").contains("%2\$s KB"))
    }
}
