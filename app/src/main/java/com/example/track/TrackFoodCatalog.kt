package com.example.track

import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class NutritionTotals(
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
) {
    operator fun plus(other: NutritionTotals) = NutritionTotals(
        safeCalorieTotal(listOf(calories, other.calories)),
        safeMacroTotal(proteinGrams, other.proteinGrams),
        safeMacroTotal(carbsGrams, other.carbsGrams),
        safeMacroTotal(fatGrams, other.fatGrams),
    )
}

const val MaxFoodAmount = 10_000
internal const val MinFoodReferenceAmount = 0.1

internal fun safeCalorieTotal(values: Iterable<Int>): Int = values.fold(0L) { total, value ->
    (total + value.coerceAtLeast(0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong())
}.toInt()

private fun safeMacroTotal(first: Float, second: Float): Float {
    val left = first.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
    val right = second.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
    return (left + right).coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
}

internal val NutritionTotals.isSafeForPersistence: Boolean
    get() = calories >= 0 && listOf(proteinGrams, carbsGrams, fatGrams).all { it.isFinite() && it >= 0f }

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
    val isLoggable: Boolean get() = nutritionForOrNull(defaultAmount) != null
    val basisLabel: String get() = "${formatNutrient(basisAmount.toFloat())} ${unit.symbol}"
    val searchMetadata: String
        get() = listOfNotNull(
            brand,
            if (isLoggable) servingLabel ?: "$defaultAmount ${unit.symbol}" else null,
            if (isLoggable) "${nutritionFor(defaultAmount).calories} kcal" else "Nutrition data incomplete",
        ).joinToString(" · ")

    fun nutritionForOrNull(amount: Int): NutritionTotals? =
        basisNutrition?.nutritionForOrNull(amount, basisAmount)

    fun nutritionFor(amount: Int): NutritionTotals = requireNotNull(nutritionForOrNull(amount)) {
        "Amount or nutrition values cannot be scaled safely"
    }
}

internal fun FoodNutrition.nutritionForOrNull(amount: Int, basisAmount: Double): NutritionTotals? {
    if (amount !in 0..MaxFoodAmount || !basisAmount.isFinite() ||
        basisAmount !in MinFoodReferenceAmount..MaxFoodAmount.toDouble()) return null
    if (listOf(calories, proteinGrams, carbsGrams, fatGrams)
            .any { !it.isFinite() || it < 0.0 }) return null
    val factor = amount / basisAmount
    if (!factor.isFinite()) return null
    // Log the same whole kcal / tenth-gram values shown in Food Details.
    fun scaledMacro(value: Double): Float? {
        val scaledTenths = value * factor * 10.0
        if (!scaledTenths.isFinite()) return null
        val rounded = round(scaledTenths) / 10.0
        return rounded.takeIf { it.isFinite() && it in 0.0..Float.MAX_VALUE.toDouble() }?.toFloat()
    }
    val scaledCalories = calories * factor
    if (!scaledCalories.isFinite()) return null
    val roundedCalories = scaledCalories.roundToLong()
    if (roundedCalories !in 0..Int.MAX_VALUE.toLong()) return null
    return NutritionTotals(
        calories = roundedCalories.toInt(),
        proteinGrams = scaledMacro(proteinGrams) ?: return null,
        carbsGrams = scaledMacro(carbsGrams) ?: return null,
        fatGrams = scaledMacro(fatGrams) ?: return null,
    )
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
