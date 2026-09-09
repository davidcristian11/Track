package com.example.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Files only: Room stores generated basenames, never external URIs or image bytes. */
class ProgressPhotoStorage(private val context: Context, private val directory: File = File(context.filesDir, "progress_photos")) {
    private val pendingDirectory = File(directory, "pending")

    fun resolve(name: String, pending: Boolean = false): File {
        require(isSafePhotoFileName(name)) { "Invalid photo filename" }
        return File(if (pending) pendingDirectory else directory, name)
    }

    suspend fun createCapture(): String = withContext(Dispatchers.IO) {
        prepareDirectory()
        newPhotoFileName().also { check(resolve(it, true).createNewFile()) }
    }

    fun captureUri(name: String): Uri = FileProvider.getUriForFile(context,
        "${context.packageName}.progressphotos", resolve(name, true))

    suspend fun importGallery(uri: Uri): String = importImage {
        context.contentResolver.openInputStream(uri) ?: throw IOException("Image could not be opened")
    }

    // Separate stream boundary permits deterministic tests without a system picker.
    internal suspend fun importImage(open: () -> InputStream): String = withContext(Dispatchers.IO) {
        prepareDirectory()
        val name = newPhotoFileName()
        try {
            open().use { input -> resolve(name, true).outputStream().use { output -> input.copyTo(output) } }
            validate(name)
            name
        } catch (error: Exception) {
            delete(name, pending = true)
            throw error
        }
    }

    suspend fun validate(name: String) = withContext(Dispatchers.IO) {
        val file = resolve(name, true)
        if (!file.isFile || file.length() == 0L) throw IOException("Empty photo")
        // Decode a tiny image to reject corrupt/unsupported input without a full-resolution bitmap.
        decode(file, 32).recycle()
    }

    suspend fun commit(name: String) = withContext(Dispatchers.IO) {
        validate(name)
        if (!resolve(name, true).renameTo(resolve(name))) throw IOException("Could not store photo")
    }

    suspend fun delete(name: String, pending: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = resolve(name, pending)
            (!file.exists() || file.delete()).also {
                if (!it) Log.w("TrackPhotos", "Photo file cleanup failed")
            }
        } catch (_: Exception) {
            Log.w("TrackPhotos", "Photo file cleanup failed")
            false
        }
    }

    suspend fun load(name: String, maxEdge: Int, pending: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        try { decode(resolve(name, pending), maxEdge.coerceIn(32, 2048)) }
        catch (_: Exception) { Log.w("TrackPhotos", "Photo unavailable: missing or unreadable image"); null }
        catch (_: OutOfMemoryError) { Log.w("TrackPhotos", "Photo could not fit in available memory"); null }
    }

    private fun decode(file: File, maxEdge: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val scale = minOf(1.0, maxEdge.toDouble() / max(info.size.width, info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        // ImageDecoder also applies the source's EXIF orientation.
    }

    private fun prepareDirectory() {
        if (!pendingDirectory.isDirectory && !pendingDirectory.mkdirs()) throw IOException("Photo storage unavailable")
        // Abandoned drafts after process death: bounded, opportunistic cleanup; never touch saved files.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        pendingDirectory.listFiles()?.filter { isSafePhotoFileName(it.name) && it.lastModified() < cutoff }?.forEach {
            if (!it.delete()) Log.w("TrackPhotos", "Abandoned draft cleanup failed")
        }
    }
}
