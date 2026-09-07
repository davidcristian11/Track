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
                    vm.addWorkout(WorkoutType.Running, 30, "Past run") {}
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
}
