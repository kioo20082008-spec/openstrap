package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.day
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.fall
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.run

class EnergyChecksTest {
    private val fx = SelfCheckFixtures

    // ---- The owner's day --------------------------------------------------------------------------

    @Test fun `the owner's day is caught as Energy stuck at zero after a ten hour night`() {
        val trace = fx.trace(fx.build(fx.ownersDay(), alert = 1))
        val f = EnergyStuckEmpty().inspectDay(trace)
        assertNotNull(f)
        f!!
        assertEquals(Severity.Medium, f.severity)
        assertTrue("minutes at zero ${f.nums[0]}", f.nums[0] >= EnergyStuckEmpty.MIN_MINUTES)
        assertEquals(601f, f.nums[2], 0f)
        assertTrue(f.plan is Plan.Search)
        assertEquals(Direction.Soften, (f.plan as Plan.Search).direction)
    }

    @Test fun `the owner's day also shows as still hours counted as effort`() {
        val trace = fx.trace(fx.build(fx.ownersDay(), alert = 1))
        val f = SittingAsEffort().inspectDay(trace)
        assertNotNull(f)
        assertEquals(Severity.Medium, f!!.severity)
        assertTrue(f.nums[0] >= SittingAsEffort.MEDIUM_MINUTES)
    }

    @Test fun `an ordinary day is quiet for every check`() {
        for (alert in 0..1) {
            val trace = fx.trace(fx.build(fx.ordinary().let { d -> SelfCheckFixtures.Day(awake = d.awake) }, alert = alert))
            assertEquals(emptyList<String>(), SelfChecks.inspectAll(Facts(fx.date, listOf(trace))).map { it.check })
        }
    }

    // ---- Stuck at zero ----------------------------------------------------------------------------

    @Test fun `zero for three hours of a still day fires, and for two hours does not`() {
        val long = day(run(900, EnergyStuckEmpty.MIN_MINUTES, 0f))
        assertNotNull(EnergyStuckEmpty().inspectDay(long))
        val short = day(run(900, EnergyStuckEmpty.MIN_MINUTES - 1, 0f))
        assertNull(EnergyStuckEmpty().inspectDay(short))
    }

    @Test fun `five hours at zero is High`() {
        val f = EnergyStuckEmpty().inspectDay(day(run(900, EnergyStuckEmpty.HIGH_MINUTES, 0f)))
        assertEquals(Severity.High, f!!.severity)
    }

    @Test fun `zero after a very hard day is believed`() {
        val workout = run(700, 100, 40f, Recovery.State.Exercise, steps = 80, workout = true)
        val d = day(workout + run(800, 300, 0f))
        assertNull(EnergyStuckEmpty().inspectDay(d))
        val walking = run(700, 150, 30f, Recovery.State.Activity, steps = 70) // 10 500 steps
        assertNull(EnergyStuckEmpty().inspectDay(day(walking + run(850, 300, 0f))))
    }

    @Test fun `zero at the end of a day of fifteen thousand steps is believed`() {
        val d = day(run(900, 300, 0f), steps = 16_000)
        assertNull(EnergyStuckEmpty().inspectDay(d))
        assertNotNull(EnergyStuckEmpty().inspectDay(day(run(900, 300, 0f), steps = 6_000)))
    }

    @Test fun `asleep minutes at zero are not an awake stretch`() {
        assertNull(EnergyStuckEmpty().inspectDay(day(run(0, 400, 0f, asleep = true))))
    }

    @Test fun `a gap in the minutes ends a stretch`() {
        val d = day(run(900, 120, 0f) + run(1040, 120, 0f))
        assertNull(EnergyStuckEmpty().inspectDay(d))
    }

    // ---- Stuck full -------------------------------------------------------------------------------

    @Test fun `full all day while walking fires, and full on a still day does not`() {
        val busy = day(run(480, EnergyStuckFull.MIN_MINUTES, 100f, steps = 15))
        assertNotNull(EnergyStuckFull().inspectDay(busy))
        val still = day(run(480, EnergyStuckFull.MIN_MINUTES, 100f, steps = 1))
        assertNull(EnergyStuckFull().inspectDay(still))
        assertNull(EnergyStuckFull().inspectDay(day(run(480, EnergyStuckFull.MIN_MINUTES - 1, 100f, steps = 15))))
    }

