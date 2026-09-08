package com.example.track

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeightProgressScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 7)

    @Test fun logUpdateCancelAndValidationUseOneRealRoomRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        val repository = TrackRepository(database)
        val historyFlow = repository.observeWeightEntries().map { WeightHistoryState(it, loading = false) }
        try {
            compose.setContent {
                val history = historyFlow.collectAsState(WeightHistoryState()).value
                TrackTheme { ProgressScreen({}, today, history, onLogWeight = {
                    repository.logWeight(today, it); true
                }) }
            }
            compose.onNodeWithText("No weight logged yet").assertIsDisplayed()
            compose.onNodeWithText("Log weight").performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Weight, kg").performTextReplacement("19.9")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Weight, kg").performTextReplacement("75,0")
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("75.0").assertIsDisplayed()
            compose.onNodeWithContentDescription("Weight chart, 1 logged days, — change").assertIsDisplayed()
            compose.onNodeWithText("Update weight").performClick()
            compose.onNodeWithContentDescription("Weight, kg").assertTextContains("75.0")
            compose.onNodeWithContentDescription("Weight, kg").performTextReplacement("74.8")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("75.0").assertIsDisplayed()
            compose.onNodeWithText("Update weight").performClick()
            compose.onNodeWithContentDescription("Weight, kg").performTextReplacement("74.8")
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("74.8").assertIsDisplayed()
            runBlocking { assertEquals(74.8, database.trackDao().observeWeightEntries().first().single().weightKg, 0.0) }
        } finally { database.close() }
    }

    @Test fun persistedMultipleDaysChangeRangesWithoutSyntheticPointsAndKeepTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        val repository = TrackRepository(database)
        try {
            runBlocking {
                repository.logWeight(today.minusDays(6), 75.0)
                repository.logWeight(today.minusDays(5), 74.9)
                repository.logWeight(today, 74.8)
                repository.logWeight(today.minusDays(40), 76.0)
            }
            val entries = runBlocking { repository.observeWeightEntries().first() }
            compose.setContent { TrackTheme {
                ProgressScreen({}, today, WeightHistoryState(entries, loading = false), TrackGoals(targetWeightKg = 69f))
            } }
            compose.onNodeWithText("7D").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Weight chart, 3 logged days, −0.2 kg change").assertIsDisplayed()
            compose.onNodeWithText("3M").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Weight chart, 4 logged days, −1.2 kg change").assertIsDisplayed()
            compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Target Weight"))
            compose.onNodeWithText("Target Weight").assertIsDisplayed()
            compose.onNodeWithText("69.0").performScrollTo().assertIsDisplayed()
        } finally { database.close() }
    }

    @Test fun emptyRangeKeepsLatestAndUnfinishedFeaturesRemainLabeled() {
        compose.setContent { TrackTheme {
            ProgressScreen({}, today, WeightHistoryState(listOf(WeightEntry(today.minusDays(40), 73.85)), loading = false))
        } }
        compose.onNodeWithText("No entries in this range").assertIsDisplayed()
        compose.onNodeWithText("73.85").assertIsDisplayed()
        compose.onNodeWithText("3M").performClick()
        compose.onNodeWithContentDescription("Weight chart, 1 logged days, — change").assertIsDisplayed()
        compose.onNodeWithText("7D").performClick()
        compose.onNodeWithText("No entries in this range").assertIsDisplayed()
        compose.onNodeWithText("Progress Photos").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("In progress").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Measurements").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("In progress").onLast().performScrollTo().assertIsDisplayed()
    }

    @Test fun failedSaveKeepsDraftAndAllowsRetry() {
        var attempts = 0
        compose.setContent { TrackTheme {
            WeightLogDialog(today, WeightEntry(today, 73.85), {}, { attempts++; false })
        } }
        compose.onNodeWithContentDescription("Weight, kg").performTextReplacement("74.3")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Could not save. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(2, attempts)
    }
}
