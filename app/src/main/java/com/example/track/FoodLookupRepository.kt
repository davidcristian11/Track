package com.example.track

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

class FoodLookupRepository(private val client: OpenFoodFactsClient = OpenFoodFactsClient()) {
    private val searchGate = Mutex()
    private var lastSearchNanos: Long? = null

    suspend fun search(query: String): List<FoodDefinition> {
        // OFF allows 10 search requests/minute. Debounce alone cannot enforce this.
        searchGate.withLock {
            lastSearchNanos?.let { previous ->
                delay((6_100 - (System.nanoTime() - previous) / 1_000_000).coerceAtLeast(0))
            }
            lastSearchNanos = System.nanoTime()
        }
        val response = client.search(query)
        return withContext(Dispatchers.Default) { OpenFoodFactsMapper.search(response) }
    }

    suspend fun lookupBarcode(code: String): FoodDefinition? {
        val response = client.lookupBarcode(code)
        return withContext(Dispatchers.Default) { OpenFoodFactsMapper.barcode(response, code) }
    }
}

/** Only normalized, as-sold nutrient fields; never infer a missing nutrient from zero. */
internal object OpenFoodFactsMapper {
    fun search(json: String): List<FoodDefinition> {
        val products = JSONObject(json).optJSONArray("products") ?: throw IOException("Invalid food search response")
        return (0 until minOf(products.length(), 15)).mapNotNull { index ->
            products.optJSONObject(index)?.let { product(it) }
        }.distinctBy { it.id }
    }

    fun barcode(json: String, requestedCode: String): FoodDefinition? {
        val response = JSONObject(json)
        if (response.optInt("status", -1) == 0) return null
        if (response.optInt("status", -1) != 1) throw IOException("Invalid barcode response")
        return product(response.optJSONObject("product") ?: throw IOException("Missing product"), requestedCode)
            ?: throw IOException("Invalid product identity")
    }

    fun product(json: JSONObject, fallbackCode: String? = null): FoodDefinition? {
        val code = json.text("code") ?: fallbackCode ?: return null
        if (!code.all { it in '0'..'9' } || code.length !in 8..14) return null
        val name = json.text("product_name") ?: json.text("product_name_en") ?: "Product $code"
        val serving = servingBasis(json)
        val nutrients = json.optJSONObject("nutriments")
        val servingNutrition = serving?.let { nutrients?.nutrition("serving", it.first) }
        val per100 = nutrients?.nutrition("100g", 100.0)
        val useServing = serving != null && servingNutrition != null
        val amount = if (useServing) requireNotNull(serving).first else 100.0
        // The _100g suffix also represents 100 ml when OFF explicitly supplies an ml basis.
        // Package quantity alone is never used to infer density or serving size.
        val unit = if (useServing || serving?.second == FoodUnit.Milliliters) serving!!.second else FoodUnit.Grams
        val nutrition = if (useServing) servingNutrition else per100
        return FoodDefinition(
            id = "off_$code", name = name,
            brand = json.text("brands"),
            defaultAmount = amount.roundToInt().coerceIn(1, MaxFoodAmount),
            basisAmount = amount, unit = unit, basisNutrition = nutrition,
            servingLabel = if (nutrition != null) "${formatNutrient(amount.toFloat())} ${unit.symbol}" else null,
            source = FoodSource.OPEN_FOOD_FACTS, barcode = code,
        )
    }

    private fun servingBasis(json: JSONObject): Pair<Double, FoodUnit>? {
        val quantity = json.number("serving_quantity")?.takeIf { it > 0 && it <= MaxFoodAmount }
        val explicitUnit = json.text("serving_quantity_unit")
        val unit = when (explicitUnit?.lowercase()) {
            "g" -> FoodUnit.Grams
            "ml" -> FoodUnit.Milliliters
            null -> null
            else -> return null
        }
        if (quantity != null && unit != null) return quantity to unit
        // Older products often only have serving_size. Parse an unambiguous g/ml label,
        // including "1 pot (150 g)"; do not convert cups, pieces, oz, or package quantity.
        val label = json.text("serving_size") ?: return null
        val matches = Regex("(?i)(?<![\\d.,])([0-9]+(?:[.,][0-9]+)?)\\s*(g|ml)\\b").findAll(label).toList()
        if (matches.size != 1) return null
        val match = matches.single()
        val parsed = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (parsed <= 0 || parsed > MaxFoodAmount || (quantity != null && kotlin.math.abs(quantity - parsed) > 0.01)) return null
        val parsedUnit = if (match.groupValues[2].equals("ml", true)) FoodUnit.Milliliters else FoodUnit.Grams
        if (unit != null && unit != parsedUnit) return null
        return parsed to parsedUnit
    }

    private fun JSONObject.nutrition(suffix: String, amount: Double): FoodNutrition? {
        val kcal = number("energy-kcal_$suffix")
            ?: (number("energy-kj_$suffix") ?: number("energy_$suffix"))?.div(4.184)
            ?: return null
        val protein = number("proteins_$suffix") ?: return null
        val carbs = number("carbohydrates_$suffix") ?: return null
        val fat = number("fat_$suffix") ?: return null
        // Reject corrupt magnitudes before amount scaling can overflow a snapshot.
        if (kcal / amount > 100 || listOf(protein, carbs, fat).any { it / amount > 10 }) return null
        return FoodNutrition(kcal, protein, carbs, fat)
    }

    private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    private fun JSONObject.number(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }
}
