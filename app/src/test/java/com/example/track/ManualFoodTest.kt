package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFoodTest {
    @Test fun defaultReferenceAmountIsOneHundredGrams() {
        val draft = ManualFoodDraft()
        assertEquals("100", draft.referenceAmount)
        assertEquals(FoodUnit.Grams, draft.unit)
    }

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
        assertTrue(validDraft().copy(referenceAmount = "1").isValid)
        assertFalse(validDraft().copy(referenceAmount = "0.0001").isValid)
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

    @Test fun breadReferenceScalesCaloriesAndMacrosAcrossNormalAmounts() {
        val bread = validDraft().copy(referenceAmount = "100", calories = "260", protein = "10",
            carbs = "40", fat = "5").toFoodDefinition("bread")
        assertEquals(NutritionTotals(130, 5f, 20f, 2.5f), bread.nutritionFor(50))
        assertEquals(NutritionTotals(182, 7f, 28f, 3.5f), bread.nutritionFor(70))
        assertEquals(NutritionTotals(260, 10f, 40f, 5f), bread.nutritionFor(100))
        assertEquals(NutritionTotals(390, 15f, 60f, 7.5f), bread.nutritionFor(150))
        assertEquals(NutritionTotals(1_300, 50f, 200f, 25f), bread.nutritionFor(500))
    }

    @Test fun decimalReferenceAndParsingRemainFiniteWithoutChangingIntegerLogAmounts() {
        val food = validDraft().copy(referenceAmount = "75.5", calories = "151", protein = "7.55",
            carbs = "15.1", fat = "3.775").toFoodDefinition("decimal-reference")
        assertEquals(NutritionTotals(200, 10f, 20f, 5f), food.nutritionFor(100))
        assertEquals(75, parseFoodAmount("75"))
        assertEquals(75, parseFoodAmount("75,0"))
        assertNull(parseFoodAmount("75.5"))
    }

    @Test fun pathologicalDensityAndMalformedValuesCannotCreateCandidate() {
        val tiny = validDraft().copy(referenceAmount = "0.0001", calories = "100000")
        assertFalse(tiny.isValid)
        assertFalse(validDraft().copy(referenceAmount = Double.MIN_VALUE.toString()).isValid)
        val overflowing = validDraft().copy(referenceAmount = "0.1", calories = "100000")
        assertFalse(overflowing.isValid)
        assertEquals("These nutrition values are too large for this amount", overflowing.scalingError)
        listOf("0", "-1", "NaN", "Infinity", "word", "75.5").forEach {
            assertNull(parseFoodAmount(it))
        }
        val unsafe = FoodDefinition(
            "unsafe", "Unsafe", 10, FoodNutrition(100_000.0, 1.0, 1.0, 1.0),
            basisAmount = Double.MIN_VALUE,
        )
        assertNull(unsafe.nutritionForOrNull(10))
        assertFalse(unsafe.isLoggable)
    }

    private fun validDraft() = ManualFoodDraft("Pasta", "", "250.5", FoodUnit.Grams,
        "420", "20", "65", "9")
}
