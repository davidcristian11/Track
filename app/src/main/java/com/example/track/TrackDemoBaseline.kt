package com.example.track

import java.time.LocalDate

data class TrackDayBaseline(
    val nutrition: NutritionTotals = NutritionTotals(0, 0f, 0f, 0f),
    val waterMl: Int = 0,
    val creatineCompleted: Boolean = false,
    val workouts: List<LoggedWorkout> = emptyList(),
    val showBreakfast: Boolean = false,
    val workoutsThisWeek: Int = 0,
)

// Presentation-only defaults, NEVER seeded into Room. Until the prototype baseline
// is retired, nutrition displays this aggregate plus real, persisted food snapshots.
// The separate static 473-kcal Breakfast rows in Nutrition are already represented here.
object TrackDemoBaseline {
    val referenceDay: LocalDate = LocalDate.of(2026, 9, 2)
    private val empty = TrackDayBaseline()
    private val reference = TrackDayBaseline(
        nutrition = NutritionTotals(1_450, 90f, 180f, 45f),
        waterMl = 1_500,
        creatineCompleted = true,
        workouts = listOf(LoggedWorkout(0, WorkoutType.Strength, 45, "Upper body")),
        showBreakfast = true,
        workoutsThisWeek = 3,
    )

    fun forDay(day: LocalDate): TrackDayBaseline = if (day == referenceDay) reference else empty
}
