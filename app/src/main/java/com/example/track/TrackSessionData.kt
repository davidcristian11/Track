package com.example.track

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.roundToInt

data class LoggedFood(
    val id: Long,
    val meal: MealContext,
    val catalogFoodId: String?,
    val name: String,
    val brand: String?,
    val amount: Int,
    val unit: FoodUnit,
    val nutrition: NutritionTotals,
) {
    // Always scale from the snapshot loaded when the editor opened.
    fun corrected(amount: Int, meal: MealContext): LoggedFood {
        require(amount in 1..MaxFoodAmount)
        require(this.amount > 0)
        val factor = amount.toDouble() / this.amount
        fun macro(value: Float) = (value * factor * 10).roundToInt() / 10f
        return copy(amount = amount, meal = meal, nutrition = NutritionTotals(
            (nutrition.calories * factor).roundToInt(),
            macro(nutrition.proteinGrams), macro(nutrition.carbsGrams), macro(nutrition.fatGrams),
        ))
    }

    companion object {
        fun snapshot(id: Long, meal: MealContext, food: FoodDefinition, amount: Int): LoggedFood {
            require(amount in 1..MaxFoodAmount)
            return LoggedFood(id, meal, food.id, food.name, food.brand, amount, food.unit, food.nutritionFor(amount))
        }
    }
}

enum class WorkoutType(val label: String, val shortLabel: String, val caloriesPerMinute: Int) {
    Strength("Strength Training", "Strength", 6),
    Calisthenics("Calisthenics", "Calisthenics", 7),
    Running("Running", "Running", 10),
    Walking("Walking", "Walking", 4),
    Cycling("Cycling", "Cycling", 7),
    Swimming("Swimming", "Swimming", 8),
    Hiking("Hiking", "Hiking", 6),
    Basketball("Basketball", "Basketball", 8),
    Football("Football / Soccer", "Football", 9),
    Tennis("Tennis", "Tennis", 7),
    Rowing("Rowing", "Rowing", 8),
    Elliptical("Elliptical", "Elliptical", 7),
    StairClimbing("Stair Climbing", "Stairs", 9),
    Yoga("Yoga", "Yoga", 3),
    Mobility("Mobility / Stretching", "Mobility", 3),
    Other("Other", "Other", 5),
}

data class LoggedWorkout(
    val id: Long,
    val type: WorkoutType,
    val durationMinutes: Int,
    val notes: String,
    val estimatedCalories: Int = estimateWorkoutCalories(type, durationMinutes),
    val startTime: String = "18:10",
)

// Immutable display value derived from Room + TrackDemoBaseline, not saveable state.
data class TrackSessionData(
    val day: LocalDate,
    val foods: List<LoggedFood> = emptyList(),
    val workouts: List<LoggedWorkout> = TrackDemoBaseline.forDay(day).workouts,
    val waterMl: Int = TrackDemoBaseline.forDay(day).waterMl,
    val creatineCompleted: Boolean = TrackDemoBaseline.forDay(day).creatineCompleted,
) {
    val baseline: TrackDayBaseline get() = TrackDemoBaseline.forDay(day)
    val nutrition: NutritionTotals
        get() = foods.fold(baseline.nutrition) { total, entry -> total + entry.nutrition }

    // Only the reference screen uses this demo weekly count. Ordinary days show
    // workouts.size as a daily count until real weekly aggregation is implemented.
    val workoutsThisWeek: Int get() = baseline.workoutsThisWeek + workouts.count { it.id != 0L }

    // Pure value transformations retained for previews/calculation tests. Runtime
    // mutations go through TrackRepository; Compose never owns a mutable copy.
    fun addFood(meal: MealContext, food: FoodDefinition, amount: Int): TrackSessionData {
        require(amount in 1..MaxFoodAmount)
        val id = (foods.maxOfOrNull { it.id } ?: 0L) + 1
        return copy(foods = foods + LoggedFood.snapshot(id, meal, food, amount))
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
