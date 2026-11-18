package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The cross-checks the payload carries: weekly deltas, associations with their counts, weekdays, streaks, debt and workouts. */
class AiInsightsTest {
    /** [n] days ending yesterday, oldest first: day [i] is `n - i` days before [AI_TODAY]. */
    private fun days(n: Int, f: (Int) -> AiDay): List<AiDay> = (0 until n).map { i -> f(i).copy(date = AI_TODAY.minusDays((n - i).toLong())) }

    private fun blank(date: LocalDate = AI_TODAY) = AiDay(date)

    private fun input(days: List<AiDay>, nights: List<AiNight> = emptyList(), workouts: List<AiWorkout> = emptyList(), todayRecovery: Int? = 60, debt: Int? = null) =
        aiInput(days = 0, nights = 0, workouts = 0).copy(
            days = days, nights = nights, workouts = workouts, today = AiToday(recovery = todayRecovery), sleepDebtMinutes = debt,
        )

    private fun compare(r: AiInsights.Result, key: String) = r.compare.single { it.key == key }

    private fun link(r: AiInsights.Result, key: String) = r.links.single { it.key == key }

    // ---- this week against the week before and the baseline

    @Test fun `this week is set against the week before and the 30 day mean, with its direction`() {
        // The last 7 days sleep 360 minutes, the 23 before 420.
        val r = AiInsights.of(input(days(30) { i -> blank().copy(sleepMinutes = if (i >= 23) 360 else 420, recovery = 60, steps = 8_000) }))
        val sleep = compare(r, "sleep")
        assertEquals(360f, sleep.now!!, 0.01f)
        assertEquals(420f, sleep.prev!!, 0.01f)
        assertEquals(-60f, sleep.deltaPrev!!, 0.01f)
        assertEquals("down", sleep.dirPrev)
        // 30-day mean: (7 * 360 + 23 * 420) / 30 = 406.
        assertEquals(406f, sleep.base!!, 0.01f)
        assertEquals(-46f, sleep.deltaBase!!, 0.01f)
        assertEquals("down", sleep.dirBase)
        assertEquals(7, sleep.nNow)
        assertEquals(7, sleep.nPrev)
        // Nothing moved in Recovery or steps: flat.
        assertEquals("flat", compare(r, "rec").dirPrev)
        assertEquals("flat", compare(r, "steps").dirBase)
    }

    @Test fun `a rise is up, and a small wobble inside the band is flat`() {
        val r = AiInsights.of(input(days(30) { i -> blank().copy(recovery = if (i >= 23) 70 else 60, restingHr = if (i >= 23) 59 else 60, hrv = 50f) }))
        assertEquals("up", compare(r, "rec").dirPrev)
        assertEquals(10f, compare(r, "rec").deltaPrev!!, 0.01f)
        // One beat a minute is inside the band.
        assertEquals("flat", compare(r, "rhr").dirPrev)
        assertEquals("flat", compare(r, "hrv").dirPrev)
    }

    @Test fun `under three days there is no mean, no delta and no direction`() {
        val r = AiInsights.of(input(days(2) { blank().copy(sleepMinutes = 400) }))
        val sleep = compare(r, "sleep")
        assertNull(sleep.now)
        assertNull(sleep.deltaPrev)
        assertNull(sleep.dirPrev)
        assertEquals(2, sleep.nNow)
    }

    // ---- associations

    @Test fun `a perfect line has r of 1 or minus 1 and needs enough pairs`() {
        val up = (1..10).map { it.toDouble() to it * 2.0 }
        assertEquals(1.0, AiInsights.pearson(up)!!, 1e-9)
        assertEquals(-1.0, AiInsights.pearson(up.map { it.first to -it.second })!!, 1e-9)
        assertNull(AiInsights.pearson(up.take(4)))
        assertNull(AiInsights.pearson((1..10).map { it.toDouble() to 5.0 }))
        assertEquals("unknown", AiInsights.association(null))
        assertEquals("negligible", AiInsights.association(0.19))
        assertEquals("mild", AiInsights.association(-0.3))
        assertEquals("moderate", AiInsights.association(0.5))
        assertEquals("strong", AiInsights.association(-0.8))
    }

