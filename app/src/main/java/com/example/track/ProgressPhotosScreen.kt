package com.example.track

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

class ProgressPhotoActions(
    val importGallery: (Uri) -> Unit = {},
    val prepareCapture: suspend () -> Uri? = { null },
    val finishCapture: (Boolean) -> Unit = {},
    val cancel: () -> Unit = {},
    val save: (LocalDate) -> Unit = {},
    val delete: suspend (Long) -> Boolean = { false },
    val load: suspend (String, Int, Boolean) -> Bitmap? = { _, _, _ -> null },
    val error: (String) -> Unit = {},
    val clearError: () -> Unit = {},
)

@Composable
internal fun ProgressPhotosSection(history: ProgressPhotoHistory, today: LocalDate,
    busy: Boolean, load: suspend (String, Int, Boolean) -> Bitmap?, onAdd: () -> Unit,
    onHistory: () -> Unit, onOpen: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Progress Photos", style = MaterialTheme.typography.titleLarge)
            when {
                history.loading -> Text("Loading photos…")
                history.error -> Text("Could not load photos")
                history.photos.isEmpty() -> Text("No progress photos yet")
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history.photos.take(3), key = { it.id }) { photo ->
                        PhotoTile(photo, today, load, onOpen)
                    }
                }
            }
            if (busy) Text("Preparing photo…")
            Button(onClick = onAdd, enabled = !busy && !history.loading && !history.error,
                modifier = Modifier.fillMaxWidth()) { Text("Add photo") }
            if (history.photos.isNotEmpty()) TextButton(onClick = onHistory,
                modifier = Modifier.fillMaxWidth()) { Text("View all") }
        }
    }
}

@Composable
private fun PhotoTile(photo: ProgressPhoto, today: LocalDate,
    load: suspend (String, Int, Boolean) -> Bitmap?, onOpen: (Long) -> Unit,
) {
    Column(Modifier.width(128.dp).clip(MaterialTheme.shapes.large)
        .clickable { onOpen(photo.id) }.testTag("photo-${photo.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LocalPhoto(photo.localFileName, false, load, Modifier.fillMaxWidth().height(160.dp),
            "Progress photo ${photo.id}", crop = true)
        Text(formatWorkoutDate(photo.day, today), style = MaterialTheme.typography.labelMedium)
    }
}

/** Only the loader touches disk. Its requested decode size is capped even on very large displays. */
@Composable
internal fun LocalPhoto(name: String, pending: Boolean, load: suspend (String, Int, Boolean) -> Bitmap?,
    modifier: Modifier, description: String, crop: Boolean = false,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val edge = with(density) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceIn(32, 2048)
        var loading by remember(name, pending, edge) { mutableStateOf(true) }
        val bitmap by produceState<Bitmap?>(null, name, pending, edge) {
            value = load(name, edge, pending)
            loading = false
        }
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp))
            bitmap == null -> Text("Photo unavailable", Modifier.padding(8.dp))
            else -> Image(bitmap!!.asImageBitmap(), description, Modifier.fillMaxSize(),
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        }
    }
}

