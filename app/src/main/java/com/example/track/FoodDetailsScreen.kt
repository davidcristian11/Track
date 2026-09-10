package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme
import java.time.LocalTime
import kotlin.math.roundToInt

private val DetailsNeutralLight = Color(0xFFF0F0F0)
private val DetailsAccentMedium = Color(0xFF8FB996)
private val DetailsAccentLight = Color(0xFFD1E2D3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailsScreen(
    meal: MealContext,
    food: FoodDefinition,
    onBack: () -> Unit,
    onAddToMeal: (MealContext, LocalTime, Int) -> Unit,
    defaultTime: LocalTime = LocalTime.now(),
) {
    var amount by rememberSaveable(food.id) { mutableIntStateOf(food.defaultAmount) }
    var mealName by rememberSaveable(food.id, meal) { mutableStateOf(meal.name) }
    var timeText by rememberSaveable(food.id) { mutableStateOf(formatWorkoutTime(defaultTime)) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val selectedMeal = MealContext.valueOf(mealName)
    val selectedTime = LocalTime.parse(timeText)

    if (showTimePicker) {
        val picker = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
        )
        var keyboardMode by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Food time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (keyboardMode) TimeInput(picker) else TimePicker(picker)
                    TextButton(onClick = { keyboardMode = !keyboardMode }) {
                        Text(if (keyboardMode) "Use clock" else "Enter time")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    timeText = formatWorkoutTime(LocalTime.of(picker.hour, picker.minute))
                    showTimePicker = false
                }) { Text("Set time") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FoodDetailsTopBar(onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 8.dp,
                    end = 24.dp,
                    bottom = 92.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { FoodHero(food = food) }
                item {
                    AmountCard(
                        amount = amount,
                        unit = food.unit,
                        onDecrease = { amount = (amount - 10).coerceAtLeast(0) },
                        onIncrease = { amount = (amount + 10).coerceAtMost(MaxFoodAmount) },
                    )
                }
                item {
                    if (food.isLoggable) NutritionDetails(nutrition = food.nutritionFor(amount))
                    else Text("Nutrition data incomplete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    MealAndTimeControls(
                        meal = selectedMeal,
                        time = timeText,
                        onMealChange = { mealName = it.name },
                        onTimeClick = { showTimePicker = true },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
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
                    onClick = { onAddToMeal(selectedMeal, selectedTime, amount) },
                    enabled = amount > 0 && food.isLoggable,
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
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add to ${selectedMeal.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodDetailsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Add Food",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun FoodHero(food: FoodDefinition) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalDining,
                        contentDescription = food.name,
                        modifier = Modifier.padding(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = food.name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = food.brand ?: if (food.source == FoodSource.LOCAL) "Local food" else "Open Food Facts",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        val basis = food.basisNutrition
        if (basis != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "PER ${food.basisLabel.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Text(text = "${basis.calories.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "P ${formatNutrient(basis.proteinGrams.toFloat())}g · " +
                            "C ${formatNutrient(basis.carbsGrams.toFloat())}g · " +
                            "F ${formatNutrient(basis.fatGrams.toFloat())}g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text("Nutrition data incomplete", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (food.source == FoodSource.OPEN_FOOD_FACTS) {
            Text("Open Food Facts", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AmountCard(
    amount: Int,
    unit: FoodUnit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AmountStepButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Decrease amount by 10 ${unit.spokenLabel}",
                    onClick = onDecrease,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amount.toString(),
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = unit.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Unit: ${unit.spokenLabel}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AmountStepButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Increase amount by 10 ${unit.spokenLabel}",
                    onClick = onIncrease,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "-10",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "+10",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AmountStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(DetailsNeutralLight, CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionDetails(nutrition: NutritionTotals) {
    val protein = formatNutrient(nutrition.proteinGrams)
    val carbs = formatNutrient(nutrition.carbsGrams)
    val fat = formatNutrient(nutrition.fatGrams)

    Column {
        Text(
            text = "Nutrition",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = nutrition.calories.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "KCAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroCard(
                    label = "Protein",
                    value = "$protein g",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                MacroCard(
                    label = "Carbs",
                    value = "$carbs g",
                    accent = DetailsAccentMedium,
                    modifier = Modifier.weight(1f),
                )
                MacroCard(
                    label = "Fat",
                    value = "$fat g",
                    accent = DetailsAccentLight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacroCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun MealAndTimeControls(
    meal: MealContext,
    time: String,
    onMealChange: (MealContext) -> Unit,
    onTimeClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxSize().clickable(role = Role.DropdownList) { expanded = true }
                    .semantics { contentDescription = "Choose meal" },
                shape = RoundedCornerShape(16.dp),
                color = DetailsNeutralLight,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = meal.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MealContext.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onMealChange(option); expanded = false },
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onTimeClick)
                .semantics { contentDescription = "Choose food time" },
            shape = RoundedCornerShape(16.dp),
            color = DetailsNeutralLight,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun FoodDetailsScreenPreview() {
    TrackTheme {
        FoodDetailsScreen(
            meal = MealContext.LUNCH,
            food = LocalFoodCatalog.first(),
            onBack = {},
            onAddToMeal = { _, _, _ -> },
        )
    }
}
