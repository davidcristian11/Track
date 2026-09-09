package com.example.track

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.track.ui.theme.TrackTheme
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProgressPhotosScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 9)

    @Test fun emptyStateOffersSourcesAndPickerCancellationReturnsWithoutImport() {
        var imported = false
        var launched = false
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertTrue(contract is ActivityResultContracts.PickVisualMedia)
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                assertNotNull(contract.createIntent(context, input))
                launched = true
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED, null) }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TrackTheme { ProgressScreen({}, today, photoActions = ProgressPhotoActions(importGallery = { imported = true })) }
            }
        }
        compose.onNodeWithText("No progress photos yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").assertIsDisplayed()
        compose.onNodeWithText("Choose from gallery").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(launched); assertFalse(imported)
        compose.onNodeWithText("No progress photos yet").assertIsDisplayed()
    }

    @Test fun cameraContractUsesOutputUriAndCancellationReturnsSafely() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, android.Manifest.permission.CAMERA)
        var captured: Boolean? = null
        val output = Uri.parse("content://com.example.track.progressphotos/progress_photo_capture/fixture.jpg")
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertTrue(contract is ActivityResultContracts.TakePicture)
                assertEquals(output, input)
                val intent = contract.createIntent(instrumentation.targetContext, input)
                assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED, null) }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TrackTheme { ProgressScreen({}, today, photoActions = ProgressPhotoActions(
                    prepareCapture = { output }, finishCapture = { captured = it })) }
            }
        }
        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").performClick()
        compose.waitUntil(5000) { captured != null }
        assertEquals(false, captured)
        compose.onNodeWithText("No progress photos yet").assertIsDisplayed()
    }

    @Test fun realLocalPreviewsHistoryViewerDeleteAndMissingFileKeepOtherEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "photo-ui-${UUID.randomUUID()}")
        val storage = ProgressPhotoStorage(context, directory)
        val db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java).build()
        val repository = TrackRepository(db)
        val ids = runBlocking {
            listOf(today.minusDays(8), today, today).map { day ->
                val name = storage.importImage { ByteArrayInputStream(syntheticPhotoBytes()) }
                storage.commit(name)
                repository.insertProgressPhoto(day, name, ProgressPhotoSource.GALLERY)
            }
        }
        val flow = repository.observeProgressPhotos().map { ProgressPhotoHistory(it, loading = false) }
        val actions = ProgressPhotoActions(load = storage::load, delete = { id ->
            val row = repository.progressPhoto(id)!!
            repository.deleteProgressPhoto(id); storage.delete(row.localFileName); true
        })
        try {
            compose.setContent {
                val history by flow.collectAsState(ProgressPhotoHistory())
                TrackTheme { ProgressScreen({}, today, photos = history, photoActions = actions) }
            }
            compose.onNodeWithTag("photo-${ids.last()}").performScrollTo().assertIsDisplayed()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Progress photo ${ids.last()}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("7D").performScrollTo().performClick()
            compose.onNodeWithText("View all").performScrollTo().performClick()
            compose.onNode(hasTestTag("photo-${ids.first()}") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
            compose.onNode(hasTestTag("photo-${ids.last()}") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
            compose.onNodeWithTag("photo-viewer").assertIsDisplayed()
            compose.onNodeWithText("Delete photo").performClick()
            compose.onNodeWithText("Delete progress photo?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Delete photo").performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.waitUntil(5000) { runBlocking { repository.progressPhoto(ids.last()) == null } }
            compose.onNode(hasTestTag("photo-${ids.last()}") and hasAnyAncestor(isDialog())).assertDoesNotExist()
            compose.onNode(hasTestTag("photo-${ids[1]}") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            runBlocking {
                assertEquals(2, repository.observeProgressPhotos().first().size)
                storage.delete(repository.progressPhoto(ids[1])!!.localFileName)
            }
            compose.onNode(hasTestTag("photo-${ids[1]}") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Photo unavailable").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Delete photo").performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.waitUntil(5000) { runBlocking { repository.progressPhoto(ids[1]) == null } }
            compose.onNode(hasTestTag("photo-${ids.first()}") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test fun editorDefaultsTodayBlocksFutureAndSavesChosenPastDate() {
        var chosen: LocalDate? = null
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        compose.setContent { TrackTheme { PhotoEditor(ProgressPhotoDraft(newPhotoFileName(), ProgressPhotoSource.GALLERY),
            false, today, ProgressPhotoActions(load = { _, _, _ -> bitmap }, save = { chosen = it })) } }
        compose.onNodeWithContentDescription("Photo date").assertTextContains("Date · Today, Sep 9")
        compose.onNodeWithContentDescription("Photo date").performClick()
        compose.onNodeWithText("Thursday, September 10, 2026", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Tuesday, September 1, 2026", substring = true).performClick()
        compose.onNodeWithText("Set date").performClick()
        compose.onNodeWithText("Save Photo").performClick()
        assertEquals(today.minusDays(8), chosen)
    }
}
