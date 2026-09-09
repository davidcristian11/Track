package com.example.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal enum class DailyMetric(val label: String) { Steps("steps"), Sleep("sleep") }

// Both dashboards use this editor. Only its draft lives in Compose; saved values come from Room.
@Composable
internal fun DailyMetricDialog(
    metric: DailyMetric,
    day: LocalDate,
    today: LocalDate,
    existingValue: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?, (Boolean) -> Unit) -> Unit,
) {
    var primary by rememberSaveable(day, metric) {
        mutableStateOf(existingValue?.let { if (metric == DailyMetric.Sleep) (it / 60).toString() else it.toString() } ?: "")
    }
    var minutes by rememberSaveable(day, metric) { mutableStateOf(((existingValue ?: 0) % 60).toString()) }
    var saving by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val value = if (metric == DailyMetric.Steps) parseSteps(primary) else {
        val hours = primary.trim().toIntOrNull()
        val remainder = minutes.trim().toIntOrNull()
        if (hours == null || remainder == null) null else sleepMinutesFromParts(hours, remainder)
    }
    fun save(valueToSave: Int?) {
        saving = true
        failed = false
        onSave(valueToSave) { success ->
            saving = false
            if (success) onDismiss() else failed = true
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("${if (existingValue == null) "Log" else "Update"} ${metric.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatWorkoutDate(day, today))
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it; failed = false },
                    label = { Text(if (metric == DailyMetric.Steps) "Steps" else "Hours") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = primary.isNotBlank() && value == null,
                )
                if (metric == DailyMetric.Sleep) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it; failed = false },
                        label = { Text("Minutes") },
                        singleLine = true,
                        enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = minutes.isNotBlank() && value == null,
                    )
                }
                Text(if (failed) "Could not save. Try again." else if (metric == DailyMetric.Steps)
                    "Enter 0–${formatSteps(MaxDailySteps)} steps." else "Hours: 0–24. Minutes: 0–59. Maximum: 24h.")
                if (existingValue != null) {
                    TextButton(enabled = !saving, onClick = { save(null) }) { Text("Remove entry") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && !saving && day <= today, onClick = { save(value) }) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
