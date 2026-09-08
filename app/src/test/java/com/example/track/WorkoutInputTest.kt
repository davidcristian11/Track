package com.example.track

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class WorkoutInputTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test fun walkingAndRunningEstimatesDiffer() {
        assertEquals(120, estimateWorkoutCalories(WorkoutType.Walking, 30))
        assertEquals(300, estimateWorkoutCalories(WorkoutType.Running, 30))
        assertTrue(estimateWorkoutCalories(WorkoutType.Running, 30) > estimateWorkoutCalories(WorkoutType.Walking, 30))
    }

    @Test fun doublingDurationDoublesCalories() {
        WorkoutType.entries.forEach { type ->
            assertEquals(2 * estimateWorkoutCalories(type, 30), estimateWorkoutCalories(type, 60))
        }
    }

    @Test fun representativeActivitiesHaveDeterministicRates() {
        mapOf(WorkoutType.Strength to 180, WorkoutType.Calisthenics to 210,
            WorkoutType.Swimming to 240, WorkoutType.Other to 150,
            WorkoutType.Yoga to 90, WorkoutType.Mobility to 90).forEach { (type, expected) ->
            assertEquals(expected, estimateWorkoutCalories(type, 30))
        }
        assertEquals(16, WorkoutType.entries.size)
    }

    @Test fun invalidDurationsAreRejectedWithoutClamping() {
        listOf(Int.MIN_VALUE, -1, 0, 1441, Int.MAX_VALUE).forEach { duration ->
            assertFalse(WorkoutInput(today, durationMinutes = duration).isValid(today))
            assertThrows(IllegalArgumentException::class.java) { estimateWorkoutCalories(WorkoutType.Running, duration) }
        }
        assertTrue(WorkoutInput(today, durationMinutes = 1).isValid(today))
        assertTrue(WorkoutInput(today, durationMinutes = 1440).isValid(today))
    }

    @Test fun selectedPastDateIsCapturedAndExplicitChoiceIsIndependent() {
        var selectedDay = today.minusDays(3)
        val initial = WorkoutInput(selectedDay)
        assertEquals(selectedDay, initial.day)
        val chosen = initial.copy(day = today.minusDays(2))
        selectedDay = today
        assertEquals(today.minusDays(3), initial.day)
        assertEquals(today.minusDays(2), chosen.day)
        assertNotEquals(selectedDay, chosen.day)
    }

    @Test fun futureDatesAreDisallowedAndPastHasNoArbitraryCutoff() {
        assertFalse(WorkoutInput(today.plusDays(1)).isValid(today))
        assertTrue(WorkoutInput(today).isValid(today))
        assertTrue(WorkoutInput(LocalDate.of(1900, 1, 1)).isValid(today))
    }

    @Test fun pickerUsesUtcCalendarDateWithoutTimezoneShift() {
        listOf(today, LocalDate.of(2024, 2, 29), LocalDate.of(1900, 1, 1)).forEach {
            assertEquals(it, it.toWorkoutPickerMillis().toWorkoutPickerDay())
        }
        assertEquals(0L, LocalDate.of(1970, 1, 1).toWorkoutPickerMillis())
    }

    @Test fun timeIsStoredAsHoursAndMinutes() {
        assertEquals("07:30", formatWorkoutTime(LocalTime.of(7, 30, 59)))
        assertTrue(WorkoutInput(today, startTime = "00:00").isValid(today))
        assertTrue(WorkoutInput(today, startTime = "23:59").isValid(today))
        listOf("24:00", "7:30", "07:30:00", "06:10 PM", "").forEach {
            assertFalse(WorkoutInput(today, startTime = it).isValid(today))
        }
    }
}
