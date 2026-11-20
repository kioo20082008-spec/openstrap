package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.night
import java.time.LocalDate

class SleepReadinessChecksTest {
    private val date = SelfCheckFixtures.date
    private fun d(back: Int): LocalDate = date.minusDays(back.toLong())

    // ---- Sleep ----------------------------------------------------------------------------------------

    @Test fun `a night under two hours, over fourteen, or with more stages than sleep fires`() {
        fun f(n: NightFact) = SleepImplausible().inspect(Facts(date, nights = listOf(n)))
        assertEquals(1, f(night(date, asleep = 90)).size)
        assertEquals(1, f(night(date, asleep = 15 * 60)).size)
        assertEquals(1, f(night(date, asleep = 300, deep = 200, rem = 150)).size)
        assertTrue(f(night(date, asleep = 460)).isEmpty())
        assertTrue(f(night(date, asleep = 480, deep = null, rem = null, awake = null)).isEmpty())
    }

    @Test fun `only the last three nights are looked at`() {
        val nights = (6 downTo 0).map { night(d(it), asleep = if (it == 5) 60 else 460) }
        assertTrue(SleepImplausible().inspect(Facts(date, nights = nights)).isEmpty())
        val recent = (6 downTo 0).map { night(d(it), asleep = if (it == 1) 60 else 460) }
        assertEquals(d(1), SleepImplausible().inspect(Facts(date, nights = recent)).single().day)
    }

    @Test fun `a score thirty points off the strap's three nights the same way fires`() {
        fun nights(vararg gap: Int) = gap.mapIndexed { i, g -> night(d(gap.size - 1 - i), score = 80, strap = 80 - g) }
        assertEquals(1, SleepScoreGap().inspect(Facts(date, nights = nights(35, 40, 31, 5, 0))).size)
        assertTrue(SleepScoreGap().inspect(Facts(date, nights = nights(35, -40, 31, 5, 0))).isEmpty())
        assertTrue(SleepScoreGap().inspect(Facts(date, nights = nights(35, 40, 10, 5, 0))).isEmpty())
        assertTrue(SleepScoreGap().inspect(Facts(date, nights = nights(10, 12, 8, 5, 0))).isEmpty())
        val one = night(date, strap = null)
        assertTrue(SleepScoreGap().inspect(Facts(date, nights = listOf(one, one, one))).isEmpty())
    }

    @Test fun `a midpoint four hours from the usual fires, around midnight too`() {
        val usual = (8 downTo 1).map { night(d(it), midpoint = 180) }
        assertEquals(1, SleepMidpointJump().inspect(Facts(date, nights = usual + night(date, midpoint = 180 + 300))).size)
        assertTrue(SleepMidpointJump().inspect(Facts(date, nights = usual + night(date, midpoint = 240))).isEmpty())
        // 23:30 and 00:30 are an hour apart, not twenty-three.
        val late = (8 downTo 1).map { night(d(it), midpoint = if (it % 2 == 0) 1410 else 30) }
        assertTrue(SleepMidpointJump().inspect(Facts(date, nights = late + night(date, midpoint = 0))).isEmpty())
        assertTrue(SleepMidpointJump().inspect(Facts(date, nights = usual.take(3) + night(date, midpoint = 900))).isEmpty())
    }

    @Test fun `circular means and distances`() {
        assertEquals(0, SleepMidpointJump.circularMean(listOf(1410, 30)))
        assertEquals(60, SleepMidpointJump.circularDistance(1410, 30))
        assertEquals(720, SleepMidpointJump.circularDistance(0, 720))
    }

    // ---- Readiness ------------------------------------------------------------------------------------

    private fun r(back: Int, score: Int?, hrv: Int? = 70, rhr: Int? = 70, sleep: Int? = 70) = ReadinessFact(d(back), score, hrv, rhr, sleep)

    @Test fun `a jump of forty with the contributors still fires, and with one that moved does not`() {
        val still = listOf(r(1, 40), r(0, 85))
        val f = ReadinessJump().inspect(Facts(date, readiness = still)).single()
        assertEquals(Severity.Medium, f.severity)
        assertEquals(45f, f.nums[0], 0f)
        assertTrue(ReadinessJump().inspect(Facts(date, readiness = listOf(r(1, 40, hrv = 40), r(0, 85, hrv = 80)))).isEmpty())
        assertTrue(ReadinessJump().inspect(Facts(date, readiness = listOf(r(1, 40), r(0, 60)))).isEmpty())
    }

    @Test fun `a fall counts too, and a gap in days or a missing score does not`() {
        assertEquals(1, ReadinessJump().inspect(Facts(date, readiness = listOf(r(1, 90), r(0, 45)))).size)
        assertTrue(ReadinessJump().inspect(Facts(date, readiness = listOf(r(2, 40), r(0, 90)))).isEmpty())
        assertTrue(ReadinessJump().inspect(Facts(date, readiness = listOf(r(1, null), r(0, 90)))).isEmpty())
        assertTrue(ReadinessJump().inspect(Facts(date, readiness = listOf(r(1, 40, null, null, 60), r(0, 90, null, null, 60)))).isEmpty())
    }

    @Test fun `the same score all week fires, a varied one does not`() {
        fun week(vararg s: Int?) = Facts(date, readinessWeek = s.toList())
        assertEquals(1, ReadinessStuck().inspect(week(71, 71, 72, 71, 71, 71, 71)).size)
        assertEquals(1, ReadinessStuck().inspect(week(null, 71, 71, 71, 71, 71, 71)).size)
        assertTrue(ReadinessStuck().inspect(week(null, null, 71, 71, 71, 71, 71)).isEmpty())
        assertTrue(ReadinessStuck().inspect(week(71, 74, 72, 71, 71, 71, 71)).isEmpty())
        assertTrue(ReadinessStuck().inspect(week(71, 71, 71)).isEmpty())
    }
}
