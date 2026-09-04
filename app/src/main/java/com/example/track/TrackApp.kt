package com.example.track

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.track.ui.theme.TrackTheme

private enum class TrackDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Today("today", "Today", Icons.Filled.CalendarToday),
    Nutrition("nutrition", "Nutrition", Icons.Filled.Restaurant),
    Activity("activity", "Activity", Icons.Filled.FitnessCenter),
    Progress("progress", "Progress", Icons.Filled.QueryStats),
}

enum class MealContext(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks");

    companion object {
        fun fromRoute(value: String?): MealContext =
            entries.firstOrNull { it.name == value } ?: LUNCH
    }
}

private const val AddFoodRoute = "add_food"
private const val FoodDetailsRoute = "food_details"

@Composable
fun TrackApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedDestination = TrackDestination.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedDestination != null) {
                TrackBottomNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(TrackDestination.Today.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = TrackDestination.Today.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            composable(TrackDestination.Today.route) { TodayScreen() }
            composable(TrackDestination.Nutrition.route) {
                NutritionScreen(
                    onAddFood = { meal ->
                        navController.navigate("$AddFoodRoute/${meal.name}")
                    },
                )
            }
            composable(TrackDestination.Activity.route) {
                PlaceholderScreen(
                    title = "Activity",
                    message = "Workout and movement details will live here soon.",
                    icon = Icons.Filled.FitnessCenter,
                )
            }
            composable(TrackDestination.Progress.route) {
                PlaceholderScreen(
                    title = "Progress",
                    message = "Your long-term trends and milestones are coming soon.",
                    icon = Icons.Filled.QueryStats,
                )
            }
            composable(
                route = "$AddFoodRoute/{meal}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }),
            ) { entry ->
                val meal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                AddFoodSearchScreen(
                    onBack = { navController.popBackStack() },
                    onFoodSelected = {
                        navController.navigate("$FoodDetailsRoute/${meal.name}")
                    },
                )
            }
            composable(
                route = "$FoodDetailsRoute/{meal}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }),
            ) { entry ->
                val meal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                FoodDetailsScreen(
                    meal = meal,
                    onBack = { navController.popBackStack() },
                    onAddToMeal = {
                        navController.popBackStack(
                            route = TrackDestination.Nutrition.route,
                            inclusive = false,
                        )
                    },
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
