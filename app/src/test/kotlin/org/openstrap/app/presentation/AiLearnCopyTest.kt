package org.openstrap.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ailearn.AiLearnState
import org.openstrap.app.data.ailearn.AiLearning
import org.openstrap.app.data.ailearn.ProfileFacts
import org.openstrap.app.data.ailearn.ProfileSection
import org.openstrap.app.data.ailearn.ProposalGate
import org.openstrap.app.data.ailearn.Statement
import org.openstrap.app.data.ailearn.incident
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.testing.assertCleanCopy
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** The AI body learning's words: the profile as sentences, what the AI proposed and what the app made of it, and the consent. */
class AiLearnCopyTest {
    private val locale = Locale.US

    @Test fun `the profile's numbers read as sentences`() {
        val lines = AiLearnCopy.facts(
            ProfileFacts(needMin = 455, bed = "23:30", wake = "06:45", recoveryDays = 1.5f, hrvMs = 55, rhrBpm = 58), locale,
        )
        assertEquals(
            listOf(
                "You seem to need about 7 h 35 of sleep.",
                "Usual bedtime 23:30, usual wake-up 06:45.",
                "A hard day takes you about two days to recover from.",
                "Your usual HRV is about 55 ms.",
                "Your usual resting heart rate is about 58 bpm.",
            ),
            lines,
        )
        assertEquals(emptyList<String>(), AiLearnCopy.facts(ProfileFacts(), locale))
        assertEquals(listOf("Usual bedtime 23:30."), AiLearnCopy.facts(ProfileFacts(bed = "23:30"), locale))
        assertEquals(listOf("A hard day takes you about one day to recover from."), AiLearnCopy.facts(ProfileFacts(recoveryDays = 0.4f), locale))
        assertCleanCopy(lines)
    }

    @Test fun `a statement says how sure it is and since when`() {
        assertEquals("80% sure", AiLearnCopy.statementMeta(Statement("ينام سبع ساعات", 80, 12), locale))
        assertEquals("65% sure, since 3 Mar", AiLearnCopy.statementMeta(Statement("ينام سبع ساعات", 65, 12, LocalDate.of(2026, 3, 3)), locale))
    }

