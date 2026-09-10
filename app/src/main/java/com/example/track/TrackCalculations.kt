package com.example.track

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

internal fun macroProgressPercent(loggedGrams: Float, goalGrams: Int): Int {
    if (!loggedGrams.isFinite() || loggedGrams <= 0f || goalGrams <= 0) return 0
    return (loggedGrams / goalGrams * 100f).roundToInt().coerceAtLeast(0)
}

internal data class MacroCalorieShares(
    val proteinPercent: Int,
    val carbsPercent: Int,
    val fatPercent: Int,
)

internal fun macroCalorieShares(proteinGrams: Int, carbsGrams: Int, fatGrams: Int): MacroCalorieShares {
    val proteinCalories = proteinGrams.coerceAtLeast(0) * 4L
    val carbsCalories = carbsGrams.coerceAtLeast(0) * 4L
    val fatCalories = fatGrams.coerceAtLeast(0) * 9L
    val total = proteinCalories + carbsCalories + fatCalories
    if (total <= 0L) return MacroCalorieShares(0, 0, 0)
    fun share(calories: Long) = (calories.toDouble() / total * 100.0).roundToInt()
    return MacroCalorieShares(share(proteinCalories), share(carbsCalories), share(fatCalories))
}

internal fun estimateStepCalories(steps: Int?, weightKg: Double?): Int? {
    if (steps == null || weightKg == null || steps < 0 || !weightKg.isFinite() || weightKg <= 0.0) return null
    return (steps * weightKg * 0.0005).roundToInt()
}

internal fun workoutCalories(workouts: List<LoggedWorkout>): Int =
    workouts.sumOf { it.estimatedCalories.coerceAtLeast(0) }

internal fun estimatedTotalBurned(stepCalories: Int?, workoutCalories: Int): Int? =
    stepCalories?.let { it.coerceAtLeast(0) + workoutCalories.coerceAtLeast(0) }

internal fun foodLogTimestamp(
    day: LocalDate,
    time: LocalTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = day.atTime(time.withSecond(0).withNano(0)).atZone(zoneId).toInstant().toEpochMilli()
