package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackProgressTrack
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate

@Composable
fun TodayScreen(
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onAddFood: () -> Unit,
    onAvatarClick: () -> Unit,
    customization: TodayCustomization,
    goals: TrackGoals,
    sessionData: TrackSessionData,
    onAddWater: () -> Unit,
    onCreatineToggle: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 20.dp,
            end = 24.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TodayHeader(sessionData.day, today, onPreviousDay, onNextDay, onAvatarClick)
                if (customization.showNutrition) {
                    NutritionSummaryCard(
                        goals = goals,
                        nutrition = sessionData.nutrition,
                        onAddFood = onAddFood,
                    )
                }
            }
        }
        if (
            customization.showWater ||
            customization.showSteps ||
            customization.showSleep ||
            customization.showWorkout ||
            customization.showWeight
        ) {
            item {
                MetricGrid(
                    customization = customization,
                    goals = goals,
                    sessionData = sessionData,
                    today = today,
                    onAddWater = onAddWater,
                )
            }
        }
        if (customization.showCreatine) {
            item { DailyHabits(completed = sessionData.creatineCompleted, onToggle = onCreatineToggle) }
        }
    }
}

@Composable
private fun TodayHeader(
    day: LocalDate, today: LocalDate, onPreviousDay: () -> Unit, onNextDay: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(27.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackDayTitle(day, today),
                style = MaterialTheme.typography.headlineMedium,
            )
            TrackDaySelector(day, today, onPreviousDay, onNextDay)
        }
    }
}

