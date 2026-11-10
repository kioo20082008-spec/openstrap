package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery.Factor
import org.openstrap.app.data.Recovery.Night
import java.time.LocalDate

class ReadinessCalibrationTest {
    private val start = LocalDate.of(2026, 9, 1)
    private val n = 60

    /**
     * [n] mornings in which only the Sleep part follows how you felt; the HRV and resting HR parts are unrelated to it. The
     * published weights lean on HRV and resting HR, so a different mix should fit better.
     */
    private fun mornings(feelOf: (Int) -> Int = { 1 + (it * 17) % n / 12 }) = List(n) { i ->
        val sleep = (i * 17) % n
        ReadinessCalibration.Day(
            start.plusDays(i.toLong()), feelOf(i),
            mapOf(
                Factor.HrvBalance to 40 + (i * 29 + 5) % n,
                Factor.RestingHr to 40 + (i * 13 + 11) % n,
                Factor.Sleep to 40 + sleep,
                Factor.SleepBalance to 40 + sleep / 2 + 20,
            ),
            alert = false,
        )
    }

    @Test fun `the default weights are the published ones`() {
        for (f in Factor.entries) assertEquals(f.weight, ReadinessWeights.Default.of(f), 0f)
        assertTrue(ReadinessWeights.Default.isDefault)
        assertEquals(ReadinessWeights.Default, ReadinessWeights.of(mapOf(Factor.Sleep to 1f)))
    }

    @Test fun `a weight is kept within half and one and a half of its default`() {
        val w = ReadinessWeights.of(mapOf(Factor.Sleep to 5f, Factor.HrvBalance to 0f))
        assertEquals(1.5f, w.multiplier(Factor.Sleep), 0f)
        assertEquals(0.5f, w.multiplier(Factor.HrvBalance), 0f)
        assertEquals(Factor.Sleep.weight * 1.5f, w.of(Factor.Sleep), 1e-6f)
        assertEquals(1f, w.multiplier(Factor.Breathing), 0f)
    }

    @Test fun `the fusion at the default weights is the published weighted mean`() {
        val parts = listOf(Factor.HrvBalance to 90, Factor.Sleep to 20)
        // (0.30 * 90 + 0.16 * 20) / 0.46
        assertEquals(65, Recovery.fusedScore(parts, alert = false).first)
        assertEquals(65, Recovery.fusedScore(parts, alert = false, ReadinessWeights.Default).first)
        assertEquals(75, Recovery.fusedScore(parts, alert = false, ReadinessWeights.of(mapOf(Factor.Sleep to 0.5f))).first)
        // The red-flag cap and the Alert cap stay whatever the weights.
        val flagged = listOf(Factor.HrvBalance to 20, Factor.Sleep to 100, Factor.SleepBalance to 100, Factor.PreviousDayLoad to 100)
        val heavySleep = ReadinessWeights.of(mapOf(Factor.Sleep to 1.5f, Factor.SleepBalance to 1.5f))
        assertEquals(50, Recovery.fusedScore(flagged, alert = false, heavySleep).first)
        assertEquals(Recovery.ALERT_CAP, Recovery.fusedScore(listOf(Factor.HrvBalance to 100, Factor.Sleep to 100), alert = true).first)
    }

    private fun night(i: Int) = Night(
        day = i * 86_400L, start = i * 86_400L - 7 * 3600, end = i * 86_400L + 30 * 60, asleepMinutes = 480, awakeMinutes = 20,
        deepMinutes = 80, remMinutes = 95, hrv = 50f + i % 5, restingHr = 55f + i % 3, rhrMeanSleep = 59f, nadirTime = i * 86_400L - 4 * 3600,
        skinTemperature = 33.5f, breathRate = 14f, hrvSamples = 20, hrMinutes = 420,
    )

    @Test fun `Readiness with the default weights is the score it always was`() {
        val history = (1..30).map(::night)
        val plain = Recovery.readiness(night(31), history)
        val explicit = Recovery.readiness(night(31), history, weights = ReadinessWeights.Default)
        assertEquals(plain.score, explicit.score)
        assertEquals(plain.contributors, explicit.contributors)
        assertEquals(plain.capped, explicit.capped)
    }

    @Test fun `under six weeks of mornings nothing is changed`() {
        val out = ReadinessCalibration.fit(mornings().take(41))
        assertEquals(CalibrationStatus.Learning, out.status)
        assertEquals(ReadinessWeights.Default, out.weights)
    }

    @Test fun `weights that follow the mornings better on days they were not fitted on are accepted`() {
        val out = ReadinessCalibration.fit(mornings())
        assertEquals(CalibrationStatus.Active, out.status)
        assertTrue("before ${out.before} after ${out.after}", out.after!! - out.before!! >= ReadinessCalibration.MIN_GAIN)
        assertTrue(out.weights.multiplier(Factor.Sleep) > 1f)
        assertTrue(out.weights.multiplier(Factor.HrvBalance) < 1f)
        assertTrue(Factor.entries.all { out.weights.multiplier(it) in ReadinessWeights.MIN..ReadinessWeights.MAX })
    }

    @Test fun `feelings unrelated to every part change nothing`() {
        val out = ReadinessCalibration.fit(mornings { 1 + ((it * 41 + 2) % n) / 12 })
        assertNotEquals(CalibrationStatus.Active, out.status)
        assertEquals(ReadinessWeights.Default, out.weights)
    }

    @Test fun `weights are left alone when the published ones already follow the mornings`() {
        val days = mornings().map { d ->
            val s = d.parts.getValue(Factor.Sleep)
            d.copy(parts = mapOf(Factor.HrvBalance to s, Factor.RestingHr to s, Factor.Sleep to s))
        }
        val out = ReadinessCalibration.fit(days)
        assertEquals(CalibrationStatus.NotNeeded, out.status)
        assertEquals(ReadinessWeights.Default, out.weights)
    }

    @Test fun `a day with neither HRV nor resting HR has no score and is left out`() {
        val days = mornings().map { it.copy(parts = it.parts - Factor.HrvBalance - Factor.RestingHr) }
        assertEquals(0, ReadinessCalibration.fit(days).days)
    }

    @Test fun `a stored result keeps its weights`() {
        val weights = ReadinessWeights.of(mapOf(Factor.Sleep to 1.5f, Factor.HrvBalance to 0.5f, Factor.Breathing to 0.75f))
        val result = CalibrationResult(
            start, EnergyCalibration.Outcome(CalibrationStatus.NotNeeded, 30, 0.7f),
            ReadinessCalibration.Outcome(CalibrationStatus.Active, 50, 0.2f, 0.4f, weights),
        )
        val back = CalibrationResult.decode(result.encode())!!
        assertEquals(result, back)
        assertEquals(weights, back.weights)
        assertFalse(back.weights.isDefault)
    }
}
