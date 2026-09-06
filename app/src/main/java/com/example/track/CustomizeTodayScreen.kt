package com.example.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.track.ui.theme.TrackTheme

@Composable
fun CustomizeTodayScreen(
    customization: TodayCustomization,
    onModuleEnabled: (TodayModule, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PersonalizationTopBar(
            title = "Customize",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Reorder or toggle modules to personalize your daily view. Changes save automatically.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                CustomizationRow(
                    label = "Nutrition",
                    icon = Icons.Filled.Restaurant,
                    checked = customization.showNutrition,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Nutrition, it)
                    },
                )
            }
            item {
                CustomizationRow(
                    label = "Water",
                    icon = Icons.Filled.WaterDrop,
                    checked = customization.showWater,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Water, it)
                    },
                )
            }
            item {
                CustomizationRow(
                    label = "Steps",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    checked = customization.showSteps,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Steps, it)
                    },
                )
            }
            item {
                CustomizationRow(
                    label = "Sleep",
                    icon = Icons.Filled.Bedtime,
                    checked = customization.showSleep,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Sleep, it)
                    },
                )
            }
            item {
                CustomizationRow(
                    label = "Workout",
                    icon = Icons.Filled.FitnessCenter,
                    checked = customization.showWorkout,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Workout, it)
                    },
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            item {
                CustomizationRow(
                    label = "Creatine",
                    icon = Icons.Filled.Science,
                    checked = customization.showCreatine,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Creatine, it)
                    },
                )
            }
            item {
                CustomizationRow(
                    label = "Weight",
                    icon = Icons.Filled.MonitorWeight,
                    checked = customization.showWeight,
                    onCheckedChange = {
                        onModuleEnabled(TodayModule.Weight, it)
                    },
                )
            }
        }
    }
}

@Composable
fun PersonalizationTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
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
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun CustomizationRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .alpha(if (checked) 1f else 0.6f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(16.dp))
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = if (checked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = label },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 809)
@Composable
private fun CustomizeTodayScreenPreview() {
    TrackTheme {
        CustomizeTodayScreen(
            customization = TodayCustomization(),
            onModuleEnabled = { _, _ -> },
            onBack = {},
        )
    }
}
