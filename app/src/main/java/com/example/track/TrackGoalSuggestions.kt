package com.example.track

import kotlin.math.roundToInt

enum class WeightGoalDirection(val label: String) {
    Lose("Lose weight"),
    Maintain("Maintain weight"),
    Gain("Gain weight"),
}

data class SuggestedNutritionTargets(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

private const val WeightGoalToleranceKg = 1.0
private const val CaloriesPerKilogram = 30.0
private const val GoalCalorieAdjustment = 300
private const val MinimumSuggestedCalories = 1_200

internal fun weightGoalDirection(
    currentWeightKg: Double,
    targetWeightKg: Double,
): WeightGoalDirection? {
    if (!currentWeightKg.isFinite() || !targetWeightKg.isFinite() ||
        currentWeightKg <= 0.0 || targetWeightKg <= 0.0
    ) return null

    val difference = targetWeightKg - currentWeightKg
    return when {
        difference < -WeightGoalToleranceKg -> WeightGoalDirection.Lose
        difference > WeightGoalToleranceKg -> WeightGoalDirection.Gain
        else -> WeightGoalDirection.Maintain
    }
}

internal fun suggestedNutritionTargets(
    currentWeightKg: Double,
    direction: WeightGoalDirection,
): SuggestedNutritionTargets? {
    if (!currentWeightKg.isFinite() || currentWeightKg <= 0.0) return null

    val maintenanceCalories = (currentWeightKg * CaloriesPerKilogram).roundToInt()
    val adjustment = when (direction) {
        WeightGoalDirection.Lose -> -GoalCalorieAdjustment
        WeightGoalDirection.Maintain -> 0
        WeightGoalDirection.Gain -> GoalCalorieAdjustment
    }
    val calories = (maintenanceCalories + adjustment).coerceAtLeast(MinimumSuggestedCalories)
    val protein = (currentWeightKg * 1.8).roundToInt()
    val fat = (currentWeightKg * 0.8).roundToInt()
    val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)

    return SuggestedNutritionTargets(calories, protein, carbs, fat)
}

internal fun formatWeightDifference(currentWeightKg: Double, targetWeightKg: Double): String? {
    if (weightGoalDirection(currentWeightKg, targetWeightKg) == null) return null
    return "${formatWeightChange(targetWeightKg - currentWeightKg)} to target"
}
