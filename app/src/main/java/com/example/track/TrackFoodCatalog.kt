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

const val MaxFoodAmount = 10_000

enum class FoodUnit(val symbol: String, val spokenLabel: String) {
    Grams("g", "grams"),
    Milliliters("ml", "milliliters"),
}

data class FoodDefinition(
    val id: String,
    val name: String,
    val defaultAmount: Int,
    val per100Units: NutritionTotals,
    val brand: String? = null,
    val servingLabel: String? = null,
    val unit: FoodUnit = FoodUnit.Grams,
) {
    val searchMetadata: String
        get() = listOfNotNull(
            brand,
            servingLabel ?: "$defaultAmount ${unit.symbol}",
            "${nutritionFor(defaultAmount).calories} kcal",
        ).joinToString(" · ")

    fun nutritionFor(amount: Int): NutritionTotals {
        require(amount in 0..MaxFoodAmount)
        val factor = amount / 100.0
        // Log the same whole kcal / tenth-gram values shown in Food Details.
        fun scaledMacro(value: Float) = (value * factor * 10).roundToInt() / 10f
        return NutritionTotals(
            calories = (per100Units.calories * factor).roundToInt(),
            proteinGrams = scaledMacro(per100Units.proteinGrams),
            carbsGrams = scaledMacro(per100Units.carbsGrams),
            fatGrams = scaledMacro(per100Units.fatGrams),
        )
    }
}

// Local demo catalog, not an external nutrition database. Serving kcal match Search.
val LocalFoodCatalog = listOf(
    FoodDefinition(
        id = "greek_yogurt",
        name = "Greek Yogurt 0%",
        defaultAmount = 119,
        per100Units = NutritionTotals(59, 10f, 3.6f, 0.4f),
        brand = "Fage",
    ),
    FoodDefinition(
        id = "chicken_breast",
        name = "Chicken Breast",
        defaultAmount = 150,
        per100Units = NutritionTotals(110, 23f, 0f, 2f),
    ),
    FoodDefinition(
        id = "banana",
        name = "Banana",
        defaultAmount = 118,
        per100Units = NutritionTotals(89, 1.1f, 22.8f, 0.3f),
        servingLabel = "1 medium",
    ),
)

// Scanner-only demo result: keep the existing text-search catalog unchanged.
val ScannedFood = FoodDefinition(
    id = "coca_cola_zero",
    name = "Coca-Cola Zero",
    defaultAmount = 500,
    per100Units = NutritionTotals(),
    servingLabel = "500 ml bottle",
    unit = FoodUnit.Milliliters,
)

internal fun findLocalFood(id: String?): FoodDefinition? =
    LocalFoodCatalog.firstOrNull { it.id == id } ?: ScannedFood.takeIf { it.id == id }

internal fun formatNutrient(value: Float): String = formatDecimal(value).removeSuffix(".0")
