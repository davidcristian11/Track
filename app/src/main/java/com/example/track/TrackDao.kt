package com.example.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM food_logs WHERE dayKey = :dayKey ORDER BY createdAt ASC, id ASC")
    fun observeFoodLogs(dayKey: String): Flow<List<LoggedFoodEntity>>

    @Insert
    suspend fun insertFood(food: LoggedFoodEntity): Long

    @Query("SELECT * FROM workouts WHERE dayKey = :dayKey ORDER BY createdAt DESC, id DESC")
    fun observeWorkouts(dayKey: String): Flow<List<WorkoutEntity>>

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    fun observeDailyState(dayKey: String): Flow<DailyTrackingStateEntity?>

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    suspend fun dailyState(dayKey: String): DailyTrackingStateEntity?

    @Upsert
    suspend fun upsertDailyState(state: DailyTrackingStateEntity)
}
