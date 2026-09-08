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
}
