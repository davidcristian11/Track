package com.example.track

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 8)
    private val past = today.minusDays(3)

    @Test fun activitiesAndManualDurationRecalculateImmediatelyAndInvalidSaveIsDisabled() {
        compose.setContent { TrackTheme { AddWorkoutScreen(past, today, {}, {}) } }
        compose.onNodeWithContentDescription("Choose activity type").performClick()
        for (type in listOf("Calisthenics", "Swimming", "Other", "Walking")) {
            compose.onNodeWithText(type).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Walking").performClick()
        compose.onNodeWithText("30", substring = false).performClick()
        compose.onNodeWithText("Estimated calories: ~120 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose activity type").performScrollTo().performClick()
        compose.onNodeWithText("Running").performScrollTo().performClick()
        compose.onNodeWithText("Estimated calories: ~300 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("60")
        compose.onNodeWithText("Estimated calories: ~600 kcal").performScrollTo().assertIsDisplayed()
        for (invalid in listOf("0", "1441", "", "abc")) {
            compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement(invalid)
            compose.onNodeWithText("Save Workout").assertIsNotEnabled()
        }
    }

    @Test fun datePickerUsesExplicitDateAndLaterSelectedDayChangesDoNotOverwriteDraft() {
        val day = mutableStateOf(past)
        var saved: WorkoutInput? = null
        compose.setContent { TrackTheme { AddWorkoutScreen(day.value, today, {}, { saved = it }) } }
        compose.onNodeWithText("Sep 5").assertIsDisplayed()
        compose.runOnIdle { day.value = today }
        compose.onNodeWithText("Sep 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change Date").performClick()
        // Material prefixes the real device date with "Today"; the fixture's future-day rule is unchanged.
        compose.onNodeWithText("Wednesday, September 9, 2026", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Sunday, September 6, 2026").performClick()
        compose.onNodeWithText("Set date").performClick()
        compose.onNodeWithText("Sep 6").assertIsDisplayed()
        compose.runOnIdle { day.value = today.minusDays(1) }
        compose.onNodeWithText("Sep 6").assertIsDisplayed()
        compose.onNodeWithText("Save Workout").performClick()
        compose.runOnIdle { assertEquals(today.minusDays(2), saved?.day) }
    }

    @Test fun editPreloadsAndSavesTimeNotesDateAndRecomputedCalories() {
        val existing = LoggedWorkout(42, WorkoutType.Running, 30, "Morning run", 280, "07:30")
        var saved: WorkoutInput? = null
        compose.setContent { TrackTheme { AddWorkoutScreen(past, today, {}, { saved = it }, existing) } }
        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.onNodeWithText("07:30").assertIsDisplayed()
        compose.onNodeWithText("Duration (minutes)").assertTextContains("30")
        compose.onNodeWithText("Notes (Optional)").performScrollTo().assertTextContains("Morning run")
        compose.onNodeWithText("Estimated calories: ~300 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Change Date").performScrollTo().performClick()
        compose.onNodeWithText("Sunday, September 6, 2026").performClick()
        compose.onNodeWithText("Set date").performClick()
        compose.onNodeWithContentDescription("Change Start Time").performClick()
        compose.onNodeWithText("Enter time").performClick()
        val timeInputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        timeInputs[0].performTextReplacement("08")
        timeInputs[1].performClick().performTextReplacement("15")
        compose.onNodeWithText("Set time").performClick()
        compose.onNodeWithText("08:15").assertIsDisplayed()
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Notes (Optional)").performScrollTo().performTextReplacement("Evening session")
        compose.onNodeWithText("Save Changes").performClick()
        compose.runOnIdle {
            assertEquals(WorkoutInput(today.minusDays(2), WorkoutType.Running, 45, "08:15", "Evening session"), saved)
        }
    }

    @Test fun backAndPickerCancelDiscardDraftWithoutSaving() {
        var saved = false
        var backed = false
        compose.setContent { TrackTheme { AddWorkoutScreen(past, today, { backed = true }, { saved = true },
            LoggedWorkout(42, WorkoutType.Running, 30, "Morning run", 300, "07:30")) } }
        compose.onNodeWithContentDescription("Change Date").performClick()
        compose.onNodeWithText("Sunday, September 6, 2026").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Sep 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change Start Time").performClick()
        compose.onNodeWithText("Enter time").performClick()
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0].performTextReplacement("09")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("07:30").assertIsDisplayed()
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("55")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(backed); assertFalse(saved) }
    }

    @Test fun softwareKeyboardKeepsMultilineNotesAndSaveVisible() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            TrackTheme { AddWorkoutScreen(past, today, {}, {}) }
        }
        // Force a real software IME even on emulator hosts with a hardware keyboard.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put secure show_ime_with_hard_keyboard 1").close()
        val notes = compose.onNodeWithText("Notes (Optional)")
        notes.performScrollTo().performClick()
        compose.waitUntil(5_000) {
            val insets = ViewCompat.getRootWindowInsets(view)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
        }
        notes.performTextInput("Morning run with a friend\nComfortable pace\nFinished feeling good")
        notes.assertTextContains("Morning run with a friend\nComfortable pace\nFinished feeling good").assertIsDisplayed()
        compose.onNodeWithText("Save Workout").assertIsDisplayed()
        val field = notes.fetchSemanticsNode().boundsInWindow
        val button = compose.onNodeWithText("Save Workout").fetchSemanticsNode().boundsInWindow
        assertTrue("Notes must fit entirely above Save", field.bottom <= button.top)
        assertTrue("Notes must not be clipped at top", field.top >= 0)
        compose.onNodeWithText("Duration (minutes)").performScrollTo().assertIsDisplayed()
        notes.performScrollTo().assertIsDisplayed()
    }
}