    @Test fun `confidence follows the count`() {
        assertEquals("insufficient", AiInsights.confidence(7))
        assertEquals("weak", AiInsights.confidence(8))
        assertEquals("weak", AiInsights.confidence(13))
        assertEquals("fair", AiInsights.confidence(14))
        assertEquals("good", AiInsights.confidence(25))
    }

    @Test fun `more sleep with a better Recovery is a positive link and heavy load with a worse next morning a negative one`() {
        // Sleep 360 to 540 in a cycle; the same morning's Recovery follows it. Load varies; the next Recovery falls as it rises.
        val loads = (0 until 30).map { i -> 30f + (i * 7 % 11) * 10f }
        val recoveryNext = loads.map { (90f - it / 2f).toInt() }
        val ds = days(30) { i ->
            val sleep = 360 + (i * 5 % 7) * 30
            blank().copy(sleepMinutes = sleep, recovery = if (i == 0) 70 else recoveryNext[i - 1], load = loads[i], hrv = 40f + sleep / 20f)
        }
        // Today's Recovery is the morning after the last day.
        val r = AiInsights.of(input(ds, todayRecovery = recoveryNext[29]))
        val negative = link(r, "load_next_rec")
        assertEquals(30, negative.n)
        assertEquals("good", negative.conf)
        assertTrue("r=${negative.r}", negative.r!! < -0.99)
        assertEquals("strong", negative.assoc)
        // The sleep to HRV link is exact: positive.
        val positive = link(r, "sleep_hrv")
        assertTrue("r=${positive.r}", positive.r!! > 0.99)
        assertEquals(30, positive.n)
    }

    @Test fun `with too few pairs a link has no figure and says insufficient`() {
        val r = AiInsights.of(input(days(6) { i -> blank().copy(sleepMinutes = 400 + i * 10, recovery = 50 + i * 3) }))
        val l = link(r, "sleep_rec")
        assertEquals(6, l.n)
        assertNull(l.r)
        assertEquals("insufficient", l.conf)
        assertEquals("unknown", l.assoc)
    }

    @Test fun `with eight to thirteen pairs it has a figure but is marked weak`() {
        val r = AiInsights.of(input(days(10) { i -> blank().copy(sleepMinutes = 400 + i * 10, recovery = 50 + i * 3) }))
        val l = link(r, "sleep_rec")
        assertEquals(10, l.n)
        assertEquals("weak", l.conf)
        assertTrue(l.r!! > 0.99)
    }

    @Test fun `a series that never changes has no figure`() {
        val r = AiInsights.of(input(days(20) { blank().copy(sleepMinutes = 420, recovery = 50 + it) }))
        val l = link(r, "sleep_rec")
        assertEquals(20, l.n)
        assertNull(l.r)
    }

    @Test fun `bedtime times are read as evening minutes, after midnight is later`() {
        assertEquals(23 * 60 + 10, AiInsights.bedMinutes("23:10"))
        assertEquals(24 * 60 + 30, AiInsights.bedMinutes("00:30"))
        assertTrue(AiInsights.bedMinutes("00:30")!! > AiInsights.bedMinutes("23:10")!!)
        assertNull(AiInsights.bedMinutes("z".repeat(450)))
        assertNull(AiInsights.bedMinutes("25:00"))
        assertNull(AiInsights.bedMinutes(null))
    }

    @Test fun `an irregular bedtime goes with a worse Recovery and a later one with more wake-ups`() {
        val bed = listOf("22:30", "23:30", "00:30", "23:00", "01:00", "22:45", "00:00", "23:15", "00:45", "22:40", "23:50", "01:15", "23:05", "00:20")
        val minutes = bed.map { AiInsights.bedMinutes(it)!! }
        val mean = minutes.average()
        val ds = days(30) { i ->
            // Only the last 14 days have a night; Recovery falls with how far the bedtime was from the mean.
            val k = i - 16
            blank().copy(recovery = if (k >= 0) (90 - abs(minutes[k] - mean) / 3).toInt() else 60 + i % 5)
        }
        val nights = bed.mapIndexed { k, b ->
            AiNight(AI_TODAY.minusDays((14 - k).toLong()), bedtime = b, asleepMinutes = 420, wakeUps = (AiInsights.bedMinutes(b)!! - 1300) / 20)
        }
        val r = AiInsights.of(input(ds, nights))
        val dev = link(r, "bed_dev_rec")
        assertEquals(14, dev.n)
        assertEquals("fair", dev.conf)
        assertTrue("r=${dev.r}", dev.r!! < -0.9)
        val late = link(r, "late_bed_wakeups")
        assertEquals(14, late.n)
        assertTrue("r=${late.r}", late.r!! > 0.9)
    }

