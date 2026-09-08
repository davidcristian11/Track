package com.example.track

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
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

class WorkoutNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: TrackDatabase
    private lateinit var vm: TrackViewModel
    private lateinit var directory: File
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val day = LocalDate.of(2026, 9, 8)

    @Before fun start() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        directory = File(context.cacheDir, "workout-navigation-${UUID.randomUUID()}").apply { mkdir() }
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

    private fun tab(name: String) { compose.onNode(hasText(name) and isSelectable()).performClick() }
    private fun chooseType(label: String) {
        compose.onNodeWithContentDescription("Choose activity type").performScrollTo().performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
    private fun time(hour: String, minute: String, period: String) {
        compose.onNodeWithContentDescription("Change Start Time").performScrollTo().performClick()
        compose.onNodeWithText("Enter time").performClick()
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performClick().performTextReplacement(minute)
        compose.onAllNodesWithText(period).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onNodeWithText(period).performClick()
        }
        compose.onNodeWithText("Set time").performClick()
    }
    private fun rows(date: LocalDate) = runBlocking { database.trackDao().observeWorkouts(date.toDayKey()).first() }
    private fun awaitActivity() {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Activity") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun newAndMovedWorkoutStayInSyncAcrossActivityTodayAndRoom() {
        tab("Activity")
        compose.onNodeWithContentDescription("Previous day").performScrollTo().performClick()
        compose.onNodeWithText("Add workout").performScrollTo().performClick()
        compose.onNodeWithText("Sep 7").assertIsDisplayed()
        chooseType("Running")
        time("07", "30", "AM")
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("30")
        compose.onNodeWithText("Estimated calories: ~300 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notes (Optional)").performScrollTo().performTextReplacement("Morning run")
        compose.onNodeWithText("Save Workout").performClick()
        awaitActivity()
        val original = rows(day.minusDays(1)).single()
        assertEquals("07:30", original.startTime)
        assertEquals(300, original.estimatedCalories)
        compose.onNodeWithText("07:30 · 30 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Morning run").performScrollTo().assertIsDisplayed()
        tab("Today")
        compose.onNodeWithText("Running ·").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30 min").performScrollTo().assertIsDisplayed()
        tab("Activity")
        compose.onNodeWithContentDescription("Workout options for Running").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Notes (Optional)").performScrollTo().assertTextContains("Morning run")
        chooseType("Calisthenics")
        compose.onNodeWithContentDescription("Change Date").performClick()
        compose.onNodeWithText("Sunday, September 6, 2026").performClick()
        compose.onNodeWithText("Set date").performClick()
        val is24 = android.text.format.DateFormat.is24HourFormat(InstrumentationRegistry.getInstrumentation().targetContext)
        time(if (is24) "18" else "06", "15", "PM")
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Estimated calories: ~315 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notes (Optional)").performScrollTo().performTextReplacement("Evening session")
        compose.onNodeWithText("Save Changes").performClick()
        awaitActivity()
        assertTrue(rows(day.minusDays(1)).isEmpty())
        val moved = rows(day.minusDays(2)).single()
        assertEquals(original.copy(dayKey = "2026-09-06", activityType = "Calisthenics", durationMinutes = 45,
            startTime = "18:15", notes = "Evening session", estimatedCalories = 315), moved)
        compose.onNodeWithText("No workout yet").performScrollTo().assertIsDisplayed()
        tab("Today")
        compose.onNodeWithText("No workout yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous day").performScrollTo().performClick()
        compose.onNodeWithText("Calisthenics ·").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("45 min").performScrollTo().assertIsDisplayed()
        tab("Activity")
        compose.onNodeWithText("18:15 · 45 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Evening session").performScrollTo().assertIsDisplayed()
    }

    @Test fun backDiscardsEditsAndReopeningLoadsPersistedValues() {
        runBlocking { TrackRepository(database).addWorkout(WorkoutInput(day, WorkoutType.Walking, 30, "07:30", "Saved note")) }
        tab("Activity")
        compose.onNodeWithContentDescription("Workout options for Walking").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("60")
        compose.onNodeWithText("Notes (Optional)").performScrollTo().performTextReplacement("Discard this")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitActivity()
        assertEquals(30, rows(day).single().durationMinutes)
        assertEquals("Saved note", rows(day).single().notes)
        compose.onNodeWithContentDescription("Workout options for Walking").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Duration (minutes)").assertTextContains("30")
        compose.onNodeWithText("Notes (Optional)").performScrollTo().assertTextContains("Saved note")
    }
}