    @Test fun `every section has a title of its own`() {
        val titles = ProfileSection.entries.map { AiLearnCopy.sectionTitle(it) }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.all { it.isNotBlank() })
    }

    @Test fun `what the AI asked for reads with the evidence it gave`() {
        val applied = incident(1, "ai.rest", note = "five days of low Energy", nums = listOf(1f, 0.9f, 0f, 0f), changes = listOf(SelfLearning.Change(Knob.Rest, 0.9f)))
        assertEquals("The AI asked for a change and it passed the app’s checks. Why: five days of low Energy", SelfCheckCopy.seen(applied, locale))
        val limited = applied.copy(nums = listOf(1f, 0.75f, 1f, 1f))
        assertEquals(
            "The AI asked for a change and it passed the app’s checks. Why: five days of low Energy " +
                "The change was held to a quarter of the range for one day. The value was put back inside the safe bounds.",
            SelfCheckCopy.seen(limited, locale),
        )
        val refused = incident(2, "ai.no.rest", note = "five days of low Energy", nums = listOf(ProposalGate.Reason.Worse.ordinal.toFloat(), 0.5f, 1f))
        assertEquals(
            "The app refused a change the AI asked for in “Energy drain while resting”: it made your last days look less plausible. Why it asked: five days of low Energy",
            SelfCheckCopy.seen(refused, locale),
        )
        val need = incident(3, "ai.${ProposalGate.SLEEP_NEED_KEY}", note = "rested after long nights", nums = listOf(450f))
        assertEquals("The AI suggested a sleep need of 7 h 30. Nothing in your settings changed. Why: rested after long nights", SelfCheckCopy.seen(need, locale))
        assertEquals(450, AiLearnCopy.offeredNeed(need))
        assertNull(AiLearnCopy.offeredNeed(applied))
    }

    @Test fun `an incident with no evidence says so, and every refusal has its own reason`() {
        val blank = incident(1, "ai.rest", note = "", nums = listOf(1f, 0.9f, 0f, 0f))
        assertTrue(SelfCheckCopy.seen(blank, locale).endsWith("Why: no reason was given"))
        val reasons = ProposalGate.Reason.entries.map { AiLearnCopy.reason(it) }
        assertEquals(reasons.size, reasons.toSet().size)
        val refused = ProposalGate.Reason.entries.map { r -> SelfCheckCopy.seen(incident(2, "ai.no.cap", note = "x", nums = listOf(r.ordinal.toFloat(), 40f, 90f)), locale) }
        assertEquals(refused.size, refused.toSet().size)
        assertCleanCopy(refused)
    }

    @Test fun `every setting the AI may name has a name, and a stranger does not crash`() {
        val names = Knob.entries.map { AiLearnCopy.knobName(it.key) } + AiLearnCopy.knobName(ProposalGate.SLEEP_NEED_KEY)
        assertEquals(names.size, names.toSet().size)
        assertEquals("an unknown setting", AiLearnCopy.knobName("password"))
        assertTrue(SelfCheckCopy.seen(incident(4, "ai.no.password", nums = emptyList()), locale).contains("an unknown setting"))
        // Missing numbers are zeros, not a crash.
        SelfCheckCopy.seen(incident(5, "ai.rest", nums = emptyList()), locale)
        SelfCheckCopy.seen(incident(6, "ai.${ProposalGate.SLEEP_NEED_KEY}", nums = emptyList()), locale)
    }

    @Test fun `what the AI wrote keeps its numbers left to right`() {
        val said = AiLearnCopy.said("نومه 7.5 ساعات و 80%")
        assertTrue(said, said.contains("⁦7.5⁩") && said.contains("⁦80%⁩"))
        val seen = SelfCheckCopy.seen(incident(1, "ai.rest", note = "خمسة أيام و 3 ليالٍ", nums = listOf(1f, 0.9f, 0f, 0f)), locale)
        assertTrue(seen, seen.contains("⁦3⁩"))
    }

    @Test fun `every failure has a sentence and the ones with a status name it`() {
        val sentences = AiFailure.entries.map { AiLearnCopy.failure(it) }
        assertTrue(sentences.all { it.isNotBlank() })
        assertTrue(AiLearnCopy.failure(AiFailure.BadKey).contains("401"))
        assertTrue(AiLearnCopy.failure(AiFailure.NoBalance).contains("402"))
        assertTrue(AiLearnCopy.failure(AiFailure.RateLimit).contains("429"))
        assertNotEquals(AiLearnCopy.failure(AiFailure.Offline), AiLearnCopy.failure(AiFailure.Timeout))
        assertNotEquals(AiLearnCopy.failure(AiFailure.Malformed), AiLearnCopy.failure(AiFailure.Server))
        assertCleanCopy(sentences)
        assertNull(AiLearnCopy.failure(AiLearnState.Empty))
        assertEquals(AiLearnCopy.failure(AiFailure.Offline), AiLearnCopy.failure(AiLearnState(failure = AiFailure.Offline)))
    }

    @Test fun `the last run says when it learned and what it did`() {
        val zone = ZoneOffset.UTC
        val noon = LocalDate.of(2026, 11, 20).atTime(10, 42).toEpochSecond(zone)
        val s = AiLearnState(lastOkAt = noon, changed = 2, refused = 1, sentBytes = 17_300)
        assertEquals("Last learned today at 10:42", AiLearnCopy.lastLearned(s, noon + 3_600, zone, use24Hour = true, locale = locale))
        assertEquals("Last run: 2 applied, 1 refused. Sent 17 KB.", AiLearnCopy.runSummary(s, locale))
        assertNull(AiLearnCopy.lastLearned(AiLearnState.Empty, noon, zone, true, locale))
        assertNull(AiLearnCopy.runSummary(AiLearnState.Empty, locale))
    }

    @Test fun `the notification says what was learned and what was changed`() {
        val applied = incident(1, "ai.rest", changes = listOf(SelfLearning.Change(Knob.Rest, 0.8f)))
        val both = AiLearning.Notice(listOf("تعلّمت أن نومك يقصر بعد السهر"), listOf(applied))
        assertEquals("What I learned today", AiLearnCopy.noticeTitle())
        assertEquals("تعلّمت أن نومك يقصر بعد السهر", AiLearnCopy.noticeText(both))
        assertEquals(
            "تعلّمت أن نومك يقصر بعد السهر\nThe AI also adjusted how Energy is worked out.\nThe drain of Energy while resting is scaled to 80%.",
            AiLearnCopy.noticeBig(both, locale),
        )
        val only = AiLearning.Notice(emptyList(), listOf(applied))
        assertEquals("The AI adjusted how Energy is worked out.", AiLearnCopy.noticeText(only))
        assertEquals("The AI adjusted how Energy is worked out.\nThe drain of Energy while resting is scaled to 80%.", AiLearnCopy.noticeBig(only, locale))
        assertEquals("تعلّمت أن نومك ثابت", AiLearnCopy.noticeBig(AiLearning.Notice(listOf("تعلّمت أن نومك ثابت"), emptyList()), locale))
    }

    @Test fun `the consent says where it goes, how much, how often, what may change and what never leaves`() {
        val lines = AiLearnCopy.consentLines(locale)
        assertEquals(6, lines.size)
        assertTrue(lines[0].contains("api.deepseek.com") && lines[0].contains("once a day") && lines[0].contains("during a workout"))
        assertTrue(lines[1].contains("90 days") && lines[1].contains("a year back"))
        assertTrue(lines[3].contains("24 KB") && lines[3].contains("4 KB"))
        assertTrue(lines[4].contains("five Energy tuning values") && lines[4].contains("quarter of its range") && lines[4].contains("undo") && lines[4].contains("never edits stored data"))
        assertTrue(lines[5].startsWith("Never sent"))
        assertCleanCopy(lines)
    }

    @Test fun `no sentence leaks a placeholder or the word null`() {
        val all = ProposalGate.Reason.entries.map { AiLearnCopy.reason(it) } + AiLearnCopy.consentLines(locale) + AiFailure.entries.map { AiLearnCopy.failure(it) }
        assertFalse(all.any { it.contains("%") && !it.contains("%.") || it.contains("null") || it.contains("\$s") })
    }
}
