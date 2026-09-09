package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFoodTest {
    @Test fun trimsNamesAndOptionalBrandWhenCreatingCandidate() {
        val food = validDraft().copy(name = " Homemade Pasta ", brand = " Kitchen ").toFoodDefinition("manual")
        assertEquals("Homemade Pasta", food.name)
        assertEquals("Kitchen", food.brand)
        assertEquals(250.5, food.basisAmount, 0.0)
        assertEquals(FoodUnit.Grams, food.unit)
        assertNull(validDraft().copy(brand = "   ").toFoodDefinition("manual").brand)
    }

    @Test fun validatesNameAndBrandLengths() {
        assertFalse(validDraft().copy(name = " ").isValid)
        assertTrue(validDraft().copy(name = "a".repeat(80)).isValid)
        assertFalse(validDraft().copy(name = "a".repeat(81)).isValid)
        assertTrue(validDraft().copy(brand = "b".repeat(80)).isValid)
        assertFalse(validDraft().copy(brand = "b".repeat(81)).isValid)
    }

    @Test fun validatesReferenceAmountAndBothUnits() {
        assertTrue(validDraft().copy(referenceAmount = "100", unit = FoodUnit.Grams).isValid)
        assertTrue(validDraft().copy(referenceAmount = "250.5", unit = FoodUnit.Milliliters).isValid)
        assertFalse(validDraft().copy(referenceAmount = "0").isValid)
        assertFalse(validDraft().copy(referenceAmount = "-1").isValid)
        assertFalse(validDraft().copy(referenceAmount = "10000.1").isValid)
    }

    @Test fun validatesNutritionIncludingCommaDecimalsAndFiniteValues() {
        assertTrue(validDraft().copy(calories = "0", protein = "0", carbs = "12,5", fat = "3.5").isValid)
        listOf("-1", "NaN", "Infinity", "word", "").forEach {
            assertFalse(validDraft().copy(protein = it).isValid)
        }
        assertEquals(12.5, parseFoodDecimal("12,5")!!, 0.0)
    }

    @Test fun manualCandidateUsesReferenceNutritionForScaling() {
        val food = validDraft().copy(referenceAmount = "300", calories = "450", protein = "20",
            carbs = "60", fat = "12").toFoodDefinition("manual")
        assertEquals(NutritionTotals(225, 10f, 30f, 6f), food.nutritionFor(150))
        assertEquals(NutritionTotals(450, 20f, 60f, 12f), food.nutritionFor(300))
    }

    private fun validDraft() = ManualFoodDraft("Pasta", "", "250.5", FoodUnit.Grams,
        "420", "20", "65", "9")
}
