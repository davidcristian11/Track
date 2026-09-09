package com.example.track

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

internal fun syntheticPhotoBytes(): ByteArray = ByteArrayOutputStream().use { output ->
    Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(90, 130, 110))
        compress(Bitmap.CompressFormat.PNG, 100, output)
        recycle()
    }
    output.toByteArray()
}

class ProgressPhotoStorageTest {
    @Test fun copyValidatePromoteResolveLoadDeleteAndUniqueNames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "photo-storage-${UUID.randomUUID()}")
        val storage = ProgressPhotoStorage(context, directory)
        try {
            val bytes = syntheticPhotoBytes()
            val first = storage.importImage { ByteArrayInputStream(bytes) }
            val second = storage.importImage { ByteArrayInputStream(bytes) }
            assertNotEquals(first, second)
            assertArrayEquals(bytes, storage.resolve(first, true).readBytes())
            assertTrue(storage.resolve(first, true).length() > 0)
            storage.commit(first)
            assertFalse(storage.resolve(first, true).exists())
            assertArrayEquals(bytes, storage.resolve(first).readBytes())
            val thumbnail = storage.load(first, 48)!!
            assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 48)
            thumbnail.recycle()
            assertTrue(storage.delete(first))
            assertTrue(storage.delete(first))
            assertNull(storage.load(first, 48))
            assertTrue(storage.delete(second, true))
            assertTrue(directory.listFiles()!!.filter { it.isFile }.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedPartialCopyCorruptAndEmptyInputsLeaveNoDraftFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "photo-storage-${UUID.randomUUID()}")
        val storage = ProgressPhotoStorage(context, directory)
        try {
            val sources: List<() -> InputStream> = listOf(
                { object : InputStream() { var reads = 0; override fun read(): Int {
                    if (reads++ < 12) return 42
                    throw IOException("Synthetic read failure")
                } } },
                { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
                { ByteArrayInputStream(byteArrayOf()) },
                { throw IOException("Synthetic open failure") },
            )
            sources.forEach { source ->
                try { storage.importImage(source); fail("Expected import failure") } catch (_: IOException) { }
                assertTrue(File(directory, "pending").listFiles()!!.isEmpty())
            }
            val camera = storage.createCapture()
            try { storage.validate(camera); fail("Empty capture should fail") } catch (_: IOException) { }
            assertTrue(storage.delete(camera, true))
            try { storage.resolve("../outside.jpg"); fail("Traversal should fail") } catch (_: IllegalArgumentException) { }
        } finally { directory.deleteRecursively() }
    }

    @Test fun providerExposesOnlyPendingCaptureAndCancellationCleansIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = ProgressPhotoStorage(context)
        val name = storage.createCapture()
        try {
            val uri = storage.captureUri(name)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.progressphotos", uri.authority)
            context.contentResolver.openOutputStream(uri)!!.use { it.write(syntheticPhotoBytes()) }
            storage.validate(name)
            storage.commit(name)
            try {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.progressphotos", storage.resolve(name))
                fail("Saved files must not be exposed by provider")
            } catch (_: IllegalArgumentException) { }
        } finally { storage.delete(name, true); storage.delete(name) }
    }
}
