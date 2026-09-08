package com.example.track

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
private const val EditFoodRoute = "edit_food"
private const val EditWorkoutRoute = "edit_workout"
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
    val weightHistory by viewModel.weightHistory.collectAsStateWithLifecycle()
    val search by viewModel.foodSearch.collectAsStateWithLifecycle()
    val selectedFood by viewModel.selectedFood.collectAsStateWithLifecycle()
    val scanner by viewModel.scanner.collectAsStateWithLifecycle()
    TrackApp(
        weightHistory = weightHistory,
        onLogWeight = viewModel::logWeight,
        search = search,
        onSearch = viewModel::searchFoods,
        onSearchClosed = viewModel::cancelFoodSearch,
        selectedFood = selectedFood,
        onFoodSelected = viewModel::selectFood,
        scanner = scanner,
        onBarcode = viewModel::scanBarcode,
        onScanAgain = viewModel::resetScanner,
        onRetryLookup = viewModel::retryBarcodeLookup,
        // Never pair a new date header with the previous day's Room snapshot.
        sessionData = tracking.takeIf { it.day == selectedDay } ?: TrackSessionData(selectedDay),
        today = today,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        uiSettings = settings,
        onAddFood = viewModel::addFood,
        onAddWorkout = viewModel::addWorkout,
        onAddWater = viewModel::addWater,
        onDecreaseWater = viewModel::decreaseWater,
        loadFood = viewModel::foodForEdit,
        loadWorkout = viewModel::workoutForEdit,
        onUpdateFood = viewModel::updateFood,
        onDeleteFood = viewModel::deleteFood,
        onUpdateWorkout = viewModel::updateWorkout,
        onDeleteWorkout = viewModel::deleteWorkout,
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
    onDecreaseWater: () -> Unit = {},
    loadFood: suspend (String, Long) -> LoggedFood? = { _, _ -> null },
    loadWorkout: suspend (String, Long) -> LoggedWorkout? = { _, _ -> null },
    onUpdateFood: (String, LoggedFood, Int, MealContext, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onDeleteFood: (String, Long, () -> Unit) -> Unit = { _, _, _ -> },
    onUpdateWorkout: (String, Long, WorkoutType, Int, String, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteWorkout: (String, Long) -> Unit = { _, _ -> },
    search: FoodSearchState = FoodSearchState(),
    onSearch: (String) -> Unit = {},
    onSearchClosed: () -> Unit = {},
    selectedFood: FoodDefinition? = null,
    onFoodSelected: (FoodDefinition) -> Unit = {},
    scanner: ScannerState = ScannerState.Scanning,
    onBarcode: (String) -> Unit = {},
    onScanAgain: () -> Unit = {},
    onRetryLookup: () -> Unit = {},
    weightHistory: WeightHistoryState = WeightHistoryState(loading = false),
    onLogWeight: suspend (Double) -> Boolean = { false },
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                    weightEntry = weightHistory.entries.weightForSelectedDay(sessionData.day, today),
                    customization = uiSettings.today,
                    goals = uiSettings.goals,
                    sessionData = sessionData,
                    onAddWater = onAddWater,
                    onDecreaseWater = onDecreaseWater,
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
                    onEditFood = { food -> navController.navigate("$EditFoodRoute/${sessionData.day.toDayKey()}/${food.id}") },
                    onDeleteFood = { food ->
                        onDeleteFood(sessionData.day.toDayKey(), food.id) {
                            scope.launch { snackbar.showSnackbar("Food deleted") }
                        }
                    },
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
                    onEditWorkout = { workout -> navController.navigate("$EditWorkoutRoute/${sessionData.day.toDayKey()}/${workout.id}") },
                    onDeleteWorkout = { workout -> onDeleteWorkout(sessionData.day.toDayKey(), workout.id) },
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
                    today = today,
                    history = weightHistory,
                    goals = uiSettings.goals,
                    onLogWeight = onLogWeight,
                    onAvatarClick = { navController.navigate(ProfileRoute) },
                )
            }
            composable(
                route = "$AddFoodRoute/{meal}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }),
            ) { entry ->
                val meal = MealContext.fromRoute(entry.arguments?.getString("meal"))
                AddFoodSearchScreen(
                    search = search,
                    onSearch = onSearch,
                    onSearchClosed = onSearchClosed,
                    onBack = { navController.popBackStack() },
                    onFoodSelected = { food ->
                        onFoodSelected(food)
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
                DisposableEffect(Unit) { onDispose { onScanAgain() } }
                BarcodeScannerScreen(
                    state = scanner,
                    onBarcode = onBarcode,
                    onScanAgain = onScanAgain,
                    onRetryLookup = onRetryLookup,
                    initialMeal = originMeal,
                    onBack = { navController.popBackStack() },
                    onAddToMeal = { meal, food, amount ->
                        completeFoodEntry(originMeal, meal, food, amount)
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
                val id = entry.arguments?.getString("foodId")
                val food = selectedFood?.takeIf { it.id == id } ?: findLocalFood(id)
                if (food == null) {
                    // A remote selection is intentionally transient after process death.
                    Column {
                        Text("Product selection expired")
                        TextButton(onClick = { navController.popBackStack() }) { Text("Back to search") }
                    }
                    return@composable
                }
                FoodDetailsScreen(
                    meal = meal,
                    food = food,
                    onBack = { navController.popBackStack() },
                    onAddToMeal = { amount ->
                        completeFoodEntry(meal, meal, food, amount)
                    },
                )
            }
            composable(
                "$EditFoodRoute/{dayKey}/{id}",
                arguments = listOf(navArgument("dayKey") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val dayKey = requireNotNull(entry.arguments?.getString("dayKey"))
                val id = requireNotNull(entry.arguments).getLong("id")
                LoadedTrackingLog(load = { loadFood(dayKey, id) }, onBack = { navController.popBackStack() }) { original ->
                    EditFoodLogScreen(original, onBack = { navController.popBackStack() }) { amount, meal ->
                        onUpdateFood(dayKey, original, amount, meal) {
                            if (navController.currentBackStackEntry == entry) navController.popBackStack()
                        }
                    }
                }
            }
            composable(
                "$EditWorkoutRoute/{dayKey}/{id}",
                arguments = listOf(navArgument("dayKey") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val dayKey = requireNotNull(entry.arguments?.getString("dayKey"))
                val id = requireNotNull(entry.arguments).getLong("id")
                LoadedTrackingLog(load = { loadWorkout(dayKey, id) }, onBack = { navController.popBackStack() }) { original ->
                    AddWorkoutScreen(
                        day = dayKey.toTrackDay(), today = today, existingWorkout = original,
                        onBack = { navController.popBackStack() },
                        onSaveWorkout = { type, duration, notes ->
                            onUpdateWorkout(dayKey, id, type, duration, notes) {
                                if (navController.currentBackStackEntry == entry) navController.popBackStack()
                            }
                        },
                    )
                }
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
                    currentWeight = weightHistory.entries.latestWeight(today),
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
                    currentWeight = weightHistory.entries.latestWeight(today),
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

// Load once per back-stack entry. The editor's original snapshot stays fixed while
// Room flows update dashboards; drafts never become a second list of stored logs.
@Composable
private fun <T : Any> LoadedTrackingLog(
    load: suspend () -> T?, onBack: () -> Unit, content: @Composable (T) -> Unit,
) {
    val loaded by produceState<Result<T?>?>(initialValue = null) {
        value = try {
            Result.success(load())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val log = loaded?.getOrNull()
    if (log != null) content(log) else Column {
        Text(when {
            loaded == null -> "Loading entry…"
            loaded?.isFailure == true -> "Could not load entry"
            else -> "Entry no longer available"
        })
        TextButton(onClick = onBack) { Text("Back") }
    }
}
