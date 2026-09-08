package com.example.track

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

class FoodLookupRepository(
    private val client: OpenFoodFactsClient = OpenFoodFactsClient(),
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val searchGate = Mutex()
    private var nextSearchMillis: Long? = null

    suspend fun search(query: String): List<FoodDefinition> {
        repeat(3) { attempt ->
            // Every attempt, including manual retries/new queries, shares OFF's 10/min gate.
            searchGate.withLock {
                nextSearchMillis?.let { pause((it - nowMillis()).coerceAtLeast(0)) }
                currentCoroutineContext().ensureActive()
                nextSearchMillis = nowMillis() + 6_100
            }
            val response = try {
                client.search(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val transient = when (error) {
                    is InvalidFoodResponseException -> false
                    is FoodHttpException -> error.status == 408 || error.status == 429 || error.status in 500..599
                    else -> true
                }
                val backoff = maxOf(1_000L shl attempt, (error as? FoodHttpException)?.retryAfterMillis ?: 0)
                if (transient) searchGate.withLock {
                    nextSearchMillis = maxOf(nextSearchMillis ?: nowMillis(), nowMillis() + backoff)
                }
                // Long server cooldowns survive new queries/manual Retry, but don't hold
                // the current automatic search loading indefinitely.
                if (!transient || attempt == 2 || backoff > 30_000) throw error
                return@repeat
            }
            currentCoroutineContext().ensureActive()
            // Invalid JSON/envelopes are not transport failures and must not be retried.
            return withContext(Dispatchers.Default) { OpenFoodFactsMapper.search(response) }
        }
        error("Search attempts exhausted")
    }

    suspend fun lookupBarcode(code: String): FoodDefinition? {
        val response = client.lookupBarcode(code)
        return withContext(Dispatchers.Default) { OpenFoodFactsMapper.barcode(response, code) }
    }
}

/** Only normalized, as-sold nutrient fields; never infer a missing nutrient from zero. */
internal object OpenFoodFactsMapper {
    fun search(json: String): List<FoodDefinition> {
        val products = JSONObject(json).optJSONArray("products") ?: throw InvalidFoodResponseException("Invalid food search response")
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
