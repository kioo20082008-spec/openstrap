package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.day
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.night
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.run

class SelfChecksTest {
    private val fx = SelfCheckFixtures

    @Test fun `ids are unique, lower case, and begin with the domain they belong to`() {
        val ids = SelfChecks.all.map { it.id }
        assertEquals(ids.distinct(), ids)
        for (c in SelfChecks.all) {
            assertTrue(c.id, Regex("[a-z]+\\.[a-z_]+").matches(c.id))
            val prefix = when (c.domain) {
                Domain.HeartRate -> "heart"
                else -> c.domain.name.lowercase()
            }
            assertTrue("${c.id} in ${c.domain}", c.id.startsWith("$prefix."))
        }
        assertEquals(Domain.entries.toSet(), SelfChecks.all.map { it.domain }.toSet())
    }

    @Test fun `every id can be found again`() {
        for (c in SelfChecks.all) assertSame(c, SelfChecks.byId(c.id))
        assertEquals(null, SelfChecks.byId("no.such"))
    }

    @Test fun `nothing at all is nothing found`() {
        assertEquals(emptyList<Finding>(), SelfChecks.inspectAll(Facts(fx.date)))
    }

    @Test fun `a healthy week is quiet for every check`() {
        val healthy = (6 downTo 0).map { back ->
            val date = fx.date.minusDays(back.toLong())
            day(
                run(480, 60, 70f, Recovery.State.Rest) + run(600, 20, 62f, Recovery.State.Activity, steps = 90, hrr = 0.3f) + run(900, 600, 60f, Recovery.State.Rest),
                atWake = 92f, wakeAsleep = 470, steps = 8000, date = date,
            )
        }
        val nights = (6 downTo 0).map { night(fx.date.minusDays(it.toLong()), asleep = 450 + it * 3, midpoint = 180 + it) }
        val readiness = (6 downTo 0).map { ReadinessFact(fx.date.minusDays(it.toLong()), 70 + it * 3, 70, 70, 70) }
        val facts = Facts(
            fx.date, healthy, nights, readiness, readinessWeek = readiness.map { it.score },
            calories = listOf(CaloriesFact(fx.date, 400f, 2400f, 8000, false, false)), strap = StrapFacts(60, false),
        )
        assertEquals(emptyList<String>(), SelfChecks.inspectAll(facts).map { it.check })
    }

    @Test fun `a training day and a desk day are quiet too`() {
        val run = SelfCheckFixtures.Day { m ->
            val h = m / 60
            when {
                h == 18 && m % 60 < 45 -> SelfCheckFixtures.Spec(150 + m % 4, 160, workout = true)
                (h == 8 || h == 13) && m % 60 < 15 -> SelfCheckFixtures.Spec(100, 90)
                else -> SelfCheckFixtures.Spec(66 + m % 6)
            }
        }
        val desk = SelfCheckFixtures.Day { m ->
            if (m in 600..1020) SelfCheckFixtures.Spec(86 + m % 5) else SelfCheckFixtures.Spec(68 + m % 5, if (m % 90 == 0) 60 else 0)
        }
        for (d in listOf(run, desk)) {
            val trace = fx.trace(fx.build(d))
            assertEquals(emptyList<String>(), SelfChecks.inspectAll(Facts(fx.date, listOf(trace))).map { it.check })
        }
    }

    @Test fun `the findings come oldest first`() {
        val facts = Facts(
            fx.date,
            listOf(
                day(emptyList(), atWake = 30f, date = fx.date),
                day(emptyList(), atWake = 30f, date = fx.date.minusDays(2)),
            ),
        )
        assertEquals(listOf(fx.date.minusDays(2), fx.date), SelfChecks.inspectAll(facts).map { it.day })
    }

    @Test fun `a check that throws does not stop the others`() {
        val broken = object : Check {
            override val id = "x.broken"
            override val domain = Domain.Energy
            override fun inspect(facts: Facts): List<Finding> = throw IllegalStateException("boom")
        }
        val facts = Facts(fx.date, listOf(day(emptyList(), atWake = 30f)))
        val found = SelfChecks.inspectAll(facts, listOf(broken, EnergyLowWake()))
        assertEquals(listOf("energy.low_wake"), found.map { it.check })
    }

    @Test fun `the owner's day is found by the registry as a whole`() {
        val trace = fx.trace(fx.build(fx.ownersDay(), alert = 1))
        val ids = SelfChecks.inspectAll(Facts(fx.date, listOf(trace))).map { it.check }
        assertTrue("energy.stuck_empty" in ids)
        assertTrue("heart.sitting_as_effort" in ids)
    }

    @Test fun `a trace built from minutes finds its wake, its night and its Alert`() {
        val t = fx.trace(fx.build(fx.ownersDay(), alert = 2))
        assertEquals(601, t.wakeAsleepMinutes)
        assertEquals(2, t.alertNights)
        assertTrue(t.atWake!! > 90f)
        assertTrue(t.minutes.size > 1000)
        assertTrue(t.minutes.zipWithNext().all { (a, b) -> b.t > a.t })
        assertEquals(0, t.peakMinutes)
    }

    @Test fun `heart rate dropouts the strap does not explain are gaps, and the open end is not`() {
        val b = fx.build(fx.ordinary().let { SelfCheckFixtures.Day(awake = it.awake) })
        val midnight = fx.at(fx.date, 0)
        val cut = b.inputs.minutes.map { m ->
            val mod = (m.t - midnight) / 60
            if (mod in 780..960 && m.t >= midnight) m.copy(hr = null) else m
        }
        val withGap = SelfFacts.trace(org.openstrap.app.data.ChargeInputs(cut, b.inputs.params, b.inputs.window, b.inputs.mainSleepEnd), org.openstrap.app.data.ChargeTuning.Default, fx.date)
        assertEquals(listOf(181), withGap.gaps.filter { !it.explained }.map { it.minutes })
        val offWrist = b.inputs.minutes.map { m ->
            val mod = (m.t - midnight) / 60
            if (mod in 780..960 && m.t >= midnight) m.copy(hr = null, worn = false) else m
        }
        val explained = SelfFacts.trace(org.openstrap.app.data.ChargeInputs(offWrist, b.inputs.params, b.inputs.window, b.inputs.mainSleepEnd), org.openstrap.app.data.ChargeTuning.Default, fx.date)
        assertTrue(explained.gaps.all { it.explained })
        // Data that stops at the end of the day is a sync not yet made.
        val ends = b.inputs.minutes.map { m -> if ((m.t - midnight) / 60 >= 1200) m.copy(hr = null) else m }
        val open = SelfFacts.trace(org.openstrap.app.data.ChargeInputs(ends, b.inputs.params, b.inputs.window, b.inputs.mainSleepEnd), org.openstrap.app.data.ChargeTuning.Default, fx.date)
        assertTrue(open.gaps.none { it.minutes > 200 })
    }
}
