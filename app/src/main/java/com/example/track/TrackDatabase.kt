package com.example.track

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LoggedFoodEntity::class, WorkoutEntity::class, DailyTrackingStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
