package com.example.track

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NutritionFoodNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: TrackDatabase
    private lateinit var vm: TrackViewModel
    private lateinit var directory: File
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val day = LocalDate.of(2026, 10, 4)

    @Before fun start() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        directory = File(context.cacheDir, "food-navigation-${UUID.randomUUID()}").apply { mkdir() }
        val settings = TrackSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "settings.preferences_pb")
        })
        compose.runOnUiThread {
            vm = TrackViewModel(TrackRepository(database), settings,
                FoodLookupRepository(OpenFoodFactsClient { """{"products":[]}""" })) { day }
            store.put("track", vm)
        }
        compose.setContent { TrackTheme { TrackApp(vm) } }
    }

    @After fun close() {
        compose.runOnUiThread { store.clear() }
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
        directory.deleteRecursively()
    }

    private fun nutrition() { compose.onNode(hasText("Nutrition") and isSelectable()).performClick() }
    private fun picker() {
        compose.onNodeWithContentDescription("Choose meal to add food").performScrollTo().performClick()
        compose.onNodeWithText("Choose a meal").assertIsDisplayed()
        MealContext.entries.forEach { compose.onNodeWithContentDescription("Choose ${it.label}").assertIsDisplayed() }
    }
    private fun details(meal: MealContext) {
        compose.onNodeWithText("Search foods").performTextInput("chicken")
        compose.onNodeWithText("Chicken Breast").performClick()
        compose.onNodeWithText("Add to ${meal.label}").assertIsDisplayed()
    }
    private fun fillManualFood(name: String = "Homemade Oatmeal") {
        fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
        field("Food name").performTextInput(name)
        field("Reference amount").performTextInput("300")
        field("Calories").performTextInput("450")
        field("Protein").performTextInput("20")
        field("Carbs").performTextInput("60")
        field("Fat").performTextInput("12")
    }
    private fun foodTime(hour: String, minute: String, period: String) {
        compose.onNodeWithContentDescription("Choose food time").performScrollTo().performClick()
        compose.onNodeWithText("Enter time").performClick()
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performClick().performTextReplacement(minute)
        compose.onAllNodesWithText(period).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onNodeWithText(period).performClick()
        }
        compose.onNodeWithText("Set time").performClick()
    }

    @Test fun bottomPickerLunchAndDinnerPersistAndReturnToNutrition() {
        nutrition()
        for (meal in listOf(MealContext.LUNCH, MealContext.DINNER)) {
            picker()
            compose.onNodeWithContentDescription("Choose ${meal.label}").performClick()
            details(meal)
            compose.onNodeWithText("Add to ${meal.label}").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Nutrition") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            val logged = runBlocking { withTimeout(5_000) {
                database.trackDao().observeFoodLogs(day.toDayKey()).first { rows -> rows.any { it.meal == meal.name } }
            } }
            assertEquals(1, logged.count { it.meal == meal.name })
            compose.onAllNodesWithText("Chicken Breast").onLast().performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun dismissPickerLeavesNutritionAndDoesNotWrite() {
        nutrition()
        picker()
        pressBack()
        compose.onNodeWithText("Choose a meal").assertDoesNotExist()
        compose.onNode(hasText("Nutrition") and isSelectable()).assertIsDisplayed()
        assertTrue(runBlocking { database.trackDao().observeFoodLogs(day.toDayKey()).first().isEmpty() })
    }

    @Test fun individualMealActionsKeepContextAndBackReturnsToSearch() {
        nutrition()
        for (meal in MealContext.entries) {
            compose.onNodeWithContentDescription("Add food to ${meal.label}").performScrollTo().performClick()
            details(meal)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(hasSetTextAction()).assertTextContains("chicken")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(hasText("Nutrition") and isSelectable()).assertIsDisplayed()
        }
    }

    @Test fun todayOriginAndMainTabsAndScannerStillOpen() {
        compose.onNodeWithText("Add Food").performScrollTo().performClick()
        details(MealContext.LUNCH)
        compose.onNodeWithText("Add to Lunch").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Today") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Today") and isSelectable()).assertIsDisplayed()
        for (tab in listOf("Nutrition", "Activity", "Progress", "Today")) {
            compose.onNode(hasText(tab) and isSelectable()).performClick().assertIsSelected()
        }
        compose.onNodeWithText("Add Food").performScrollTo().performClick()
        val cameraGranted = InstrumentationRegistry.getInstrumentation().targetContext
            .checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        compose.onNodeWithContentDescription("Barcode scanner").performClick()
        if (!cameraGranted) {
            // The system permission dialog owns focus outside the Compose hierarchy.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            var deny: android.view.accessibility.AccessibilityNodeInfo? = null
            compose.waitUntil(5_000) {
                val root = automation.rootInActiveWindow
                deny = root?.findAccessibilityNodeInfosByText("Don’t allow")?.firstOrNull()
                    ?: root?.findAccessibilityNodeInfosByText("Don't allow")?.firstOrNull()
                deny != null || runCatching {
                    compose.onAllNodesWithText("Open Settings").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            deny?.let { check(it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) }
        }
        compose.waitUntil(5_000) {
            runCatching { compose.onAllNodesWithText("Scanner").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Scanner").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add Food").assertIsDisplayed()
    }

    @Test fun manualFoodAddsToCurrentMealThenAppearsInRoomBackedRecents() {
        nutrition()
        compose.onNodeWithContentDescription("Add food to Dinner").performScrollTo().performClick()
        compose.onNodeWithText("No recent foods yet").assertIsDisplayed()
        compose.onNodeWithText("Create Food").performClick()
        fillManualFood()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Add to Dinner").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { runBlocking {
            database.trackDao().observeFoodLogs(day.toDayKey()).first().any { it.name == "Homemade Oatmeal" }
        } }
        nutrition()
        compose.onNodeWithContentDescription("Add food to Lunch").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Homemade Oatmeal").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Homemade Oatmeal").performClick()
        compose.onNodeWithText("Add to Lunch").assertIsDisplayed().performClick()
        val rows = runBlocking { withTimeout(5_000) {
            database.trackDao().observeFoodLogs(day.toDayKey()).first { it.size == 2 }
        } }
        assertEquals(listOf("DINNER", "LUNCH"), rows.map { it.meal })
        assertEquals(listOf(450, 450), rows.map { it.calories })
    }

    @Test fun recentFoodUsesCurrentMealAndCapturedHistoricalSelectedDay() {
        runBlocking {
            database.trackDao().insertFood(LoggedFoodEntity(dayKey = day.toDayKey(), meal = "BREAKFAST",
                catalogFoodId = null, name = "Saved Yogurt", brand = "Fage", amount = 150, unit = "g",
                calories = 300, proteinGrams = 15f, carbsGrams = 30f, fatGrams = 10f, createdAt = 1))
        }
        nutrition()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Add food to Dinner").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved Yogurt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved Yogurt").performClick()
        compose.onNodeWithText("Add to Dinner").assertIsDisplayed().performClick()
        val historicalDay = day.minusDays(1).toDayKey()
        val added = runBlocking { withTimeout(5_000) {
            database.trackDao().observeFoodLogs(historicalDay).first { it.isNotEmpty() }.single()
        } }
        assertEquals("DINNER", added.meal)
        assertEquals(150, added.amount)
        assertEquals(300, added.calories)
        assertEquals(1, runBlocking { database.trackDao().observeFoodLogs(day.toDayKey()).first().size })
    }

    @Test fun leavingManualDetailsWithoutAddDoesNotPersistOrCreateRecent() {
        nutrition()
        compose.onNodeWithContentDescription("Add food to Snacks").performScrollTo().performClick()
        compose.onNodeWithText("Create Food").performClick()
        fillManualFood("Cancelled Recipe")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Choose meal").performScrollTo().performClick()
        compose.onNodeWithText("Breakfast").performClick()
        foodTime("07", "15", "AM")
        compose.onNodeWithText("Add to Breakfast").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Cancelled Recipe").assertDoesNotExist()
        assertTrue(runBlocking { database.trackDao().observeFoodLogs(day.toDayKey()).first().isEmpty() })
    }

    @Test fun recentFoodDraftMealAndTimePersistForCapturedHistoricalDay() {
        runBlocking {
            database.trackDao().insertFood(LoggedFoodEntity(dayKey = day.toDayKey(), meal = "DINNER",
                catalogFoodId = null, name = "Pateu vegetal cu ciuperci foarte gustos", brand = "Fixture brand",
                amount = 150, unit = "g", calories = 300, proteinGrams = 15f, carbsGrams = 30f,
                fatGrams = 10f, createdAt = 1))
        }
        nutrition()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Add food to Dinner").performScrollTo().performClick()
        compose.onNodeWithText("Pateu vegetal cu ciuperci foarte gustos").performClick()
        compose.onNodeWithText("Pateu vegetal cu ciuperci foarte gustos").assertIsDisplayed()
        compose.onNodeWithText("Fixture brand").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose meal").performScrollTo().performClick()
        compose.onNodeWithText("Lunch").performClick()
        compose.onNodeWithText("Add to Lunch").assertIsDisplayed()
        val is24 = android.text.format.DateFormat.is24HourFormat(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        foodTime(if (is24) "18" else "06", "35", "PM")
        compose.onNodeWithText("18:35").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add to Lunch").performClick()

        val historicalDay = day.minusDays(1)
        val added = runBlocking { withTimeout(5_000) {
            database.trackDao().observeFoodLogs(historicalDay.toDayKey()).first { it.isNotEmpty() }.single()
        } }
        val loggedDateTime = java.time.Instant.ofEpochMilli(added.createdAt)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals("LUNCH", added.meal)
        assertEquals(historicalDay, loggedDateTime.toLocalDate())
        assertEquals(LocalTime.of(18, 35), loggedDateTime.toLocalTime())
        assertEquals(1, runBlocking { database.trackDao().observeFoodLogs(day.toDayKey()).first().size })
    }
}
