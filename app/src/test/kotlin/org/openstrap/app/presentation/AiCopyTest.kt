package org.openstrap.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ai.AiKeyState
import org.openstrap.app.testing.assertCleanCopy
import java.time.ZoneOffset
import java.util.Locale

/** The smart assistant's words: every failure has its own sentence, and the settings page says where the data goes. */
class AiCopyTest {
    @Test fun `every failure has its own plain sentence`() {
        val sentences = AiFailure.entries.map { AiCopy.failure(it, code = 418) }
        assertTrue(sentences.all { it.isNotBlank() })
        // Unknown and a refusal without a status share their words; everything else is told apart.
        assertEquals(sentences.size, sentences.toSet().size)
        assertCleanCopy(sentences)
    }

    @Test fun `the failures name what the person can do`() {
        assertTrue(AiCopy.failure(AiFailure.BadKey).contains("401"))
        assertTrue(AiCopy.failure(AiFailure.NoBalance).contains("402"))
        assertTrue(AiCopy.failure(AiFailure.RateLimit).contains("429"))
        assertTrue(AiCopy.failure(AiFailure.Timeout).contains("60"))
        assertTrue(AiCopy.failure(AiFailure.Rejected, 422).contains("422"))
        assertEquals(AiCopy.failure(AiFailure.Unknown), AiCopy.failure(AiFailure.Rejected, null))
    }

    @Test fun `the basis counts days, nights and workouts, one or many`() {
        assertEquals("From 30 days, 14 nights and 9 workouts", AiCopy.basis(30, 14, 9))
        assertEquals("From 1 day, 1 night and 1 workout", AiCopy.basis(1, 1, 1))
        assertCleanCopy(AiCopy.basis(30, 14, 0))
    }

    @Test fun `the time of an analysis is said in the day's words`() {
        val zone = ZoneOffset.UTC
        val now = java.time.LocalDate.of(2026, 11, 17).atTime(20, 0).toEpochSecond(zone)
        val morning = java.time.LocalDate.of(2026, 11, 17).atTime(9, 5).toEpochSecond(zone)
        val yesterday = java.time.LocalDate.of(2026, 11, 16).atTime(9, 5).toEpochSecond(zone)
        val today = AiCopy.updated(morning, now, zone, Locale.US)
        assertTrue(today, today.startsWith("Updated ") && today.contains("9:05") || today.contains("09:05"))
        assertNotEquals(today, AiCopy.updated(yesterday, now, zone, Locale.US))
        assertTrue(AiCopy.updatedEarlier(yesterday, now, zone, Locale.US).startsWith("From an earlier day"))
    }

    @Test fun `the You row says what is missing, on or off`() {
        assertEquals("Add your DeepSeek key", AiCopy.rowSummary(AiKeyState(enabled = false, hasKey = false, masked = "")))
        assertEquals("Add your DeepSeek key", AiCopy.rowSummary(AiKeyState(enabled = true, hasKey = false, masked = "")))
        assertEquals("On", AiCopy.rowSummary(AiKeyState(enabled = true, hasKey = true, masked = "sk-••••••••abcd")))
        assertEquals("Off", AiCopy.rowSummary(AiKeyState(enabled = false, hasKey = true, masked = "sk-••••••••abcd")))
    }

    @Test fun `the key note names the prefix and the saved key shows masked`() {
        assertTrue(AiCopy.keyInvalid().contains(AiCopy.KEY_PREFIX))
        assertTrue(AiCopy.keySaved("sk-••••••••abcd").endsWith("sk-••••••••abcd"))
    }

    @Test fun `the consent text names where the data goes`() {
        assertTrue(AiCopy.consentWhere().contains("api.deepseek.com"))
    }
}
