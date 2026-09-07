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

    @Query("SELECT * FROM food_logs WHERE id = :id AND dayKey = :dayKey")
    suspend fun food(dayKey: String, id: Long): LoggedFoodEntity?

    @Query("""UPDATE food_logs SET amount = :amount, meal = :meal, calories = :calories,
        proteinGrams = :protein, carbsGrams = :carbs, fatGrams = :fat
        WHERE id = :id AND dayKey = :dayKey AND id > 0""")
    suspend fun updateFood(dayKey: String, id: Long, amount: Int, meal: String,
        calories: Int, protein: Float, carbs: Float, fat: Float)

    @Query("DELETE FROM food_logs WHERE id = :id AND dayKey = :dayKey AND id > 0")
    suspend fun deleteFood(dayKey: String, id: Long)

    @Query("SELECT * FROM workouts WHERE id = :id AND dayKey = :dayKey")
    suspend fun workout(dayKey: String, id: Long): WorkoutEntity?

    @Query("""UPDATE workouts SET activityType = :type, durationMinutes = :duration, notes = :notes
        WHERE id = :id AND dayKey = :dayKey AND id > 0""")
    suspend fun updateWorkout(dayKey: String, id: Long, type: String, duration: Int, notes: String)

    @Query("DELETE FROM workouts WHERE id = :id AND dayKey = :dayKey AND id > 0")
    suspend fun deleteWorkout(dayKey: String, id: Long)

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    fun observeDailyState(dayKey: String): Flow<DailyTrackingStateEntity?>

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    suspend fun dailyState(dayKey: String): DailyTrackingStateEntity?

    @Upsert
    suspend fun upsertDailyState(state: DailyTrackingStateEntity)
}
