package com.example.track

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TrackSettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settingsFlow: Flow<TrackUiSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toTrackUiSettings() }

    suspend fun updateGoals(goals: TrackGoals) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.calories] = goals.calories
            preferences[SettingsKeys.protein] = goals.proteinGrams
            preferences[SettingsKeys.carbs] = goals.carbsGrams
            preferences[SettingsKeys.fat] = goals.fatGrams
            preferences[SettingsKeys.waterLiters] = goals.waterLiters
            preferences[SettingsKeys.steps] = goals.steps
            preferences[SettingsKeys.weightKg] = goals.targetWeightKg
        }
    }

    suspend fun setTodayModuleEnabled(module: TodayModule, enabled: Boolean) {
        // Change only this key, never a whole customization copied from stale UI state.
        dataStore.edit { it[SettingsKeys.module(module)] = enabled }
    }
}

private object SettingsKeys {
    val calories = intPreferencesKey("goal_calories")
    val protein = intPreferencesKey("goal_protein_g")
    val carbs = intPreferencesKey("goal_carbs_g")
    val fat = intPreferencesKey("goal_fat_g")
    // Keep the existing Float model's precision without rounding during storage.
    val waterLiters = floatPreferencesKey("goal_water_liters")
    val steps = intPreferencesKey("goal_steps")
    val weightKg = floatPreferencesKey("goal_weight_kg")
    val nutrition = booleanPreferencesKey("today_nutrition_enabled")
    val water = booleanPreferencesKey("today_water_enabled")
    val stepsModule = booleanPreferencesKey("today_steps_enabled")
    val sleep = booleanPreferencesKey("today_sleep_enabled")
    val workout = booleanPreferencesKey("today_workout_enabled")
    val creatine = booleanPreferencesKey("today_creatine_enabled")
    val weight = booleanPreferencesKey("today_weight_enabled")

    fun module(module: TodayModule): Preferences.Key<Boolean> = when (module) {
        TodayModule.Nutrition -> nutrition
        TodayModule.Water -> water
        TodayModule.Steps -> stepsModule
        TodayModule.Sleep -> sleep
        TodayModule.Workout -> workout
        TodayModule.Creatine -> creatine
        TodayModule.Weight -> weight
    }
}

internal fun Preferences.toTrackUiSettings(): TrackUiSettings {
    val defaults = TrackUiSettings()
    return TrackUiSettings(
        goals = TrackGoals(
            calories = this[SettingsKeys.calories] ?: defaults.goals.calories,
            proteinGrams = this[SettingsKeys.protein] ?: defaults.goals.proteinGrams,
            carbsGrams = this[SettingsKeys.carbs] ?: defaults.goals.carbsGrams,
            fatGrams = this[SettingsKeys.fat] ?: defaults.goals.fatGrams,
            waterLiters = this[SettingsKeys.waterLiters] ?: defaults.goals.waterLiters,
            steps = this[SettingsKeys.steps] ?: defaults.goals.steps,
            targetWeightKg = this[SettingsKeys.weightKg] ?: defaults.goals.targetWeightKg,
        ),
        today = TodayCustomization(
            showNutrition = this[SettingsKeys.nutrition] ?: defaults.today.showNutrition,
            showWater = this[SettingsKeys.water] ?: defaults.today.showWater,
            showSteps = this[SettingsKeys.stepsModule] ?: defaults.today.showSteps,
            showSleep = this[SettingsKeys.sleep] ?: defaults.today.showSleep,
            showWorkout = this[SettingsKeys.workout] ?: defaults.today.showWorkout,
            showCreatine = this[SettingsKeys.creatine] ?: defaults.today.showCreatine,
            showWeight = this[SettingsKeys.weight] ?: defaults.today.showWeight,
        ),
    )
}
