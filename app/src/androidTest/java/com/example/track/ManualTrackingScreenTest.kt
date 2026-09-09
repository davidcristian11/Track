package com.example.track

import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ManualTrackingScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: TrackDatabase
    private lateinit var vm: TrackViewModel
    private lateinit var settings: TrackSettingsRepository
    private lateinit var directory: File
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val day = LocalDate.of(2026, 9, 8)

    @Before fun start() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        directory = File(context.cacheDir, "manual-tracking-${UUID.randomUUID()}").apply { mkdir() }
        settings = TrackSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "settings.preferences_pb")
        })
        compose.runOnUiThread {
            vm = TrackViewModel(TrackRepository(database), settings) { day }
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
    private fun edit(metric: String, existing: Boolean = false) {
        compose.onNodeWithContentDescription("${if (existing) "Edit" else "Log"} $metric")
            .performScrollTo().performClick()
    }
    private fun field(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performTextReplacement(value)
    }
    private fun saved() {
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }
    private fun removed() {
        compose.onNodeWithText("Remove entry").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }
    private fun state(date: LocalDate = day) = runBlocking { database.trackDao().dailyState(date.toDayKey()) }

    @Test fun todayLogsUpdatesRemovesAndPreservesOtherDailyMetrics() {
        compose.onNodeWithText("No steps logged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No sleep logged").performScrollTo().assertIsDisplayed()
        runBlocking {
            TrackRepository(database).addWater(day.toDayKey(), 500)
            TrackRepository(database).toggleCreatine(day.toDayKey())
        }
        edit("steps")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Steps", "8000"); saved()
        compose.onNodeWithText(formatSteps(8000)).performScrollTo().assertIsDisplayed()
        edit("sleep"); field("Hours", "7"); field("Minutes", "30"); saved()
        compose.onNodeWithText("7h 30m").performScrollTo().assertIsDisplayed()
        edit("steps", true)
        compose.onNode(hasText("Steps") and hasSetTextAction()).assertTextContains("8000")
        field("Steps", "9000"); saved()
        compose.onNodeWithText(formatSteps(9000)).performScrollTo().assertIsDisplayed()
        assertEquals(DailyTrackingStateEntity(day.toDayKey(), 500, true, 9000, 450), state())
        edit("sleep", true); field("Hours", "8"); field("Minutes", "0"); saved()
        compose.onNodeWithText("8h").performScrollTo().assertIsDisplayed()
        edit("steps", true); removed()
        compose.onNodeWithText("No steps logged").performScrollTo().assertIsDisplayed()
        assertEquals(DailyTrackingStateEntity(day.toDayKey(), 500, true, null, 480), state())
        edit("steps"); field("Steps", "0"); saved()
        edit("sleep", true); removed()
        compose.onNodeWithText("No sleep logged").performScrollTo().assertIsDisplayed()
        assertEquals(DailyTrackingStateEntity(day.toDayKey(), 500, true, 0, null), state())
    }

    @Test fun activitySharesEditorUsesSavedGoalAndHistoricalDaysStayIsolated() {
        tab("Activity")
        compose.onNodeWithText("No steps logged").assertIsDisplayed()
        compose.onNodeWithText("6,432").assertDoesNotExist()
        compose.onNodeWithText("Log steps").performScrollTo().performClick()
        field("Steps", "8432"); saved()
        compose.onNodeWithText(formatSteps(8432)).performScrollTo().assertIsDisplayed()
        fun assertProgress(target: Int) {
            val bars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
            assertTrue(bars.any { kotlin.math.abs(it.config[SemanticsProperties.ProgressBarRangeInfo].current - progressFraction(8432, target)) < 0.0001f })
        }
        assertProgress(10000)
        runBlocking { settings.updateGoals(TrackGoals(steps = 20000)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Goal: 20,000").fetchSemanticsNodes().isNotEmpty() }
        assertProgress(20000)
        compose.onNodeWithText("4.8").assertDoesNotExist()
        compose.onNodeWithText("58").assertDoesNotExist()
        compose.onNodeWithText("SYNCED FROM HEALTH DATA").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous day").performScrollTo().performClick()
        compose.onNodeWithText("No steps logged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log steps").performScrollTo().performClick()
        field("Steps", "5000"); saved()
        tab("Today")
        edit("sleep"); field("Hours", "6"); field("Minutes", "15"); saved()
        assertEquals(5000, state(day.minusDays(1))?.steps)
        assertEquals(375, state(day.minusDays(1))?.sleepMinutes)
        compose.onNodeWithContentDescription("Next day").performScrollTo().performClick()
        compose.onNodeWithText(formatSteps(8432)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No sleep logged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Next day").performScrollTo().assertIsNotEnabled()
        assertEquals(8432, state()?.steps)
        assertNull(state()?.sleepMinutes)
    }

    @Test fun invalidInputAndCancelledDraftNeverChangeRoom() {
        edit("steps"); field("Steps", "200001")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Steps", "8432"); saved()
        edit("steps", true); field("Steps", "1234")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(8432, state()?.steps)
        edit("steps", true)
        compose.onNode(hasText("Steps") and hasSetTextAction()).assertTextContains("8432")
        compose.onNodeWithText("Cancel").performClick()
        edit("sleep"); field("Hours", "24"); field("Minutes", "1")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Hours", "7"); field("Minutes", "60")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Minutes", "30")
        compose.onNodeWithText("Cancel").performClick()
        assertNull(state()?.sleepMinutes)
    }

    @Test fun referenceDayHasNoSyntheticManualMetrics() {
        compose.runOnUiThread { repeat(6) { vm.previousDay() } }
        compose.onNodeWithText("No steps logged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No sleep logged").performScrollTo().assertIsDisplayed()
        tab("Activity")
        compose.onNodeWithText("No steps logged").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("8,420").assertDoesNotExist()
    }
}
