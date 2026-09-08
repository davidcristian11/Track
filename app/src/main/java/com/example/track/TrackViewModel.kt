package com.example.track

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: TrackSettingsRepository,
    private val foodLookup: FoodLookupRepository = FoodLookupRepository(),
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

    val weightHistory = repository.observeWeightEntries()
        .map { WeightHistoryState(entries = it, loading = false) }
        .catch { error ->
            Log.e("TrackPersistence", "Could not load weight history", error)
            emit(WeightHistoryState(loading = false, error = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightHistoryState())

    // Progress always writes real today, independently of the dashboard's selected day.
    suspend fun logWeight(weightKg: Double): Boolean {
        if (!isValidWeight(weightKg)) return false
        refreshToday()
        return try {
            repository.logWeight(today.value, weightKg)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("TrackPersistence", "Could not save weight", error)
            false
        }
    }

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

    // Remote discovery is transient; only addFood writes a snapshot to Room.
    private val _foodSearch = MutableStateFlow(FoodSearchState())
    val foodSearch = _foodSearch.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private val _selectedFood = MutableStateFlow<FoodDefinition?>(null)
    val selectedFood = _selectedFood.asStateFlow()

    fun selectFood(food: FoodDefinition) { _selectedFood.value = food }

    fun searchFoods(query: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        val trimmed = query.trim()
        val local = if (trimmed.isEmpty()) emptyList() else LocalFoodCatalog.filter {
            it.name.contains(trimmed, true) || it.searchMetadata.contains(trimmed, true)
        }
        val previous = _foodSearch.value
        val retained = if (trimmed.equals(previous.query.trim(), ignoreCase = true)) previous.foods else emptyList()
        val available = (local + retained).distinctBy { it.id }
        _foodSearch.value = FoodSearchState(query, available, loading = trimmed.length >= 2)
        if (trimmed.length < 2) return
        searchJob = viewModelScope.launch {
            try {
                delay(600)
                val remote = foodLookup.search(trimmed)
                ensureActive()
                if (generation == searchGeneration) {
                    _foodSearch.value = FoodSearchState(query, (local + remote).distinctBy { it.id })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == searchGeneration) _foodSearch.value = FoodSearchState(query, available, unavailable = true)
            }
        }
    }

    fun cancelFoodSearch() {
        searchJob?.cancel()
        searchGeneration++
    }

    private val _scanner = MutableStateFlow<ScannerState>(ScannerState.Scanning)
    val scanner = _scanner.asStateFlow()
    private var barcodeJob: Job? = null

    fun scanBarcode(code: String) {
        if (_scanner.value != ScannerState.Scanning || !isRetailBarcode(code)) return
        lookupBarcode(code)
    }

    private fun lookupBarcode(code: String) {
        barcodeJob?.cancel()
        _scanner.value = ScannerState.LookingUp(code)
        barcodeJob = viewModelScope.launch {
            try {
                val product = foodLookup.lookupBarcode(code)
                ensureActive()
                _scanner.value = product?.let { ScannerState.Found(it) } ?: ScannerState.NotFound(code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                _scanner.value = ScannerState.Unavailable(code)
            }
        }
    }

    fun retryBarcodeLookup() {
        (_scanner.value as? ScannerState.Unavailable)?.let { lookupBarcode(it.code) }
    }

    fun resetScanner() {
        barcodeJob?.cancel()
        _scanner.value = ScannerState.Scanning
    }

    // Tracking actions (Room).
    private var savingLog = false

    fun addFood(meal: MealContext, food: FoodDefinition, amount: Int, onSaved: () -> Unit) {
        if (!food.isLoggable || amount !in 1..MaxFoodAmount) return
        val dayKey = selectedDay.value.toDayKey()
        saveLog(onSaved) { repository.addFood(dayKey, meal, food, amount) }
    }

    fun addWorkout(input: WorkoutInput, onSaved: () -> Unit) {
        if (!input.isValid(todayProvider())) return
        saveLog(onSaved) { repository.addWorkout(input) }
    }

    suspend fun foodForEdit(dayKey: String, id: Long) = repository.food(dayKey, id)

    suspend fun workoutForEdit(dayKey: String, id: Long) = repository.workout(dayKey, id)

    // Edit routes retain the original day, including after process recreation.
    fun updateFood(dayKey: String, original: LoggedFood, amount: Int, meal: MealContext, onSaved: () -> Unit) {
        if (amount !in 1..MaxFoodAmount || original.id <= 0) return
        saveLog(onSaved) { repository.updateFood(dayKey, original, amount, meal) }
    }

    fun deleteFood(dayKey: String, id: Long, onDeleted: () -> Unit) {
        write { repository.deleteFood(dayKey, id); onDeleted() }
    }

    fun updateWorkout(originalDayKey: String, id: Long, input: WorkoutInput, onSaved: () -> Unit) {
        if (!input.isValid(todayProvider()) || id <= 0) return
        saveLog(onSaved) { repository.updateWorkout(originalDayKey, id, input) }
    }

    fun deleteWorkout(dayKey: String, id: Long) {
        write { repository.deleteWorkout(dayKey, id) }
    }

    fun decreaseWater() {
        val dayKey = selectedDay.value.toDayKey()
        write { repository.adjustWater(dayKey, -250) }
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

    fun updateProfile(profile: TrackProfile, onSaved: () -> Unit) = writeSettings {
        settingsRepository.updateProfile(profile)
        onSaved()
    }

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
        private val foodLookup: FoodLookupRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TrackViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return TrackViewModel(repository, settingsRepository, foodLookup) as T
        }
    }
}


data class FoodSearchState(
    val query: String = "",
    val foods: List<FoodDefinition> = emptyList(),
    val loading: Boolean = false,
    val unavailable: Boolean = false,
)

sealed interface ScannerState {
    data object Scanning : ScannerState
    data class LookingUp(val code: String) : ScannerState
    data class Found(val food: FoodDefinition) : ScannerState
    data class NotFound(val code: String) : ScannerState
    data class Unavailable(val code: String) : ScannerState
}
