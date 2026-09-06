package com.example.track

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: TrackSettingsRepository,
) : ViewModel() {
    val tracking = repository.observeTracking(TrackPrototypeDay)
        .catch { error -> Log.e("TrackPersistence", "Could not load tracking data", error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackSessionData())

    val uiSettings = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiSettings())

    // Tracking actions (Room).
    private var savingLog = false

    fun addFood(meal: MealContext, food: FoodDefinition, amount: Int, onSaved: () -> Unit) =
        saveLog(onSaved) { repository.addFood(TrackPrototypeDay, meal, food, amount) }

    fun addWorkout(type: WorkoutType, duration: Int, notes: String, onSaved: () -> Unit) =
        saveLog(onSaved) { repository.addWorkout(TrackPrototypeDay, type, duration, notes) }

    fun addWater() = write { repository.addWater(TrackPrototypeDay) }

    fun toggleCreatine() = write { repository.toggleCreatine(TrackPrototypeDay) }

    // Avoid duplicate log submissions while a commit is in flight. Navigation only
    // completes after a successful insert; a failure leaves the local form intact.
    private fun saveLog(onSaved: () -> Unit, block: suspend () -> Unit) {
        if (savingLog) return
        savingLog = true
        write {
            try {
                block()
                onSaved()
            } finally {
                savingLog = false
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("TrackPersistence", "Could not save tracking change", error)
            }
        }
    }

    // Settings actions (DataStore). Goals remain a local draft until Save succeeds.
    private var savingGoals = false

    fun updateGoals(goals: TrackGoals, onSaved: () -> Unit) {
        if (savingGoals) return
        savingGoals = true
        writeSettings {
            try {
                settingsRepository.updateGoals(goals)
                onSaved()
            } finally {
                savingGoals = false
            }
        }
    }

    fun setTodayModuleEnabled(module: TodayModule, enabled: Boolean) =
        writeSettings { settingsRepository.setTodayModuleEnabled(module, enabled) }

    private fun writeSettings(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: IOException) {
                Log.e("TrackSettings", "Could not save settings change", error)
            }
        }
    }

    class Factory(
        private val repository: TrackRepository,
        private val settingsRepository: TrackSettingsRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TrackViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return TrackViewModel(repository, settingsRepository) as T
        }
    }
}
