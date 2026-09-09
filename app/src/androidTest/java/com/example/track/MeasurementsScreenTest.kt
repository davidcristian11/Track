package com.example.track

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MeasurementsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 9)

    @Test fun progressLogPartialEditPreloadClearAndDeleteUseRealRoomHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        val repository = TrackRepository(db)
        val flow = repository.observeMeasurements().map { MeasurementHistoryState(it, loading = false) }
        try {
            compose.setContent {
                val history = flow.collectAsState(MeasurementHistoryState()).value
                TrackTheme { ProgressScreen({}, today, measurements = history,
                    onSaveMeasurements = repository::saveMeasurements,
                    onDeleteMeasurements = { repository.deleteMeasurementsForDay(it); true }) }
            }
            compose.onNodeWithText("No measurements logged yet").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Log measurements").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Measurement date").assertIsDisplayed()
            compose.onNodeWithText("Save Measurements").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Waist, cm").performTextReplacement("9.9")
            compose.onNodeWithText("Save Measurements").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Waist, cm").performTextReplacement("82,25")
            compose.onNodeWithContentDescription("Arm, cm").performScrollTo().performTextReplacement("36")
            compose.onNodeWithContentDescription("Thigh, cm").performScrollTo().performTextReplacement("56")
            compose.onNodeWithContentDescription("Thigh, cm").assertTextContains("56").assertIsDisplayed()
            compose.onNodeWithText("Save Measurements").assertIsDisplayed().performClick()
            compose.onNodeWithTag("measurement-Waist").performScrollTo().assert(hasAnyDescendant(hasText("82.25 cm")))
            compose.onNodeWithText("View history").performScrollTo().performClick()
            compose.onNodeWithTag("measurement-history-$today").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Waist, cm").assertTextContains("82.25")
            compose.onNodeWithContentDescription("Waist, cm").performTextReplacement("80.0")
            compose.onNodeWithContentDescription("Thigh, cm").performScrollTo().performTextClearance()
            compose.onNodeWithText("Save Measurements").performClick()
            compose.onNodeWithTag("measurement-history-$today").assertIsDisplayed()
            runBlocking {
                val row = db.trackDao().observeMeasurements().first().single()
                assertEquals(80.0, row.waistCm!!, 0.0)
                assertEquals(36.0, row.armCm!!, 0.0)
                assertNull(row.thighCm)
                assertNull(row.chestCm)
            }
            compose.onNodeWithTag("measurement-history-$today").performClick()
            compose.onNodeWithText("Delete entry").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Delete entry").performScrollTo().performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.onNode(hasText("No measurements logged yet") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            runBlocking { assertTrue(db.trackDao().observeMeasurements().first().isEmpty()) }
        } finally { db.close() }
    }

    @Test fun latestPerMetricAndRangeChangesStayIndependent() {
        val history = listOf(
            BodyMeasurement(today.minusDays(8), BodyMeasurements(82.0, 100.5, armCm = 35.5)),
            BodyMeasurement(today, BodyMeasurements(80.5, 101.0, armCm = 36.0, thighCm = 56.0)),
            BodyMeasurement(today.minusDays(1), BodyMeasurements(hipsCm = 96.5)),
        )
        compose.setContent { TrackTheme { ProgressScreen({}, today,
            measurements = MeasurementHistoryState(history, loading = false)) } }
        compose.onNodeWithTag("measurement-Waist").performScrollTo().assert(hasAnyDescendant(hasText("−1.5 cm")))
        compose.onNodeWithTag("measurement-Chest").performScrollTo().assert(hasAnyDescendant(hasText("+0.5 cm")))
        compose.onNodeWithTag("measurement-Hips").performScrollTo().assert(hasAnyDescendant(hasText("96.5 cm")))
        compose.onNodeWithText("7D").performScrollTo().performClick()
        compose.onNodeWithTag("measurement-Waist").performScrollTo()
            .assert(hasAnyDescendant(hasText("80.5 cm"))).assert(hasAnyDescendant(hasText("—")))
        compose.onNodeWithText("View history").performScrollTo().performClick()
        compose.onNodeWithTag("measurement-history-${today.minusDays(8)}").assertDoesNotExist()
        compose.onNodeWithTag("measurement-history-$today").assertIsDisplayed()
    }

    @Test fun softwareKeyboardKeepsThighValueAndSaveAboveIme() {
        compose.setContent { TrackTheme { MeasurementEditorDialog(today, null, emptyList(), {},
            onSave = { _, _ -> MeasurementSaveResult.Saved }, onDelete = { true }) } }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put secure show_ime_with_hard_keyboard 1").close()
        val thigh = compose.onNodeWithContentDescription("Thigh, cm")
        thigh.performScrollTo().performClick()
        compose.waitUntil(5_000) {
            android.view.inspector.WindowInspector.getGlobalWindowViews().any { view ->
                androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(
                    androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
        }
        thigh.performTextInput("56.25")
        thigh.assertTextContains("56.25").assertIsDisplayed()
        compose.onNodeWithText("Save Measurements").assertIsDisplayed().assertIsEnabled()
        val field = thigh.fetchSemanticsNode().boundsInWindow
        val button = compose.onNodeWithText("Save Measurements").fetchSemanticsNode().boundsInWindow
        assertTrue("Thigh must fit above Save", field.bottom <= button.top)
        assertTrue("Thigh must not be clipped", field.top >= 0)
        compose.onNodeWithText("Save Measurements").performClick()
    }

    @Test fun datePickerAndFailedMoveKeepDraftAndExistingDates() {
        val original = BodyMeasurement(today, BodyMeasurements(80.5))
        var attempted: BodyMeasurement? = null
        compose.setContent { TrackTheme { MeasurementEditorDialog(today, original, listOf(original), {},
            onSave = { entry, _ -> attempted = entry; MeasurementSaveResult.DateOccupied }, onDelete = { false }) } }
        compose.onNodeWithContentDescription("Measurement date").performClick()
        compose.onNodeWithText("Thursday, September 10, 2026", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Tuesday, September 8, 2026", substring = true).performClick()
        compose.onNodeWithText("Set date").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Waist, cm").assertTextContains("80.5")
        compose.onNodeWithText("Save Measurements").performClick()
        compose.onNodeWithText("That date already has measurements. Choose another date or edit its entry from history.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Waist, cm").performScrollTo().assertTextContains("80.5")
        assertEquals(original.copy(day = today.minusDays(1)), attempted)
    }
}
