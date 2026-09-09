package com.example.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

internal const val MaxManualCalories = 100_000.0
internal const val MaxManualMacroGrams = 10_000.0

data class ManualFoodDraft(
    val name: String = "",
    val brand: String = "",
    val referenceAmount: String = "",
    val unit: FoodUnit = FoodUnit.Grams,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    val parsedReferenceAmount: Double? get() = parseFoodDecimal(referenceAmount)
    val parsedCalories: Double? get() = parseFoodDecimal(calories)
    val parsedProtein: Double? get() = parseFoodDecimal(protein)
    val parsedCarbs: Double? get() = parseFoodDecimal(carbs)
    val parsedFat: Double? get() = parseFoodDecimal(fat)

    val nameError: String? get() = when {
        name.trim().isEmpty() -> "Food name is required"
        name.trim().length > 80 -> "Use 80 characters or fewer"
        else -> null
    }
    val brandError: String? get() = if (brand.trim().length > 80) "Use 80 characters or fewer" else null
    val referenceAmountError: String? get() = numberError(parsedReferenceAmount, MaxFoodAmount.toDouble(), false)
    val caloriesError: String? get() = numberError(parsedCalories, MaxManualCalories, true)
    val proteinError: String? get() = numberError(parsedProtein, MaxManualMacroGrams, true)
    val carbsError: String? get() = numberError(parsedCarbs, MaxManualMacroGrams, true)
    val fatError: String? get() = numberError(parsedFat, MaxManualMacroGrams, true)

    val isValid: Boolean get() = listOf(nameError, brandError, referenceAmountError, caloriesError,
        proteinError, carbsError, fatError).all { it == null }

    fun toFoodDefinition(id: String = "manual_${UUID.randomUUID()}"): FoodDefinition {
        require(isValid)
        val amount = requireNotNull(parsedReferenceAmount)
        return FoodDefinition(
            id = id,
            name = name.trim(),
            brand = brand.trim().takeIf(String::isNotEmpty),
            defaultAmount = amount.roundToInt().coerceIn(1, MaxFoodAmount),
            unit = unit,
            basisAmount = amount,
            basisNutrition = FoodNutrition(requireNotNull(parsedCalories), requireNotNull(parsedProtein),
                requireNotNull(parsedCarbs), requireNotNull(parsedFat)),
        )
    }
}

internal fun parseFoodDecimal(value: String): Double? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
}

private fun numberError(value: Double?, maximum: Double, zeroAllowed: Boolean): String? = when {
    value == null -> "Enter a valid number"
    value < 0 || (!zeroAllowed && value == 0.0) -> if (zeroAllowed) "Must be 0 or more" else "Must be greater than 0"
    value > maximum -> "Value is too large"
    else -> null
}

@Composable
fun CreateFoodScreen(
    onBack: () -> Unit,
    onContinue: (FoodDefinition) -> Unit,
) {
    var draft by remember { mutableStateOf(ManualFoodDraft()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Create Food", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        ManualTextField("Food name", draft.name, { draft = draft.copy(name = it) },
            draft.nameError.takeIf { draft.name.isNotEmpty() }, KeyboardType.Text)
        ManualTextField("Brand (optional)", draft.brand, { draft = draft.copy(brand = it) },
            draft.brandError, KeyboardType.Text)
        ManualTextField("Reference amount", draft.referenceAmount, { draft = draft.copy(referenceAmount = it) },
            draft.referenceAmountError.takeIf { draft.referenceAmount.isNotEmpty() }, KeyboardType.Decimal,
            suffix = draft.unit.symbol)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodUnit.entries.forEach { unit ->
                FilterChip(
                    selected = draft.unit == unit,
                    onClick = { draft = draft.copy(unit = unit) },
                    label = { Text(unit.symbol) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val amountLabel = draft.referenceAmount.trim().ifEmpty { "reference amount" }
        Text("Nutrition for $amountLabel ${draft.unit.symbol}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ManualTextField("Calories", draft.calories, { draft = draft.copy(calories = it) },
            draft.caloriesError.takeIf { draft.calories.isNotEmpty() }, KeyboardType.Decimal, suffix = "kcal")
        ManualTextField("Protein", draft.protein, { draft = draft.copy(protein = it) },
            draft.proteinError.takeIf { draft.protein.isNotEmpty() }, KeyboardType.Decimal, suffix = "g")
        ManualTextField("Carbs", draft.carbs, { draft = draft.copy(carbs = it) },
            draft.carbsError.takeIf { draft.carbs.isNotEmpty() }, KeyboardType.Decimal, suffix = "g")
        ManualTextField("Fat", draft.fat, { draft = draft.copy(fat = it) },
            draft.fatError.takeIf { draft.fat.isNotEmpty() }, KeyboardType.Decimal, suffix = "g",
            imeAction = ImeAction.Done)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onContinue(draft.toFoodDefinition()) },
            enabled = draft.isValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
        ) { Text("Continue") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ManualTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    )
}