    @Test fun `the full check strengthens and the empty check softens`() {
        val busy = EnergyStuckFull().inspectDay(day(run(480, 400, 100f, steps = 15)))!!
        assertEquals(Direction.Strengthen, (busy.plan as Plan.Search).direction)
    }

    // ---- Low at wake ------------------------------------------------------------------------------

    @Test fun `a low wake after a long night fires, a normal one does not`() {
        assertNotNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = 50f, wakeAsleep = 9 * 60)))
        assertEquals(Severity.High, EnergyLowWake().inspectDay(day(emptyList(), atWake = 30f, wakeAsleep = 9 * 60))!!.severity)
        assertNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = 70f, wakeAsleep = 9 * 60)))
    }

    @Test fun `a low wake after a short night or an estimated one is believed`() {
        assertNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = 40f, wakeAsleep = 6 * 60)))
        assertNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = 40f, wakeAsleep = 9 * 60, offOvernight = true)))
        assertNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = null, wakeAsleep = 9 * 60)))
        assertNull(EnergyLowWake().inspectDay(day(emptyList(), atWake = 40f, wakeAsleep = null)))
    }

    // ---- Drain faster than the day explains ---------------------------------------------------------

    @Test fun `forty points lost in two still hours fires`() {
        val d = day(fall(600, 120, 80f, 40f))
        val f = EnergyDrainFast().inspectDay(d)
        assertNotNull(f)
        assertTrue(f!!.nums[0] >= EnergyDrainFast.MIN_LOST)
    }

    @Test fun `the same loss on a run of ten thousand steps does not`() {
        val d = day(fall(600, 120, 80f, 40f, state = Recovery.State.Exercise, steps = 90))
        assertNull(EnergyDrainFast().inspectDay(d))
    }

    @Test fun `the same loss inside a recorded workout does not`() {
        val minutes = fall(600, 120, 80f, 40f, state = Recovery.State.Exercise).map {
            TraceMinute(it.t, it.value, it.state, 20, it.hrr, false, true)
        }
        assertNull(EnergyDrainFast().inspectDay(day(minutes)))
    }

    @Test fun `a small loss, or a loss in rest, does not`() {
        assertNull(EnergyDrainFast().inspectDay(day(fall(600, 120, 80f, 65f))))
        assertNull(EnergyDrainFast().inspectDay(day(fall(600, 120, 80f, 40f, state = Recovery.State.Rest))))
        assertNull(EnergyDrainFast().inspectDay(day(fall(600, 30, 80f, 40f))))
    }

    // ---- The Alert run ----------------------------------------------------------------------------

    @Test fun `the Alert for four nights halves its effect on Energy and a week quarters it`() {
        fun finding(nights: Int) = EnergyAlertStreak().inspect(Facts(fx.date, listOf(day(emptyList(), alertNights = nights)))).firstOrNull()
        assertNull(finding(0))
        assertNull(finding(3))
        val four = finding(4)!!
        assertEquals(mapOf(Knob.Alert to 0.5f), (four.plan as Plan.Set).to)
        assertEquals(Severity.Medium, four.severity)
        val week = finding(7)!!
        assertEquals(mapOf(Knob.Alert to 0.25f), (week.plan as Plan.Set).to)
        assertEquals(Severity.High, week.severity)
    }

    @Test fun `every Energy finding is a Search or a Set, and names knobs a day can change`() {
        val fired = listOf(
            EnergyStuckEmpty().inspectDay(day(run(900, 300, 0f))),
            EnergyStuckFull().inspectDay(day(run(480, 400, 100f, steps = 15))),
            EnergyLowWake().inspectDay(day(emptyList(), atWake = 30f)),
            EnergyDrainFast().inspectDay(day(fall(600, 120, 80f, 40f))),
        )
        for (f in fired) {
            val plan = f!!.plan
            assertTrue(plan is Plan.Search)
            assertTrue((plan as Plan.Search).knobs.isNotEmpty())
        }
    }
}
