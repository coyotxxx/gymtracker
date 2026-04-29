package pl.filebit.gymtracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.backup.BackupScreen
import pl.filebit.gymtracker.ui.exercises.ExerciseLibraryScreen
import pl.filebit.gymtracker.ui.history.HistoryScreen
import pl.filebit.gymtracker.ui.history.WorkoutDetailScreen
import pl.filebit.gymtracker.ui.home.HomeScreen
import pl.filebit.gymtracker.ui.plans.PlanEditScreen
import pl.filebit.gymtracker.ui.plans.PlanListScreen
import pl.filebit.gymtracker.ui.profile.ProfileScreen
import pl.filebit.gymtracker.ui.workout.ActiveWorkoutScreen
import pl.filebit.gymtracker.ui.workout.CoachWorkoutScreen
import pl.filebit.gymtracker.ui.workout.ExercisePickerScreen

private data class TabItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector
)

private val tabs = listOf(
    TabItem(Screen.Home, R.string.nav_home, Icons.Default.Home),
    TabItem(Screen.History, R.string.nav_history, Icons.Default.History),
    TabItem(Screen.Plans, R.string.nav_plans, Icons.Default.EventNote),
    TabItem(Screen.ExerciseLibrary, R.string.nav_exercises, Icons.Default.FitnessCenter),
    TabItem(Screen.Profile, R.string.nav_profile, Icons.Default.Person)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomBar = currentRoute in tabs.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartCoachWorkout = { navController.navigate(Screen.CoachWorkout.route) },
                    onStartAdhocWorkout = { navController.navigate(Screen.ActiveWorkout.route) },
                    onOpenWorkout = { id ->
                        navController.navigate(Screen.WorkoutDetail.create(id))
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onOpenWorkout = { id ->
                        navController.navigate(Screen.WorkoutDetail.create(id))
                    }
                )
            }
            composable(Screen.Plans.route) {
                PlanListScreen(
                    onEditPlan = { id -> navController.navigate(Screen.PlanEdit.create(id)) },
                    onCreateNewPlan = { navController.navigate(Screen.PlanEdit.create(0L)) },
                    onStartedCoachWorkout = { navController.navigate(Screen.CoachWorkout.route) }
                )
            }
            composable(Screen.ExerciseLibrary.route) {
                ExerciseLibraryScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onOpenBackup = { navController.navigate(Screen.Backup.route) }
                )
            }
            composable(Screen.ActiveWorkout.route) {
                ActiveWorkoutScreen(
                    onAddExerciseClick = { navController.navigate(Screen.ExercisePicker.create("WORKOUT")) },
                    onWorkoutFinished = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            }
            composable(Screen.CoachWorkout.route) {
                CoachWorkoutScreen(
                    onWorkoutFinished = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            }
            composable(
                route = Screen.ExercisePicker.route,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "WORKOUT"
                    }
                )
            ) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "WORKOUT"
                ExercisePickerScreen(
                    mode = mode,
                    onPicked = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                    onPickedForPlan = { exerciseId ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("picked_exercise_id", exerciseId)
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Backup.route) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.PlanEdit.route,
                arguments = listOf(navArgument("planId") { type = NavType.LongType })
            ) { entry ->
                val pickedExerciseId by entry.savedStateHandle
                    .getStateFlow<Long?>("picked_exercise_id", null)
                    .collectAsStateWithLifecycle()

                PlanEditScreen(
                    pickedExerciseId = pickedExerciseId,
                    onConsumePickedExerciseId = {
                        entry.savedStateHandle["picked_exercise_id"] = null
                    },
                    onBack = { navController.popBackStack() },
                    onAddExercise = {
                        navController.navigate(Screen.ExercisePicker.create("PLAN"))
                    },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.WorkoutDetail.route,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("workoutId") ?: 0L
                WorkoutDetailScreen(
                    workoutId = id,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
