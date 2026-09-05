package com.example.track

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.util.Locale

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

val TrackUiSettingsSaver: Saver<TrackUiSettings, Any> = listSaver(
    save = { settings ->
        listOf(
            settings.today.showNutrition,
            settings.today.showWater,
            settings.today.showSteps,
            settings.today.showSleep,
            settings.today.showWorkout,
            settings.today.showCreatine,
            settings.today.showWeight,
            settings.goals.calories,
            settings.goals.proteinGrams,
            settings.goals.carbsGrams,
            settings.goals.fatGrams,
            settings.goals.waterLiters,
            settings.goals.steps,
            settings.goals.targetWeightKg,
        )
    },
    restore = { values ->
        TrackUiSettings(
            today = TodayCustomization(
                showNutrition = values[0] as Boolean,
                showWater = values[1] as Boolean,
                showSteps = values[2] as Boolean,
                showSleep = values[3] as Boolean,
                showWorkout = values[4] as Boolean,
                showCreatine = values[5] as Boolean,
                showWeight = values[6] as Boolean,
            ),
            goals = TrackGoals(
                calories = values[7] as Int,
                proteinGrams = values[8] as Int,
                carbsGrams = values[9] as Int,
                fatGrams = values[10] as Int,
                waterLiters = values[11] as Float,
                steps = values[12] as Int,
                targetWeightKg = values[13] as Float,
            ),
        )
    },
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
