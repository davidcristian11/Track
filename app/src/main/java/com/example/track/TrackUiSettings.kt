package com.example.track

import java.util.Locale

enum class TodayModule { Nutrition, Water, Steps, Sleep, Workout, Creatine, Weight }

data class TodayCustomization(
    val showNutrition: Boolean = true,
    val showWater: Boolean = true,
    val showSteps: Boolean = true,
    val showSleep: Boolean = true,
    val showWorkout: Boolean = true,
    val showCreatine: Boolean = true,
    val showWeight: Boolean = false,
)

data class TrackGoals(
    val calories: Int = 2_200,
    val proteinGrams: Int = 150,
    val carbsGrams: Int = 250,
    val fatGrams: Int = 70,
    val waterLiters: Float = 2.5f,
    val steps: Int = 10_000,
    val targetWeightKg: Float = 68f,
)

data class TrackUiSettings(
    val today: TodayCustomization = TodayCustomization(),
    val goals: TrackGoals = TrackGoals(),
)

internal fun progressFraction(current: Int, target: Int): Float =
    if (target <= 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)

internal fun progressFraction(current: Float, target: Float): Float =
    if (!current.isFinite() || !target.isFinite() || target <= 0f) {
        0f
    } else {
        (current / target).coerceIn(0f, 1f)
    }

internal fun remainingCalories(consumed: Int, target: Int): Int =
    (target - consumed).coerceAtLeast(0)

internal fun formatWholeNumber(value: Int): String =
    String.format(Locale.US, "%,d", value)

internal fun formatDecimal(value: Float): String =
    String.format(Locale.US, "%.1f", value)

internal fun formatCompactSteps(value: Int): String = when {
    value % 1_000 == 0 -> "${value / 1_000}k"
    else -> formatWholeNumber(value)
}
