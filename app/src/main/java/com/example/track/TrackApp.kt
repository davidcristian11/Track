package com.example.track

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.example.track.ui.theme.TrackTheme

private enum class TrackDestination(
    val label: String,
    val icon: ImageVector,
) {
    Today("Today", Icons.Filled.CalendarToday),
    Nutrition("Nutrition", Icons.Filled.Restaurant),
    Activity("Activity", Icons.Filled.FitnessCenter),
    Progress("Progress", Icons.Filled.QueryStats),
}

@Composable
fun TrackApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(TrackDestination.Today) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            TrackBottomNavigation(
                selectedDestination = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (selectedDestination) {
                TrackDestination.Today -> TodayScreen()
                TrackDestination.Nutrition -> PlaceholderScreen(
                    title = "Nutrition",
                    message = "Meal insights and food tracking are coming in the next phase.",
                    icon = Icons.Filled.Restaurant,
                )
                TrackDestination.Activity -> PlaceholderScreen(
                    title = "Activity",
                    message = "Workout and movement details will live here soon.",
                    icon = Icons.Filled.FitnessCenter,
                )
                TrackDestination.Progress -> PlaceholderScreen(
                    title = "Progress",
                    message = "Your long-term trends and milestones are coming soon.",
                    icon = Icons.Filled.QueryStats,
                )
            }
        }
    }
}

@Composable
private fun TrackBottomNavigation(
    selectedDestination: TrackDestination,
    onDestinationSelected: (TrackDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
    ) {
        TrackDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackAppPreview() {
    TrackTheme { TrackApp() }
}
