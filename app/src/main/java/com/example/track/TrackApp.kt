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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.track.ui.theme.TrackTheme
import java.time.LocalDate

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
private const val BarcodeScannerRoute = "barcode_scanner"
private const val AddWorkoutRoute = "add_workout"
private const val ActivityConnectionRoute = "activity_connection"
private const val ProfileRoute = "profile"
private const val CustomizeTodayRoute = "customize_today"
private const val GoalsTargetsRoute = "goals_targets"

@Composable
fun TrackApp(viewModel: TrackViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val settings by viewModel.uiSettings.collectAsStateWithLifecycle()
    TrackApp(
        // Never pair a new date header with the previous day's Room snapshot.
        sessionData = tracking.takeIf { it.day == selectedDay } ?: TrackSessionData(selectedDay),
        today = today,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        uiSettings = settings,
        onAddFood = viewModel::addFood,
        onAddWorkout = viewModel::addWorkout,
        onAddWater = viewModel::addWater,
        onCreatineToggle = viewModel::toggleCreatine,
        onUpdateGoals = viewModel::updateGoals,
        onTodayModuleEnabled = viewModel::setTodayModuleEnabled,
    )
}

@Composable
private fun TrackApp(
    sessionData: TrackSessionData,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    uiSettings: TrackUiSettings,
    onAddFood: (MealContext, FoodDefinition, Int, () -> Unit) -> Unit,
    onAddWorkout: (WorkoutType, Int, String, () -> Unit) -> Unit,
    onAddWater: () -> Unit,
    onCreatineToggle: () -> Unit,
    onUpdateGoals: (TrackGoals, () -> Unit) -> Unit,
    onTodayModuleEnabled: (TodayModule, Boolean) -> Unit,
) {
    val navController = rememberNavController()
    fun completeFoodEntry(originMeal: MealContext, meal: MealContext, food: FoodDefinition, amount: Int) {
        val formEntry = navController.currentBackStackEntry
        onAddFood(meal, food, amount) {
            // Pop the originating Search, even if Scanner changed the destination meal.
            // If the user already pressed Back during the write, leave that route alone.
            if (navController.currentBackStackEntry == formEntry) {
                navController.popBackStack("$AddFoodRoute/${originMeal.name}", inclusive = true)
            }
        }
    }

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
            composable(TrackDestination.Today.route) {
                TodayScreen(
                    today = today,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onAddFood = {
                        navController.navigate("$AddFoodRoute/${MealContext.LUNCH.name}")
                    },
                    onAvatarClick = { navController.navigate(ProfileRoute) },
                    customization = uiSettings.today,
                    goals = uiSettings.goals,
                    sessionData = sessionData,
                    onAddWater = onAddWater,
                    onCreatineToggle = onCreatineToggle,
                )
            }
            composable(TrackDestination.Nutrition.route) {
                NutritionScreen(
                    today = today,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onAddFood = { meal ->
                        navController.navigate("$AddFoodRoute/${meal.name}")
                    },
                    onAvatarClick = { navController.navigate(ProfileRoute) },
                    goals = uiSettings.goals,
                    sessionData = sessionData,
                )
            }
            composable(TrackDestination.Activity.route) {
                ActivityScreen(
                    today = today,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onAddWorkout = { navController.navigate(AddWorkoutRoute) },
                    onHealthConnectionClick = { navController.navigate(ActivityConnectionRoute) },
                    onAvatarClick = { navController.navigate(ProfileRoute) },
                    goals = uiSettings.goals,
                    sessionData = sessionData,
                )
            }
            composable(ActivityConnectionRoute) {
                ActivityConnectionScreen(
                    day = sessionData.day,
                    today = today,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TrackDestination.Progress.route) {
                ProgressScreen(
                    onAvatarClick = { navController.navigate(ProfileRoute) },
                )
            }
            composable(
                route = "$AddFoodRoute/{meal}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }),
            ) { entry ->
                val meal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                AddFoodSearchScreen(
                    onBack = { navController.popBackStack() },
                    onFoodSelected = { food ->
                        navController.navigate("$FoodDetailsRoute/${meal.name}/${food.id}")
                    },
                    onBarcodeClick = { navController.navigate("$BarcodeScannerRoute/${meal.name}") },
                )
            }
            composable(
                route = "$BarcodeScannerRoute/{meal}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }),
            ) { entry ->
                val originMeal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                BarcodeScannerScreen(
                    initialMeal = originMeal,
                    onBack = { navController.popBackStack() },
                    onAddToMeal = { meal, amount ->
                        completeFoodEntry(originMeal, meal, ScannedFood, amount)
                    },
                )
            }
            composable(
                route = "$FoodDetailsRoute/{meal}/{foodId}",
                arguments = listOf(
                    navArgument("meal") { type = NavType.StringType },
                    navArgument("foodId") { type = NavType.StringType },
                ),
            ) { entry ->
                val meal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                val food = findLocalFood(entry.arguments?.getString("foodId"))
                    ?: LocalFoodCatalog.first()
                FoodDetailsScreen(
                    meal = meal,
                    food = food,
                    onBack = { navController.popBackStack() },
                    onAddToMeal = { amount ->
                        completeFoodEntry(meal, meal, food, amount)
                    },
                )
            }
            composable(AddWorkoutRoute) {
                AddWorkoutScreen(
                    day = sessionData.day,
                    today = today,
                    onBack = { navController.popBackStack() },
                    onSaveWorkout = { type, duration, notes ->
                        val formEntry = navController.currentBackStackEntry
                        onAddWorkout(type, duration, notes) {
                            if (navController.currentBackStackEntry == formEntry) navController.popBackStack()
                        }
                    },
                )
            }
            composable(ProfileRoute) {
                ProfileScreen(
                    goals = uiSettings.goals,
                    onBack = { navController.popBackStack() },
                    onGoalsClick = { navController.navigate(GoalsTargetsRoute) },
                    onCustomizeTodayClick = { navController.navigate(CustomizeTodayRoute) },
                )
            }
            composable(CustomizeTodayRoute) {
                CustomizeTodayScreen(
                    customization = uiSettings.today,
                    onModuleEnabled = onTodayModuleEnabled,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(GoalsTargetsRoute) {
                GoalsTargetsScreen(
                    goals = uiSettings.goals,
                    onBack = { navController.popBackStack() },
                    onSave = { goals ->
                        val formEntry = navController.currentBackStackEntry
                        onUpdateGoals(goals) {
                            if (navController.currentBackStackEntry == formEntry) navController.popBackStack()
                        }
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
    TrackTheme {
        TrackApp(
            TrackSessionData(TrackDemoBaseline.referenceDay), TrackDemoBaseline.referenceDay, {}, {}, TrackUiSettings(),
            { _, _, _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> },
        )
    }
}
