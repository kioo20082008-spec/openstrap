package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Recovery

/** What each knob does to Energy, and that the published tuning is untouched by them. */
class SelfTuningModelTest {
    private val fx = SelfCheckFixtures

    private fun values(b: SelfCheckFixtures.Built, t: ChargeTuning) = fx.trace(b, t).minutes.map { it.value }

    /** The day's mean Energy: the end of the day alone can be zero under both. */
    private fun mean(b: SelfCheckFixtures.Built, t: ChargeTuning) = values(b, t).average()

    @Test fun `the default knobs are the published model`() {
        val b = fx.build(fx.ownersDay(), alert = 1)
        assertEquals(values(b, ChargeTuning.Default), values(b, Knob.entries.fold(ChargeTuning.Default) { t, k -> k.write(t, k.default) }))
        assertTrue(ChargeTuning.Default.isDefault)
        assertEquals(Recovery.SUSTAINED_EFFORT_MAX_MINUTES, ChargeTuning.Default.sustainedCap)
    }

    @Test fun `Alert strength zero is the same as no Alert at all, and in between is in between`() {
        val withAlert = fx.build(fx.ownersDay(), alert = 1)
        val none = fx.build(fx.ownersDay(), alert = 0)
        val off = values(withAlert, ChargeTuning(alert = 0f))
        assertEquals(values(none, ChargeTuning.Default), off)
        val half = mean(withAlert, ChargeTuning(alert = 0.5f))
        val full = mean(withAlert, ChargeTuning.Default)
        assertTrue(off.average() > half && half > full)
    }

    @Test fun `a shorter sustained cap and a higher resting offset both give the day back some Energy`() {
        val b = fx.build(fx.ownersDay(), alert = 1)
        val base = mean(b, ChargeTuning.Default)
        assertTrue(mean(b, ChargeTuning(sustainedCap = 30)) > base)
        assertTrue(mean(b, ChargeTuning(restingOffset = 8f)) > base)
        assertTrue(mean(b, ChargeTuning(active = 0.6f)) > base)
    }

    @Test fun `a stronger active drain and rest drain take more`() {
        val b = fx.build(SelfCheckFixtures.Day(awake = fx.ordinary().awake))
        val base = mean(b, ChargeTuning.Default)
        assertTrue(mean(b, ChargeTuning(active = 1.4f)) < base)
        assertTrue(mean(b, ChargeTuning(rest = 1.5f)) < base)
        assertTrue(mean(b, ChargeTuning(rest = 0.5f)) > base)
    }

    @Test fun `the sustained cap leaves the check-in multipliers alone`() {
        val t = ChargeTuning(1.2f, 0.8f, 1.1f)
        val learned = ChargeTuning(alert = 0.5f, sustainedCap = 60, restingOffset = 3f)
        val both = t.withLearned(learned)
        assertEquals(1.2f, both.sleep, 0f)
        assertEquals(0.8f, both.exercise, 0f)
        assertEquals(0.5f, both.alert, 0f)
        assertEquals(60, both.sustainedCap)
        assertEquals(learned, both.learnedPart())
        assertNotEquals(t, both)
        assertTrue(t.withLearned(ChargeTuning.Default) == t)
    }

    @Test fun `knobs read and write back, snap onto their grid and stay inside their range`() {
        for (k in Knob.entries) {
            assertEquals(k.default, k.read(ChargeTuning.Default), 0f)
            for (v in listOf(k.min - 100f, k.min, (k.min + k.max) / 2f, k.max, k.max + 100f)) {
                val written = k.write(ChargeTuning.Default, v)
                assertEquals(written, written.clamped())
                assertTrue(k.read(written) in k.min..k.max)
                assertEquals(k.snap(v), k.read(written), 0f)
            }
            val soft = k.candidates(Direction.Soften)
            assertEquals(k.default, soft.first(), 0f)
            assertTrue(soft.all { it in k.min..k.max })
            assertEquals(soft.distinct(), soft)
            assertEquals(k, Knob.of(k.key))
        }
        assertEquals(null, Knob.of("nope"))
        assertEquals(listOf(90f, 60f, 30f), Knob.Cap.candidates(Direction.Soften))
        assertEquals(listOf(0f, 4f, 8f), Knob.Resting.candidates(Direction.Soften))
        assertEquals(listOf(1f), Knob.Alert.candidates(Direction.Strengthen))
        assertEquals(Knob.entries.map { it.key }.distinct().size, Knob.entries.size)
    }
}
