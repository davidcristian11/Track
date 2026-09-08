package com.example.track

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FoodDiscoveryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val product = requireNotNull(OpenFoodFactsMapper.barcode("""{"status":1,"product":{
        "code":"1234567890128","product_name":"Fixture drink","brands":"Fixture brand",
        "serving_quantity":250,"serving_quantity_unit":"ml","nutriments":{
        "energy-kcal_serving":100,"proteins_serving":2,"carbohydrates_serving":20,"fat_serving":1}}}""", "1234567890128"))

    @Test fun localResultsStaySelectableWithRemoteErrorAndQueryEditableWhileLoading() {
        val state = mutableStateOf(FoodSearchState())
        var selected: FoodDefinition? = null
        compose.setContent { TrackTheme {
            AddFoodSearchScreen({}, { selected = it }, {}, search = state.value,
                onSearch = { query -> state.value = FoodSearchState(query,
                    LocalFoodCatalog.filter { it.name.contains(query, true) }, loading = true) })
        } }
        compose.onNodeWithText("Search foods").performTextReplacement("chicken")
        compose.onNodeWithText("Searching Open Food Facts…").assertIsDisplayed()
        compose.onNodeWithText("Chicken Breast").assertIsDisplayed()
        compose.onNodeWithText("chicken").performTextReplacement("banana")
        compose.onNodeWithText("Banana").assertIsDisplayed()
        compose.onNodeWithText("Chicken Breast").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(loading = false, unavailable = true) }
        compose.onNodeWithText("Online search unavailable").assertIsDisplayed()
        compose.onNodeWithText("Banana").performClick()
        assertEquals("banana", selected?.id)
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Online search unavailable").assertDoesNotExist()
        compose.onNodeWithText("Banana").assertIsDisplayed()
    }

    @Test fun scannerResultUsesMappedAmountMealAndRoomSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        try {
            compose.setContent {
                TrackTheme {
                    BarcodeScannerScreen(MealContext.LUNCH, {}, { meal, food, amount ->
                        runBlocking { TrackRepository(database).addFood("2026-09-05", meal, food, amount) }
                    }, state = ScannerState.Found(product))
                }
            }
            compose.onNodeWithText("Fixture drink").assertIsDisplayed()
            compose.onNodeWithContentDescription("Scanned amount, ml").performScrollTo().performTextReplacement("500")
            compose.onNodeWithContentDescription("Choose scanner meal").performScrollTo().performClick()
            compose.onNodeWithText("Dinner").performClick()
            compose.onNodeWithText("Add to Dinner").performScrollTo().performClick()
            compose.waitForIdle()
            val food = runBlocking {
                withTimeout(5_000) { database.trackDao().observeFoodLogs("2026-09-05").first { it.isNotEmpty() }.single() }
            }
            assertEquals("DINNER", food.meal)
            assertEquals(500, food.amount)
            assertEquals("ml", food.unit)
            assertEquals(200, food.calories)
            assertEquals(4f, food.proteinGrams)
        } finally { database.close() }
    }

    @Test fun incompleteDetailsDisableAdd() {
        compose.setContent {
            TrackTheme { FoodDetailsScreen(MealContext.LUNCH, product.copy(basisNutrition = null), {}, {}) }
        }
        compose.onNodeWithText("Add to Lunch").assertIsNotEnabled()
    }

    @Test fun incompleteScannerDisablesAddAndOffersRecovery() {
        compose.setContent {
            TrackTheme { BarcodeScannerScreen(MealContext.LUNCH, {}, { _, _, _ -> },
                state = ScannerState.Found(product.copy(basisNutrition = null))) }
        }
        compose.onNodeWithText("Nutrition data incomplete").assertIsDisplayed()
        compose.onNodeWithText("Add to Lunch").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Search by name").performScrollTo().assertIsDisplayed()
    }

    @Test fun scannerUnknownProductOffersRescanAndSearch() {
        compose.setContent {
            TrackTheme { BarcodeScannerScreen(MealContext.LUNCH, {}, { _, _, _ -> },
                state = ScannerState.NotFound("00000000")) }
        }
        compose.onNodeWithText("Product not found").assertIsDisplayed()
        compose.onNodeWithText("Try scanning again").assertIsDisplayed()
        compose.onNodeWithText("Search by name").assertIsDisplayed()
    }
}
