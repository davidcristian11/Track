package com.example.track

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackUiSettingsTest {
    @Test
    fun defaultGoalsPreserveApprovedDashboardTargets() {
        val goals = TrackGoals()

        assertEquals(2_200, goals.calories)
        assertEquals(150, goals.proteinGrams)
        assertEquals(250, goals.carbsGrams)
        assertEquals(70, goals.fatGrams)
        assertEquals(2.5f, goals.waterLiters, 0f)
        assertEquals(10_000, goals.steps)
        assertEquals(68f, goals.targetWeightKg, 0f)
        assertTrue(TodayCustomization().showNutrition)
        assertTrue(TodayCustomization().showWater)
        assertTrue(TodayCustomization().showSteps)
        assertTrue(TodayCustomization().showSleep)
        assertTrue(TodayCustomization().showWorkout)
        assertTrue(TodayCustomization().showCreatine)
        assertFalse(TodayCustomization().showWeight)
    }

    @Test
    fun progressFractionIsDerivedAndClamped() {
        assertEquals(0.5f, progressFraction(50, 100), 0.0001f)
        assertEquals(1f, progressFraction(150, 100), 0.0001f)
        assertEquals(0f, progressFraction(50, 0), 0.0001f)
        assertEquals(0f, progressFraction(-50, 100), 0.0001f)
    }

    @Test
    fun waterProgressHandlesInvalidAndExceededTargets() {
        assertEquals(0.6f, progressFraction(1.5f, 2.5f), 0.0001f)
        assertEquals(1f, progressFraction(3f, 2.5f), 0f)
        assertEquals(0f, progressFraction(1.5f, 0f), 0f)
        assertEquals(0f, progressFraction(1.5f, Float.NaN), 0f)
        assertEquals(0f, progressFraction(1.5f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, progressFraction(Float.NaN, 2.5f), 0f)
    }

    @Test
    fun goalsRejectInvalidTargets() {
        fun parse(
            calories: String = "2200", steps: String = "10000", water: String = "2.5",
            protein: String = "150", targetWeight: String = "68.0",
        ) = parseGoals(calories, protein, "250", "70", water, steps, targetWeight)

        assertEquals(TrackGoals(), parse())
        assertNull(parse(calories = "0"))
        assertNull(parse(calories = ""))
        assertNull(parse(calories = "not a number"))
        assertNull(parse(steps = "-100"))
        assertNull(parse(water = "NaN"))
        assertNull(parse(water = "Infinity"))
        assertNull(parse(water = "0"))
        assertNull(parse(protein = "-1"))
        assertNull(parse(targetWeight = "67..5"))
    }

    @Test
    fun missingPreferencesUseApprovedDefaults() {
        assertEquals(TrackUiSettings(), emptyPreferences().toTrackUiSettings())
        assertEquals(
            TrackUiSettings(goals = TrackGoals(calories = 2_400)),
            preferencesOf(intPreferencesKey("goal_calories") to 2_400).toTrackUiSettings(),
        )
    }

    @Test
    fun storedGoalsMapWithoutChangingModuleDefaults() {
        val preferences = preferencesOf(
            intPreferencesKey("goal_calories") to 2_400,
            intPreferencesKey("goal_protein_g") to 180,
            intPreferencesKey("goal_carbs_g") to 275,
            intPreferencesKey("goal_fat_g") to 80,
            floatPreferencesKey("goal_water_liters") to 3.125f,
            intPreferencesKey("goal_steps") to 12_000,
            floatPreferencesKey("goal_weight_kg") to 67.5f,
        )
        assertEquals(
            TrackUiSettings(goals = TrackGoals(2_400, 180, 275, 80, 3.125f, 12_000, 67.5f)),
            preferences.toTrackUiSettings(),
        )
    }

    @Test
    fun storedModuleBooleansMapWithoutChangingGoalDefaults() {
        val preferences = preferencesOf(
            booleanPreferencesKey("today_nutrition_enabled") to false,
            booleanPreferencesKey("today_water_enabled") to false,
            booleanPreferencesKey("today_steps_enabled") to false,
            booleanPreferencesKey("today_sleep_enabled") to false,
            booleanPreferencesKey("today_workout_enabled") to false,
            booleanPreferencesKey("today_creatine_enabled") to false,
            booleanPreferencesKey("today_weight_enabled") to true,
        )
        assertEquals(
            TrackUiSettings(today = TodayCustomization(false, false, false, false, false, false, true)),
            preferences.toTrackUiSettings(),
        )
    }

    @Test
    fun remainingCaloriesNeverBecomesNegative() {
        assertEquals(750, remainingCalories(consumed = 1_450, target = 2_200))
        assertEquals(0, remainingCalories(consumed = 2_300, target = 2_200))
    }
}
