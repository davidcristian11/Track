package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate

private val NutritionCarbs = Color(0xFF5F7A61)
private val NutritionFat = Color(0xFFDEE4DF)
private val NutritionNeutralLight = Color(0xFFF0F0F0)

private data class FoodEntry(
    val name: String,
    val details: String,
    val calories: String,
    val id: String = name,
    val loggedFood: LoggedFood? = null,
)

// Approved visual snapshots, already represented in the initial day aggregate.
private val breakfastFoods = listOf(
    FoodEntry("Greek Yogurt", "200g • P:20 C:7 F:1", "118 kcal"),
    FoodEntry("Oats", "70g • P:9 C:47 F:5", "260 kcal"),
    FoodEntry("Banana", "1 Medium • P:1 C:27 F:0", "95 kcal"),
)

@Composable
fun NutritionScreen(
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onAddFood: (MealContext) -> Unit,
    onAvatarClick: () -> Unit,
    goals: TrackGoals,
    sessionData: TrackSessionData,
    onEditFood: (LoggedFood) -> Unit = {},
    onDeleteFood: (LoggedFood) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 16.dp,
            end = 24.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item { NutritionHeader(sessionData.day, today, onPreviousDay, onNextDay, onAvatarClick) }
        item { NutritionSummaryCard(goals = goals, nutrition = sessionData.nutrition) }
        item { MealsList(onAddFood, sessionData.foods, sessionData.baseline.showBreakfast, onEditFood, onDeleteFood) }
    }
}

@Composable
private fun NutritionHeader(
    day: LocalDate, today: LocalDate, onPreviousDay: () -> Unit, onNextDay: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(23.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nutrition",
                style = MaterialTheme.typography.headlineMedium,
            )
            TrackDaySelector(day, today, onPreviousDay, onNextDay)
        }
    }
}

@Composable
private fun NutritionSummaryCard(goals: TrackGoals, nutrition: NutritionTotals) {
    val caloriesConsumed = nutrition.calories
    NutritionCard {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Calories",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatWholeNumber(caloriesConsumed),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "/ ${formatWholeNumber(goals.calories)} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        trackColor = NutritionNeutralLight,
                        strokeWidth = 4.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    CircularProgressIndicator(
                        progress = { progressFraction(caloriesConsumed, goals.calories) },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Text(
                        text = "${(progressFraction(caloriesConsumed, goals.calories) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                NutritionMacroSummary(
                    label = "Protein",
                    value = "${formatNutrient(nutrition.proteinGrams)}/${formatWholeNumber(goals.proteinGrams)}g",
                    progress = progressFraction(nutrition.proteinGrams, goals.proteinGrams.toFloat()),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                NutritionMacroSummary(
                    label = "Carbs",
                    value = "${formatNutrient(nutrition.carbsGrams)}/${formatWholeNumber(goals.carbsGrams)}g",
                    progress = progressFraction(nutrition.carbsGrams, goals.carbsGrams.toFloat()),
                    color = NutritionCarbs,
                    modifier = Modifier.weight(1f),
                )
                NutritionMacroSummary(
                    label = "Fat",
                    value = "${formatNutrient(nutrition.fatGrams)}/${formatWholeNumber(goals.fatGrams)}g",
                    progress = progressFraction(nutrition.fatGrams, goals.fatGrams.toFloat()),
                    color = NutritionFat,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NutritionMacroSummary(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Longer session totals wrap without changing the approved default layout.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = NutritionNeutralLight,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MealsList(
    onAddFood: (MealContext) -> Unit, loggedFoods: List<LoggedFood>, showDemoBreakfast: Boolean,
    onEditFood: (LoggedFood) -> Unit, onDeleteFood: (LoggedFood) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MealContext.entries.forEach { meal ->
            val additions = loggedFoods.filter { it.meal == meal }
            val addedCalories = additions.sumOf { it.nutrition.calories }
            val baselineRows = if (showDemoBreakfast && meal == MealContext.BREAKFAST) breakfastFoods else emptyList()
            MealCard(
                title = meal.label,
                subtitle = when {
                    baselineRows.isNotEmpty() -> "08:10 · ${formatWholeNumber(473 + addedCalories)} kcal"
                    additions.isEmpty() -> "No foods logged yet"
                    else -> "${formatWholeNumber(addedCalories)} kcal"
                },
                icon = when (meal) {
                    MealContext.BREAKFAST -> Icons.Outlined.WbSunny
                    MealContext.LUNCH -> Icons.Outlined.Restaurant
                    MealContext.DINNER -> Icons.Outlined.NightlightRound
                    MealContext.SNACKS -> Icons.Outlined.Icecream
                },
                foods = baselineRows + additions.map { entry ->
                    val nutrition = entry.nutrition
                    FoodEntry(
                        name = entry.name,
                        details = "${entry.amount} ${entry.unit.symbol} • P:${formatNutrient(nutrition.proteinGrams)} " +
                            "C:${formatNutrient(nutrition.carbsGrams)} F:${formatNutrient(nutrition.fatGrams)}",
                        calories = "${formatWholeNumber(nutrition.calories)} kcal",
                        id = "logged-${entry.id}",
                        loggedFood = entry,
                    )
                },
                onAddFood = { onAddFood(meal) },
                onEditFood = onEditFood,
                onDeleteFood = onDeleteFood,
            )
        }
        Box(modifier = Modifier.padding(top = 16.dp)) {
            AddMealButton()
        }
    }
}

@Composable
private fun MealCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    foods: List<FoodEntry> = emptyList(),
    onEditFood: (LoggedFood) -> Unit, onDeleteFood: (LoggedFood) -> Unit,
    onAddFood: () -> Unit,
) {
    NutritionCard {
        Column(modifier = Modifier.padding(20.dp)) {
            MealHeader(
                title = title,
                subtitle = subtitle,
                icon = icon,
                onAddFood = onAddFood,
            )
            if (foods.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NutritionNeutralLight)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    foods.forEach { food -> key(food.id) { FoodRow(food, onEditFood, onDeleteFood) } }
                }
            }
        }
    }
}

@Composable
private fun MealHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onAddFood: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NutritionNeutralLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(
            onClick = onAddFood,
            modifier = Modifier.semantics { contentDescription = "Add food to $title" },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add food",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun FoodRow(food: FoodEntry, onEdit: (LoggedFood) -> Unit, onDelete: (LoggedFood) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = food.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = food.calories,
            style = MaterialTheme.typography.bodyMedium,
        )
        food.loggedFood?.takeIf { it.id > 0 }?.let { logged ->
            TrackingLogOptions("Food options for ${food.name}", { onEdit(logged) }, { onDelete(logged) })
        }
    }
}

@Composable
private fun AddMealButton() {
    Button(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = NutritionNeutralLight,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AddCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Add Meal",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun NutritionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        content()
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NutritionScreenPreview() {
    TrackTheme {
        NutritionScreen(
            today = TrackDemoBaseline.referenceDay,
            onPreviousDay = {},
            onNextDay = {},
            onAddFood = {},
            onAvatarClick = {},
            goals = TrackGoals(),
            sessionData = TrackSessionData(TrackDemoBaseline.referenceDay),
        )
    }
}
