package pl.filebit.gymtracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.backup.BackupScreen
import pl.filebit.gymtracker.ui.exercises.ExerciseDetailScreen
import pl.filebit.gymtracker.ui.exercises.ExerciseLibraryScreen
import pl.filebit.gymtracker.ui.history.HistoryScreen
import pl.filebit.gymtracker.ui.history.WorkoutDetailScreen
import pl.filebit.gymtracker.ui.home.HomeScreen
import pl.filebit.gymtracker.ui.plans.PlanEditScreen
import pl.filebit.gymtracker.ui.plans.PlanListScreen
import pl.filebit.gymtracker.ui.plans.TemplatesScreen
import pl.filebit.gymtracker.ui.onerm.OneRmCalculatorScreen
import pl.filebit.gymtracker.ui.profile.ProfileScreen
import pl.filebit.gymtracker.ui.stats.StatsScreen
import pl.filebit.gymtracker.ui.body.BodyMeasurementsScreen
import pl.filebit.gymtracker.ui.muscles.MuscleEngagementScreen
import pl.filebit.gymtracker.ui.photos.ProgressPhotosScreen
import pl.filebit.gymtracker.ui.strength.StrengthStandardsScreen
import pl.filebit.gymtracker.ui.ai.AiConversationsScreen
import pl.filebit.gymtracker.ui.ai.AiOverlayFab
import pl.filebit.gymtracker.ui.ai.screenLabel
import pl.filebit.gymtracker.ui.ai.AiOverlayViewModel
import pl.filebit.gymtracker.ui.ai.AiSettingsScreen
import pl.filebit.gymtracker.ui.ai.AiTrainerScreen
import pl.filebit.gymtracker.ui.shell.ActiveWorkoutMiniBar
import pl.filebit.gymtracker.ui.shell.ActiveWorkoutShellViewModel
import pl.filebit.gymtracker.ui.goals.GoalsScreen
import pl.filebit.gymtracker.ui.glossary.GlossaryScreen
import pl.filebit.gymtracker.ui.tools.PlateCalculatorScreen
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

    // Bottom nav widoczny też na ekranach aktywnego treningu — user może
    // przeglądać aplikację bez konieczności kończenia treningu.
    val workoutRoutes = setOf(Screen.ActiveWorkout.route, Screen.CoachWorkout.route)
    val tabRoutes = tabs.map { it.screen.route }.toSet()
    val showBottomBar = currentRoute in tabRoutes || currentRoute in workoutRoutes

    val overlayVm: AiOverlayViewModel = hiltViewModel()
    val overlayState by overlayVm.state.collectAsStateWithLifecycle()
    var aiChoiceVisible by remember { mutableStateOf(false) }
    var aiQuickAskVisible by remember { mutableStateOf(false) }
    val hideOverlayRoutes = setOf(
        Screen.AiTrainer.route,
        Screen.AiSettings.route,
        Screen.AiConversations.route
    )
    val showOverlay = overlayState.enabled && currentRoute !in hideOverlayRoutes

    val workoutShellVm: ActiveWorkoutShellViewModel = hiltViewModel()
    val workoutShellState by workoutShellVm.state.collectAsStateWithLifecycle()
    val showActiveBar = workoutShellState.hasActive && currentRoute !in workoutRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        bottomBar = {
            // navigationBarsPadding na cały Column gwarantuje że nawet gdy widoczny
                            // jest TYLKO mini-bar (bez NavigationBar), gesture bar Androida nie
            // przykrywa jego klikalnego obszaru.
            Column(modifier = Modifier.navigationBarsPadding()) {
                if (showActiveBar) {
                    ActiveWorkoutMiniBar(
                        state = workoutShellState,
                        onClick = {
                            val target = if (workoutShellState.isFromPlan)
                                Screen.CoachWorkout.route
                            else Screen.ActiveWorkout.route
                            navController.navigate(target) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                if (showBottomBar) {
                    // windowInsets = zero — bottom inset zarządza Column wyżej
                    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
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
                    },
                    onSelectPlanTab = {
                        navController.navigate(Screen.Plans.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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
                    onStartedCoachWorkout = { navController.navigate(Screen.CoachWorkout.route) },
                    onOpenTemplates = { navController.navigate(Screen.PlanTemplates.route) }
                )
            }
            composable(Screen.PlanTemplates.route) {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { newPlanId ->
                        navController.popBackStack()
                        navController.navigate(Screen.PlanEdit.create(newPlanId))
                    }
                )
            }
            composable(Screen.ExerciseLibrary.route) {
                ExerciseLibraryScreen(
                    onOpenDetail = { id -> navController.navigate(Screen.ExerciseDetail.create(id)) }
                )
            }
            composable(
                route = Screen.ExerciseDetail.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) {
                ExerciseDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onOpenBackup = { navController.navigate(Screen.Backup.route) },
                    onOpenStats = { navController.navigate(Screen.Stats.route) },
                    onOpenOneRm = { navController.navigate(Screen.OneRm.route) },
                    onOpenPlateCalc = { navController.navigate(Screen.PlateCalc.route) },
                    onOpenBody = { navController.navigate(Screen.BodyMeasurements.route) },
                    onOpenMuscles = { navController.navigate(Screen.MuscleEngagement.route) },
                    onOpenPhotos = { navController.navigate(Screen.ProgressPhotos.route) },
                    onOpenStrength = { navController.navigate(Screen.StrengthStandards.route) },
                    onOpenAiTrainer = { navController.navigate(Screen.AiConversations.route) },
                    onOpenAiSettings = { navController.navigate(Screen.AiSettings.route) },
                    onOpenGoals = { navController.navigate(Screen.Goals.route) },
                    onOpenGlossary = { navController.navigate(Screen.Glossary.route) }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAchievements = { navController.navigate(Screen.Achievements.route) }
                )
            }
            composable(Screen.Achievements.route) {
                pl.filebit.gymtracker.ui.stats.AchievementsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.OneRm.route) {
                OneRmCalculatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.PlateCalc.route) {
                PlateCalculatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BodyMeasurements.route) {
                BodyMeasurementsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MuscleEngagement.route) {
                MuscleEngagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ProgressPhotos.route) {
                ProgressPhotosScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.StrengthStandards.route) {
                StrengthStandardsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AiConversations.route) {
                AiConversationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Screen.AiSettings.route) },
                    onOpenConversation = { id ->
                        navController.navigate(Screen.AiTrainer.create(id))
                    },
                    onNewConversation = {
                        navController.navigate(Screen.AiTrainer.create(0L))
                    }
                )
            }
            composable(
                route = Screen.AiTrainer.route,
                arguments = listOf(navArgument("conversationId") {
                    type = NavType.StringType
                    defaultValue = "0"
                })
            ) {
                AiTrainerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Screen.AiSettings.route) },
                    onPlanApplied = { _ ->
                        navController.navigate(Screen.Plans.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.AiSettings.route) {
                AiSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Goals.route) {
                GoalsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Glossary.route) {
                GlossaryScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ActiveWorkout.route) {
                ActiveWorkoutScreen(
                    onAddExerciseClick = { navController.navigate(Screen.ExercisePicker.create("WORKOUT")) },
                    onWorkoutFinished = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    onSavedAsPlan = { newPlanId ->
                        navController.navigate(Screen.PlanEdit.create(newPlanId))
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
                    onBack = { navController.popBackStack() },
                    onSavedAsPlan = { newPlanId ->
                        navController.popBackStack()
                        navController.navigate(Screen.PlanEdit.create(newPlanId))
                    },
                    onRepeated = {
                        navController.popBackStack()
                        navController.navigate(Screen.ActiveWorkout.route)
                    }
                )
            }
        }
    }

        // Pływający FAB asystenta AI — widoczny tylko gdy włączony w Profilu
        // i NIE jesteśmy już na ekranie AI. Pozycja: prawy górny róg, pod
        // paskiem statusu systemu (statusBarsPadding bo activity = edgeToEdge).
        // Małe (40dp) żeby mieściło się w obszarze TopAppBar bez zasłaniania.
        if (showOverlay) {
            AiOverlayFab(
                onClick = {
                    if (overlayState.connected) {
                        aiChoiceVisible = true
                    } else {
                        navController.navigate(Screen.AiSettings.route)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 8.dp)
            )
        }

        if (aiChoiceVisible) {
            pl.filebit.gymtracker.ui.ai.AiChoiceDialog(
                screenLabel = currentRoute.screenLabel(),
                onDismiss = { aiChoiceVisible = false },
                onOpenAssistant = {
                    aiChoiceVisible = false
                    navController.navigate(Screen.AiConversations.route)
                },
                onAskAboutScreen = {
                    aiChoiceVisible = false
                    aiQuickAskVisible = true
                }
            )
        }

        if (aiQuickAskVisible) {
            pl.filebit.gymtracker.ui.ai.AiQuickAskSheet(
                screenLabel = currentRoute.screenLabel(),
                onDismiss = { aiQuickAskVisible = false }
            )
        }
    }
}
