package com.example.track

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDatesTest {
    private val reference = TrackDemoBaseline.referenceDay

    @Test
    fun referenceDayKeepsTheApprovedBaselineOnlyInMemory() {
        val state = trackingSnapshot(reference, emptyList(), emptyList(), null)
        assertEquals(NutritionTotals(1_450, 90f, 180f, 45f), state.nutrition)
        assertEquals(1_500, state.waterMl)
        assertTrue(state.creatineCompleted)
        assertTrue(state.baseline.showBreakfast)
        assertTrue(state.foods.isEmpty())
        assertEquals(LoggedWorkout(0, WorkoutType.Strength, 45, "Upper body", estimatedCalories = 280), state.workouts.single())
    }

    @Test
    fun ordinaryDaysOnEitherSideOfReferenceStartCompletelyEmpty() {
        listOf(reference.minusDays(1), reference.plusDays(1), reference.plusYears(1)).forEach { day ->
            val state = trackingSnapshot(day, emptyList(), emptyList(), null)
            assertEquals(TrackDayBaseline(), state.baseline)
            assertEquals(NutritionTotals(), state.nutrition)
            assertEquals(0, state.waterMl)
            assertFalse(state.creatineCompleted)
            assertFalse(state.baseline.showBreakfast)
            assertTrue(state.workouts.isEmpty())
            assertTrue(state.foods.isEmpty())
        }
    }

    @Test
    fun sameFoodSnapshotAddsToOnlyThatDaysBaselineAndDailyRowsOverrideFallbacks() {
        listOf(reference, reference.plusDays(1)).forEach { day ->
            val food = LoggedFood.snapshot(1, MealContext.LUNCH, LocalFoodCatalog.first(), 119)
                .toEntity(day.toDayKey(), 1)
            val state = trackingSnapshot(
                day, listOf(food), emptyList(), DailyTrackingStateEntity(day.toDayKey(), 250, false),
            )
            assertEquals(if (day == reference) 1_520 else 70, state.nutrition.calories)
            assertEquals(if (day == reference) 101.9f else 11.9f, state.nutrition.proteinGrams, 0.001f)
            assertEquals(250, state.waterMl)
            assertFalse(state.creatineCompleted)
            assertEquals(day, state.day)
        }
    }

    @Test
    fun isoDayKeyRoundTripsLeapDayWithoutLocaleFormatting() {
        val day = LocalDate.of(2024, 2, 29)
        assertEquals("2024-02-29", day.toDayKey())
        assertEquals(day, day.toDayKey().toTrackDay())
    }

    @Test
    fun previousAndNextHandleCalendarBoundariesAndCannotEnterFuture() {
        val today = LocalDate.of(2024, 3, 1)
        val yesterday = previousTrackDay(today)
        assertEquals(LocalDate.of(2024, 2, 29), yesterday)
        assertTrue(canNavigateNext(yesterday, today))
        assertEquals(today, nextTrackDay(yesterday, today))
        assertFalse(canNavigateNext(today, today))
        assertEquals(today, nextTrackDay(today, today))
        assertEquals(today, nextTrackDay(today.plusDays(1), today))
        assertEquals(LocalDate.of(2023, 12, 31), previousTrackDay(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun dateLabelsDistinguishTodayPastDaysAndOtherYears() {
        val today = reference.plusDays(1)
        assertEquals("Today", trackDayTitle(today, today, Locale.US))
        assertEquals("Wednesday", trackDayTitle(reference, today, Locale.US))
        assertEquals("Wednesday, September 2", formatTrackDate(reference, today, Locale.US))
        assertEquals("Wednesday, September 2, 2026", formatTrackDate(reference, today.plusYears(1), Locale.US))
        assertEquals("Today, Sep 3", formatWorkoutDate(today, today, Locale.US))
        assertEquals("Sep 2", formatWorkoutDate(reference, today, Locale.US))
        assertEquals("mercredi, septembre 2", formatTrackDate(reference, today, Locale.FRANCE))
    }
}
