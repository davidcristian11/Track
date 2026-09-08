package com.example.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM weight_entries ORDER BY dayKey ASC")
    fun observeWeightEntries(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE dayKey BETWEEN :startDay AND :endDay ORDER BY dayKey ASC")
    fun observeWeightEntries(startDay: String, endDay: String): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE dayKey = :dayKey")
    suspend fun weightForDay(dayKey: String): WeightEntryEntity?

    @Upsert
    suspend fun upsertWeight(entry: WeightEntryEntity)

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

    // One atomic UPDATE retains the stable ID/createdAt while moving the indexed dayKey.
    @Query("""UPDATE workouts SET dayKey = :newDayKey, activityType = :type,
        durationMinutes = :duration, notes = :notes, startTime = :startTime, estimatedCalories = :calories
        WHERE id = :id AND dayKey = :originalDayKey AND id > 0""")
    suspend fun updateWorkout(originalDayKey: String, id: Long, newDayKey: String,
        type: String, duration: Int, notes: String, startTime: String, calories: Int)

    @Query("DELETE FROM workouts WHERE id = :id AND dayKey = :dayKey AND id > 0")
    suspend fun deleteWorkout(dayKey: String, id: Long)

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    fun observeDailyState(dayKey: String): Flow<DailyTrackingStateEntity?>

    @Query("SELECT * FROM daily_tracking_state WHERE dayKey = :dayKey")
    suspend fun dailyState(dayKey: String): DailyTrackingStateEntity?

    @Upsert
    suspend fun upsertDailyState(state: DailyTrackingStateEntity)
}
