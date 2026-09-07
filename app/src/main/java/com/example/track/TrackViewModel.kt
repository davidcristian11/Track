package com.example.track

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: TrackSettingsRepository,
    private val todayProvider: () -> LocalDate = TrackDateProvider::today,
) : ViewModel() {
    private val _today = MutableStateFlow(todayProvider())
    val today = _today.asStateFlow()
    private val _selectedDay = MutableStateFlow(_today.value)
    val selectedDay = _selectedDay.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracking = selectedDay.flatMapLatest { day ->
        repository.observeTracking(day.toDayKey())
            .onStart { emit(TrackSessionData(day)) }
            .catch { error -> Log.e("TrackPersistence", "Could not load tracking data", error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackSessionData(selectedDay.value))

    val uiSettings = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiSettings())

    // Refresh the local date on resume (including timezone changes). A past-day
    // selection stays selected; a dashboard following Today follows the new day.
    fun refreshToday() {
        val current = todayProvider()
        if (_selectedDay.value == _today.value || _selectedDay.value > current) _selectedDay.value = current
        _today.value = current
    }

    fun previousDay() {
        refreshToday()
        _selectedDay.value = previousTrackDay(_selectedDay.value)
    }

    fun nextDay() {
        refreshToday()
        _selectedDay.value = nextTrackDay(_selectedDay.value, _today.value)
    }

    // Tracking actions (Room).
    private var savingLog = false

    fun addFood(meal: MealContext, food: FoodDefinition, amount: Int, onSaved: () -> Unit) {
        val dayKey = selectedDay.value.toDayKey()
        saveLog(onSaved) { repository.addFood(dayKey, meal, food, amount) }
    }

    fun addWorkout(type: WorkoutType, duration: Int, notes: String, onSaved: () -> Unit) {
        val dayKey = selectedDay.value.toDayKey()
        saveLog(onSaved) { repository.addWorkout(dayKey, type, duration, notes) }
    }

    fun addWater() {
        val dayKey = selectedDay.value.toDayKey()
        write { repository.addWater(dayKey) }
    }

    fun toggleCreatine() {
        val dayKey = selectedDay.value.toDayKey()
        write { repository.toggleCreatine(dayKey) }
    }

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