@Composable
internal fun ProgressPhotoDialogs(
    history: ProgressPhotoHistory, editor: ProgressPhotoEditState, actions: ProgressPhotoActions,
    today: LocalDate, adding: Boolean, onCloseAdd: () -> Unit,
    showingHistory: Boolean, onCloseHistory: () -> Unit, viewedId: Long?, onView: (Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), actions.finishCapture)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.importGallery(uri)
    }
    fun launchCamera() {
        // Finish launching or cancel the target even if rotation disposes this composition.
        scope.launch(NonCancellable) {
            val uri = actions.prepareCapture() ?: return@launch
            try { camera.launch(uri) }
            catch (_: Exception) {
                actions.finishCapture(false)
                actions.error("Camera unavailable. You can choose a photo from the gallery.")
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else actions.error("Camera permission is needed to take a photo. Allow it in app settings or choose from gallery.")
    }
    if (adding) {
        AlertDialog(onDismissRequest = onCloseAdd,
            title = { Text("Add progress photo") },
            text = { Text("Photos stay on this device in Track's private storage.") },
            confirmButton = {
                TextButton(onClick = {
                    onCloseAdd()
                    // Track declares CAMERA for barcode scanning, so capture also needs its runtime grant.
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        launchCamera()
                    } else permission.launch(Manifest.permission.CAMERA)
                }) { Text("Take photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onCloseAdd()
                    try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (_: Exception) { actions.error("Gallery unavailable. Try again.") }
                }) { Text("Choose from gallery") }
            })
    }
    if (showingHistory) PhotoPage(onCloseHistory) {
        PhotoTopBar("Photo history", onCloseHistory)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (history.loading || history.error || history.photos.isEmpty()) item {
                Text(when {
                    history.loading -> "Loading photos…"
                    history.error -> "Could not load photos"
                    else -> "No progress photos yet"
                })
            }
            history.photos.groupBy { it.day }.forEach { (day, photos) ->
                item(key = "date-$day") { Text(formatWorkoutDate(day, today), style = MaterialTheme.typography.titleMedium) }
                items(photos.chunked(2), key = { "row-${it.first().id}" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { PhotoTile(it, today, actions.load) { id -> onView(id) } }
                    }
                }
            }
        }
    }
    history.photos.firstOrNull { it.id == viewedId }?.let { photo ->
        PhotoViewer(photo, today, actions, onDismiss = { onView(null) })
    }
    editor.draft?.let { draft -> PhotoEditor(draft, editor.busy, today, actions) }
    editor.error?.let { error ->
        AlertDialog(onDismissRequest = actions.clearError, title = { Text("Progress photo") },
            text = { Text(error) }, confirmButton = { TextButton(onClick = actions.clearError) { Text("OK") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoEditor(draft: ProgressPhotoDraft, busy: Boolean, today: LocalDate, actions: ProgressPhotoActions) {
    var dayKey by rememberSaveable(draft.fileName) { mutableStateOf(today.toDayKey()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val day = dayKey.toTrackDay()
    val dismiss = { if (!busy) actions.cancel() }
    PhotoPage(dismiss) {
        PhotoTopBar("Save progress photo", dismiss, enabled = !busy)
        LocalPhoto(draft.fileName, true, actions.load, Modifier.weight(1f).fillMaxWidth().padding(24.dp), "Photo preview")
        OutlinedButton(onClick = { pickingDate = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).semantics { contentDescription = "Photo date" }) {
            Text("Date · ${formatWorkoutDate(day, today)}")
        }
        Button(onClick = { actions.save(day) }, enabled = !busy && isValidPhotoDay(day, today),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(if (busy) "Saving…" else "Save Photo")
        }
    }
    if (pickingDate) {
        val selectable = remember(today) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = isValidPhotoDay(utcTimeMillis.toWorkoutPickerDay(), today)
            override fun isSelectableYear(year: Int) = year <= today.year
        } }
        val picker = rememberDatePickerState(initialSelectedDateMillis = day.toWorkoutPickerMillis(),
            yearRange = 1..today.year, selectableDates = selectable)
        DatePickerDialog(onDismissRequest = { pickingDate = false }, confirmButton = {
            val picked = picker.selectedDateMillis?.toWorkoutPickerDay()
            TextButton(enabled = picked != null && isValidPhotoDay(picked, today), onClick = {
                if (picked != null && isValidPhotoDay(picked, today)) { dayKey = picked.toDayKey(); pickingDate = false }
            }) { Text("Set date") }
        }, dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable
private fun PhotoViewer(photo: ProgressPhoto, today: LocalDate, actions: ProgressPhotoActions, onDismiss: () -> Unit) {
    var confirming by rememberSaveable(photo.id) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PhotoPage({ if (!deleting) onDismiss() }) {
        PhotoTopBar(formatWorkoutDate(photo.day, today), onDismiss, enabled = !deleting)
        LocalPhoto(photo.localFileName, false, actions.load,
            Modifier.weight(1f).fillMaxWidth().testTag("photo-viewer"), "Progress photo ${photo.id}")
        if (error) Text("Could not delete photo. Try again.", Modifier.padding(24.dp))
        TextButton(onClick = { confirming = true }, enabled = !deleting,
            modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Delete photo") }
    }
    if (confirming) AlertDialog(onDismissRequest = { if (!deleting) confirming = false },
        title = { Text("Delete progress photo?") }, text = { Text("This removes the photo from Track. It cannot be undone.") },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            deleting = true
            scope.launch {
                try {
                    if (actions.delete(photo.id)) onDismiss() else error = true
                    confirming = false
                } finally { deleting = false }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { confirming = false }) { Text("Cancel") } })
}

@Composable
private fun PhotoPage(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding(), content = content)
        }
    }
}

@Composable
private fun PhotoTopBar(title: String, onBack: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = enabled) { Text("Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}
