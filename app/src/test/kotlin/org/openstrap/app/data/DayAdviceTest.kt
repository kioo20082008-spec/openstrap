package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DayAdviceTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private fun night(
        bed: LocalTime? = t(23), usual: LocalTime? = t(23), asleep: Int? = 450, need: Int? = 450,
        hrv: Int? = 0, rhr: Float? = 0f, skin: Float? = 0f,
    ) = LateNight.read(bed, usual, asleep, need, hrv, rhr, skin)

    @Test fun `minutes late wrap around midnight`() {
        assertEquals(90, LateNight.minutesLate(t(0, 30), t(23)))
        assertEquals(-60, LateNight.minutesLate(t(22), t(23)))
        assertEquals(0, LateNight.minutesLate(t(23), t(23)))
        assertEquals(150, LateNight.minutesLate(t(1), t(22, 30)))
    }

    @Test fun `an ordinary night is no verdict`() {
        val v = night()
        assertEquals(LateNight.Level.None, v.level)
        assertTrue(v.causes.isEmpty())
    }

    @Test fun `a late bed alone is not a heavy night`() {
        assertEquals(LateNight.Level.None, night(bed = t(1)).level)
        assertEquals(LateNight.Level.None, night(asleep = 350).level)
        assertEquals(LateNight.Level.None, night(bed = t(1), asleep = 350).level)
    }

    @Test fun `one body signal alone is not enough`() {
        assertEquals(LateNight.Level.None, night(hrv = -15).level)
    }

    @Test fun `a late bed with HRV down is rough`() {
        val v = night(bed = t(1), hrv = -12)
        assertEquals(LateNight.Level.Rough, v.level)
        assertEquals(listOf(LateNight.Reason.LateBed, LateNight.Reason.HrvDown), v.causes.map { it.reason })
        assertEquals(120, v.causes.first().value)
        assertEquals(12, v.causes.last().value)
    }

    @Test fun `three causes with a body signal are heavy`() {
        val v = night(bed = t(1, 30), asleep = 330, hrv = -14, rhr = 6f)
        assertEquals(LateNight.Level.Heavy, v.level)
        assertEquals(4, v.causes.size)
    }

    @Test fun `thresholds are inclusive and the skin counts in tenths`() {
        val v = night(hrv = -10, rhr = 4f, skin = 0.6f)
        assertEquals(LateNight.Level.Heavy, v.level)
        assertEquals(6, v.causes.first { it.reason == LateNight.Reason.SkinWarm }.value)
        assertEquals(LateNight.Level.None, night(hrv = -9, rhr = 3.9f, skin = 0.4f).level)
    }

    @Test fun `missing readings are not causes`() {
        val v = LateNight.read(null, null, null, null, -20, 5f, null)
        assertEquals(LateNight.Level.Rough, v.level)
        assertEquals(2, v.causes.size)
    }

    @Test fun `bedtime takes the need, up to an hour of debt and half an hour after a heavy night`() {
        assertEquals(480, BedtimePlan.plannedMinutes(480, 0, false))
        assertEquals(540, BedtimePlan.plannedMinutes(480, 200, false))
        assertEquals(510, BedtimePlan.plannedMinutes(480, 0, true))
        assertEquals(t(22, 30), BedtimePlan.bedtime(t(6, 30), 480, 0, false))
        assertEquals(t(21, 30), BedtimePlan.bedtime(t(6, 30), 480, 30, true))
    }

    private fun nap(
        now: LocalTime = t(14), charge: Int? = 30, debt: Int = 0, short: Int = 0, heavy: Boolean = false,
        napToday: Boolean = false, recording: Boolean = false, bed: LocalTime? = t(23),
    ) = NapPlan.suggest(now, charge, debt, short, heavy, napToday, recording, bed)

    @Test fun `a low Charge in the afternoon gets a short nap`() {
        val p = nap()
        assertNotNull(p)
        assertEquals(NapPlan.NAP_MINUTES, p!!.minutes)
        // Bed at 23:00 leaves until 16:00.
        assertEquals(t(16), p.latestStart)
    }

    @Test fun `a deep debt and a very low Charge make it a little longer`() {
        assertEquals(NapPlan.LONG_NAP_MINUTES, nap(charge = 25, debt = 150)!!.minutes)
        assertEquals(NapPlan.NAP_MINUTES, nap(charge = 35, debt = 150)!!.minutes)
    }

    @Test fun `a middling Charge needs a short or heavy night behind it`() {
        assertNull(nap(charge = 50))
        assertNotNull(nap(charge = 50, short = 90))
        assertNotNull(nap(charge = 50, heavy = true))
        assertNull(nap(charge = 70, short = 120, heavy = true))
    }

    @Test fun `no nap in the morning, late, after one, or while recording`() {
        assertNull(nap(now = t(11, 30)))
        assertNull(nap(now = t(16, 10)))
        assertNull(nap(napToday = true))
        assertNull(nap(recording = true))
        assertNull(nap(charge = null))
    }

    @Test fun `an early bedtime brings the last nap forward`() {
        assertEquals(t(14), nap(now = t(13), bed = t(21))!!.latestStart)
        assertNull(nap(now = t(14, 30), bed = t(21)))
        // A late bed never pushes it past the afternoon dip.
        assertEquals(NapPlan.LATEST, nap(now = t(13), bed = t(2))!!.latestStart)
    }
}