@Composable
private fun NutritionSummaryCard(
    goals: TrackGoals,
    nutrition: NutritionTotals,
    onAddFood: () -> Unit,
) {
    val caloriesConsumed = nutrition.calories
    TrackCard {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progressFraction(caloriesConsumed, goals.calories) },
                    modifier = Modifier.size(152.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = TrackProgressTrack,
                    strokeWidth = 7.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatWholeNumber(
                            remainingCalories(caloriesConsumed, goals.calories),
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "KCAL LEFT",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Consumed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatWholeNumber(caloriesConsumed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "/ ${formatWholeNumber(goals.calories)} kcal",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            MacroProgress(
                "Protein",
                "${formatNutrient(nutrition.proteinGrams)} / ${formatWholeNumber(goals.proteinGrams)}g",
                progressFraction(nutrition.proteinGrams, goals.proteinGrams.toFloat()),
            )
            Spacer(Modifier.height(14.dp))
            MacroProgress(
                "Carbs",
                "${formatNutrient(nutrition.carbsGrams)} / ${formatWholeNumber(goals.carbsGrams)}g",
                progressFraction(nutrition.carbsGrams, goals.carbsGrams.toFloat()),
            )
            Spacer(Modifier.height(14.dp))
            MacroProgress(
                "Fat",
                "${formatNutrient(nutrition.fatGrams)} / ${formatWholeNumber(goals.fatGrams)}g",
                progressFraction(nutrition.fatGrams, goals.fatGrams.toFloat()),
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onAddFood,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add Food",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MacroProgress(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = TrackProgressTrack,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private data class TodayMetricData(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val detail: String? = null,
    val topAction: String? = null,
    val onTopAction: (() -> Unit)? = null,
    val progress: Float? = null,
    val titleIsLabel: Boolean = false,
    val bottomFillFraction: Float? = null,
)

@Composable
private fun MetricGrid(
    customization: TodayCustomization,
    goals: TrackGoals,
    sessionData: TrackSessionData,
    today: LocalDate,
    onAddWater: () -> Unit,
) {
    val latestWorkout = sessionData.workouts.firstOrNull()
    val showCurrentSteps = sessionData.day == today
    val showDemoBodyMetrics = showCurrentSteps || sessionData.day == TrackDemoBaseline.referenceDay
    val metrics = buildList {
        if (customization.showWater) {
            add(
                TodayMetricData(
                    title = "Water",
                    value = formatWaterLiters(sessionData.waterMl),
                    detail = "/ ${formatDecimal(goals.waterLiters)} L",
                    icon = Icons.Filled.WaterDrop,
                    topAction = "+ 250 ml",
                    onTopAction = onAddWater,
                    bottomFillFraction = waterProgress(sessionData.waterMl, goals.waterLiters),
                ),
            )
        }
        if (customization.showSteps) {
            add(
                TodayMetricData(
                    title = "Steps",
                    value = if (showCurrentSteps) "6,432" else "—",
                    detail = if (showCurrentSteps) "/ ${formatCompactSteps(goals.steps)}" else "No step data",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    progress = if (showCurrentSteps) progressFraction(6_432, goals.steps) else null,
                ),
            )
        }
        if (customization.showSleep) {
            add(
                TodayMetricData(
                    title = "Sleep",
                    value = if (showDemoBodyMetrics) "7h 15m" else "—",
                    detail = if (showDemoBodyMetrics) null else "No sleep data",
                    icon = Icons.Filled.Bedtime,
                    topAction = if (showDemoBodyMetrics) "TARGET MET" else null,
                ),
            )
        }
        if (customization.showWorkout) {
            add(
                TodayMetricData(
                    title = if (latestWorkout == null) "Workout" else "WORKOUT",
                    value = latestWorkout?.let { "${it.type.shortLabel} ·" } ?: "—",
                    detail = latestWorkout?.let { "${it.durationMinutes} min" } ?: "No workout yet",
                    icon = latestWorkout?.type?.icon ?: Icons.Filled.FitnessCenter,
                    titleIsLabel = latestWorkout != null,
                ),
            )
        }
        if (customization.showWeight) {
            add(
                TodayMetricData(
                    title = "Weight",
                    value = if (showDemoBodyMetrics) "72.4" else "—",
                    detail = if (showDemoBodyMetrics) "kg" else "No weight data",
                    icon = Icons.Filled.MonitorWeight,
                ),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowMetrics.forEach { metric ->
                    MetricCard(
                        title = metric.title,
                        value = metric.value,
                        detail = metric.detail,
                        icon = metric.icon,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (rowMetrics.size == 1) 2f else 1f),
                        topAction = metric.topAction,
                        onTopAction = metric.onTopAction,
                        progress = metric.progress,
                        titleIsLabel = metric.titleIsLabel,
                        bottomFillFraction = metric.bottomFillFraction,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    detail: String? = null,
    topAction: String? = null,
    onTopAction: (() -> Unit)? = null,
    progress: Float? = null,
    titleIsLabel: Boolean = false,
    bottomFillFraction: Float? = null,
) {
    TrackCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bottomFillFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(bottomFillFraction)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (topAction != null) {
                        val isWaterAction = topAction.startsWith("+")
                        Box(
                            modifier = if (onTopAction != null) {
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable(role = Role.Button, onClick = onTopAction)
                                    .semantics { contentDescription = "Add 250 ml water" }
                            } else {
                                Modifier
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = topAction,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(
                                        horizontal = if (isWaterAction) 12.dp else 8.dp,
                                        vertical = if (isWaterAction) 8.dp else 4.dp,
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = if (isWaterAction) 12.sp else 9.sp,
                                lineHeight = if (isWaterAction) 16.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = if (titleIsLabel) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = if (titleIsLabel) 0.7.sp else 0.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    if (titleIsLabel && detail != null) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (detail != null) {
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 1.dp),
                                )
                            }
                        }
                    }
                    if (progress != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
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
    }
}

@Composable
private fun DailyHabits(completed: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Daily Habits",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
        TrackCard(shape = RoundedCornerShape(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Creatine",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "5g daily",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .toggleable(value = completed, role = Role.Checkbox, onValueChange = { onToggle() })
                        .semantics { contentDescription = "Creatine completion" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (completed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .then(
                                if (completed) Modifier else Modifier.border(
                                    1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (completed) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        content()
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TodayScreenPreview() {
    TrackTheme {
        TodayScreen(
            today = TrackDemoBaseline.referenceDay,
            onPreviousDay = {},
            onNextDay = {},
            onAddFood = {},
            onAvatarClick = {},
            customization = TodayCustomization(),
            goals = TrackGoals(),
            sessionData = TrackSessionData(TrackDemoBaseline.referenceDay),
            onAddWater = {},
            onCreatineToggle = {},
        )
    }
}
