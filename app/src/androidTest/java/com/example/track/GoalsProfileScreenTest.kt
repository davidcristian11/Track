package com.example.track

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoalsProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private val weight = WeightEntry(LocalDate.of(2026, 9, 8), 75.0)

    @Test fun targetDirectionUpdatesForPhysicalPhoneCases() {
        var target by mutableStateOf(68f)
        compose.setContent { TrackTheme {
            ProfileScreen(TrackProfile(), TrackGoals(targetWeightKg = target), weight, {}, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Lose weight").assertIsDisplayed()
        compose.runOnUiThread { target = 75f }
        compose.onNodeWithText("Maintain weight").assertIsDisplayed()
        compose.runOnUiThread { target = 80f }
        compose.onNodeWithText("Gain weight").assertIsDisplayed()
        compose.onNodeWithText("+5.0 kg to target").assertIsDisplayed()
    }

    @Test fun noWeightIsHonestAndCannotRecalculate() {
        compose.setContent { TrackTheme {
            GoalsTargetsScreen(TrackGoals(targetWeightKg = 80f), null, {}, {})
        } }
        compose.onNodeWithText("Log your weight to calculate your goal").assertIsDisplayed()
        compose.onNodeWithText("Log your weight first").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recalculate suggested targets").assertIsNotEnabled()
    }

    @Test fun savedTargetImmediatelyUpdatesProfileDirection() {
        var persistedGoals by mutableStateOf(TrackGoals(targetWeightKg = 68f))
        var showGoals by mutableStateOf(false)
        compose.setContent { TrackTheme {
            if (showGoals) {
                GoalsTargetsScreen(
                    goals = persistedGoals,
                    currentWeight = weight,
                    onBack = { showGoals = false },
                    onSave = { persistedGoals = it; showGoals = false },
                )
            } else {
                ProfileScreen(
                    profile = TrackProfile(), goals = persistedGoals, currentWeight = weight,
                    onBack = {}, onSaveProfile = { _, _ -> }, onGoalsClick = { showGoals = true },
                    onCustomizeTodayClick = {},
                )
            }
        } }

        compose.onNodeWithText("Goals & targets").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Target weight, kg").performTextReplacement("80")
        compose.onNodeWithText("Gain weight").assertIsDisplayed()
        compose.onNodeWithText("Save Targets").performClick()
        compose.onNodeWithText("Gain weight").assertIsDisplayed()
        assertEquals(80f, persistedGoals.targetWeightKg, 0f)
    }

    @Test fun recalculatedDraftIsDiscardedOnBack() {
        var saved: TrackGoals? = null
        var backCalled = false
        compose.setContent { TrackTheme {
            GoalsTargetsScreen(
                TrackGoals(targetWeightKg = 80f), weight,
                onBack = { backCalled = true }, onSave = { saved = it },
            )
        } }
        compose.onNodeWithText("Recalculate suggested targets").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(
            "Suggested targets applied: 2550 kcal · 135 g protein · 368 g carbs · 60 g fat",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(true, backCalled)
        assertEquals(null, saved)
    }

    @Test fun recalculatedDraftPersistsOnlySuggestedNutritionOnSave() {
        var saved: TrackGoals? = null
        compose.setContent { TrackTheme {
            GoalsTargetsScreen(
                TrackGoals(targetWeightKg = 80f), weight, {}, { saved = it },
            )
        } }
        compose.onNodeWithText("Recalculate suggested targets").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Save Targets").performClick()
        val goals = requireNotNull(saved)
        assertEquals(2_550, goals.calories)
        assertEquals(135, goals.proteinGrams)
        assertEquals(368, goals.carbsGrams)
        assertEquals(60, goals.fatGrams)
        assertEquals(80f, goals.targetWeightKg, 0f)
        assertEquals(2.5f, goals.waterLiters, 0f)
        assertEquals(10_000, goals.steps)
    }

    @Test fun editProfileValidatesSavesAndDiscardsDraft() {
        var profile by mutableStateOf(TrackProfile())
        var saveCalled = false
        compose.setContent { TrackTheme {
            ProfileScreen(
                profile, TrackGoals(), null, {},
                onSaveProfile = { saved, onSaved -> profile = saved; saveCalled = true; onSaved() },
                onGoalsClick = {},
                onCustomizeTodayClick = {},
            )
        } }
        compose.onNodeWithText("Edit profile").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Display name").performTextReplacement("  Taylor  ")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        assertEquals(TrackProfile("Taylor"), profile)

        compose.onNodeWithText("Edit profile").performClick()
        compose.onNodeWithText("Display name").performTextReplacement("Discard me")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        compose.onNodeWithText("Discard me").assertDoesNotExist()
        assertEquals(TrackProfile("Taylor"), profile)
        assertEquals(true, saveCalled)
    }
}
