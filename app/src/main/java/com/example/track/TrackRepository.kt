package com.example.track

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TrackRepository(private val database: TrackDatabase) {
    private val dao = database.trackDao()

    fun observeProgressPhotos(): Flow<List<ProgressPhoto>> = dao.observeProgressPhotos().map { rows ->
        rows.map { ProgressPhoto(it.id, it.dayKey.toTrackDay(), it.localFileName,
            ProgressPhotoSource.parse(it.source), it.createdAt) }
    }

    suspend fun insertProgressPhoto(day: LocalDate, fileName: String, source: ProgressPhotoSource): Long {
        require(isSafePhotoFileName(fileName))
        return dao.insertProgressPhoto(ProgressPhotoEntity(dayKey = day.toDayKey(),
            localFileName = fileName, source = source.name, createdAt = System.currentTimeMillis()))
    }

    suspend fun progressPhoto(id: Long): ProgressPhotoEntity? = dao.progressPhoto(id)
    suspend fun deleteProgressPhoto(id: Long) = dao.deleteProgressPhoto(id)

    fun observeMeasurements(): Flow<List<BodyMeasurement>> = dao.observeMeasurements().map { rows ->
        rows.map { it.toBodyMeasurement() }
    }

    suspend fun measurementForDay(day: LocalDate): BodyMeasurement? =
        dao.measurementForDay(day.toDayKey())?.toBodyMeasurement()

    // The destination check and move are atomic. A stale editor cannot recreate a deleted row.
    suspend fun saveMeasurements(entry: BodyMeasurement, originalDay: LocalDate? = null): MeasurementSaveResult {
        if (!entry.values.isValid) return MeasurementSaveResult.Invalid
        return database.withTransaction {
            if (originalDay != null && dao.measurementForDay(originalDay.toDayKey()) == null) {
                return@withTransaction MeasurementSaveResult.MissingEntry
            }
            if (entry.values.isEmpty) {
                dao.deleteMeasurementDay((originalDay ?: entry.day).toDayKey())
                return@withTransaction MeasurementSaveResult.Saved
            }
            if (originalDay != entry.day && dao.measurementForDay(entry.day.toDayKey()) != null) {
                return@withTransaction MeasurementSaveResult.DateOccupied
            }
            val values = entry.values
            dao.upsertMeasurement(BodyMeasurementEntity(entry.day.toDayKey(), values.waistCm, values.chestCm,
                values.hipsCm, values.armCm, values.thighCm, System.currentTimeMillis()))
            if (originalDay != null && originalDay != entry.day) dao.deleteMeasurementDay(originalDay.toDayKey())
            MeasurementSaveResult.Saved
        }
    }

    suspend fun deleteMeasurementsForDay(day: LocalDate) = dao.deleteMeasurementDay(day.toDayKey())

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

    suspend fun setSteps(day: LocalDate, steps: Int?) {
        require(isValidSteps(steps))
        database.withTransaction {
            val current = dailyStateOrDefault(day.toDayKey())
            dao.upsertDailyState(current.copy(steps = steps))
        }
    }

    suspend fun setSleep(day: LocalDate, sleepMinutes: Int?) {
        require(isValidSleepMinutes(sleepMinutes))
        database.withTransaction {
            val current = dailyStateOrDefault(day.toDayKey())
            dao.upsertDailyState(current.copy(sleepMinutes = sleepMinutes))
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
    steps = daily?.steps,
    sleepMinutes = daily?.sleepMinutes,
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

internal fun BodyMeasurementEntity.toBodyMeasurement() = BodyMeasurement(dayKey.toTrackDay(),
    BodyMeasurements(waistCm, chestCm, hipsCm, armCm, thighCm))
