package com.example.track

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal

data class LoggedFood(
    val id: Long,
    val meal: MealContext,
    val food: FoodDefinition,
    val amountGrams: Int,
) {
    val nutrition: NutritionTotals get() = food.nutritionFor(amountGrams)
}

enum class WorkoutType(val label: String, val shortLabel: String) {
    Strength("Strength Training", "Strength"),
    Running("Running", "Running"),
    Cycling("Cycling", "Cycling"),
    Walking("Walking", "Walking"),
}

// The existing form uses a fixed demo estimate and a read-only 06:10 PM start time.
const val WorkoutCalorieEstimate = 280

data class LoggedWorkout(
    val id: Long,
    val type: WorkoutType,
    val durationMinutes: Int,
    val notes: String,
    val estimatedCalories: Int = WorkoutCalorieEstimate,
    val startTime: String = "18:10",
)

// Stitch's approved day aggregate is intentionally separate from its visible
// 473-kcal Breakfast snapshot. Only session additions count as new deltas.
private val InitialDailyNutrition = NutritionTotals(1_450, 90f, 180f, 45f)

data class TrackSessionData(
    val foods: List<LoggedFood> = emptyList(),
    val workouts: List<LoggedWorkout> = listOf(
        LoggedWorkout(0, WorkoutType.Strength, 45, "Upper body"),
    ),
    val waterMl: Int = 1_500,
    val creatineCompleted: Boolean = true,
) {
    val nutrition: NutritionTotals
        get() = foods.fold(InitialDailyNutrition) { total, entry -> total + entry.nutrition }

    // The approved weekly baseline already includes the initial workout (ID 0).
    val workoutsThisWeek: Int get() = 3 + workouts.count { it.id != 0L }

    fun addFood(meal: MealContext, food: FoodDefinition, amountGrams: Int): TrackSessionData {
        require(amountGrams in 1..MaxFoodAmountGrams)
        val id = (foods.maxOfOrNull { it.id } ?: 0L) + 1
        return copy(foods = foods + LoggedFood(id, meal, food, amountGrams))
    }

    fun addWorkout(type: WorkoutType, durationMinutes: Int, notes: String): TrackSessionData {
        require(durationMinutes in 1..1_440)
        val id = (workouts.maxOfOrNull { it.id } ?: 0L) + 1
        val workout = LoggedWorkout(id, type, durationMinutes, notes.trim())
        return copy(workouts = listOf(workout) + workouts)
    }

    fun addWater(milliliters: Int = 250): TrackSessionData {
        require(milliliters > 0)
        return copy(waterMl = (waterMl.toLong() + milliliters).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun toggleCreatine(): TrackSessionData = copy(creatineCompleted = !creatineCompleted)
}

internal fun waterProgress(waterMl: Int, targetLiters: Float): Float =
    progressFraction(waterMl.toFloat(), targetLiters * 1_000f)

internal fun formatWaterLiters(waterMl: Int): String =
    BigDecimal.valueOf(waterMl.toLong(), 3).stripTrailingZeros().toPlainString()

// Saved-instance state only: no disk store, day history, or process-restart guarantee.
val TrackSessionDataSaver: Saver<TrackSessionData, Any> = listSaver(
    save = { session ->
        listOf(
            session.waterMl,
            session.creatineCompleted,
            session.foods.map { listOf(it.id, it.meal.name, it.food.id, it.amountGrams) },
            session.workouts.map {
                listOf(it.id, it.type.name, it.durationMinutes, it.notes, it.estimatedCalories, it.startTime)
            },
        )
    },
    restore = { values ->
        TrackSessionData(
            waterMl = values[0] as Int,
            creatineCompleted = values[1] as Boolean,
            foods = (values[2] as List<*>).map { saved ->
                val row = saved as List<*>
                LoggedFood(
                    id = row[0] as Long,
                    meal = MealContext.valueOf(row[1] as String),
                    food = LocalFoodCatalog.first { it.id == row[2] },
                    amountGrams = row[3] as Int,
                )
            },
            workouts = (values[3] as List<*>).map { saved ->
                val row = saved as List<*>
                LoggedWorkout(
                    id = row[0] as Long,
                    type = WorkoutType.valueOf(row[1] as String),
                    durationMinutes = row[2] as Int,
                    notes = row[3] as String,
                    estimatedCalories = row[4] as Int,
                    startTime = row[5] as String,
                )
            },
        )
    },
)
