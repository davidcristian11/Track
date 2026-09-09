package com.example.track

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Logs are snapshots: rendering never joins back to the mutable search catalog.
@Entity(tableName = "food_logs", indices = [Index("dayKey")])
data class LoggedFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val meal: String,
    val catalogFoodId: String?,
    val name: String,
    val brand: String?,
    val amount: Int,
    val unit: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val createdAt: Long,
)

@Entity(tableName = "workouts", indices = [Index("dayKey")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val activityType: String,
    val durationMinutes: Int,
    val notes: String,
    val estimatedCalories: Int,
    val startTime: String,
    val createdAt: Long,
)

@Entity(tableName = "daily_tracking_state")
data class DailyTrackingStateEntity(
    @PrimaryKey val dayKey: String,
    val waterMl: Int,
    val creatineCompleted: Boolean,
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
)

// One current measurement per local calendar day. Re-logging replaces this row.
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val dayKey: String,
    val weightKg: Double,
    val updatedAt: Long,
)
