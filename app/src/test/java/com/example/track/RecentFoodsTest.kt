package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFoodsTest {
    @Test fun newestSnapshotWinsAcrossNormalizedDuplicates() {
        val foods = recentFoodsFromLogs(listOf(
            log(4, "Greek Yogurt", "Fage", "g", 150, 300),
            log(3, "Banana", " ", "g", 120, 105),
            log(2, " greek   yogurt ", " fage ", "g", 100, 200),
            log(1, "Greek Yogurt", "AnotherBrand", "g", 100, 210),
        ))

        assertEquals(listOf("Greek Yogurt", "Banana", "Greek Yogurt"), foods.map { it.name })
        assertEquals(listOf("Fage", null, "AnotherBrand"), foods.map { it.brand })
        assertEquals(150, foods.first().defaultAmount)
        assertEquals(300, foods.first().nutritionFor(150).calories)
    }

    @Test fun unitIsPartOfIdentityAndResultLimitIsAppliedAfterDeduplication() {
        val foods = recentFoodsFromLogs(listOf(
            log(5, "Drink", null, "ml"),
            log(4, "Drink", null, "g"),
            log(3, "Other", null, "g"),
            log(2, "Third", null, "g"),
            log(1, "Fourth", null, "g"),
        ), limit = 3)
        assertEquals(listOf("ml", "g", "g"), foods.map { it.unit.symbol })
        assertEquals(listOf("Drink", "Drink", "Other"), foods.map { it.name })
    }

    @Test fun invalidSnapshotsAreExcludedWithoutHidingOlderValidOccurrence() {
        val foods = recentFoodsFromLogs(listOf(
            log(5, "Yogurt", null, "g", amount = 0),
            log(4, "Bad unit", null, "serving"),
            log(3, "Bad macro", null, "g").copy(proteinGrams = Float.NaN),
            log(2, "Yogurt", null, "g", amount = 100),
        ))
        assertEquals(listOf("Yogurt"), foods.map { it.name })
        assertEquals(100, foods.single().defaultAmount)
        assertTrue(recentFoodsFromLogs(emptyList()).isEmpty())
    }

    @Test fun persistedSnapshotIsAnActualReferenceBasis() {
        val food = requireNotNull(log(1, "Meal", null, "g", 150, 300).copy(
            proteinGrams = 15f, carbsGrams = 30f, fatGrams = 10f,
        ).toRecentFoodDefinition())
        assertEquals(NutritionTotals(150, 7.5f, 15f, 5f), food.nutritionFor(75))
        assertEquals(NutritionTotals(600, 30f, 60f, 20f), food.nutritionFor(300))
    }

    private fun log(
        id: Long,
        name: String,
        brand: String?,
        unit: String,
        amount: Int = 100,
        calories: Int = 100,
    ) = LoggedFoodEntity(id, "2026-09-05", MealContext.LUNCH.name, null, name, brand, amount,
        unit, calories, 1f, 2f, 3f, id)
}