    private fun abs(v: Double) = kotlin.math.abs(v)

    // ---- weekdays

    @Test fun `the best and worst weekday are found, with how big the difference is against the noise`() {
        // 2026-11-02 is a Monday; four weeks. Wednesdays 80, Sundays 20, the rest 50.
        val start = LocalDate.of(2026, 11, 2)
        val values = (0 until 28).map { i ->
            val d = start.plusDays(i.toLong())
            d to when (d.dayOfWeek.value) { 3 -> 80f; 7 -> 20f; else -> 50f }
        }
        val w = AiInsights.weekday("rec", values)!!
        assertEquals(3, w.hiDay)
        assertEquals(80f, w.hiAvg, 0.01f)
        assertEquals(4, w.hiN)
        assertEquals(7, w.loDay)
        assertEquals(20f, w.loAvg, 0.01f)
        assertEquals(60f, w.spread, 0.01f)
        assertEquals("strong", w.assoc)
        assertEquals(28, w.n)
        assertEquals("good", w.conf)
    }

    @Test fun `a flat week, or too few days, gives no weekday pattern`() {
        val start = LocalDate.of(2026, 11, 2)
        assertNull(AiInsights.weekday("rec", (0 until 28).map { start.plusDays(it.toLong()) to 50f }))
        assertNull(AiInsights.weekday("rec", (0 until 7).map { start.plusDays(it.toLong()) to it * 10f }))
    }

    @Test fun `a small difference between weekdays is called negligible`() {
        val start = LocalDate.of(2026, 11, 2)
        // Whole weeks alternate between 50 and 60, which is the same for every weekday; Thursdays are 1 point higher.
        val values = (0 until 28).map { i ->
            val d = start.plusDays(i.toLong())
            d to 50f + (i / 7 % 2) * 10f + if (d.dayOfWeek.value == 4) 1f else 0f
        }
        val w = AiInsights.weekday("rec", values)!!
        assertEquals(4, w.hiDay)
        assertEquals(1f, w.spread, 0.01f)
        assertEquals("negligible", w.assoc)
    }

    // ---- best and worst days

    @Test fun `the best and worst days name what came before them`() {
        val ds = days(30) { i ->
            when (i) {
                10 -> blank().copy(recovery = 95, sleepMinutes = 520, load = 20f, steps = 4_000)
                9 -> blank().copy(recovery = 70, load = 20f, steps = 4_000)
                21 -> blank().copy(recovery = 20, sleepMinutes = 300, load = 100f)
                20 -> blank().copy(recovery = 70, load = 120f, steps = 15_000)
                else -> blank().copy(recovery = 60 + i % 3, sleepMinutes = 420, load = 40f)
            }
        }
        val workouts = listOf(AiWorkout(ds[20].date, "Running", 50, load = 90))
        val r = AiInsights.of(input(ds, workouts = workouts))
        val best = r.bestDays.first()
        assertEquals(ds[10].date, best.date)
        assertEquals(95, best.recovery)
        assertEquals(20f, best.prevLoad!!, 0.01f)
        assertTrue("under the mean load", best.prevLoadPct!! < 0)
        assertTrue(best.sleepVs!! > 0)
        assertEquals(3, r.bestDays.size)
        val worst = r.worstDays.first()
        assertEquals(ds[21].date, worst.date)
        assertEquals(20, worst.recovery)
        assertEquals(120f, worst.prevLoad!!, 0.01f)
        assertTrue("over the mean load", worst.prevLoadPct!! > 100)
        assertEquals(15_000, worst.prevSteps)
        assertEquals("Running 50min", worst.prevWorkout)
        assertTrue(worst.sleepVs!! < 0)
        // No day is both.
        assertTrue(r.bestDays.map { it.date }.intersect(r.worstDays.map { it.date }.toSet()).isEmpty())
    }

