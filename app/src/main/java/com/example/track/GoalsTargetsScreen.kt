package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.track.ui.theme.TrackProgressTrack
import com.example.track.ui.theme.TrackTheme

@Composable
fun GoalsTargetsScreen(
    goals: TrackGoals,
    onBack: () -> Unit,
    onSave: (TrackGoals) -> Unit,
) {
    var calories by rememberSaveable { mutableStateOf(goals.calories.toString()) }
    var protein by rememberSaveable { mutableStateOf(goals.proteinGrams.toString()) }
    var carbs by rememberSaveable { mutableStateOf(goals.carbsGrams.toString()) }
    var fat by rememberSaveable { mutableStateOf(goals.fatGrams.toString()) }
    var water by rememberSaveable { mutableStateOf(formatDecimal(goals.waterLiters)) }
    var steps by rememberSaveable { mutableStateOf(goals.steps.toString()) }
    var targetWeight by rememberSaveable { mutableStateOf(formatDecimal(goals.targetWeightKg)) }

    val parsedGoals = parseGoals(
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        water = water,
        steps = steps,
        targetWeight = targetWeight,
    )
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        PersonalizationTopBar(
            title = "Goals & Targets",
            onBack = onBack,
        )
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep the focused field above the pinned Save button while editing.
                    .padding(bottom = if (keyboardVisible) 112.dp else 0.dp),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 16.dp,
                    end = 24.dp,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                item {
                    PrimaryGoalCard(
                        targetWeight = targetWeight,
                        onTargetWeightChange = { targetWeight = decimalInput(it) },
                    )
                }
                item {
                    DailyTargetsSection(
                        fields = listOf(
                            DailyTargetField(Icons.Outlined.LocalFireDepartment, "Calories", calories, "kcal") {
                                calories = wholeNumberInput(it)
                            },
                            DailyTargetField(Icons.Outlined.EggAlt, "Protein", protein, "g") {
                                protein = wholeNumberInput(it)
                            },
                            DailyTargetField(Icons.Outlined.BakeryDining, "Carbs", carbs, "g") {
                                carbs = wholeNumberInput(it)
                            },
                            DailyTargetField(Icons.Outlined.WaterDrop, "Fat", fat, "g") {
                                fat = wholeNumberInput(it)
                            },
                            DailyTargetField(Icons.Outlined.LocalDrink, "Water", water, "L", decimal = true) {
                                water = decimalInput(it)
                            },
                            DailyTargetField(Icons.AutoMirrored.Filled.DirectionsWalk, "Steps", steps) {
                                steps = wholeNumberInput(it)
                            },
                        ),
                        showValidationMessage = parsedGoals == null,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Button(
                    onClick = { parsedGoals?.let(onSave) },
                    enabled = parsedGoals != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Save Targets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryGoalCard(
    targetWeight: String,
    onTargetWeightChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Primary Goal",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "ACTIVE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(
                        text = "Lose weight",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Steady & sustainable pace",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WeightTargetTile(
                    label = "Current",
                    value = "72.4",
                    modifier = Modifier.weight(1f),
                )
                WeightTargetTile(
                    label = "Target",
                    value = targetWeight,
                    modifier = Modifier.weight(1f),
                    editable = true,
                    onValueChange = onTargetWeightChange,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Progress",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "25%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { 0.25f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = TrackProgressTrack,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun WeightTargetTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onValueChange: (String) -> Unit = {},
) {
    Surface(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editable) {
                    EditableGoalValue(
                        value = value,
                        onValueChange = onValueChange,
                        decimal = true,
                        modifier = Modifier
                            .width(64.dp)
                            .semantics { contentDescription = "Target weight, kg" },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                } else {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class DailyTargetField(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val unit: String? = null,
    val decimal: Boolean = false,
    val onValueChange: (String) -> Unit,
)

@Composable
private fun DailyTargetsSection(
    fields: List<DailyTargetField>,
    showValidationMessage: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Daily Targets",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                fields.forEachIndexed { index, field ->
                    DailyTargetRow(
                        icon = field.icon,
                        label = field.label,
                        value = field.value,
                        unit = field.unit,
                        decimal = field.decimal,
                        onValueChange = field.onValueChange,
                    )
                    if (index != fields.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        if (showValidationMessage) {
            Text(
                text = "Enter a positive value for every target.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        TextButton(
            onClick = {},
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Recalculate suggested targets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyTargetRow(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String? = null,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        EditableGoalValue(
            value = value,
            onValueChange = onValueChange,
            decimal = decimal,
            modifier = Modifier
                .width(92.dp)
                .semantics { contentDescription = listOfNotNull(label, unit).joinToString(", ") },
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditableGoalValue(
    value: String,
    onValueChange: (String) -> Unit,
    decimal: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
    ),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(textAlign = TextAlign.End),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

internal fun parseGoals(
    calories: String,
    protein: String,
    carbs: String,
    fat: String,
    water: String,
    steps: String,
    targetWeight: String,
): TrackGoals? {
    val parsedCalories = calories.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val parsedProtein = protein.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val parsedCarbs = carbs.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val parsedFat = fat.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val parsedWater = water.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
    val parsedSteps = steps.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val parsedTargetWeight = targetWeight.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null

    return TrackGoals(
        calories = parsedCalories,
        proteinGrams = parsedProtein,
        carbsGrams = parsedCarbs,
        fatGrams = parsedFat,
        waterLiters = parsedWater,
        steps = parsedSteps,
        targetWeightKg = parsedTargetWeight,
    )
}

private fun wholeNumberInput(value: String): String =
    value.filter(Char::isDigit).take(6)

private fun decimalInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }.take(6)
    val firstDecimal = filtered.indexOf('.')
    return if (firstDecimal == -1) {
        filtered
    } else {
        filtered.take(firstDecimal + 1) +
            filtered.drop(firstDecimal + 1).replace(".", "").take(1)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 934)
@Composable
private fun GoalsTargetsScreenPreview() {
    TrackTheme {
        GoalsTargetsScreen(
            goals = TrackGoals(),
            onBack = {},
            onSave = {},
        )
    }
}
