package com.example.track

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenFoodFactsTest {
    private fun product(extra: String = "", nutrients: String = complete100) = JSONObject(
        """{"code":"1234567890128","product_name":"Example food","brands":"Example brand",
            $extra "nutriments":{$nutrients}}""",
    )
    private fun mapped(json: JSONObject = product()) = requireNotNull(OpenFoodFactsMapper.product(json))

    @Test fun complete100gAndAmountScaling() {
        val food = mapped()
        assertEquals(100.0, food.basisAmount, 0.0)
        assertEquals(FoodUnit.Grams, food.unit)
        assertEquals(NutritionTotals(300, 15f, 30f, 7.5f), food.nutritionFor(150))
        assertEquals(food.nutritionFor(150), LoggedFood.snapshot(0, MealContext.LUNCH, food, 150).nutrition)
    }

    @Test fun supportedServingTakesPrecedenceAndKeepsMl() {
        val food = mapped(product(""""serving_quantity":250,"serving_quantity_unit":"ml",""",
            """$complete100,"energy-kcal_serving":105.5,"proteins_serving":0,"carbohydrates_serving":26.5,"fat_serving":0"""))
        assertEquals(250.0, food.basisAmount, 0.0)
        assertEquals(250, food.defaultAmount)
        assertEquals(FoodUnit.Milliliters, food.unit)
        assertEquals(NutritionTotals(211, 0f, 53f, 0f), food.nutritionFor(500))
    }

    @Test fun explicitZerosAreValidAndMissingIsNotZero() {
        val zero = mapped(product(nutrients = """"energy-kcal_100g":0,"proteins_100g":0,"carbohydrates_100g":0,"fat_100g":0"""))
        assertTrue(zero.isLoggable)
        assertEquals(NutritionTotals(), zero.nutritionFor(500))
        for (missing in listOf("energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")) {
            val json = product().apply { getJSONObject("nutriments").remove(missing) }
            val food = mapped(json)
            assertFalse(food.isLoggable)
            assertNull(food.basisNutrition)
            assertTrue(food.searchMetadata.contains("Nutrition data incomplete"))
            assertThrows(IllegalArgumentException::class.java) { LoggedFood.snapshot(0, MealContext.LUNCH, food, 100) }
        }
    }

    @Test fun missingBrandAndNameHaveSafeFallbacks() {
        val json = product().apply { put("brands", " "); put("product_name", " "); put("product_name_en", "English name") }
        assertEquals("English name", mapped(json).name)
        assertNull(mapped(json).brand)
        json.remove("product_name_en")
        assertEquals("Product 1234567890128", mapped(json).name)
    }

    @Test fun barcodeAndSourceIdentityAreSharedAcrossSearchAndLookup() {
        val food = mapped()
        val lookup = OpenFoodFactsMapper.barcode("""{"status":1,"product":${product()}}""", "1234567890128")
        assertEquals(food, lookup)
        assertEquals("off_1234567890128", food.id)
        assertEquals("1234567890128", food.barcode)
        assertEquals(FoodSource.OPEN_FOOD_FACTS, food.source)
        val missingCode = product().apply { remove("code") }
        assertEquals(food, OpenFoodFactsMapper.barcode("""{"status":1,"product":$missingCode}""", "1234567890128"))
    }

    @Test fun unsupportedServingFallsBackWithoutConvertingUnits() {
        val json = product(""""serving_quantity":1,"serving_quantity_unit":"cups","serving_size":"1 cup","quantity":"1 l",""",
            """$complete100,"energy-kcal_serving":50,"proteins_serving":5,"carbohydrates_serving":10,"fat_serving":2""")
        assertEquals(100.0, mapped(json).basisAmount, 0.0)
        assertEquals(FoodUnit.Grams, mapped(json).unit)
        json.getJSONObject("nutriments").remove("fat_100g")
        assertFalse(mapped(json).isLoggable)
    }

    @Test fun legacyServingLabelAndFractionalBasisKeepPrecision() {
        val food = mapped(product(""""serving_size":"1 pot (12.5 g)","serving_quantity":12.5,""",
            """"energy-kcal_serving":20.25,"proteins_serving":1.25,"carbohydrates_serving":2.5,"fat_serving":0.5"""))
        assertEquals(12.5, food.basisAmount, 0.0)
        assertEquals(NutritionTotals(81, 5f, 10f, 2f), food.nutritionFor(50))
    }

    @Test fun explicitMlBasisCanUse100NutritionButPackageVolumeAloneCannot() {
        val json = product(""""serving_size":"250 ml","serving_quantity":250,""")
        assertEquals(FoodUnit.Milliliters, mapped(json).unit)
        assertEquals(100.0, mapped(json).basisAmount, 0.0)
        assertEquals(FoodUnit.Grams, mapped(product(""""quantity":"500 ml",""")).unit)
    }

    @Test fun kjConvertsToKcalAndMalformedNutrientsAreUnavailable() {
        val json = product().apply { getJSONObject("nutriments").remove("energy-kcal_100g") }
        json.getJSONObject("nutriments").put("energy_100g", 836.8)
        assertEquals(200, mapped(json).nutritionFor(100).calories)
        for (bad in listOf(JSONObject.NULL, "unknown", "NaN", -1, 1e30)) {
            json.getJSONObject("nutriments").put("fat_100g", bad)
            assertFalse(mapped(json).isLoggable)
        }
    }

    @Test fun localYogurtAndScannerFixtureRemainUnchanged() {
        assertEquals(NutritionTotals(70, 11.9f, 4.3f, 0.5f), LocalFoodCatalog.first().nutritionFor(119))
        assertEquals(NutritionTotals(), ScannedFood.nutritionFor(500))
    }

    @Test fun fakeTransportTestsRepositoryEndpointsDeduplicationAndUnknownBarcode() = runBlocking {
        val urls = mutableListOf<okhttp3.HttpUrl>()
        val client = OpenFoodFactsClient { url ->
            urls += url
            when {
                url.encodedPath == "/cgi/search.pl" -> """{"products":[${product()},null,{},${product()}]}"""
                url.encodedPath.contains("1234567890128") -> """{"status":1,"product":${product()}}"""
                else -> """{"status":0}"""
            }
        }
        val repository = FoodLookupRepository(client)
        assertEquals(listOf(mapped()), repository.search("coca cola & skyr"))
        assertEquals(mapped(), repository.lookupBarcode("1234567890128"))
        assertNull(repository.lookupBarcode("00000000"))
        assertEquals("coca cola & skyr", urls.first().queryParameter("search_terms"))
        assertNull(urls.first().queryParameter("search_term"))
        assertEquals("15", urls.first().queryParameter("page_size"))
        assertEquals(OpenFoodFactsClient.Fields, urls.first().queryParameter("fields"))
        assertEquals("/api/v2/product/1234567890128.json", urls[1].encodedPath)
    }

    @Test fun emptySearchMalformedResponsesAndNetworkFailureAreDistinct() = runBlocking {
        assertTrue(OpenFoodFactsMapper.search("""{"products":[]}""").isEmpty())
        assertThrows(IOException::class.java) { OpenFoodFactsMapper.search("{}") }
        assertThrows(IOException::class.java) { OpenFoodFactsMapper.barcode("{}", "1234567890128") }
        val repository = FoodLookupRepository(OpenFoodFactsClient { throw IOException("offline") })
        try {
            repository.lookupBarcode("1234567890128")
            fail("Expected network failure")
        } catch (_: IOException) { /* ViewModel exposes this as unavailable. */ }
    }

    companion object {
        private const val complete100 = """"energy-kcal_100g":200,"proteins_100g":10,"carbohydrates_100g":20,"fat_100g":5"""
    }
}