    @Test fun `no best and worst days when Recovery barely moved`() {
        val r = AiInsights.of(input(days(30) { blank().copy(recovery = 60 + it % 4) }))
        assertTrue(r.bestDays.isEmpty())
        assertTrue(r.worstDays.isEmpty())
    }

    // ---- streaks

    @Test fun `a run of short nights and the longest one are counted, a missing night breaks the run`() {
        // Need 480, so a night is short under 420. Earlier a run of 4, then a night with no data, then 3 short ones up to yesterday.
        val ds = days(30) { i ->
            when (i) {
                in 10..13 -> blank().copy(sleepMinutes = 380)
                in 27..29 -> blank().copy(sleepMinutes = 390)
                26 -> blank()
                else -> blank().copy(sleepMinutes = 450)
            }
        }
        val s = AiInsights.of(input(ds)).shortNights!!
        assertEquals(3, s.now)
        assertEquals(4, s.max)
        assertEquals(7, s.count)
        assertEquals(420, s.limit)
    }

    @Test fun `no short nights now means a current run of zero`() {
        val s = AiInsights.of(input(days(30) { blank().copy(sleepMinutes = if (it == 5) 300 else 460) })).shortNights!!
        assertEquals(0, s.now)
        assertEquals(1, s.max)
    }

    @Test fun `high load days in a row are counted against 125 percent of the mean`() {
        // 28 days of 40 and 2 of 100: mean 44, so the limit is 55.
        val s = AiInsights.of(input(days(30) { blank().copy(load = if (it >= 28) 100f else 40f) })).highLoad!!
        assertEquals(55, s.limit)
        assertEquals(2, s.now)
        assertEquals(2, s.max)
        assertEquals(2, s.count)
    }

    @Test fun `days in the red in a row are counted from 34`() {
        val s = AiInsights.of(input(days(30) { blank().copy(recovery = if (it in 25..28) 30 else 70) })).lowRecovery!!
        assertEquals(0, s.now)
        assertEquals(4, s.max)
        assertEquals(34, s.limit)
    }

    // ---- sleep debt

    @Test fun `a week of short sleep grows the running debt and the weekly shortfall`() {
        // Need 480. The week before 420 a night (60 short), this week 360 (120 short).
        val r = AiInsights.of(input(days(30) { i -> blank().copy(sleepMinutes = if (i >= 23) 360 else 420) }, debt = 240))
        val d = r.debt!!
        assertEquals(240, d.appDebtMin)
        assertEquals(120, d.avgShort7)
        assertEquals(60, d.avgShortPrev7)
        assertEquals("growing", d.trend)
        assertEquals(7, d.nights7)
        // 14 nights: 7 at 60 short then 7 at 120 short.
        assertEquals(7 * 60 + 7 * 120, d.runningNow)
        assertEquals(7 * 60, d.running7dAgo)
    }

    @Test fun `sleeping over the need pays the running debt down and never below zero`() {
        val d = AiInsights.of(input(days(30) { i -> blank().copy(sleepMinutes = if (i >= 23) 600 else 420) })).debt!!
        assertEquals("shrinking", d.trend)
        assertEquals(0, d.runningNow)
        assertEquals(-120, d.avgShort7)
    }

    // ---- workouts

