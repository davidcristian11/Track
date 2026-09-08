package com.example.track

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TrackRepository(private val database: TrackDatabase) {
    private val dao = database.trackDao()

    fun observeWeightEntries(): Flow<List<WeightEntry>> = dao.observeWeightEntries().map { rows ->
        rows.map { WeightEntry(it.dayKey.toTrackDay(), it.weightKg) }
    }

    suspend fun weightForDay(day: LocalDate): WeightEntry? = dao.weightForDay(day.toDayKey())?.let {
        WeightEntry(day, it.weightKg)
    }

    suspend fun logWeight(day: LocalDate, weightKg: Double) {
        require(isValidWeight(weightKg))
        dao.upsertWeight(WeightEntryEntity(day.toDayKey(), weightKg, System.currentTimeMillis()))
    }

    fun observeTracking(dayKey: String): Flow<TrackSessionData> = combine(
        dao.observeFoodLogs(dayKey), dao.observeWorkouts(dayKey), dao.observeDailyState(dayKey),
    ) { foods, workouts, daily -> trackingSnapshot(dayKey.toTrackDay(), foods, workouts, daily) }

    suspend fun addFood(dayKey: String, meal: MealContext, food: FoodDefinition, amount: Int) {
        val snapshot = LoggedFood.snapshot(0, meal, food, amount)
        dao.insertFood(snapshot.toEntity(dayKey, System.currentTimeMillis()))
    }

    suspend fun addWorkout(input: WorkoutInput) {
        require(isValidWorkoutDuration(input.durationMinutes))
        val workout = LoggedWorkout(0, input.type, input.durationMinutes, input.notes.trim(),
            estimateWorkoutCalories(input.type, input.durationMinutes), input.startTime)
        dao.insertWorkout(workout.toEntity(input.day.toDayKey(), System.currentTimeMillis()))
    }

    suspend fun food(dayKey: String, id: Long): LoggedFood? = dao.food(dayKey, id)?.toLoggedFood()

    suspend fun updateFood(dayKey: String, original: LoggedFood, amount: Int, meal: MealContext) {
        val corrected = original.corrected(amount, meal)
        val nutrition = corrected.nutrition
        dao.updateFood(dayKey, original.id, amount, meal.name, nutrition.calories,
            nutrition.proteinGrams, nutrition.carbsGrams, nutrition.fatGrams)
    }

    suspend fun deleteFood(dayKey: String, id: Long) = dao.deleteFood(dayKey, id)

    suspend fun workout(dayKey: String, id: Long): LoggedWorkout? = dao.workout(dayKey, id)?.toLoggedWorkout()

    suspend fun updateWorkout(originalDayKey: String, id: Long, input: WorkoutInput) {
        require(isValidWorkoutDuration(input.durationMinutes))
        dao.updateWorkout(originalDayKey, id, input.day.toDayKey(), input.type.name,
            input.durationMinutes, input.notes.trim(), input.startTime,
            estimateWorkoutCalories(input.type, input.durationMinutes))
    }

    suspend fun deleteWorkout(dayKey: String, id: Long) = dao.deleteWorkout(dayKey, id)

    // Read-modify-write inside Room's transaction, not from a potentially stale UI
    // Flow value. This also prevents Water and Creatine from overwriting one another.
    suspend fun addWater(dayKey: String, milliliters: Int = 250) {
        require(milliliters > 0)
        adjustWater(dayKey, milliliters)
    }

    suspend fun adjustWater(dayKey: String, milliliters: Int) {
        database.withTransaction {
            val current = dailyStateOrDefault(dayKey)
            dao.upsertDailyState(current.copy(
                waterMl = (current.waterMl.toLong() + milliliters).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            ))
        }
    }

    suspend fun toggleCreatine(dayKey: String) {
        database.withTransaction {
            val current = dailyStateOrDefault(dayKey)
            dao.upsertDailyState(current.copy(creatineCompleted = !current.creatineCompleted))
        }
    }

    private suspend fun dailyStateOrDefault(dayKey: String): DailyTrackingStateEntity {
        val baseline = TrackDemoBaseline.forDay(dayKey.toTrackDay())
        return dao.dailyState(dayKey)
            ?: DailyTrackingStateEntity(dayKey, baseline.waterMl, baseline.creatineCompleted)
    }
}

internal fun trackingSnapshot(
    day: LocalDate,
    foods: List<LoggedFoodEntity>, workouts: List<WorkoutEntity>, daily: DailyTrackingStateEntity?,
) = TrackSessionData(
    day = day,
    foods = foods.map { it.toLoggedFood() },
    workouts = workouts.map { it.toLoggedWorkout() } + TrackDemoBaseline.forDay(day).workouts,
    waterMl = daily?.waterMl ?: TrackDemoBaseline.forDay(day).waterMl,
    creatineCompleted = daily?.creatineCompleted ?: TrackDemoBaseline.forDay(day).creatineCompleted,
)

internal fun LoggedFood.toEntity(dayKey: String, createdAt: Long) = LoggedFoodEntity(
    id, dayKey, meal.name, catalogFoodId, name, brand, amount, unit.symbol,
    nutrition.calories, nutrition.proteinGrams, nutrition.carbsGrams, nutrition.fatGrams, createdAt,
)

internal fun LoggedFoodEntity.toLoggedFood() = LoggedFood(
    id, MealContext.valueOf(meal), catalogFoodId, name, brand, amount,
    FoodUnit.entries.first { it.symbol == unit },
    NutritionTotals(calories, proteinGrams, carbsGrams, fatGrams),
)

internal fun LoggedWorkout.toEntity(dayKey: String, createdAt: Long) = WorkoutEntity(
    id, dayKey, type.name, durationMinutes, notes, estimatedCalories, startTime, createdAt,
)

internal fun WorkoutEntity.toLoggedWorkout() = LoggedWorkout(
    id, WorkoutType.valueOf(activityType), durationMinutes, notes, estimatedCalories, startTime,
)
