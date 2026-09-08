package com.example.track

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackDaoTest {
    private lateinit var database: TrackDatabase
    private lateinit var dao: TrackDao
    private val day = TrackDemoBaseline.referenceDay.toDayKey()
    private val otherDay = "2026-09-03"

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, TrackDatabase::class.java,
        ).build()
        dao = database.trackDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun emptyDatabaseHasNoDemoRows() = runBlocking {
        assertTrue(dao.observeFoodLogs(day).first().isEmpty())
        assertTrue(dao.observeWorkouts(day).first().isEmpty())
        assertNull(dao.dailyState(day))
        assertEquals(TrackSessionData(TrackDemoBaseline.referenceDay), TrackRepository(database).observeTracking(day).first())
    }

    @Test
    fun insertedFoodSnapshotIsReturnedByFlowWithoutCatalogJoin() = runBlocking {
        val row = food().copy(catalogFoodId = "removed-from-catalog", name = "Saved yogurt")
        val id = dao.insertFood(row)
        assertEquals(row.copy(id = id), dao.observeFoodLogs(day).first().single())
        val display = TrackRepository(database).observeTracking(day).first()
        assertEquals(1_520, display.nutrition.calories)
        assertEquals("Saved yogurt", display.foods.single().name)
    }

    @Test
    fun foodDaysAndLoggedOrderAreDeterministic() = runBlocking {
        val late = dao.insertFood(food().copy(createdAt = 20))
        val early = dao.insertFood(food().copy(createdAt = 10))
        val tied = dao.insertFood(food().copy(createdAt = 20))
        dao.insertFood(food().copy(dayKey = otherDay))
        assertEquals(listOf(early, late, tied), dao.observeFoodLogs(day).first().map { it.id })
        assertEquals(1, dao.observeFoodLogs(otherDay).first().size)
    }

    @Test
    fun newestWorkoutsComeFirstWithIdTieBreakerAndDayIsolation() = runBlocking {
        val old = dao.insertWorkout(workout(10))
        val first = dao.insertWorkout(workout(20))
        val second = dao.insertWorkout(workout(20))
        dao.insertWorkout(workout(30).copy(dayKey = otherDay))
        assertEquals(listOf(second, first, old), dao.observeWorkouts(day).first().map { it.id })
        assertEquals(1, dao.observeWorkouts(otherDay).first().size)
        val display = TrackRepository(database).observeTracking(day).first()
        assertEquals(second, display.workouts.first().id)
        assertEquals(TrackDemoBaseline.forDay(TrackDemoBaseline.referenceDay).workouts.single(), display.workouts.last())
    }

    @Test
    fun waterUpsertReplacesDailyValueWithoutAffectingOtherDays() = runBlocking {
        dao.upsertDailyState(DailyTrackingStateEntity(day, 1_750, true))
        dao.upsertDailyState(DailyTrackingStateEntity(otherDay, 500, false))
        dao.upsertDailyState(DailyTrackingStateEntity(day, 2_000, true))
        assertEquals(2_000, dao.observeDailyState(day).first()?.waterMl)
        assertEquals(500, dao.dailyState(otherDay)?.waterMl)
    }

    @Test
    fun creatineUpsertAndToggleKeepWater() = runBlocking {
        dao.upsertDailyState(DailyTrackingStateEntity(day, 1_750, false))
        assertEquals(false, dao.observeDailyState(day).first()?.creatineCompleted)
        TrackRepository(database).toggleCreatine(day)
        assertEquals(DailyTrackingStateEntity(day, 1_750, true), dao.dailyState(day))
    }

    @Test
    fun rapidWaterAndCreatineMutationsDoNotLoseUpdates() = runBlocking {
        val repository = TrackRepository(database)
        coroutineScope {
            repeat(20) { launch(Dispatchers.Default) { repository.addWater(day) } }
            repeat(17) { launch(Dispatchers.Default) { repository.toggleCreatine(day) } }
        }
        val state = requireNotNull(dao.dailyState(day))
        assertEquals(6_500, state.waterMl)
        assertFalse(state.creatineCompleted)
        assertNull(dao.dailyState(otherDay))
    }

    @Test
    fun scannerUsesSamePersistentFoodPathAndZeroNutrition() = runBlocking {
        val repository = TrackRepository(database)
        repository.addFood(day, MealContext.SNACKS, ScannedFood, 750)
        val row = dao.observeFoodLogs(day).first().single()
        assertEquals("ml", row.unit)
        assertEquals(750, row.amount)
        assertEquals(MealContext.SNACKS.name, row.meal)
        assertEquals(TrackDemoBaseline.forDay(TrackDemoBaseline.referenceDay).nutrition, repository.observeTracking(day).first().nutrition)
    }

    @Test
    fun foodCorrectionsKeepIdentityMoveMealAndDeleteOnlyOneDuplicate() = runBlocking {
        val repository = TrackRepository(database)
        val first = dao.insertFood(food())
        val duplicate = dao.insertFood(food())
        val other = dao.insertFood(food().copy(dayKey = otherDay))
        val original = requireNotNull(repository.food(day, first))
        repository.updateFood(day, original, 150, MealContext.DINNER)
        assertEquals(original.corrected(150, MealContext.DINNER), repository.food(day, first))
        assertEquals(10L, dao.food(day, first)?.createdAt)
        assertEquals(food().copy(id = duplicate), dao.food(day, duplicate))
        repository.deleteFood(otherDay, first) // A stale or wrong day cannot target the row.
        repository.updateFood(otherDay, original, 200, MealContext.SNACKS)
        assertEquals(150, repository.food(day, first)?.amount)
        repository.deleteFood(day, first)
        repository.updateFood(day, original, 200, MealContext.SNACKS) // Gone before Save: safe no-op.
        assertEquals(listOf(duplicate), dao.observeFoodLogs(day).first().map { it.id })
        assertEquals(food().copy(id = other, dayKey = otherDay), dao.food(otherDay, other))
    }

    @Test
    fun workoutCorrectionsPreserveTimeAndNewestRemainingFallback() = runBlocking {
        val repository = TrackRepository(database)
        val older = dao.insertWorkout(workout(10))
        val newer = dao.insertWorkout(workout(20))
        val other = dao.insertWorkout(workout(30).copy(dayKey = otherDay))
        repository.updateWorkout(day, newer, WorkoutInput(day.toTrackDay(), WorkoutType.Walking, 40, "18:10", " Updated run "))
        assertEquals(workout(20).copy(id = newer, activityType = "Walking", durationMinutes = 40,
            notes = "Updated run", estimatedCalories = 160), dao.workout(day, newer))
        repository.deleteWorkout(otherDay, newer)
        repository.updateWorkout(otherDay, newer, WorkoutInput(otherDay.toTrackDay(), WorkoutType.Cycling, 60, "18:10", "Wrong day"))
        assertEquals(newer, repository.observeTracking(day).first().workouts.first().id)
        repository.deleteWorkout(day, newer)
        assertEquals(older, repository.observeTracking(day).first().workouts.first().id)
        repository.deleteWorkout(day, older)
        repository.updateWorkout(day, older, WorkoutInput(day.toTrackDay(), WorkoutType.Cycling, 60, "18:10", "Gone"))
        repository.deleteWorkout(day, 0) // Demo fixture is never a persisted row.
        assertEquals(TrackDemoBaseline.forDay(day.toTrackDay()).workouts, repository.observeTracking(day).first().workouts)
        assertEquals(other, repository.observeTracking(otherDay).first().workouts.single().id)
        repository.deleteWorkout(otherDay, other)
        assertTrue(repository.observeTracking(otherDay).first().workouts.isEmpty())
    }

    @Test
    fun waterCorrectionsClampAtZeroAndRespectReferenceFallback() = runBlocking {
        val repository = TrackRepository(database)
        repository.adjustWater(otherDay, -250)
        assertEquals(0, dao.dailyState(otherDay)?.waterMl)
        repository.addWater(otherDay, 500)
        repository.adjustWater(otherDay, -250)
        assertEquals(250, dao.dailyState(otherDay)?.waterMl)
        repository.adjustWater(day, -250)
        assertEquals(1_250, dao.dailyState(day)?.waterMl)
        assertTrue(requireNotNull(dao.dailyState(day)).creatineCompleted)
        repository.adjustWater(otherDay, -150)
        repository.adjustWater(otherDay, -250)
        assertEquals(0, dao.dailyState(otherDay)?.waterMl)
    }

    @Test
    fun concurrentWaterIncreasesDecreasesAndCreatineKeepEveryMutation() = runBlocking {
        val repository = TrackRepository(database)
        repository.addWater(otherDay, 5_000) // Keep concurrent decrements away from the zero clamp.
        coroutineScope {
            repeat(20) { launch(Dispatchers.Default) { repository.adjustWater(otherDay, 250) } }
            repeat(12) { launch(Dispatchers.Default) { repository.adjustWater(otherDay, -250) } }
            repeat(17) { launch(Dispatchers.Default) { repository.toggleCreatine(otherDay) } }
        }
        assertEquals(DailyTrackingStateEntity(otherDay, 7_000, true), dao.dailyState(otherDay))
        assertNull(dao.dailyState(day))
    }

    @Test
    fun weightUpsertKeepsOneRowPerDayWithFullPrecision() = runBlocking {
        val repository = TrackRepository(database)
        repository.logWeight(day.toTrackDay(), 73.85)
        assertEquals(73.85, repository.weightForDay(day.toTrackDay())!!.weightKg, 0.0)
        repository.logWeight(day.toTrackDay(), 74.8)
        assertEquals(74.8, dao.observeWeightEntries().first().single().weightKg, 0.0)
        assertTrue(dao.weightForDay(day)!!.updatedAt > 0)
        assertNull(repository.weightForDay(otherDay.toTrackDay()))
    }

    @Test
    fun weightsAreOrderedAndRangesAreInclusiveWithoutFillingMissingDays() = runBlocking {
        listOf("2026-09-07", "2026-08-01", "2026-09-01", "2026-09-04").forEach {
            dao.upsertWeight(WeightEntryEntity(it, 75.0, 1))
        }
        assertEquals(listOf("2026-08-01", "2026-09-01", "2026-09-04", "2026-09-07"),
            dao.observeWeightEntries().first().map { it.dayKey })
        assertEquals(listOf("2026-09-01", "2026-09-04", "2026-09-07"),
            dao.observeWeightEntries("2026-09-01", "2026-09-07").first().map { it.dayKey })
        assertTrue(dao.observeWeightEntries("2026-09-08", "2026-09-10").first().isEmpty())
    }

    @Test
    fun weightWritesLeaveFoodsWorkoutsWaterAndCreatineUntouched() = runBlocking {
        val foodId = dao.insertFood(food())
        val workoutId = dao.insertWorkout(workout(10))
        val state = DailyTrackingStateEntity(day, 1750, true)
        dao.upsertDailyState(state)
        dao.upsertWeight(WeightEntryEntity(day, 75.0, 1))
        dao.upsertWeight(WeightEntryEntity(day, 74.8, 2))
        assertEquals(food().copy(id = foodId), dao.food(day, foodId))
        assertEquals(workout(10).copy(id = workoutId), dao.workout(day, workoutId))
        assertEquals(state, dao.dailyState(day))
        assertNull(dao.dailyState(otherDay))
    }

    @Test
    fun invalidWeightCannotOverwriteStoredValue() = runBlocking {
        val repository = TrackRepository(database)
        repository.logWeight(day.toTrackDay(), 75.0)
        listOf(19.9, 400.1, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            try { repository.logWeight(day.toTrackDay(), it); error("Expected validation failure") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(75.0, dao.observeWeightEntries().first().single().weightKg, 0.0)
    }

    @Test fun insertAndEditWorkoutPersistAllFieldsAndRecalculateSnapshot() = runBlocking {
        val repository = TrackRepository(database)
        val input = WorkoutInput(otherDay.toTrackDay(), WorkoutType.Running, 30, "07:30", " Morning run ")
        repository.addWorkout(input)
        val row = dao.observeWorkouts(otherDay).first().single()
        assertEquals("07:30", row.startTime)
        assertEquals("Morning run", row.notes)
        assertEquals(300, row.estimatedCalories)
        repository.updateWorkout(otherDay, row.id,
            input.copy(type = WorkoutType.Calisthenics, durationMinutes = 45, startTime = "18:15", notes = "Evening session"))
        assertEquals(row.copy(activityType = "Calisthenics", durationMinutes = 45, startTime = "18:15",
            notes = "Evening session", estimatedCalories = 315), dao.workout(otherDay, row.id))
    }

    @Test fun moveWorkoutRetainsIdAndCreatedAtWithoutDuplicatesOrUnrelatedChanges() = runBlocking {
        val repository = TrackRepository(database)
        val moving = dao.insertWorkout(workout(10).copy(estimatedCalories = 280))
        val staying = dao.insertWorkout(workout(20))
        val destination = dao.insertWorkout(workout(30).copy(dayKey = otherDay))
        val input = WorkoutInput(otherDay.toTrackDay(), WorkoutType.Calisthenics, 45, "18:15", "Evening session")
        repository.updateWorkout(day, moving, input)
        assertNull(dao.workout(day, moving))
        assertEquals(listOf(staying), dao.observeWorkouts(day).first().map { it.id })
        assertEquals(setOf(moving, destination), dao.observeWorkouts(otherDay).first().map { it.id }.toSet())
        assertEquals(WorkoutEntity(moving, otherDay, "Calisthenics", 45, "Evening session", 315, "18:15", 10),
            dao.workout(otherDay, moving))
        assertEquals(workout(20).copy(id = staying), dao.workout(day, staying))
        assertEquals(workout(30).copy(id = destination, dayKey = otherDay), dao.workout(otherDay, destination))
        repository.updateWorkout(day, moving, input.copy(durationMinutes = 60)) // Stale editor cannot reinsert/move it again.
        repository.deleteWorkout(day, moving)
        assertEquals(45, dao.workout(otherDay, moving)?.durationMinutes)
    }

    private fun food() = LoggedFood.snapshot(0, MealContext.LUNCH, LocalFoodCatalog.first(), 119)
        .toEntity(day, 10)

    private fun workout(createdAt: Long) = LoggedWorkout(0, WorkoutType.Running, 30, "Easy run")
        .toEntity(day, createdAt)
}
