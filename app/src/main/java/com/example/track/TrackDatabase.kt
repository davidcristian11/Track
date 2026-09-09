package com.example.track

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LoggedFoodEntity::class, WorkoutEntity::class, DailyTrackingStateEntity::class, WeightEntryEntity::class, BodyMeasurementEntity::class],
    version = 4,
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `body_measurements` (
            `dayKey` TEXT NOT NULL, `waistCm` REAL, `chestCm` REAL, `hipsCm` REAL,
            `armCm` REAL, `thighCm` REAL, `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`dayKey`))""")
    }
}