    @Test fun `each workout day is set against the next morning, and against days without one`() {
        // Recovery 70 every morning; the morning after each workout 55.
        val workoutDays = listOf(20, 15, 10, 5)
        val ds = days(30) { i -> blank().copy(recovery = if ((i - 1) in workoutDays) 55 else 70, load = if (i in workoutDays) 90f else 20f, hrv = 50f) }
        val workouts = workoutDays.map { AiWorkout(ds[it].date, "Running", 40, load = 90) }
        val w = AiInsights.of(input(ds, workouts = workouts)).workouts!!
        assertEquals(4, w.items.size)
        val first = w.items.first()
        assertEquals(ds[5].date, first.date)
        assertEquals(70, first.rec)
        assertEquals(55, first.recNext)
        assertEquals(-15, first.recDelta)
        assertEquals(90, first.load)
        assertEquals(-15f, w.avgDelta!!, 0.01f)
        assertEquals(4, w.n)
        // After the days without a workout Recovery stays level or comes back up from the dip.
        assertNotNull(w.restAvgDelta)
        assertTrue(w.restAvgDelta!! > w.avgDelta!!)
    }

    @Test fun `the last day's workout looks to today's Recovery, and a workout today has no next morning yet`() {
        val ds = days(30) { blank().copy(recovery = 70) }
        val workouts = listOf(
            AiWorkout(AI_TODAY.minusDays(1), "Cycling", 60, load = 70),
            AiWorkout(AI_TODAY, "Running", 30, load = 40),
        )
        val w = AiInsights.of(input(ds, workouts = workouts, todayRecovery = 52)).workouts!!
        assertEquals(1, w.items.size)
        assertEquals(52, w.items.single().recNext)
        assertEquals(-18, w.items.single().recDelta)
        // One workout is too few for an average.
        assertNull(w.avgDelta)
        assertEquals(1, w.n)
    }

    @Test fun `two workouts on a day are one day, named by the longer`() {
        val ds = days(30) { blank().copy(recovery = 70) }
        val d = ds[10].date
        val w = AiInsights.of(input(ds, workouts = listOf(AiWorkout(d, "Walking", 20, load = 10), AiWorkout(d, "Running", 40, load = 60)))).workouts!!
        assertEquals(1, w.items.size)
        assertEquals("Running", w.items.single().sport)
        assertEquals(60, w.items.single().minutes)
        assertEquals(70, w.items.single().load)
    }

    @Test fun `no workouts, no workout section`() {
        assertNull(AiInsights.of(input(days(30) { blank().copy(recovery = 60) })).workouts)
    }

    // ---- the JSON

    @Test fun `the JSON holds the sections, only whitelisted keys, no nulls and rounded figures`() {
        val json = AiInsights.toJson(AiInsights.of(aiInput()))
        for (k in listOf("periods", "links", "weekday", "streaks", "debt", "workout_next_day")) assertTrue(k, json.has(k))
        val keys = linkedSetOf<String>()
        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> v.keys().forEach { k -> keys += k; walk(v.get(k)) }
                is JSONArray -> for (i in 0 until v.length()) walk(v.get(i))
            }
        }
        walk(json)
        assertEquals(emptySet<String>(), keys - AiInsights.KEYS)
        assertFalse(json.toString().contains("null"))
        val sleep = json.getJSONObject("periods").getJSONObject("sleep")
        for (k in listOf("now", "prev", "base", "d_prev", "d_base", "dir", "dir_base", "n", "n_prev")) assertTrue(k, sleep.has(k))
        val l = json.getJSONArray("links").getJSONObject(0)
        assertTrue(l.has("k") && l.has("n") && l.has("assoc") && l.has("conf"))
    }

    @Test fun `an input with no days or nights still gives a result and its JSON`() {
        val r = AiInsights.of(aiInput(days = 0, nights = 0, workouts = 0))
        assertTrue(r.compare.isEmpty())
        assertTrue(r.links.isEmpty())
        val json = AiInsights.toJson(r)
        assertEquals(0, json.getJSONObject("periods").length())
    }

    @Test fun `an unreadable bedtime is skipped, never a crash`() {
        val ds = days(14) { blank().copy(recovery = 60 + it) }
        val nights = (0 until 14).map { AiNight(AI_TODAY.minusDays((14 - it).toLong()), bedtime = "z".repeat(450), asleepMinutes = 400) }
        val r = AiInsights.of(input(ds, nights))
        assertEquals(0, link(r, "bed_dev_rec").n)
        assertEquals("insufficient", link(r, "late_bed_wakeups").conf)
    }
}
