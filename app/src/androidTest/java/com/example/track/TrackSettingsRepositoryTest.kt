package com.example.track

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackSettingsRepositoryTest {
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: TrackSettingsRepository

    @Before
    fun openStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "settings-test-${UUID.randomUUID()}")
        check(directory.mkdir())
        createRepository()
    }

    private fun createRepository() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = TrackSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "isolated.preferences_pb")
        })
    }

    @After
    fun closeStore() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        // Only this test's unique cache directory, never the production settings file.
        assertTrue(directory.deleteRecursively())
    }

    @Test
    fun goalsCustomizationAndProfileSurviveStoreRecreation() = runBlocking {
        withTimeout(10_000) {
            assertEquals(TrackUiSettings(), repository.settingsFlow.first())
            val goals = TrackGoals(2_400, 180, 275, 80, 3.125f, 12_000, 67.5f)
            repository.setTodayModuleEnabled(TodayModule.Workout, false)
            repository.updateGoals(goals)
            repository.updateProfile(TrackProfile("Taylor"))
            val expected = TrackUiSettings(TodayCustomization(showWorkout = false), goals, TrackProfile("Taylor"))
            assertEquals(expected, repository.settingsFlow.first())

            scope.coroutineContext.job.cancelAndJoin()
            createRepository()
            assertEquals(expected, repository.settingsFlow.first())
            repository.setTodayModuleEnabled(TodayModule.Workout, true)
            assertEquals(TrackUiSettings(goals = goals, profile = TrackProfile("Taylor")), repository.settingsFlow.first())
        }
    }

    @Test
    fun profileEditChangesOnlyItsOwnKey() = runBlocking {
        withTimeout(10_000) {
            val goals = TrackGoals(calories = 2_400, targetWeightKg = 80f)
            repository.updateGoals(goals)
            repository.setTodayModuleEnabled(TodayModule.Weight, true)
            repository.updateProfile(TrackProfile("  Morgan  "))
            assertEquals(
                TrackUiSettings(
                    TodayCustomization(showWeight = true),
                    goals,
                    TrackProfile("Morgan"),
                ),
                repository.settingsFlow.first(),
            )
        }
    }

    @Test
    fun concurrentModuleAndGoalEditsDoNotOverwriteOtherKeys() = runBlocking {
        withTimeout(10_000) {
            val goals = TrackGoals(calories = 2_400, waterLiters = 3f)
            coroutineScope {
                launch { repository.updateGoals(goals) }
                TodayModule.entries.forEach { module ->
                    launch { repository.setTodayModuleEnabled(module, module == TodayModule.Weight) }
                }
            }
            val expected = TrackUiSettings(
                TodayCustomization(false, false, false, false, false, false, true), goals,
            )
            assertEquals(expected, repository.settingsFlow.first())
        }
    }

    @Test
    fun readIOExceptionFallsBackToDefaults() = runBlocking {
        val failingRepository = TrackSettingsRepository(failingStore(IOException("Unavailable file")))
        assertEquals(TrackUiSettings(), failingRepository.settingsFlow.first())
    }

    @Test(expected = IllegalStateException::class)
    fun readProgrammingErrorsAreNotSwallowed() {
        runBlocking {
            TrackSettingsRepository(failingStore(IllegalStateException("Invalid state")))
                .settingsFlow.first()
        }
    }

    private fun failingStore(error: Exception) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw error
    }
}
