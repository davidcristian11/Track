package com.example.track

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LoggedFoodEntity::class, WorkoutEntity::class, DailyTrackingStateEntity::class, WeightEntryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `weight_entries` (
            `dayKey` TEXT NOT NULL, `weightKg` REAL NOT NULL, `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`dayKey`))""")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_tracking_state ADD COLUMN steps INTEGER")
        db.execSQL("ALTER TABLE daily_tracking_state ADD COLUMN sleepMinutes INTEGER")
    }
}
