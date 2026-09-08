package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackGoalSuggestionsTest {
    @Test fun directionUsesInclusiveOneKilogramMaintenanceRange() {
        assertEquals(WeightGoalDirection.Lose, weightGoalDirection(75.0, 73.0))
        assertEquals(WeightGoalDirection.Lose, weightGoalDirection(75.0, 73.99))
        assertEquals(WeightGoalDirection.Maintain, weightGoalDirection(75.0, 74.0))
        assertEquals(WeightGoalDirection.Maintain, weightGoalDirection(75.0, 75.0))
        assertEquals(WeightGoalDirection.Maintain, weightGoalDirection(75.0, 76.0))
        assertEquals(WeightGoalDirection.Gain, weightGoalDirection(75.0, 76.01))
        assertEquals(WeightGoalDirection.Gain, weightGoalDirection(75.0, 80.0))
    }

    @Test fun directionHandlesDecimalWeightsWithoutExactEquality() {
        assertEquals(WeightGoalDirection.Lose, weightGoalDirection(75.25, 74.249))
        assertEquals(WeightGoalDirection.Maintain, weightGoalDirection(75.25, 74.25))
        assertEquals(WeightGoalDirection.Gain, weightGoalDirection(75.25, 76.251))
    }

    @Test fun invalidWeightsHaveNoDirection() {
        assertNull(weightGoalDirection(Double.NaN, 75.0))
        assertNull(weightGoalDirection(75.0, Double.POSITIVE_INFINITY))
        assertNull(weightGoalDirection(0.0, 75.0))
        assertNull(weightGoalDirection(75.0, -1.0))
    }

    @Test fun seventyFiveKilogramsProducesDeterministicDirectionSpecificSuggestions() {
        val lose = suggestedNutritionTargets(75.0, WeightGoalDirection.Lose)
        val maintain = suggestedNutritionTargets(75.0, WeightGoalDirection.Maintain)
        val gain = suggestedNutritionTargets(75.0, WeightGoalDirection.Gain)

        assertEquals(SuggestedNutritionTargets(1_950, 135, 218, 60), lose)
        assertEquals(SuggestedNutritionTargets(2_250, 135, 293, 60), maintain)
        assertEquals(SuggestedNutritionTargets(2_550, 135, 368, 60), gain)
        assertEquals(gain, suggestedNutritionTargets(75.0, WeightGoalDirection.Gain))
    }

    @Test fun lowerBoundPreventsNegativeCaloriesAndCarbohydrates() {
        val lowWeight = requireNotNull(suggestedNutritionTargets(0.01, WeightGoalDirection.Lose))
        assertEquals(1_200, lowWeight.calories)
        val highWeight = requireNotNull(suggestedNutritionTargets(1_000.0, WeightGoalDirection.Lose))
        assertTrue(highWeight.calories > 0)
        assertTrue(highWeight.carbsGrams >= 0)
        assertTrue(highWeight.proteinGrams > 0)
        assertTrue(highWeight.fatGrams > 0)
    }

    @Test fun invalidSuggestionInputIsRejected() {
        assertNull(suggestedNutritionTargets(0.0, WeightGoalDirection.Maintain))
        assertNull(suggestedNutritionTargets(-75.0, WeightGoalDirection.Maintain))
        assertNull(suggestedNutritionTargets(Double.NaN, WeightGoalDirection.Maintain))
        assertNull(suggestedNutritionTargets(Double.POSITIVE_INFINITY, WeightGoalDirection.Maintain))
    }

    @Test fun suggestionHasNoTargetWeightToMutate() {
        val fields = SuggestedNutritionTargets::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("weight", ignoreCase = true) })
    }
}
