package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.ui.dashboard.Narrative.Rating

class NarrativeTest {
    /** A night with [asleep] minutes asleep; wake counts weren't recorded for the real nights, so they default to null. */
    private fun night(
        asleep: Int, awake: Int, score: Int?, wakes: Int? = null, deep: Int? = null, rem: Int? = null,
    ) = Pair(
        SleepSession(start = 0, end = (asleep + awake) * 60L, score = score, awakeMinutes = awake, wakeCount = wakes, deepMinutes = deep, remMinutes = rem),
        asleep * 60L,
    )

    private fun headline(n: Pair<SleepSession, Long>) = Narrative.sleepHeadline(n.first, n.second)

    @Test
    fun realNightsNameTheirCause() {
        assertEquals("Short night", headline(night(4 * 60 + 7, 24, 34)))
        assertEquals("Broken sleep", headline(night(7 * 60 + 6, 57, 70)))
        assertEquals("Short night", headline(night(5 * 60 + 42, 4, 58)))
        assertEquals("Short night", headline(night(5 * 60 + 36, 8, 47)))
        assertEquals("Short night", headline(night(3 * 60 + 4, 1, 31)))
    }

    @Test
    fun noCauseFollowsTheScore() {
        assertEquals("Solid night", headline(night(8 * 60, 10, 92, wakes = 2, deep = 90, rem = 110)))
        assertEquals("Restful night", headline(night(7 * 60 + 20, 15, 84, wakes = 3, deep = 80, rem = 95)))
        assertEquals("Okay night", headline(night(6 * 60 + 40, 12, 72, wakes = 2, deep = 70, rem = 80)))
    }

    @Test
    fun lightSleepOnlyWhenNothingElseAndBelowGood() {
        // 7 h asleep with 30 + 40 min deep + REM: 17 %, under a quarter.
        assertEquals("Shallow night", headline(night(7 * 60, 10, 72, wakes = 2, deep = 30, rem = 40)))
        // A good score outranks it, and short outranks it.
        assertEquals("Restful night", headline(night(7 * 60, 10, 82, wakes = 2, deep = 30, rem = 40)))
        assertEquals("Short night", headline(night(5 * 60, 10, 50, wakes = 2, deep = 20, rem = 20)))
        // Without REM (strap without REM support) it isn't judged.
        assertEquals("Okay night", headline(night(7 * 60, 10, 72, wakes = 2, deep = 30, rem = null)))
    }

    @Test
    fun shortAndBrokenCombine() {
        assertEquals("Short, broken night", headline(night(5 * 60, 45, 40)))
        assertEquals("Short, broken night", headline(night(5 * 60 + 30, 12, 50, wakes = Narrative.BROKEN_WAKE_COUNT)))
    }

    @Test
    fun brokenOutranksTheScoreBand() {
        assertEquals("Broken sleep", headline(night(8 * 60, 35, 91)))
        assertEquals("Broken sleep", headline(night(8 * 60, 10, 85, wakes = 7)))
        assertEquals("Restful night", headline(night(8 * 60, 10, 85, wakes = Narrative.BROKEN_WAKE_COUNT - 1)))
        assertEquals("Short night", headline(night(5 * 60 + 59, 29, 70)))
    }

    @Test
    fun noScoreNoHeadline() {
        assertNull(headline(night(7 * 60, 10, null)))
    }

    @Test
    fun totalSleepRatesOversleep() {
        fun total(min: Int) = Narrative.sleepContributors(SleepSession(start = 0, end = min * 60L), min * 60L).first { it.label == "Total sleep" }.rating
        // By the Sleep score's duration part: full within an hour of the 8 h need.
        assertEquals(Rating.OPTIMAL, total(7 * 60))
        assertEquals(Rating.OPTIMAL, total(8 * 60))
        assertEquals(Rating.OPTIMAL, total(9 * 60))
        // Past 9 h it's never what helped: 10 h scores 85 and stays On track, 12 h 55 and 13 h the floor of 40.
        assertEquals(Rating.GOOD, total(9 * 60 + 1))
        assertEquals(Rating.GOOD, total(10 * 60))
        assertEquals(Rating.FAIR, total(12 * 60))
        assertEquals(Rating.LOW, total(13 * 60))
        // Short nights rate against need: 6 h scores 60, 5 h 30.
        assertEquals(Rating.FAIR, total(6 * 60))
        assertEquals(Rating.LOW, total(5 * 60))
    }

    @Test fun `the contributors are the Sleep score's parts, regular timing among them`() {
        val score = org.openstrap.app.data.SleepScore.of(7 * 60 + 40, 480, 35, 2, 105)
        val c = Narrative.sleepContributors(score, (7 * 60 + 40) * 60L)
        assertEquals(listOf("Total sleep", "Continuity", "Regularity"), c.map { it.label })
        assertEquals(0.5833f, c[1].fraction, 1e-3f)
        assertEquals(Rating.FAIR, c[1].rating)
        assertEquals(0.5f, c[2].fraction, 1e-4f)
        assertEquals("Middle of the night 1 h 45 off your usual", c[2].detail)
        val factors = SleepCopy.factors(c, (7 * 60 + 40) * 60L, 480, 35)
        assertEquals(listOf("Time asleep", "Staying asleep", "Regular timing"), factors.map { it.name })
        assertEquals("Middle of the night 1 h 45 off your usual", factors[2].detail)
        // Within half an hour of the usual it just says so; and a night without stages or enough nights has no such rows.
        assertEquals(
            "Middle of the night close to your usual",
            Narrative.sleepContributors(org.openstrap.app.data.SleepScore.of(480, 480, null, null, 20), 480 * 60L).last().detail,
        )
        val bare = org.openstrap.app.data.SleepScore.of(480, 480, null, null, null)
        assertEquals(listOf("Total sleep"), Narrative.sleepContributors(bare, 480 * 60L).map { it.label })
    }

    @Test fun `the contributors write lengths of time as the rest of the app does`() {
        val c = Narrative.sleepContributors(SleepSession(start = 0, end = 8 * 3600L), (7 * 60 + 40) * 60L, 480, 45)
        assertEquals("7 h 40 of 8 h", c.first { it.label == "Total sleep" }.detail)
        assertEquals("45 min awake after falling asleep", c.first { it.label == "Continuity" }.detail)
    }
}
