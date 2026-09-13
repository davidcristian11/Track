package com.example.track

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FoodPrecisionTest {
    private val food = FoodDefinition("precise", "Precise food", 100,
        FoodNutrition(884.0, 12.3456, 23.4567, 45.6789))
    private val day = "2026-09-13"

    @Test fun repeatedEditsRecoverCaloriesAndMacrosAfterPersistence() {
        var log = LoggedFood.snapshot(7, MealContext.LUNCH, food, 1)
        assertEquals(9, log.nutrition.calories)
        repeat(10) {
            log = persisted(log).corrected(100, MealContext.DINNER)
            assertEquals(NutritionTotals(884, 12.3f, 23.5f, 45.7f), log.nutrition)
            assertEquals(food.basisNutrition, log.basisNutrition)
            assertEquals(100.0, log.basisAmount, 0.0)
            // Values below display precision must still affect larger amounts.
            log = persisted(log).corrected(1_000, MealContext.DINNER)
            assertEquals(NutritionTotals(8_840, 123.5f, 234.6f, 456.8f), log.nutrition)
            log = persisted(log).corrected(1, MealContext.LUNCH)
            assertEquals(9, log.nutrition.calories)
        }
    }

    @Test fun recentReuseKeepsOriginalBasisAcrossGenerations() {
        var log = LoggedFood.snapshot(1, MealContext.LUNCH, food, 1)
        repeat(10) { index ->
            val recent = recentFoodsFromLogs(listOf(log.toEntity(day, 10))).single()
            assertEquals(1, recent.defaultAmount)
            assertEquals(food.basisNutrition, recent.basisNutrition)
            log = LoggedFood.snapshot(index + 2L, MealContext.DINNER, recent, 100)
            assertEquals(NutritionTotals(884, 12.3f, 23.5f, 45.7f), persisted(log).nutrition)
            log = persisted(log).corrected(1, MealContext.LUNCH)
        }
    }

    @Test fun manualReferenceSurvivesLogEditAndReuseInBothUnits() {
        FoodUnit.entries.forEach { unit ->
            val manual = ManualFoodDraft(name = "Manual food", unit = unit,
                calories = "884.1234", protein = "12.3456", carbs = "23.4567", fat = "45.6789")
                .toFoodDefinition("manual")
            val saved = persisted(LoggedFood.snapshot(1, MealContext.LUNCH, manual, 1))
            val edited = persisted(saved.corrected(100, MealContext.DINNER))
            val recent = requireNotNull(edited.toEntity(day, 1).toRecentFoodDefinition())
            val reused = persisted(LoggedFood.snapshot(2, MealContext.LUNCH, recent, 1_000))
            assertEquals(unit, reused.unit)
            assertEquals(100.0, reused.basisAmount, 0.0)
            assertEquals(manual.basisNutrition, reused.basisNutrition)
            assertEquals(NutritionTotals(8_841, 123.5f, 234.6f, 456.8f), reused.nutrition)
        }
    }

    @Test fun externalSearchAndBarcodePreserveFractionalServingBasis() {
        val product = JSONObject("""{"code":"1234567890128","product_name":"Serving food",
            "serving_quantity":12.5,"serving_quantity_unit":"ml","nutriments":{
            "energy-kcal_serving":20.25,"proteins_serving":1.2345,
            "carbohydrates_serving":2.3456,"fat_serving":0.4567}}""")
        val search = OpenFoodFactsMapper.search("""{"products":[$product]}""").single()
        val barcode = requireNotNull(OpenFoodFactsMapper.barcode(
            """{"status":1,"product":$product}""", "1234567890128"))
        listOf(search, barcode).forEach { source ->
            val saved = persisted(LoggedFood.snapshot(1, MealContext.LUNCH, source, 1))
            val recent = requireNotNull(saved.toEntity(day, 1).toRecentFoodDefinition())
            assertEquals(12.5, recent.basisAmount, 0.0)
            assertEquals(FoodUnit.Milliliters, recent.unit)
            assertEquals(FoodNutrition(20.25, 1.2345, 2.3456, 0.4567), recent.basisNutrition)
            assertEquals(NutritionTotals(162, 9.9f, 18.8f, 3.7f), saved.corrected(100, saved.meal).nutrition)
            assertEquals(saved.corrected(100, saved.meal).nutrition, recent.nutritionFor(100))
        }
    }

    @Test fun invalidPersistedBasisCannotBeEditedReusedOrPersistedAgain() {
        val row = LoggedFood.snapshot(1, MealContext.LUNCH, food, 1).toEntity(day, 1)
        listOf(
            row.copy(basisAmount = 0.0), row.copy(basisAmount = Double.NaN),
            row.copy(basisCalories = Double.POSITIVE_INFINITY), row.copy(basisCalories = -1.0),
            row.copy(basisProteinGrams = Double.NaN), row.copy(basisCarbsGrams = Double.MAX_VALUE),
            row.copy(basisFatGrams = -1.0),
        ).forEach { invalid ->
            val log = invalid.toLoggedFood()
            assertNull(log.correctedOrNull(100, log.meal))
            assertNull(invalid.toRecentFoodDefinition())
            assertThrows(IllegalArgumentException::class.java) { log.toEntity(day, 1) }
        }
    }

    @Test fun safeSmallSnapshotStillRejectsOverflowingAmountEditsAndReuse() {
        val dense = food.copy(basisAmount = 1.0,
            basisNutrition = FoodNutrition(1_000_000.0, 1.0, 1.0, 1.0))
        val saved = persisted(LoggedFood.snapshot(1, MealContext.LUNCH, dense, 1))
        assertNull(saved.correctedOrNull(MaxFoodAmount, saved.meal))
        val recent = requireNotNull(saved.toEntity(day, 1).toRecentFoodDefinition())
        assertThrows(IllegalArgumentException::class.java) {
            LoggedFood.snapshot(2, MealContext.LUNCH, recent, MaxFoodAmount)
        }
    }

    private fun persisted(log: LoggedFood) = log.toEntity(day, 1).toLoggedFood()
}
