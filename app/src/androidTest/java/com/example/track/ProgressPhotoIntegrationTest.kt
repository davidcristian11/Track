package com.example.track

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ProgressPhotoIntegrationTest {
    @Test fun daoAllowsSameDayAndOrdersByDateTimeThenIdWithoutAffectingTracking() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        try {
            val dao = db.trackDao()
            val daily = DailyTrackingStateEntity("2026-09-09", 1250, true, 8432, 450)
            val weight = WeightEntryEntity(daily.dayKey, 74.8, 15)
            val measurement = BodyMeasurementEntity(daily.dayKey, 82.0, 101.0, null, 36.0, null, 16)
            dao.upsertDailyState(daily); dao.upsertWeight(weight); dao.upsertMeasurement(measurement)
            val food = LoggedFood.snapshot(0, MealContext.LUNCH, LocalFoodCatalog.first(), 119).toEntity(daily.dayKey, 10)
            val foodId = dao.insertFood(food)
            val workout = LoggedWorkout(0, WorkoutType.Running, 30, "Fixture").toEntity(daily.dayKey, 10)
            val workoutId = dao.insertWorkout(workout)
            suspend fun insert(day: String, time: Long) = dao.insertProgressPhoto(ProgressPhotoEntity(
                dayKey = day, localFileName = newPhotoFileName(), source = "GALLERY", createdAt = time))
            val oldDate = insert("2026-09-01", 999)
            val older = insert(daily.dayKey, 1)
            val first = insert(daily.dayKey, 2)
            val tied = insert(daily.dayKey, 2)
            assertEquals(listOf(tied, first, older, oldDate), dao.observeProgressPhotos().first().map { it.id })
            dao.deleteProgressPhoto(first)
            assertEquals(listOf(tied, older, oldDate), dao.observeProgressPhotos().first().map { it.id })
            assertNull(dao.progressPhoto(first))
            assertEquals(daily, dao.dailyState(daily.dayKey)); assertEquals(weight, dao.weightForDay(daily.dayKey))
            assertEquals(measurement, dao.measurementForDay(daily.dayKey))
            assertEquals(food.copy(id = foodId), dao.food(daily.dayKey, foodId))
            assertEquals(workout.copy(id = workoutId), dao.workout(daily.dayKey, workoutId))
        } finally { db.close() }
    }

    @Test fun viewModelDraftCancelSaveReopenDeleteFutureAndFailedInsertCleanup() = runBlocking {
        withTimeout(25_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "photo-integration-${UUID.randomUUID()}").apply { mkdirs() }
            val name = "photo-integration-${UUID.randomUUID()}.db"
            val db = Room.databaseBuilder(context, TrackDatabase::class.java, name).build()
            val storage = ProgressPhotoStorage(context)
            val createdNames = mutableListOf<String>()
            val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val settings = TrackSettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope) { File(directory, "settings.preferences_pb") })
            val owner = ViewModelStore()
            val today = LocalDate.of(2026, 9, 9)
            val vm = withContext(Dispatchers.Main) {
                TrackViewModel(TrackRepository(db), settings, photoStorage = storage) { today }.also { owner.put("test", it) }
            }
            val collect = launch { vm.progressPhotos.collect() }
            suspend fun draft(): ProgressPhotoDraft {
                withContext(Dispatchers.Main) { vm.importProgressPhoto { storage.importImage { ByteArrayInputStream(syntheticPhotoBytes()) } } }
                return vm.photoEditor.first { !it.busy && it.draft != null }.draft!!.also { createdNames += it.fileName }
            }
            try {
                val canceled = draft()
                assertTrue(db.trackDao().observeProgressPhotos().first().isEmpty())
                withContext(Dispatchers.Main) { vm.cancelProgressPhoto() }
                vm.photoEditor.first { !it.busy && it.draft == null }
                assertFalse(storage.resolve(canceled.fileName, true).exists())
                val saved = draft()
                withContext(Dispatchers.Main) { vm.saveProgressPhoto(today.plusDays(1)) }
                assertNotNull(vm.photoEditor.value.error)
                assertTrue(db.trackDao().observeProgressPhotos().first().isEmpty())
                withContext(Dispatchers.Main) { vm.saveProgressPhoto(today.minusDays(8)) }
                val photo = vm.progressPhotos.first { it.photos.size == 1 }.photos.single()
                assertEquals(today.minusDays(8), photo.day)
                assertEquals(ProgressPhotoSource.GALLERY, photo.source)
                assertEquals(saved.fileName, photo.localFileName)
                assertTrue(storage.resolve(photo.localFileName).isFile)
                val reopened = Room.databaseBuilder(context, TrackDatabase::class.java, name).build()
                try { assertEquals(photo.id, reopened.trackDao().observeProgressPhotos().first().single().id) }
                finally { reopened.close() }
                assertTrue(vm.deleteProgressPhoto(photo.id))
                vm.progressPhotos.first { it.photos.isEmpty() }
                assertFalse(storage.resolve(photo.localFileName).exists())
                vm.photoEditor.first { !it.busy }
                val failed = draft()
                db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_photo BEFORE INSERT ON progress_photos BEGIN SELECT RAISE(ABORT, 'Synthetic failure'); END")
                withContext(Dispatchers.Main) { vm.saveProgressPhoto(today) }
                vm.photoEditor.first { !it.busy && it.error != null }
                assertFalse(storage.resolve(failed.fileName).exists())
                assertFalse(storage.resolve(failed.fileName, true).exists())
                assertTrue(db.trackDao().observeProgressPhotos().first().isEmpty())
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_photo")
                val canceledUri = withContext(Dispatchers.Main) { vm.preparePhotoCapture() }!!
                val canceledName = canceledUri.lastPathSegment!!
                createdNames += canceledName
                withContext(Dispatchers.Main) { vm.finishPhotoCapture(false) }
                vm.photoEditor.first { !it.busy }
                assertFalse(storage.resolve(canceledName, true).exists())
                assertTrue(db.trackDao().observeProgressPhotos().first().isEmpty())
                val cameraUri = withContext(Dispatchers.Main) { vm.preparePhotoCapture() }!!
                createdNames += cameraUri.lastPathSegment!!
                context.contentResolver.openOutputStream(cameraUri)!!.use { it.write(syntheticPhotoBytes()) }
                withContext(Dispatchers.Main) { vm.finishPhotoCapture(true) }
                val cameraDraft = vm.photoEditor.first { !it.busy && it.draft != null }.draft!!
                assertEquals(ProgressPhotoSource.CAMERA, cameraDraft.source)
                assertTrue(db.trackDao().observeProgressPhotos().first().isEmpty())
                withContext(Dispatchers.Main) { vm.saveProgressPhoto(today) }
                val cameraPhoto = vm.progressPhotos.first { it.photos.size == 1 }.photos.single()
                assertEquals(today, cameraPhoto.day)
                assertEquals(ProgressPhotoSource.CAMERA, cameraPhoto.source)
                assertTrue(storage.resolve(cameraPhoto.localFileName).isFile)
                assertTrue(vm.deleteProgressPhoto(cameraPhoto.id))
            } finally {
                collect.cancelAndJoin()
                withContext(Dispatchers.Main) { owner.clear() }
                storeScope.coroutineContext.job.cancelAndJoin()
                createdNames.forEach { storage.delete(it, true); storage.delete(it) }
                db.close(); context.deleteDatabase(name); directory.deleteRecursively()
            }
        }
    }
}
