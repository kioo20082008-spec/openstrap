package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery.Minute
import org.openstrap.app.data.db.SleepStage
import java.time.LocalDate

class EnergyCalibrationTest {
    private val start = LocalDate.of(2026, 9, 1)
    private val star = ChargeTuning(1.3f, 1f, 1f)
    private val starIndex = EnergyCalibration.GRID.indexOf(star)

    /** A latent 0..n-1 per day, in an order that is not the order of the days. */
    private fun latent(i: Int, n: Int) = (i * 11) % n

    /** An order unrelated to [latent]. */
    private fun scrambled(i: Int, n: Int) = (i * 7 + 3) % n

    /**
     * [n] mornings. The feeling follows the latent value. The Energy at wake of every tuning is [scrambled], except
     * [star]'s, which is [fromLatent] of the day.
     */
    private fun mornings(n: Int, fromLatent: (Int) -> Boolean = { true }, defaultFollows: Boolean = false) = List(n) { i ->
        val feeling = 1 + latent(i, n) * 5 / n
        val wake = List(EnergyCalibration.GRID.size) { k ->
            val follows = (k == starIndex && fromLatent(i)) || (k == 0 && defaultFollows)
            (if (follows) latent(i, n) else scrambled(i, n)) * 3f + 10f
        }
        EnergyCalibration.Sample(start.plusDays(i.toLong()), feeling, wake)
    }

    @Test fun `the grid starts at the published constants and stays within the allowed range`() {
        assertEquals(27, EnergyCalibration.GRID.size)
        assertEquals(ChargeTuning.Default, EnergyCalibration.GRID.first())
        assertTrue(EnergyCalibration.GRID.all { t -> listOf(t.sleep, t.exercise, t.carry).all { it in 0.7f..1.3f } })
        assertEquals(EnergyCalibration.GRID.size, EnergyCalibration.GRID.toSet().size)
    }

    @Test fun `under three weeks of mornings nothing is changed`() {
        val out = EnergyCalibration.fit(mornings(20))
        assertEquals(CalibrationStatus.Learning, out.status)
        assertEquals(20, out.days)
        assertEquals(ChargeTuning.Default, out.tuning)
    }

    @Test fun `a tuning that follows the mornings better on days it was not fitted on is accepted`() {
        val out = EnergyCalibration.fit(mornings(30))
        assertEquals(CalibrationStatus.Active, out.status)
        assertEquals(star, out.tuning)
        assertTrue("before ${out.before} after ${out.after}", out.after!! - out.before!! >= EnergyCalibration.MIN_GAIN)
    }

    @Test fun `a tuning that fits only the fitting days is not accepted`() {
        // Right for the first 20 days (the fitting two thirds), unrelated to how you felt on the last 10.
        val out = EnergyCalibration.fit(mornings(30, fromLatent = { it < 20 }))
        assertNotEquals(CalibrationStatus.Active, out.status)
        assertEquals(ChargeTuning.Default, out.tuning)
    }

    @Test fun `a formula that already follows the mornings is left alone`() {
        val out = EnergyCalibration.fit(mornings(30, defaultFollows = true))
        assertEquals(CalibrationStatus.NotNeeded, out.status)
        assertEquals(ChargeTuning.Default, out.tuning)
    }

    @Test fun `no tuning that does better means the defaults stay`() {
        val noise = mornings(30, fromLatent = { false })
        val out = EnergyCalibration.fit(noise)
        assertEquals(CalibrationStatus.NoGain, out.status)
        assertEquals(ChargeTuning.Default, out.tuning)
        assertEquals(30, out.days)
    }

    @Test fun `only the latest six weeks count and a morning without a wake value is skipped`() {
        val many = mornings(50)
        assertEquals(EnergyCalibration.MAX_DAYS, EnergyCalibration.fit(many).days)
        val gap = many.take(25).mapIndexed { i, s -> if (i == 3) s.copy(wake = s.wake.toMutableList().also { w -> w[5] = null }) else s }
        assertEquals(24, EnergyCalibration.fit(gap).days)
    }

    @Test fun `tuning is kept inside its range and the default is exactly the published constants`() {
        assertEquals(ChargeTuning(0.7f, 1.3f, 1f), ChargeTuning(0.1f, 9f, 1f).clamped())
        assertTrue(ChargeTuning().isDefault)
        assertFalse(star.isDefault)
    }

