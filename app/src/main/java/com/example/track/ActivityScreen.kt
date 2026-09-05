package com.example.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.track.ui.theme.TrackTheme

private val ActivityProgressTrack = Color(0xFFDEE4DF)
private val ActivityNeutral = Color(0xFFF0F0F0)
private val ActivityMuted = Color(0xFF737971)
private val ActivityButtonBackground = Color(0xFFDBE1DC)

@Composable
fun ActivityScreen(
    onAddWorkout: () -> Unit,
    onAvatarClick: () -> Unit,
    onHealthConnectionClick: () -> Unit,
    goals: TrackGoals,
    sessionData: TrackSessionData,
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
        item { ActivityHeader(onAvatarClick = onAvatarClick) }
        item { StepsHeroCard(stepGoal = goals.steps, onHealthConnectionClick = onHealthConnectionClick) }
        item { WorkoutsSection(onAddWorkout = onAddWorkout, workouts = sessionData.workouts) }
        item { ActivitySummarySection(workoutsThisWeek = sessionData.workoutsThisWeek) }
    }
}

@Composable
private fun ActivityHeader(onAvatarClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        Column {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Wednesday, September 2",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepsHeroCard(stepGoal: Int, onHealthConnectionClick: () -> Unit) {
    ActivityCard {
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.size(192.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    trackColor = ActivityProgressTrack,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                CircularProgressIndicator(
                    progress = { progressFraction(6_432, stepGoal) },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "6,432",
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "steps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = ActivityNeutral.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "Goal: ${formatWholeNumber(stepGoal)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = ActivityNeutral)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActivityHeroMetric(
                    icon = Icons.Outlined.Route,
                    value = "4.8",
                    unit = "km",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(ActivityNeutral),
                )
                ActivityHeroMetric(
                    icon = Icons.Outlined.Timer,
                    value = "58",
                    unit = "min",
                    modifier = Modifier.weight(1f),
                )
            }

            // Use the existing surrounding whitespace for a 48-dp target without moving the status.
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onHealthConnectionClick)
                    .semantics { contentDescription = "Health data connection" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = ActivityMuted.copy(alpha = 0.7f),
                )
                Text(
                    text = "SYNCED FROM HEALTH DATA",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    color = ActivityMuted.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ActivityHeroMetric(
    icon: ImageVector,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ActivityMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = ActivityMuted,
        )
    }
}

@Composable
private fun WorkoutsSection(onAddWorkout: () -> Unit, workouts: List<LoggedWorkout>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Today's Workouts",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        workouts.forEach { workout ->
            key(workout.id) { WorkoutCard(workout) }
        }
        Button(
            onClick = onAddWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ActivityButtonBackground,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add workout",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WorkoutCard(workout: LoggedWorkout) {
    ActivityCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = workout.type.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.type.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${workout.startTime} · ${workout.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (workout.notes.isNotBlank()) {
                    Text(
                        text = workout.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = ActivityMuted,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "~${workout.estimatedCalories} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivitySummarySection(workoutsThisWeek: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Summary",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        ActivityCard {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                ActivitySummaryRow(
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    label = "7-day average",
                    value = "8,420",
                    suffix = "/ day",
                )
                HorizontalDivider(color = ActivityNeutral)
                ActivitySummaryRow(
                    icon = Icons.Outlined.EventAvailable,
                    label = "Workouts this week",
                    value = workoutsThisWeek.toString(),
                )
            }
        }
    }
}

@Composable
private fun ActivitySummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    suffix: String? = null,
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
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(7.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        if (suffix != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodyMedium,
                color = ActivityMuted,
            )
        }
    }
}

@Composable
private fun ActivityCard(content: @Composable () -> Unit) {
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
private fun ActivityScreenPreview() {
    TrackTheme {
        ActivityScreen(
            onAddWorkout = {},
            onAvatarClick = {},
            onHealthConnectionClick = {},
            goals = TrackGoals(),
            sessionData = TrackSessionData(),
        )
    }
}
