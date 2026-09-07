package com.example.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun EditFoodLogScreen(original: LoggedFood, onBack: () -> Unit, onSave: (Int, MealContext) -> Unit) {
    var amountText by rememberSaveable(original.id) { mutableStateOf(original.amount.toString()) }
    var mealName by rememberSaveable(original.id) { mutableStateOf(original.meal.name) }
    var mealMenu by remember { mutableStateOf(false) }
    val amount = amountText.toIntOrNull()
    val valid = amount != null && amount in 1..MaxFoodAmount
    val meal = MealContext.valueOf(mealName)
    val preview = if (valid) original.corrected(requireNotNull(amount), meal).nutrition else null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Edit Food", style = MaterialTheme.typography.titleLarge)
        }
        Column {
            Text(original.name, style = MaterialTheme.typography.headlineSmall)
            original.brand?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (${original.unit.symbol})") },
            suffix = { Text(original.unit.symbol) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !valid,
            supportingText = { if (!valid) Text("Enter an amount from 1 to $MaxFoodAmount") },
            modifier = Modifier.fillMaxWidth(),
        )
        Box {
            TextButton(onClick = { mealMenu = true }) { Text("Meal: ${meal.label}") }
            DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                MealContext.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = {
                        mealName = option.name
                        mealMenu = false
                    })
                }
            }
        }
        if (preview != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nutrition", style = MaterialTheme.typography.titleLarge)
                    Text("${preview.calories} kcal", style = MaterialTheme.typography.headlineMedium)
                    Text("Protein: ${formatNutrient(preview.proteinGrams)} g")
                    Text("Carbs: ${formatNutrient(preview.carbsGrams)} g")
                    Text("Fat: ${formatNutrient(preview.fatGrams)} g")
                }
            }
        }
        Button(
            onClick = { if (valid) onSave(requireNotNull(amount), meal) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save Changes") }
    }
}
