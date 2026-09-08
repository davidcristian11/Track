package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSessionDataTest {
    private val yogurt = LocalFoodCatalog.first { it.id == "greek_yogurt" }

    @Test
    fun defaultSessionPreservesApprovedBaseline() {
        val session = TrackSessionData(TrackDemoBaseline.referenceDay)
        assertEquals(NutritionTotals(1_450, 90f, 180f, 45f), session.nutrition)
        assertTrue(session.foods.isEmpty())
        assertEquals(1_500, session.waterMl)
        assertTrue(session.creatineCompleted)
    }

    @Test
    fun yogurt119GramsAddsDisplayedNutrientsToLunch() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay)
        val updated = original.addFood(MealContext.LUNCH, yogurt, 119)
        val logged = updated.foods.single()

        assertEquals(MealContext.LUNCH, logged.meal)
        assertEquals(yogurt.id, logged.catalogFoodId)
        assertEquals(yogurt.name, logged.name)
        assertEquals(119, logged.amount)
        assertEquals(FoodUnit.Grams, logged.unit)
        assertEquals(NutritionTotals(70, 11.9f, 4.3f, 0.5f), logged.nutrition)
        assertEquals(1_520, updated.nutrition.calories)
        assertEquals(101.9f, updated.nutrition.proteinGrams, 0.0001f)
        assertEquals(184.3f, updated.nutrition.carbsGrams, 0.0001f)
        assertEquals(45.5f, updated.nutrition.fatGrams, 0.0001f)
        assertTrue(original.foods.isEmpty())
    }

    @Test
    fun repeatedFoodsAppendWithoutOverwritingOrCountingBaselineRowsAgain() {
        val session = TrackSessionData(TrackDemoBaseline.referenceDay)
            .addFood(MealContext.BREAKFAST, yogurt, 119)
            .addFood(MealContext.BREAKFAST, yogurt, 119)
        assertEquals(2, session.foods.size)
        assertEquals(2, session.foods.map { it.id }.distinct().size)
        assertEquals(1_590, session.nutrition.calories)
    }

    @Test
    fun allCatalogFoodsHaveConsistentSearchServingCalories() {
        val expected = mapOf("greek_yogurt" to 70, "chicken_breast" to 165, "banana" to 105)
        var session = TrackSessionData(TrackDemoBaseline.referenceDay)
        LocalFoodCatalog.forEach { food ->
            assertEquals(expected.getValue(food.id), food.nutritionFor(food.defaultAmount).calories)
            session = session.addFood(MealContext.DINNER, food, food.defaultAmount)
        }
        assertEquals(3, session.foods.size)
        assertEquals(1_790, session.nutrition.calories)
    }

    @Test
    fun foodAmountCalculationIsSharedWithDetails() {
        assertEquals(NutritionTotals(76, 12.9f, 4.6f, 0.5f), yogurt.nutritionFor(129))
        assertEquals(NutritionTotals(), yogurt.nutritionFor(0))
    }

    @Test
    fun invalidFoodAmountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { yogurt.nutritionFor(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            TrackSessionData(TrackDemoBaseline.referenceDay).addFood(MealContext.LUNCH, yogurt, 0)
        }
        assertThrows(IllegalArgumentException::class.java) { yogurt.nutritionFor(MaxFoodAmount + 1) }
    }

    @Test
    fun goalsAndCurrentSessionDataStayIndependent() {
        val goals = TrackGoals()
        val session = TrackSessionData(TrackDemoBaseline.referenceDay).addFood(MealContext.LUNCH, yogurt, 119).addWater()
        val changedGoals = goals.copy(calories = 2_400, waterLiters = 3f)

        assertEquals(TrackGoals(), goals)
        assertEquals(1_520, session.nutrition.calories)
        assertEquals(880, remainingCalories(session.nutrition.calories, changedGoals.calories))
        assertEquals(1_750, session.waterMl)
        assertEquals(1_750f / 3_000f, waterProgress(session.waterMl, changedGoals.waterLiters), 0.0001f)
    }

    @Test
    fun waterAddsExactQuarterLiters() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay)
        val once = original.addWater()
        assertEquals(1_500, original.waterMl)
        assertEquals(1_750, once.waterMl)
        assertEquals(2_000, once.addWater().waterMl)
    }

    @Test
    fun waterCanExceedGoalWhileProgressStaysSafe() {
        val session = TrackSessionData(TrackDemoBaseline.referenceDay).addWater(1_500)
        assertEquals(3_000, session.waterMl)
        assertEquals(1f, waterProgress(session.waterMl, 2.5f), 0f)
        assertEquals(0f, waterProgress(session.waterMl, 0f), 0f)
        assertEquals(0f, waterProgress(session.waterMl, Float.NaN), 0f)
        assertEquals(0f, waterProgress(session.waterMl, Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun formattingAvoidsFloatDriftAndUnneededZeros() {
        assertEquals("1.5", formatWaterLiters(1_500))
        assertEquals("1.75", formatWaterLiters(1_750))
        assertEquals("2", formatWaterLiters(2_000))
        assertEquals("101.9", formatNutrient(101.900001f))
        assertEquals("90", formatNutrient(90f))
    }

    @Test
    fun creatineTogglesWithoutChangingOtherSessionData() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay).addWater()
        assertFalse(original.toggleCreatine().creatineCompleted)
        assertEquals(original, original.toggleCreatine().toggleCreatine())
    }

    @Test
    fun newestWorkoutComesFirstAndBaselineRemains() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay)
        val baseline = original.workouts.single()
        val updated = original.addWorkout(WorkoutType.Running, 30, "Easy run")

        assertEquals(WorkoutType.Strength, baseline.type)
        assertEquals(45, baseline.durationMinutes)
        assertEquals("Upper body", baseline.notes)
        assertEquals(280, baseline.estimatedCalories)
        assertEquals("18:10", baseline.startTime)
        assertEquals(2, updated.workouts.size)
        assertEquals(3, original.workoutsThisWeek)
        assertEquals(4, updated.workoutsThisWeek)
        assertEquals(baseline, updated.workouts.last())
        assertEquals(WorkoutType.Running, updated.workouts.first().type)
        assertEquals(30, updated.workouts.first().durationMinutes)
        assertEquals("Easy run", updated.workouts.first().notes)
        assertEquals(300, updated.workouts.first().estimatedCalories)
        assertEquals(2, updated.workouts.map { it.id }.distinct().size)
    }

    @Test
    fun invalidWorkoutDurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackSessionData(TrackDemoBaseline.referenceDay).addWorkout(WorkoutType.Running, 0, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrackSessionData(TrackDemoBaseline.referenceDay).addWorkout(WorkoutType.Running, -30, "")
        }
    }

    @Test
    fun databaseMappingRoundTripsAndFurtherEntriesKeepUniqueIds() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay)
            .addFood(MealContext.LUNCH, yogurt, 119)
            .addFood(MealContext.SNACKS, LocalFoodCatalog.last(), 118)
            .addWater()
            .toggleCreatine()
            .addWorkout(WorkoutType.Running, 30, "Easy run")
        val restored = roundTripEntities(original)
        assertEquals(original, restored)
        assertEquals(original.nutrition, restored.nutrition)
        val updated = restored.addFood(MealContext.DINNER, yogurt, 129)
            .addWorkout(WorkoutType.Walking, 60, "")
        assertEquals(3, updated.foods.map { it.id }.distinct().size)
        assertEquals(3, updated.workouts.map { it.id }.distinct().size)
    }

    @Test
    fun scannerProductUsesMillilitersWithoutChangingSearchCatalog() {
        assertEquals(FoodUnit.Milliliters, ScannedFood.unit)
        assertEquals(500, ScannedFood.defaultAmount)
        assertEquals("500 ml bottle", ScannedFood.servingLabel)
        assertFalse(LocalFoodCatalog.any { it.id == ScannedFood.id })
        assertEquals(ScannedFood, findLocalFood(ScannedFood.id))
    }

    @Test
    fun scannedFoodLogsChosenMealAndAmountWithoutChangingNutrition() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay).addFood(MealContext.LUNCH, yogurt, 119)
        val updated = original.addFood(MealContext.SNACKS, ScannedFood, 750)
        val entry = updated.foods.last()
        assertEquals(MealContext.SNACKS, entry.meal)
        assertEquals(750, entry.amount)
        assertEquals("ml", entry.unit.symbol)
        assertEquals(NutritionTotals(), entry.nutrition)
        assertEquals(original.nutrition, updated.nutrition)
        assertThrows(IllegalArgumentException::class.java) {
            original.addFood(MealContext.SNACKS, ScannedFood, 0)
        }
    }

    @Test
    fun repeatedScansAndUnitsSurviveDatabaseMapping() {
        val original = TrackSessionData(TrackDemoBaseline.referenceDay)
            .addFood(MealContext.LUNCH, ScannedFood, 500)
            .addFood(MealContext.DINNER, ScannedFood, 500)
            .addFood(MealContext.BREAKFAST, yogurt, 119)
        val restored = roundTripEntities(original)
        assertEquals(original, restored)
        assertEquals(3, restored.foods.map { it.id }.distinct().size)
        assertEquals(listOf("ml", "ml", "g"), restored.foods.map { it.unit.symbol })
        assertEquals(1_520, restored.nutrition.calories)
    }

    @Test
    fun foodSnapshotDoesNotRecalculateOrDependOnCatalogLookup() {
        val row = LoggedFood.snapshot(1, MealContext.LUNCH, yogurt, 119)
            .toEntity(TrackDemoBaseline.referenceDay.toDayKey(), 100)
            .copy(catalogFoodId = "retired-food", name = "Original recipe", calories = 123, proteinGrams = 8f)
        val session = trackingSnapshot(TrackDemoBaseline.referenceDay, listOf(row), emptyList(), null)
        assertEquals("Original recipe", session.foods.single().name)
        assertEquals(NutritionTotals(123, 8f, 4.3f, 0.5f), session.foods.single().nutrition)
        assertEquals(1_573, session.nutrition.calories)
        assertEquals(TrackDemoBaseline.forDay(TrackDemoBaseline.referenceDay).workouts.single(), session.workouts.single())
    }

    @Test
    fun emptyDatabaseMappingIsExactlyTheDemoDisplay() {
        assertEquals(TrackSessionData(TrackDemoBaseline.referenceDay), trackingSnapshot(TrackDemoBaseline.referenceDay, emptyList(), emptyList(), null))
    }

    @Test
    fun correctionScalesFromOriginalSnapshotAndKeepsIdentity() {
        val original = LoggedFood.snapshot(7, MealContext.LUNCH, yogurt, 100)
            .copy(nutrition = NutritionTotals(200, 10f, 20f, 5f))
        original.corrected(123, MealContext.LUNCH) // An earlier draft must not compound rounding.
        val corrected = original.corrected(150, MealContext.DINNER)
        assertEquals(original.copy(amount = 150, meal = MealContext.DINNER,
            nutrition = NutritionTotals(300, 15f, 30f, 7.5f)), corrected)
        assertEquals(original, original.corrected(100, MealContext.LUNCH))
    }

    @Test
    fun correctionPreservesZeroDrinkNutrition() {
        val original = LoggedFood.snapshot(8, MealContext.LUNCH, ScannedFood, 500)
        val corrected = original.corrected(750, MealContext.SNACKS)
        assertEquals(NutritionTotals(), corrected.nutrition)
        assertEquals(FoodUnit.Milliliters, corrected.unit)
        assertEquals(750, corrected.amount)
    }

    @Test
    fun correctionRejectsInvalidAmountsAndRoundsToTenths() {
        val original = LoggedFood.snapshot(7, MealContext.LUNCH, yogurt, 119)
        assertThrows(IllegalArgumentException::class.java) { original.corrected(0, original.meal) }
        assertThrows(IllegalArgumentException::class.java) { original.corrected(MaxFoodAmount + 1, original.meal) }
        assertEquals(NutritionTotals(88, 15f, 5.4f, 0.6f), original.corrected(150, original.meal).nutrition)
    }

    private fun roundTripEntities(session: TrackSessionData) = trackingSnapshot(
        session.day,
        session.foods.map { it.toEntity(session.day.toDayKey(), it.id) },
        session.workouts.filter { it.id != 0L }.map { it.toEntity(session.day.toDayKey(), it.id) },
        DailyTrackingStateEntity(session.day.toDayKey(), session.waterMl, session.creatineCompleted),
    )
}
