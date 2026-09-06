package com.example.track

// Phase 3A keeps the approved prototype date; calendar navigation comes later.
const val TrackPrototypeDay = "2026-09-02"

// Presentation-only defaults, NEVER seeded into Room. Until the prototype baseline
// is retired, nutrition displays this aggregate plus real, persisted food snapshots.
// The separate static 473-kcal Breakfast rows in Nutrition are already represented here.
object TrackDemoBaseline {
    val nutrition = NutritionTotals(1_450, 90f, 180f, 45f)
    const val waterMl = 1_500
    const val creatineCompleted = true
    val workout = LoggedWorkout(0, WorkoutType.Strength, 45, "Upper body")
    const val workoutsThisWeek = 3
}
