package pl.filebit.gymtracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
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
import pl.filebit.gymtracker.ui.ai.screenLabel
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

internal data class TabItem(
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

    var aiChoiceVisible by remember { mutableStateOf(false) }
    var aiQuickAskVisible by remember { mutableStateOf(false) }
    // TopBar widoczny WSZĘDZIE poza ekranami workout (mają własny tytuł +
    // skomplikowane akcje) i ekranami AI (Trener AI ma własny topBar z labelem
    // "PLAN NA TYDZIEŃ" — duplikacja byłaby brzydka).
    val hideTopBarRoutes = setOf(
        Screen.ActiveWorkout.route,
        Screen.CoachWorkout.route,
        Screen.AiTrainer.route,
        Screen.AiConversations.route,
        Screen.AiSettings.route,
        Screen.AiWeeklyReport.route
    )
    val showTopBar = currentRoute != null && currentRoute !in hideTopBarRoutes

    val workoutShellVm: ActiveWorkoutShellViewModel = hiltViewModel()
    val workoutShellState by workoutShellVm.state.collectAsStateWithLifecycle()
    val showActiveBar = workoutShellState.hasActive && currentRoute !in workoutRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            if (showTopBar) {
                AppTopBar(
                    onOpenAiAssistant = { aiChoiceVisible = true }
                )
            }
        },
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
                    // Customowy bottom nav zgodny z propozycją Claude Design:
                    // - 80dp wysokość, surface tło, top border 4% white
                    // - Aktywny: mały pill 36x28dp 10% akcent + dot 4x4 z glow pod ikoną
                    // - Label 10sp SemiBold, ikona 22dp, akcent żółty na aktywnej
                    GymBottomNav(
                        currentRoute = currentRoute,
                        tabs = tabs,
                        onTabClick = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
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
                    },
                    onOpenStats = { navController.navigate(Screen.Stats.route) },
                    onOpenAchievements = { navController.navigate(Screen.Achievements.route) },
                    onOpenHistory = {
                        navController.navigate(Screen.History.route) {
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
                    onOpenAiWeeklyReport = { navController.navigate(Screen.AiWeeklyReport.route) },
                    onOpenGoals = { navController.navigate(Screen.Goals.route) },
                    onOpenGlossary = { navController.navigate(Screen.Glossary.route) },
                    onOpenAchievements = { navController.navigate(Screen.Achievements.route) }
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
            composable(Screen.AiWeeklyReport.route) {
                pl.filebit.gymtracker.ui.ai.WeeklyReportScreen(
                    onBack = { navController.popBackStack() }
                )
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

        // FAB AI overlay usunięty w v0.55.1 — jedyny przycisk AI to ten
        // w globalnym AppTopBar (tylko na 5 zakładkach).

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

/**
 * Globalny TopBar — widoczny na 5 zakładkach (Home/Historia/Plany/Ćwiczenia/Profil).
 * Logo "● GymTracker" po lewej, dzwonek + AI button po prawej.
 */
@Composable
private fun AppTopBar(onOpenAiAssistant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo "● GymTracker"
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AccentOrange, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "GymTracker",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = (-0.2).sp
            ),
            color = DarkOnSurface
        )
        Spacer(Modifier.weight(1f))
        // Dzwonek (placeholder bez akcji)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { /* TODO: notyfikacje */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Powiadomienia",
                tint = DarkOnSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        // AI button — żółte kółko
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AccentOrange)
                .clickable(onClick = onOpenAiAssistant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Asystent AI",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