    // --- the tuning in the Charge model ----------------------------------------------------------------------

    private val params = Recovery.ChargeParams(restingHr = 58f, maxHr = 188f, nightRestingHr = 52f)

    private fun night() = List(450) { Minute(it * 60L, 54, 0, null, SleepStage.LIGHT) }

    @Test fun `the default tuning leaves Charge exactly as it was`() {
        val a = Recovery.charge(night(), params, 0L..450 * 60L, start = 30f)
        val b = Recovery.charge(night(), params.copy(tuning = ChargeTuning(1f, 1f, 1f)), 0L..450 * 60L, start = 30f)
        assertEquals(a.latest, b.latest)
        assertEquals(a.atWake, b.atWake)
        assertEquals(Recovery.exerciseDrain(0.6f, 80f), Recovery.exerciseDrain(0.6f, 80f, 1f), 0f)
    }

    @Test fun `a higher sleep multiplier recharges more`() {
        fun wake(sleep: Float) = Recovery.charge(night(), params.copy(tuning = ChargeTuning(sleep = sleep)), 0L..450 * 60L, start = 30f).latest!!
        assertTrue(wake(0.7f) < wake(1f))
        assertTrue(wake(1f) < wake(1.3f))
    }

    @Test fun `a higher exercise multiplier drains more and leaves the base drain alone`() {
        val low = Recovery.exerciseDrain(0.6f, 80f, 0.7f)
        val mid = Recovery.exerciseDrain(0.6f, 80f)
        val high = Recovery.exerciseDrain(0.6f, 80f, 1.3f)
        assertTrue(low < mid && mid < high)
        assertEquals(Recovery.BASE_DRAIN, Recovery.exerciseDrain(0f, 80f, 1.3f), 1e-6f)
    }

    @Test fun `a higher carry multiplier makes the night after hard exercise recharge less`() {
        val day = List(60) { Minute(it * 60L, 150, 100, 70, null) } +
            List(450) { Minute((60 + it) * 60L, 54, 0, null, SleepStage.LIGHT) }
        fun end(carry: Float) =
            Recovery.charge(day, params.copy(tuning = ChargeTuning(carry = carry)), 0L..510 * 60L, start = 80f).latest!!
        assertTrue("${end(0.7f)} ${end(1.3f)}", end(1.3f) < end(0.7f))
    }

    @Test fun `a result survives storage and a damaged one is ignored`() {
        val result = CalibrationResult(
            LocalDate.of(2026, 11, 10),
            EnergyCalibration.Outcome(CalibrationStatus.Active, 30, 0.12f, 0.55f, star),
            ReadinessCalibration.Outcome(CalibrationStatus.Learning, 12),
        )
        assertEquals(result, CalibrationResult.decode(result.encode()))
        assertEquals(star, result.tuning)
        assertEquals(ReadinessWeights.Default, result.weights)
        assertNull(CalibrationResult.decode("junk"))
        assertNull(CalibrationResult.decode(null))
        assertNull(CalibrationResult.decode(result.encode().replace("active", "mystery")))
        // Only an Active calibration changes a score.
        val kept = result.copy(energy = result.energy.copy(status = CalibrationStatus.NoGain))
        assertEquals(ChargeTuning.Default, kept.tuning)
    }

    @Test fun `the calibration is worked out again after three days, or daily while it is only collecting`() {
        val day = LocalDate.of(2026, 11, 10)
        val active = CalibrationResult(
            day, EnergyCalibration.Outcome(CalibrationStatus.NoGain, 30), ReadinessCalibration.Outcome(CalibrationStatus.Learning, 12),
        )
        assertTrue(CalibrationResult.due(null, day))
        assertFalse(CalibrationResult.due(active, day))
        assertFalse(CalibrationResult.due(active, day.plusDays(2)))
        assertTrue(CalibrationResult.due(active, day.plusDays(3)))
        assertTrue(CalibrationResult.due(active, day.minusDays(1)))
        val learning = active.copy(energy = EnergyCalibration.Outcome(CalibrationStatus.Learning, 5))
        assertFalse(CalibrationResult.due(learning, day))
        assertTrue(CalibrationResult.due(learning, day.plusDays(1)))
    }
}
