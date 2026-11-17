package org.openstrap.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The key as typed, checked, and as shown. */
class AiKeyTest {
    private val key = "sk-1234567890abcdef1234567890abcdef"

    @Test fun `a key is shown by its first three and last four characters`() {
        assertEquals("sk-••••••••cdef", AiKey.mask(key))
    }

    @Test fun `a mask never contains the middle of the key`() {
        val masked = AiKey.mask(key)
        assertFalse(masked.contains("1234567890abcdef1234"))
        assertEquals(15, masked.length)
    }

    @Test fun `a key too short to hide anything shows only dots`() {
        assertEquals("••••••••", AiKey.mask("sk-abc"))
        assertEquals("••••••••", AiKey.mask("sk-1234567"))
    }

    @Test fun `nothing masks to nothing`() {
        assertEquals("", AiKey.mask(null))
        assertEquals("", AiKey.mask(""))
    }

    @Test fun `a pasted key is cleaned of spaces, quotes and Bearer`() {
        assertEquals(key, AiKey.clean("  $key \n"))
        assertEquals(key, AiKey.clean("\"$key\""))
        assertEquals(key, AiKey.clean("'$key'"))
        assertEquals(key, AiKey.clean("Bearer $key"))
        assertEquals(key, AiKey.clean("bearer   $key"))
    }

    @Test fun `what can't be a key is refused`() {
        assertNull(AiKey.clean(null))
        assertNull(AiKey.clean(""))
        assertNull(AiKey.clean("sk-short"))
        assertNull(AiKey.clean("sk-1234567890 abcdef1234567890"))
        assertNull(AiKey.clean("sk-1234567890abcdef!@#\$%^&*()"))
        assertNull(AiKey.clean("مفتاح-" + "a".repeat(30)))
        assertNull(AiKey.clean("sk-" + "a".repeat(250)))
    }

    @Test fun `only a key that starts with sk- looks like one`() {
        assertTrue(AiKey.looksValid(key))
        assertFalse(AiKey.looksValid("abcdef1234567890abcdef"))
        assertFalse(AiKey.looksValid(" $key"))
        assertFalse(AiKey.looksValid(null))
    }
}
