package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.day
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.run

class SignalChecksTest {
    private val date = SelfCheckFixtures.date

    // ---- Still minutes counted as effort -------------------------------------------------------------

    @Test fun `hours of effort without a step fire, and the ordinary still day does not`() {
        val still = run(480, 600, 70f, Recovery.State.Rest)
        val effort = run(1080, 200, 40f, Recovery.State.Exercise)
        val f = SittingAsEffort().inspectDay(day(still + effort))!!
        assertEquals(Severity.Medium, f.severity)
        assertEquals(200f, f.nums[0], 0f)
        assertNull(SittingAsEffort().inspectDay(day(still)))
    }

    @Test fun `ninety minutes of it is only Low, and effort with steps or in a workout is not still`() {
        val still = run(480, 600, 70f, Recovery.State.Rest)
        assertEquals(Severity.Low, SittingAsEffort().inspectDay(day(still + run(1080, 100, 40f, Recovery.State.Exercise)))!!.severity)
        assertNull(SittingAsEffort().inspectDay(day(still + run(1080, 200, 40f, Recovery.State.Exercise, steps = 40))))
        assertNull(SittingAsEffort().inspectDay(day(still + run(1080, 200, 40f, Recovery.State.Exercise, workout = true))))
    }

    @Test fun `too little of the day is not judged`() {
        assertNull(SittingAsEffort().inspectDay(day(run(480, 100, 40f, Recovery.State.Exercise))))
    }

    // ---- Heart rate over the maximum ------------------------------------------------------------------

    @Test fun `minutes over the maximum heart rate are recorded, and a stray one is not`() {
        assertNull(PeakOverMax().inspectDay(day(emptyList(), peakHr = 200, peakMinutes = 2)))
        val f = PeakOverMax().inspectDay(day(emptyList(), peakHr = 203, peakMinutes = 12))!!
        assertEquals(Severity.Medium, f.severity)
        assertEquals(Plan.Inform, f.plan)
        assertNull(PeakOverMax().inspectDay(day(emptyList(), peakHr = null, peakMinutes = 0)))
    }

    // ---- Steps ----------------------------------------------------------------------------------------

    @Test fun `steps with no heart response are recorded`() {
        val desk = run(600, 40, 80f, steps = 60, hrr = 0.02f)
        val walk = run(700, 30, 70f, steps = 60, hrr = 0.3f)
        assertNotNull(StepsWithoutResponse().inspectDay(day(desk, steps = 2400)))
        assertNull(StepsWithoutResponse().inspectDay(day(walk, steps = 1800)))
        assertNull(StepsWithoutResponse().inspectDay(day(desk, steps = 2400, partial = true)))
        assertNull(StepsWithoutResponse().inspectDay(day(desk + run(700, 100, 70f, steps = 60, hrr = 0.3f), steps = 8400)))
    }

    @Test fun `most steps taken out as false is recorded`() {
        assertNotNull(StepsMostlyFalse().inspectDay(day(emptyList(), steps = 1500, falseSteps = 4000)))
        assertNull(StepsMostlyFalse().inspectDay(day(emptyList(), steps = 6000, falseSteps = 800)))
        assertNull(StepsMostlyFalse().inspectDay(day(emptyList(), steps = 500, falseSteps = 900)))
        assertNull(StepsMostlyFalse().inspectDay(day(emptyList(), steps = 1500, falseSteps = 4000, partial = true)))
    }

    // ---- Calories -------------------------------------------------------------------------------------

    @Test fun `an implausible activity level on a whole day is recorded`() {
        fun facts(vararg c: CaloriesFact) = Facts(date, calories = c.toList())
        assertEquals(1, CaloriesImplausible().inspect(facts(CaloriesFact(date, 900f, 4800f, 2000, false, true))).size)
        assertTrue(CaloriesImplausible().inspect(facts(CaloriesFact(date, 400f, 2400f, 8000, false, false))).isEmpty())
        assertTrue(CaloriesImplausible().inspect(facts(CaloriesFact(date, 900f, 4800f, 2000, true, true))).isEmpty())
    }

    // ---- The strap ------------------------------------------------------------------------------------

    private fun gapDay(offset: Int, minutes: Int, explained: Boolean) =
        day(emptyList(), gaps = listOf(Gap(minutes, explained)), date = date.minusDays(offset.toLong()))

    @Test fun `unexplained hours without heart rate on two of three days fire`() {
        val bad = Facts(date, listOf(gapDay(2, 150, false), gapDay(1, 30, false), gapDay(0, 200, false)))
        val f = StrapGaps().inspect(bad).single()
        assertEquals(Severity.Medium, f.severity)
        assertEquals(200f, f.nums[0], 0f)
        assertEquals(2f, f.nums[1], 0f)
        assertTrue(StrapGaps().inspect(Facts(date, listOf(gapDay(2, 20, false), gapDay(1, 30, false), gapDay(0, 200, false)))).isEmpty())
        assertTrue(StrapGaps().inspect(Facts(date, listOf(gapDay(1, 400, true), gapDay(0, 400, true)))).isEmpty())
    }

    @Test fun `a nearly flat strap off the charger is recorded once`() {
        fun facts(b: Int?, charging: Boolean) = Facts(date, strap = StrapFacts(b, charging))
        assertEquals(1, StrapBatteryLow().inspect(facts(4, false)).size)
        assertEquals(Severity.Low, StrapBatteryLow().inspect(facts(4, false)).single().severity)
        assertTrue(StrapBatteryLow().inspect(facts(4, true)).isEmpty())
        assertTrue(StrapBatteryLow().inspect(facts(40, false)).isEmpty())
        assertTrue(StrapBatteryLow().inspect(facts(null, false)).isEmpty())
        assertTrue(StrapBatteryLow().inspect(Facts(date)).isEmpty())
    }
}
