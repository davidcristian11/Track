package com.example.track

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackCalculationsTest {
    @Test fun dailyMacroProgressKeepsOverGoalValuesAndHandlesZeroGoal() {
        assertEquals(0, macroProgressPercent(0f, 150))
        assertEquals(50, macroProgressPercent(75f, 150))
        assertEquals(100, macroProgressPercent(150f, 150))
        assertEquals(120, macroProgressPercent(180f, 150))
        assertEquals(0, macroProgressPercent(75f, 0))
        assertEquals(0, macroProgressPercent(Float.NaN, 150))
    }

    @Test fun macroSharesUseCaloriesAndRoundDeterministically() {
        assertEquals(MacroCalorieShares(27, 45, 28), macroCalorieShares(150, 250, 70))
        assertEquals(MacroCalorieShares(31, 46, 23), macroCalorieShares(100, 150, 33))
        assertEquals(MacroCalorieShares(0, 0, 0), macroCalorieShares(0, 0, 0))
    }

    @Test fun stepAndWorkoutBurnEstimatesRespectMissingInputs() {
        assertEquals(375, estimateStepCalories(10_000, 75.0))
        assertEquals(200, estimateStepCalories(5_000, 80.0))
        assertEquals(0, estimateStepCalories(0, 75.0))
        assertNull(estimateStepCalories(null, 75.0))
        assertNull(estimateStepCalories(10_000, null))

        val workouts = listOf(
            LoggedWorkout(1, WorkoutType.Running, 30, "", estimatedCalories = 300),
            LoggedWorkout(2, WorkoutType.Walking, 30, "", estimatedCalories = 120),
        )
        assertEquals(420, workoutCalories(workouts))
        assertEquals(795, estimatedTotalBurned(375, workoutCalories(workouts)))
        assertNull(estimatedTotalBurned(null, workoutCalories(workouts)))
    }

    @Test fun foodTimestampCombinesCapturedDayAndChosenLocalTime() {
        val zone = ZoneId.of("Europe/Bucharest")
        val timestamp = foodLogTimestamp(
            LocalDate.of(2026, 9, 8),
            LocalTime.of(18, 35),
            zone,
        )
        val restored = java.time.Instant.ofEpochMilli(timestamp).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 8), restored.toLocalDate())
        assertEquals(LocalTime.of(18, 35), restored.toLocalTime())
    }
}
