package org.openstrap.app.data.db

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.testing.MigrationHarness
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A version 28 database opens at 29 with its daily energy rows intact and their false steps not yet worked out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FalseStepsMigrationTest {
    @get:Rule val migrations = MigrationHarness()

    @Test fun `old rows keep their kcal and have no false steps yet`() = runTest {
        val day = LocalDate.of(2026, 1, 15)
        val room = migrations.open(migrations.create(28) {
            execSQL(
                "INSERT INTO daily_energy (day, updatedAt, totalKcal, activeKcal, bmrKcal, minutesWithData, partial, workoutKcal, walkingKcal, hourly) " +
                    "VALUES (${org.openstrap.app.data.DayKey.of(day)}, 5, 2100.0, 400.0, 1650.0, 1440, 0, 120.0, 200.0, '1.0')",
            )
        })
        assertEquals(29, room.openHelper.readableDatabase.version)
        val dao = room.healthDao()
        val row = dao.dailyCaloriesOnce(day, day).single()
        assertEquals(2100f, row.totalKcal)
        assertEquals("", row.falseSteps)
        assertTrue(dao.falseStepsDaysOnce().isEmpty())

        // Worked out, the day is listed with its hours; the same day again replaces it.
        dao.insertDailyCalories(listOf(row.copy(falseSteps = List(24) { if (it == 10) "360" else "0" }.joinToString(","))))
        assertEquals(listOf(day), dao.falseStepsDaysOnce())
        assertEquals(listOf(day), dao.falseStepsOnce(day, day).map { it.day })
        assertEquals(emptyList<FalseStepsDay>(), dao.falseStepsOnce(day.plusDays(1), day.plusDays(2)))
    }
}
