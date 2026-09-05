package com.example.track

import kotlin.math.roundToInt

data class NutritionTotals(
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
) {
    operator fun plus(other: NutritionTotals) = NutritionTotals(
        calories + other.calories,
        proteinGrams + other.proteinGrams,
        carbsGrams + other.carbsGrams,
        fatGrams + other.fatGrams,
    )
}

const val MaxFoodAmountGrams = 10_000

data class FoodDefinition(
    val id: String,
    val name: String,
    val defaultAmountGrams: Int,
    val per100Grams: NutritionTotals,
    val brand: String? = null,
    val servingLabel: String? = null,
) {
    val searchMetadata: String
        get() = listOfNotNull(
            brand,
            servingLabel ?: "$defaultAmountGrams g",
            "${nutritionFor(defaultAmountGrams).calories} kcal",
        ).joinToString(" · ")

    fun nutritionFor(amountGrams: Int): NutritionTotals {
        require(amountGrams in 0..MaxFoodAmountGrams)
        val factor = amountGrams / 100.0
        // Log the same whole kcal / tenth-gram values shown in Food Details.
        fun scaledMacro(value: Float) = (value * factor * 10).roundToInt() / 10f
        return NutritionTotals(
            calories = (per100Grams.calories * factor).roundToInt(),
            proteinGrams = scaledMacro(per100Grams.proteinGrams),
            carbsGrams = scaledMacro(per100Grams.carbsGrams),
            fatGrams = scaledMacro(per100Grams.fatGrams),
        )
    }
}

// Local demo catalog, not an external nutrition database. Serving kcal match Search.
val LocalFoodCatalog = listOf(
    FoodDefinition(
        id = "greek_yogurt",
        name = "Greek Yogurt 0%",
        defaultAmountGrams = 119,
        per100Grams = NutritionTotals(59, 10f, 3.6f, 0.4f),
        brand = "Fage",
    ),
    FoodDefinition(
        id = "chicken_breast",
        name = "Chicken Breast",
        defaultAmountGrams = 150,
        per100Grams = NutritionTotals(110, 23f, 0f, 2f),
    ),
    FoodDefinition(
        id = "banana",
        name = "Banana",
        defaultAmountGrams = 118,
        per100Grams = NutritionTotals(89, 1.1f, 22.8f, 0.3f),
        servingLabel = "1 medium",
    ),
)

internal fun formatNutrient(value: Float): String = formatDecimal(value).removeSuffix(".0")
