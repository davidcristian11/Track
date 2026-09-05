package com.example.track

import androidx.compose.runtime.saveable.SaverScope
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
        fun parse(calories: String = "2200", steps: String = "10000", water: String = "2.5") =
            parseGoals(calories, "150", "250", "70", water, steps, "68.0")

        assertEquals(TrackGoals(), parse())
        assertNull(parse(calories = "0"))
        assertNull(parse(calories = ""))
        assertNull(parse(calories = "not a number"))
        assertNull(parse(steps = "-100"))
        assertNull(parse(water = "NaN"))
        assertNull(parse(water = "Infinity"))
    }

    @Test
    fun settingsSaverRoundTripsCustomizationAndGoals() {
        val settings = TrackUiSettings(
            today = TodayCustomization(showWorkout = false, showWeight = true),
            goals = TrackGoals(calories = 2_400, steps = 12_000, waterLiters = 3f),
        )
        val saved = with(TrackUiSettingsSaver) {
            SaverScope { true }.save(settings)
        }

        assertEquals(settings, TrackUiSettingsSaver.restore(requireNotNull(saved)))
    }

    @Test
    fun remainingCaloriesNeverBecomesNegative() {
        assertEquals(750, remainingCalories(consumed = 1_450, target = 2_200))
        assertEquals(0, remainingCalories(consumed = 2_300, target = 2_200))
    }
}
