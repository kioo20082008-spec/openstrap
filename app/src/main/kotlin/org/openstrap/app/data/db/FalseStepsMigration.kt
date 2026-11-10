package org.openstrap.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 28 to 29: each day's false steps ([DailyCaloriesRow.falseSteps]) are kept beside its calories, so the step
 * history, your usual steps, Habit Age and the rewards count the same steps as the day's page. One column; existing
 * rows get an empty one, which [org.openstrap.app.data.DailyCaloriesHistory] reads as "not worked out yet" and fills
 * after the next sync. No row is changed. A named object like [IndicesMigration], so it can be renumbered on its own.
 * The SQL is Room's own, from the schema exported for this version.
 */
@Suppress("MagicNumber") // The versions it runs between are what the migration is.
object FalseStepsMigration : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `daily_energy` ADD COLUMN `falseSteps` TEXT NOT NULL DEFAULT ''")
    }
}
