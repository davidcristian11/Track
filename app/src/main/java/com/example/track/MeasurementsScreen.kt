package com.example.track

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun MeasurementsCard(
    history: MeasurementHistoryState, range: ProgressRange, today: LocalDate,
    onLog: () -> Unit, onHistory: () -> Unit,
) {
    val latest = latestMeasurements(history.entries, today)
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Measurements", style = MaterialTheme.typography.titleLarge)
            when {
                history.loading -> Text("Loading measurements…")
                history.error -> Text("Could not load measurements")
                latest.isEmpty -> Text("No measurements logged yet")
            }
            Text("Latest values · ${range.label} change", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            MeasurementMetric.entries.forEach { metric ->
                val value = metric.value(latest)?.let { "${formatMeasurement(it)} cm" } ?: "—"
                val change = formatMeasurementChange(measurementChange(history.entries, metric, range, today))
                Row(Modifier.fillMaxWidth().testTag("measurement-${metric.name}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(metric.label, Modifier.weight(1f))
                    Text(value)
                    Text(change, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onLog, enabled = !history.loading && !history.error,
                modifier = Modifier.fillMaxWidth()) { Text("Log measurements") }
            TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("View history") }
        }
    }
}

@Composable
internal fun MeasurementHistoryDialog(
    history: MeasurementHistoryState, range: ProgressRange, today: LocalDate,
    onDismiss: () -> Unit, onLog: () -> Unit, onEdit: (BodyMeasurement) -> Unit,
) {
    MeasurementPage(onDismiss) {
        MeasurementTopBar("Measurement history", onDismiss)
        Text("${range.label} · ${formatWorkoutDate(range.startDay(today), today)} – ${formatWorkoutDate(today, today)}",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        val entries = measurementsInRange(history.entries, range, today).asReversed()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (history.loading || history.error || entries.isEmpty()) {
                item { Text(when {
                    history.loading -> "Loading measurements…"
                    history.error -> "Could not load measurements"
                    history.entries.isEmpty() -> "No measurements logged yet"
                    else -> "No measurements in this range"
                }) }
            }
            items(entries, key = { it.day.toDayKey() }) { entry ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(entry) }.testTag("measurement-history-${entry.day}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatWorkoutDate(entry.day, today), style = MaterialTheme.typography.titleMedium)
                        Text("${measurementSummary(entry.values)} cm", style = MaterialTheme.typography.bodyMedium)
                        Text("Edit", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(onClick = onLog, enabled = !history.loading && !history.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) { Text("Log measurements") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementEditorDialog(
    today: LocalDate, initial: BodyMeasurement?, history: List<BodyMeasurement>, onDismiss: () -> Unit,
    onSave: suspend (BodyMeasurement, LocalDate?) -> MeasurementSaveResult,
    onDelete: suspend (LocalDate) -> Boolean,
) {
    var dayKey by rememberSaveable { mutableStateOf((initial?.day ?: today).toDayKey()) }
    val day = dayKey.toTrackDay()
    // Logging an occupied date loads that snapshot; editing keeps the original draft when moving.
    val stored = initial ?: history.firstOrNull { it.day == day }
    val draftKey = initial?.day?.toDayKey() ?: dayKey
    var inputs by rememberSaveable(draftKey) {
        mutableStateOf(ArrayList(MeasurementMetric.entries.map { it.value(stored?.values ?: BodyMeasurements())?.toString().orEmpty() }))
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parsed = inputs.map(::parseMeasurement)
    val values = BodyMeasurements(parsed[0], parsed[1], parsed[2], parsed[3], parsed[4])
    val valid = inputs.indices.all { inputs[it].isBlank() || parsed[it] != null } && day <= today
    val dismiss = { if (!saving) onDismiss() }
    MeasurementPage(dismiss) {
        BackHandler(saving) { }
        MeasurementTopBar("Body Measurements", dismiss)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickingDate = true }, enabled = !saving,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Measurement date" }) {
                Text("Date · ${formatWorkoutDate(day, today)}")
            }
            Text("Record any measurements in cm. Leave unmeasured fields blank.", style = MaterialTheme.typography.bodyMedium)
            MeasurementMetric.entries.forEachIndexed { index, metric ->
                OutlinedTextField(value = inputs[index], onValueChange = { value ->
                    inputs = ArrayList(inputs).apply { set(index, value) }; error = null
                }, enabled = !saving, label = { Text(metric.label) }, suffix = { Text("cm") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = inputs[index].isNotBlank() && parsed[index] == null,
                    supportingText = { if (inputs[index].isNotBlank() && parsed[index] == null) Text("Enter 10–300 cm") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${metric.label}, cm" })
            }
            if (values.isEmpty && stored != null) Text("To remove all measurements for this day, delete the entry.")
            if (stored != null) TextButton(onClick = { confirmingDelete = true }, enabled = !saving) { Text("Delete entry") }
        }
        error?.let {
            Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        Button(enabled = valid && !values.isEmpty && !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), onClick = {
                saving = true
                scope.launch {
                    try {
                        when (onSave(BodyMeasurement(day, values), stored?.day)) {
                            MeasurementSaveResult.Saved -> onDismiss()
                            MeasurementSaveResult.DateOccupied -> error = "That date already has measurements. Choose another date or edit its entry from history."
                            MeasurementSaveResult.MissingEntry -> error = "This entry no longer exists. Close and log a new entry."
                            MeasurementSaveResult.Invalid -> error = "Choose today or a past date and enter 10–300 cm."
                            MeasurementSaveResult.Failed -> error = "Could not save. Try again."
                        }
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save Measurements") }
    }
    if (pickingDate) {
        val selectableDates = remember(today) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toWorkoutPickerDay() <= today
            override fun isSelectableYear(year: Int) = year <= today.year
        } }
        val picker = rememberDatePickerState(initialSelectedDateMillis = day.toWorkoutPickerMillis(),
            yearRange = 1..maxOf(today.year, day.year), selectableDates = selectableDates)
        DatePickerDialog(onDismissRequest = { pickingDate = false }, confirmButton = {
            val picked = picker.selectedDateMillis?.toWorkoutPickerDay()
            TextButton(enabled = picked != null && picked <= today, onClick = {
                if (picked != null && picked <= today) { dayKey = picked.toDayKey(); pickingDate = false; error = null }
            }) { Text("Set date") }
        }, dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    if (confirmingDelete && stored != null) {
        AlertDialog(onDismissRequest = { if (!saving) confirmingDelete = false },
            title = { Text("Delete measurements?") },
            text = { Text("Remove the measurement entry for ${formatWorkoutDate(stored.day, today)}?") },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        if (onDelete(stored.day)) onDismiss() else error = "Could not delete. Try again."
                        confirmingDelete = false
                    } finally { saving = false }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !saving, onClick = { confirmingDelete = false }) { Text("Cancel") } })
    }
}

@Composable
private fun MeasurementPage(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), content = content)
        }
    }
}

@Composable
private fun MeasurementTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
    }
}
