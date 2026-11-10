package org.openstrap.app.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.HeartRateRecovery.Result

/** The heart rate recovery fact on a workout's page: a trend against your 30 days, never a finding. */
class WorkoutRecoveryFactTest {
    // English figures; the Arabic ones are read in the app.
    @get:org.junit.Rule val localeRule = org.openstrap.app.testing.LocaleRule()

    @Test fun `no recovery, no fact`() {
        assertNull(WorkoutDetailCopy.recoveryFact(null, 28))
    }

    @Test fun `the fall is the value, with the peak and two minutes under it`() {
        val f = WorkoutDetailCopy.recoveryFact(Result(172, 30, 46), null)!!
        assertEquals(WorkoutFact.Kind.HeartRecovery, f.kind)
        assertEquals("30", f.value)
        assertEquals("bpm down from a peak of 172, 46 down at 2 min.", f.status)
        assertEquals("bpm down from a peak of 172.", WorkoutDetailCopy.recoveryFact(Result(172, 30, null), null)!!.status)
    }

    @Test fun `against your usual it is faster, about usual or slower`() {
        assertEquals(
            "bpm down from a peak of 172. Faster than your usual 25 over 30 days.",
            WorkoutDetailCopy.recoveryFact(Result(172, 30, null), 25)!!.status,
        )
        assertEquals(
            "bpm down from a peak of 172. About your usual 28 over 30 days.",
            WorkoutDetailCopy.recoveryFact(Result(172, 30, null), 28)!!.status,
        )
        assertEquals(
            "bpm down from a peak of 172. Slower than your usual 36 over 30 days.",
            WorkoutDetailCopy.recoveryFact(Result(172, 30, null), 36)!!.status,
        )
    }

    @Test fun `twelve or under is a trend to watch, only when there is no usual to judge by`() {
        val low = WorkoutDetailCopy.recoveryFact(Result(170, 12, null), null)!!.status!!
        assertEquals("bpm down from a peak of 170. A slow recovery. Take it as a trend to watch, not a diagnosis.", low)
        assertFalse(WorkoutDetailCopy.recoveryFact(Result(170, 30, null), null)!!.status!!.contains("slow"))
        // With a usual, the trend line stands in its place.
        assertFalse(WorkoutDetailCopy.recoveryFact(Result(170, 12, null), 30)!!.status!!.contains("diagnosis"))
    }

    @Test fun `the words leave out the separators the page avoids`() {
        listOf(Result(172, 30, 46), Result(170, 12, null)).flatMap { r -> listOf(null, 20, 30, 36).map { WorkoutDetailCopy.recoveryFact(r, it)!! } }
            .flatMap { listOfNotNull(it.name, it.value, it.status) }
            .forEach { l -> listOf("·", ";", "—").forEach { assertFalse("$l has $it", l.contains(it)) } }
    }
}
