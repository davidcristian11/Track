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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun WeightLogDialog(
    today: LocalDate, entry: WeightEntry?, onDismiss: () -> Unit, onSave: suspend (Double) -> Boolean,
) {
    // Preserve the stored precision when editing; rounding is for display only.
    var input by rememberSaveable(today) { mutableStateOf(entry?.weightKg?.toString() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val weight = parseWeight(input)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (entry == null) "Log weight" else "Update weight") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatWorkoutDate(today, today))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; failed = false },
                    enabled = !saving,
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = input.isNotBlank() && weight == null,
                    supportingText = {
                        Text(if (failed) "Could not save. Try again." else "Enter 20–400 kg. Decimals welcome.")
                    },
                    modifier = Modifier.semantics { contentDescription = "Weight, kg" },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = weight != null && !saving, onClick = {
                val value = weight ?: return@TextButton
                saving = true
                scope.launch {
                    try {
                        if (onSave(value)) onDismiss() else failed = true
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
