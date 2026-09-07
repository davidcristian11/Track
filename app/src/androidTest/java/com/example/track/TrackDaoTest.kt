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

    private fun food() = LoggedFood.snapshot(0, MealContext.LUNCH, LocalFoodCatalog.first(), 119)
        .toEntity(day, 10)

    private fun workout(createdAt: Long) = LoggedWorkout(0, WorkoutType.Running, 30, "Easy run")
        .toEntity(day, createdAt)
}
