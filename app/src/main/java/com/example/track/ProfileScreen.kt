package com.example.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.track.ui.theme.TrackTheme

@Composable
fun ProfileScreen(
    profile: TrackProfile,
    goals: TrackGoals,
    currentWeight: WeightEntry?,
    onBack: () -> Unit,
    onSaveProfile: (TrackProfile, () -> Unit) -> Unit,
    onGoalsClick: () -> Unit,
    onCustomizeTodayClick: () -> Unit,
) {
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        ProfileTopBar(onBack = onBack)
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
            item {
                ProfileIdentity(
                    displayName = profile.displayName,
                    onEditProfile = { showEditProfile = true },
                )
            }
            item { ProfileSummaryCard(goals = goals, currentWeight = currentWeight) }
            item {
                ProfileMenuCard(
                    onGoalsClick = onGoalsClick,
                    onCustomizeTodayClick = onCustomizeTodayClick,
                )
            }
        }
    }
    if (showEditProfile) {
        EditProfileDialog(
            savedDisplayName = profile.displayName,
            onDismiss = { showEditProfile = false },
            onSave = {
                onSaveProfile(TrackProfile(it)) { showEditProfile = false }
            },
        )
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
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
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun ProfileIdentity(displayName: String, onEditProfile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Profile placeholder",
                modifier = Modifier.padding(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = displayName.ifBlank { "Add your name" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        OutlinedButton(onClick = onEditProfile, shape = CircleShape) {
            Text("Edit profile")
        }
    }
}

@Composable
private fun ProfileSummaryCard(goals: TrackGoals, currentWeight: WeightEntry?) {
    val direction = currentWeight?.let {
        weightGoalDirection(it.weightKg, goals.targetWeightKg.toDouble())
    }
    ProfileCard {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProfileSummaryRow(
                icon = Icons.Outlined.Flag,
                label = "Goal",
                value = direction?.label ?: "Log your weight first",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSummaryRow(
                icon = Icons.Outlined.Restaurant,
                label = "Daily target",
                value = "${formatWholeNumber(goals.calories)} kcal",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSummaryRow(
                icon = Icons.Outlined.MonitorWeight,
                label = "Current weight",
                value = currentWeight?.let { "${formatWeight(it.weightKg)} kg" } ?: "Not logged",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSummaryRow(
                icon = Icons.Outlined.TrackChanges,
                label = "Target weight",
                value = "${formatDecimal(goals.targetWeightKg)} kg",
            )
            currentWeight?.let { weight ->
                formatWeightDifference(weight.weightKg, goals.targetWeightKg.toDouble())?.let { difference ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProfileSummaryRow(
                        icon = Icons.Outlined.TrackChanges,
                        label = "Difference",
                        value = difference,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(7.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileMenuCard(
    onGoalsClick: () -> Unit,
    onCustomizeTodayClick: () -> Unit,
) {
    val rows = listOf(
        ProfileMenuItem(Icons.Outlined.TrackChanges, "Goals & targets", onGoalsClick),
        ProfileMenuItem(Icons.Outlined.Tune, "Customize Today", onCustomizeTodayClick),
    )

    ProfileCard {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            rows.forEachIndexed { index, item ->
                ProfileMenuRow(item = item)
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private data class ProfileMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileMenuRow(item: ProfileMenuItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun EditProfileDialog(
    savedDisplayName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf(savedDisplayName) }
    val validatedName = validatedDisplayName(displayName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                isError = displayName.isNotEmpty() && validatedName == null,
                supportingText = {
                    if (displayName.isNotEmpty() && validatedName == null) {
                        Text("Enter a name from 1 to 40 characters")
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { validatedName?.let(onSave) },
                enabled = validatedName != null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
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
private fun ProfileScreenPreview() {
    TrackTheme {
        ProfileScreen(
            profile = TrackProfile(),
            goals = TrackGoals(),
            currentWeight = null,
            onBack = {},
            onSaveProfile = { _, _ -> },
            onGoalsClick = {},
            onCustomizeTodayClick = {},
        )
    }
}
