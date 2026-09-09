package com.example.track

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackDayViewModelTest {
    private lateinit var database: TrackDatabase
    private lateinit var repository: TrackRepository
    private lateinit var settings: TrackSettingsRepository
    private lateinit var storeScope: CoroutineScope
    private lateinit var directory: File
    private val viewModels = ViewModelStore()
    private var currentDate = LocalDate.of(2026, 10, 4)

    @Before
    fun openIsolatedStores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        repository = TrackRepository(database)
        directory = File(context.cacheDir, "day-test-${UUID.randomUUID()}")
        check(directory.mkdir())
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = TrackSettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope) {
            File(directory, "isolated.preferences_pb")
        })
    }

    private suspend fun newViewModel(lookup: FoodLookupRepository = FoodLookupRepository()) = withContext(Dispatchers.Main) {
        TrackViewModel(repository, settings, lookup) { currentDate }.also { viewModels.put("track", it) }
    }

    @After
    fun closeIsolatedStores() = runBlocking {
        withContext(Dispatchers.Main) { viewModels.clear() }
        storeScope.coroutineContext.job.cancelAndJoin()
        database.close()
        assertTrue(directory.deleteRecursively())
    }

    @Test
    fun selectedDaySwitchesObservationAndInFlightWritesKeepTheirOriginDay() = runBlocking {
        withTimeout(10_000) {
            val vm = newViewModel()
            val observing = launch { vm.tracking.collect() }
            try {
                val today = currentDate
                val past = today.minusDays(1)
                assertEquals(today, vm.selectedDay.value)
                withContext(Dispatchers.Main) {
                    vm.previousDay()
                    vm.addWater()
                    vm.toggleCreatine()
                    vm.addFood(MealContext.LUNCH, LocalFoodCatalog.first(), 119) {}
                    vm.nextDay() // Switch before Room finishes the writes.
                }
                val saved = repository.observeTracking(past.toDayKey()).first {
                    it.foods.size == 1 && it.waterMl == 250 && it.creatineCompleted
                }
                assertEquals(70, saved.nutrition.calories)
                assertNull(database.trackDao().dailyState(today.toDayKey()))
                assertEquals(TrackSessionData(today), repository.observeTracking(today.toDayKey()).first())

                withContext(Dispatchers.Main) { vm.previousDay() }
                assertEquals(saved, vm.tracking.first { it.day == past && it.foods.size == 1 && it.waterMl == 250 && it.creatineCompleted })
                withContext(Dispatchers.Main) {
                    vm.addWorkout(WorkoutInput(past, WorkoutType.Running, 30, "07:30", "Past run")) {}
                    vm.nextDay()
                }
                repository.observeTracking(past.toDayKey()).first { it.workouts.size == 1 }
                withContext(Dispatchers.Main) {
                    vm.previousDay()
                    vm.addFood(MealContext.DINNER, ScannedFood, 500) {}
                    vm.nextDay()
                }
                val scanned = repository.observeTracking(past.toDayKey()).first { it.foods.size == 2 }
                assertEquals(MealContext.DINNER, scanned.foods.last().meal)
                assertEquals(70, scanned.nutrition.calories)
                assertEquals(WorkoutType.Running, scanned.workouts.single().type)
                withContext(Dispatchers.Main) {
                    vm.previousDay()
                    vm.previousDay()
                    vm.nextDay()
                    vm.nextDay()
                    vm.nextDay()
                }
                assertEquals(today, vm.selectedDay.value)
                assertEquals(TrackSessionData(today), vm.tracking.first { it.day == today })

                withContext(Dispatchers.Main) { vm.previousDay() }
                val freshVm = newViewModel()
                assertEquals(today, freshVm.selectedDay.value)
                assertEquals(scanned, repository.observeTracking(past.toDayKey()).first())
            } finally {
                observing.cancelAndJoin()
            }
        }
    }

    @Test fun manualMetricsCaptureDayBeforeAsyncWritesAndRejectFutureDates() = runBlocking<Unit> {
        withTimeout(10_000) {
            val vm = newViewModel()
            val observing = launch { vm.tracking.collect() }
            try {
                val past = currentDate.minusDays(1)
                val stepsSaved = CompletableDeferred<Boolean>()
                val sleepSaved = CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    vm.previousDay()
                    vm.setSteps(8000, onResult = { stepsSaved.complete(it) })
                    vm.setSleep(450, onResult = { sleepSaved.complete(it) })
                    vm.nextDay()
                }
                assertTrue(stepsSaved.await())
                assertTrue(sleepSaved.await())
                val empty = vm.tracking.first { it.day == currentDate }
                assertNull(empty.steps)
                assertNull(empty.sleepMinutes)
                withContext(Dispatchers.Main) { vm.previousDay() }
                val stored = vm.tracking.first { it.day == past && it.steps == 8000 && it.sleepMinutes == 450 }
                assertEquals(8000, stored.steps)
                assertEquals(450, stored.sleepMinutes)
                val clearedSteps = CompletableDeferred<Boolean>()
                val clearedSleep = CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    vm.setSteps(null, onResult = { clearedSteps.complete(it) })
                    vm.setSleep(null, onResult = { clearedSleep.complete(it) })
                    vm.nextDay()
                    vm.nextDay()
                    vm.setSteps(9000, currentDate.plusDays(1)) { assertTrue(!it) }
                    vm.setSleep(480, currentDate.plusDays(1)) { assertTrue(!it) }
                }
                assertTrue(clearedSteps.await())
                assertTrue(clearedSleep.await())
                assertEquals(currentDate, vm.selectedDay.value)
                assertNull(database.trackDao().dailyState(currentDate.plusDays(1).toDayKey()))
                withContext(Dispatchers.Main) { vm.previousDay() }
                vm.tracking.first { it.day == past && it.steps == null && it.sleepMinutes == null }
            } finally { observing.cancelAndJoin() }
        }
    }

    @Test
    fun remoteSearchAndScannerSnapshotsKeepSelectedDay() = runBlocking {
        withTimeout(10_000) {
            val lookup = FoodLookupRepository(OpenFoodFactsClient { """{"status":1,"product":$remoteFixture}""" })
            val vm = newViewModel(lookup)
            val today = currentDate
            val remote = requireNotNull(lookup.lookupBarcode("1234567890128"))
            withContext(Dispatchers.Main) {
                vm.addFood(MealContext.LUNCH, remote, 150) {}
            }
            assertEquals(NutritionTotals(300, 15f, 30f, 7.5f),
                repository.observeTracking(today.toDayKey()).first { it.foods.size == 1 }.nutrition)
            withContext(Dispatchers.Main) {
                vm.previousDay()
                vm.scanBarcode("1234567890128")
            }
            val scanned = (vm.scanner.first { it is ScannerState.Found } as ScannerState.Found).food
            withContext(Dispatchers.Main) {
                vm.addFood(MealContext.DINNER, scanned, 250) {}
                vm.nextDay()
            }
            val past = repository.observeTracking(today.minusDays(1).toDayKey()).first { it.foods.size == 1 }
            assertEquals("off_1234567890128", past.foods.single().catalogFoodId)
            assertEquals(MealContext.DINNER, past.foods.single().meal)
            assertEquals(NutritionTotals(500, 25f, 50f, 12.5f), past.nutrition)
            assertEquals(1, repository.observeTracking(today.toDayKey()).first().foods.size)
        }
    }

    @Test
    fun staleSearchCannotReplaceNewQueryEvenIfTransportIgnoresCancellation() = runBlocking {
        withTimeout(15_000) {
            val oldStarted = CompletableDeferred<Unit>()
            val finishOld = CompletableDeferred<Unit>()
            val vm = newViewModel(FoodLookupRepository(OpenFoodFactsClient { url ->
                if (url.queryParameter("search_terms") == "old") {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { finishOld.await() }
                    """{"products":[$remoteFixture]}"""
                } else """{"products":[]}"""
            }))
            withContext(Dispatchers.Main) { vm.searchFoods("old") }
            oldStarted.await()
            withContext(Dispatchers.Main) { vm.searchFoods("new") }
            vm.foodSearch.first { it.query == "new" && !it.loading }
            finishOld.complete(Unit)
            delay(100)
            assertEquals(FoodSearchState("new"), vm.foodSearch.value)
        }
    }

    @Test
    fun scannerLocksDuplicatesAndSupportsRetryNotFoundAndIncomplete() = runBlocking {
        withTimeout(10_000) {
            var calls = 0
            var response = "offline"
            val waiting = CompletableDeferred<Unit>()
            val vm = newViewModel(FoodLookupRepository(OpenFoodFactsClient {
                calls++
                waiting.await()
                if (response == "offline") throw IOException("offline")
                response
            }))
            withContext(Dispatchers.Main) {
                repeat(30) { vm.scanBarcode("1234567890128") }
            }
            waiting.complete(Unit)
            vm.scanner.first { it is ScannerState.Unavailable }
            assertEquals(1, calls)
            response = """{"status":0}"""
            withContext(Dispatchers.Main) { vm.retryBarcodeLookup() }
            vm.scanner.first { it is ScannerState.NotFound }
            assertEquals(2, calls)
            response = """{"status":1,"product":{"code":"1234567890128","product_name":"Incomplete"}}"""
            withContext(Dispatchers.Main) { vm.resetScanner(); vm.scanBarcode("1234567890128") }
            val product = (vm.scanner.first { it is ScannerState.Found } as ScannerState.Found).food
            assertTrue(!product.isLoggable)
            withContext(Dispatchers.Main) { vm.addFood(MealContext.LUNCH, product, 100) { error("Must not save") } }
            assertTrue(database.trackDao().observeFoodLogs(currentDate.toDayKey()).first().isEmpty())
        }
    }

    @Test
    fun inFlightCorrectionsStayOnOriginalDayAfterSelectionChanges() = runBlocking {
        withTimeout(10_000) {
            val vm = newViewModel()
            val past = currentDate.minusDays(1).toDayKey()
            repository.addFood(past, MealContext.LUNCH, LocalFoodCatalog.first(), 119)
            repository.addWorkout(WorkoutInput(past.toTrackDay(), WorkoutType.Running, 30, "07:30", "Morning run"))
            repository.addWater(past, 500)
            val original = repository.observeTracking(past).first()
            val food = original.foods.single()
            val workout = original.workouts.single()
            val foodSaved = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                vm.previousDay()
                vm.updateFood(past, food, 150, MealContext.DINNER) { foodSaved.complete(Unit) }
                vm.decreaseWater()
                vm.nextDay()
            }
            foodSaved.await()
            val edited = repository.observeTracking(past).first { it.foods.single().amount == 150 && it.waterMl == 250 }
            assertEquals(MealContext.DINNER, edited.foods.single().meal)
            val workoutSaved = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                vm.updateWorkout(past, workout.id, WorkoutInput(past.toTrackDay(), WorkoutType.Running, 40, "18:15", "Updated run")) { workoutSaved.complete(Unit) }
            }
            workoutSaved.await()
            assertEquals(40, repository.workout(past, workout.id)?.durationMinutes)
            val deleted = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                vm.previousDay()
                vm.deleteFood(past, food.id) { deleted.complete(Unit) }
                vm.deleteWorkout(past, workout.id)
                vm.nextDay()
            }
            deleted.await()
            repository.observeTracking(past).first { it.foods.isEmpty() && it.workouts.isEmpty() }
            assertEquals(TrackSessionData(currentDate), repository.observeTracking(currentDate.toDayKey()).first())
            val fresh = newViewModel()
            assertEquals(currentDate, fresh.selectedDay.value)
            assertEquals(250, repository.observeTracking(past).first().waterMl)
        }
    }

    @Test
    fun obsoleteRetryIsCancelledDuringBackoffAndNewQueryWins() = runBlocking {
        withTimeout(10_000) {
            val backoff = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            var clock = 0L
            val lookup = FoodLookupRepository(OpenFoodFactsClient { url ->
                val query = requireNotNull(url.queryParameter("search_terms"))
                calls += query
                if (query == "old") throw FoodHttpException(503)
                """{"products":[]}"""
            }, { clock }, { wait ->
                if (!backoff.isCompleted) { backoff.complete(Unit); release.await() }
                clock += wait
            })
            val vm = newViewModel(lookup)
            withContext(Dispatchers.Main) { vm.searchFoods("old") }
            backoff.await()
            assertTrue(vm.foodSearch.value.loading)
            assertTrue(!vm.foodSearch.value.unavailable)
            withContext(Dispatchers.Main) { vm.searchFoods("chicken") }
            vm.foodSearch.first { it.query == "chicken" && !it.loading }
            release.complete(Unit)
            assertEquals(listOf("old", "chicken"), calls)
            assertEquals("chicken_breast", vm.foodSearch.value.foods.single().id)
        }
    }

    @Test
    fun localAndSameQueryRemoteResultsSurviveExhaustedRefresh() = runBlocking {
        withTimeout(10_000) {
            var fail = false
            var calls = 0
            var clock = 0L
            val vm = newViewModel(FoodLookupRepository(OpenFoodFactsClient {
                calls++
                if (fail) throw IOException("offline")
                """{"products":[$remoteFixture]}"""
            }, { clock }, { clock += it }))
            withContext(Dispatchers.Main) { vm.searchFoods("chicken") }
            val successful = vm.foodSearch.first { it.query == "chicken" && !it.loading }
            assertEquals(2, successful.foods.size)
            fail = true
            withContext(Dispatchers.Main) {
                vm.searchFoods("chicken")
                assertEquals(successful.foods, vm.foodSearch.value.foods)
                assertTrue(vm.foodSearch.value.loading)
                assertTrue(!vm.foodSearch.value.unavailable)
            }
            val failed = vm.foodSearch.first { it.unavailable }
            assertEquals(successful.foods, failed.foods)
            assertEquals(4, calls)
            withContext(Dispatchers.Main) { vm.searchFoods("banana") }
            val localOnly = vm.foodSearch.first { it.query == "banana" && it.unavailable }
            assertEquals("banana", localOnly.foods.single().id)
            assertEquals(7, calls)
        }
    }

    @Test fun workoutChosenDateAndMovesUpdateObservedDaysWithCalculatedCalories() = runBlocking<Unit> {
        withTimeout(10_000) {
            val vm = newViewModel()
            val observing = launch { vm.tracking.collect() }
            try {
                val origin = currentDate.minusDays(1)
                val destination = origin.minusDays(1)
                val input = WorkoutInput(origin, WorkoutType.Running, 30, "07:30", "Morning run")
                val added = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) {
                    vm.previousDay()
                    vm.nextDay() // The form was opened on origin, but selectedDay changed before Save.
                    vm.addWorkout(input) { added.complete(Unit) }
                }
                added.await()
                assertTrue(repository.observeTracking(currentDate.toDayKey()).first().workouts.isEmpty())
                val original = repository.observeTracking(origin.toDayKey()).first().workouts.single()
                assertEquals(300, original.estimatedCalories)
                assertEquals("07:30", original.startTime)
                withContext(Dispatchers.Main) { vm.previousDay() }
                vm.tracking.first { it.day == origin && it.workouts.size == 1 }
                val moved = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) {
                    vm.updateWorkout(origin.toDayKey(), original.id,
                        input.copy(day = destination, type = WorkoutType.Calisthenics,
                            durationMinutes = 45, startTime = "18:15", notes = "Evening session")) { moved.complete(Unit) }
                }
                moved.await()
                vm.tracking.first { it.day == origin && it.workouts.isEmpty() }
                withContext(Dispatchers.Main) { vm.previousDay() }
                val updated = vm.tracking.first { it.day == destination && it.workouts.size == 1 }.workouts.single()
                assertEquals(original.copy(type = WorkoutType.Calisthenics, durationMinutes = 45,
                    startTime = "18:15", notes = "Evening session", estimatedCalories = 315), updated)
                withContext(Dispatchers.Main) { vm.deleteWorkout(destination.toDayKey(), original.id) }
                vm.tracking.first { it.day == destination && it.workouts.isEmpty() }
            } finally { observing.cancelAndJoin() }
        }
    }

    @Test fun workoutRejectsFutureDayInvalidTimeAndDuration() = runBlocking {
        val vm = newViewModel()
        val valid = WorkoutInput(currentDate)
        withContext(Dispatchers.Main) {
            listOf(valid.copy(day = currentDate.plusDays(1)), valid.copy(durationMinutes = 0),
                valid.copy(durationMinutes = 1441), valid.copy(startTime = "25:00")).forEach {
                vm.addWorkout(it) { error("Invalid submission saved") }
            }
        }
        assertTrue(repository.observeTracking(currentDate.toDayKey()).first().workouts.isEmpty())
        assertTrue(repository.observeTracking(currentDate.plusDays(1).toDayKey()).first().workouts.isEmpty())
    }

    private val remoteFixture = """{"code":"1234567890128","product_name":"Fixture food","nutriments":{
        "energy-kcal_100g":200,"proteins_100g":10,"carbohydrates_100g":20,"fat_100g":5}}"""

    @Test
    fun localDateRefreshFollowsTodayButPreservesPastSelectionAndClampsFuture() = runBlocking {
        val vm = newViewModel()
        withContext(Dispatchers.Main) {
            currentDate = currentDate.plusDays(1)
            vm.refreshToday()
            assertEquals(currentDate, vm.today.value)
            assertEquals(currentDate, vm.selectedDay.value)
            vm.previousDay()
            val selectedPast = vm.selectedDay.value
            currentDate = currentDate.plusDays(1)
            vm.refreshToday()
            assertEquals(selectedPast, vm.selectedDay.value)
            currentDate = selectedPast.minusDays(1) // Device timezone/date moved back.
            vm.refreshToday()
            assertEquals(currentDate, vm.selectedDay.value)
            vm.nextDay()
            assertEquals(currentDate, vm.selectedDay.value)
        }
    }
    @Test
    fun weightLoggingUsesRealTodayWhilePastDayIsSelectedAndFlowEmitsUpdates() = runBlocking {
        withTimeout(10_000) {
            val vm = newViewModel()
            val observing = launch { vm.weightHistory.collect() }
            try {
                withContext(Dispatchers.Main) { vm.previousDay() }
                val past = vm.selectedDay.value
                withContext(Dispatchers.Main) { assertTrue(vm.logWeight(75.0)) }
                assertEquals(75.0, vm.weightHistory.first { it.entries.isNotEmpty() }.entries.single().weightKg, 0.0)
                assertNull(repository.weightForDay(past))
                assertNull(vm.weightHistory.value.entries.weightForSelectedDay(past, currentDate))
                withContext(Dispatchers.Main) { assertTrue(vm.logWeight(74.8)) }
                assertEquals(currentDate, vm.weightHistory.first { it.entries.singleOrNull()?.weightKg == 74.8 }.entries.single().day)
                assertEquals(1, database.trackDao().observeWeightEntries().first().size)
                currentDate = currentDate.plusDays(1) // Injected clock, never the device clock.
                withContext(Dispatchers.Main) { assertTrue(vm.logWeight(74.3)) }
                assertEquals(past, vm.selectedDay.value)
                assertEquals(2, vm.weightHistory.first { it.entries.size == 2 }.entries.size)
                assertEquals(74.3, vm.weightHistory.value.entries.latestWeight(currentDate)!!.weightKg, 0.0)
            } finally { observing.cancelAndJoin() }
        }
    }

    @Test
    fun targetAndWeightModuleChangesFlowWithoutChangingWeightHistory() = runBlocking {
        withTimeout(10_000) {
            val vm = newViewModel()
            val observing = launch { vm.uiSettings.collect() }
            try {
                withContext(Dispatchers.Main) { assertTrue(vm.logWeight(73.85)) }
                val before = database.trackDao().observeWeightEntries().first()
                withContext(Dispatchers.Main) {
                    vm.updateGoals(TrackGoals(targetWeightKg = 69f)) {}
                    vm.setTodayModuleEnabled(TodayModule.Weight, true)
                }
                vm.uiSettings.first { it.goals.targetWeightKg == 69f && it.today.showWeight }
                withContext(Dispatchers.Main) { vm.setTodayModuleEnabled(TodayModule.Weight, false) }
                vm.uiSettings.first { !it.today.showWeight }
                withContext(Dispatchers.Main) { vm.setTodayModuleEnabled(TodayModule.Weight, true) }
                vm.uiSettings.first { it.today.showWeight }
                assertEquals(before, database.trackDao().observeWeightEntries().first())
            } finally { observing.cancelAndJoin() }
        }
    }

}
