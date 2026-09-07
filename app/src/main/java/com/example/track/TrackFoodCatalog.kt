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

enum class FoodSource { LOCAL, OPEN_FOOD_FACTS }

// Keep API precision until the final displayed/logged snapshot is rounded.
data class FoodNutrition(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

data class FoodDefinition(
    val id: String,
    val name: String,
    val defaultAmount: Int,
    val basisNutrition: FoodNutrition?,
    val brand: String? = null,
    val servingLabel: String? = null,
    val unit: FoodUnit = FoodUnit.Grams,
    val basisAmount: Double = 100.0,
    val source: FoodSource = FoodSource.LOCAL,
    val barcode: String? = null,
) {
    val isLoggable: Boolean get() = basisNutrition != null
    val basisLabel: String get() = "${formatNutrient(basisAmount.toFloat())} ${unit.symbol}"
    val searchMetadata: String
        get() = listOfNotNull(
            brand,
            if (isLoggable) servingLabel ?: "$defaultAmount ${unit.symbol}" else null,
            if (isLoggable) "${nutritionFor(defaultAmount).calories} kcal" else "Nutrition data incomplete",
        ).joinToString(" · ")

    fun nutritionFor(amount: Int): NutritionTotals {
        require(amount in 0..MaxFoodAmount)
        val nutrition = requireNotNull(basisNutrition) { "Nutrition data incomplete" }
        val factor = amount / basisAmount
        // Log the same whole kcal / tenth-gram values shown in Food Details.
        fun scaledMacro(value: Double) = (value * factor * 10).roundToInt() / 10f
        return NutritionTotals(
            calories = (nutrition.calories * factor).roundToInt(),
            proteinGrams = scaledMacro(nutrition.proteinGrams),
            carbsGrams = scaledMacro(nutrition.carbsGrams),
            fatGrams = scaledMacro(nutrition.fatGrams),
        )
    }
}

// Local demo catalog. Preserve the original Float values when widening to the shared basis.
// Serving kcal and rounding stay identical to the original local calculations.
val LocalFoodCatalog = listOf(
    FoodDefinition(
        id = "greek_yogurt",
        name = "Greek Yogurt 0%",
        defaultAmount = 119,
        basisNutrition = FoodNutrition(59.0, 10.0, 3.6f.toDouble(), 0.4f.toDouble()),
        brand = "Fage",
    ),
    FoodDefinition(
        id = "chicken_breast",
        name = "Chicken Breast",
        defaultAmount = 150,
        basisNutrition = FoodNutrition(110.0, 23.0, 0.0, 2.0),
    ),
    FoodDefinition(
        id = "banana",
        name = "Banana",
        defaultAmount = 118,
        basisNutrition = FoodNutrition(89.0, 1.1f.toDouble(), 22.8f.toDouble(), 0.3f.toDouble()),
        servingLabel = "1 medium",
    ),
)

// Scanner-only demo result: keep the existing text-search catalog unchanged.
val ScannedFood = FoodDefinition(
    id = "coca_cola_zero",
    name = "Coca-Cola Zero",
    defaultAmount = 500,
    basisNutrition = FoodNutrition(0.0, 0.0, 0.0, 0.0),
    servingLabel = "500 ml bottle",
    unit = FoodUnit.Milliliters,
)

internal fun findLocalFood(id: String?): FoodDefinition? =
    LocalFoodCatalog.firstOrNull { it.id == id } ?: ScannedFood.takeIf { it.id == id }

internal fun formatNutrient(value: Float): String = formatDecimal(value).removeSuffix(".0")
