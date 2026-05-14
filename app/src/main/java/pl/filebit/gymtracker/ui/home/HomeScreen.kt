package pl.filebit.gymtracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import pl.filebit.gymtracker.ai.DataMaturity
import pl.filebit.gymtracker.ai.HealthInsight
import pl.filebit.gymtracker.ai.LoadZone
import pl.filebit.gymtracker.ai.MuscleRecoveryReport
import pl.filebit.gymtracker.ai.ReadinessZone
import pl.filebit.gymtracker.ai.RecoveryFactorSeverity
import pl.filebit.gymtracker.ai.RecoveryScore
import pl.filebit.gymtracker.ai.RecoveryStatus
import pl.filebit.gymtracker.ai.RecoveryZone
import pl.filebit.gymtracker.ai.TrainingLoad
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseStatus
import pl.filebit.gymtracker.ai.TrainingReadiness
import pl.filebit.gymtracker.ai.TrainingRecommendation
import pl.filebit.gymtracker.ai.WorkoutAdjustment
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartCoachWorkout: () -> Unit,
    onStartAdhocWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onSelectPlanTab: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenHealthScreenshot: () -> Unit = {},
    onOpenDiet: () -> Unit = {},   // v1.24.41: CTA refeed dla CUT prowadzi do zakładki Dieta
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPostponeDialog by remember { mutableStateOf(false) }
    var showWeekPlanDialog by remember { mutableStateOf(false) }
    var showDeloadExplain by remember {
        mutableStateOf<pl.filebit.gymtracker.util.DeloadRecommendation?>(null)
    }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Bez wewnętrznego Scaffold — outer Scaffold w AppNavigation ma już bottomBar.
    // Tylko statusBarsPadding na góra żeby content nie chował się pod statusbar.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                GreetingHeader(
                    displayName = state.displayName,
                    isActive = state.activeWorkout != null,
                    activePlanName = state.activePlanName.ifBlank { "Aktywny trening" }
                )
            }

            // v1.15.0: karta "AI TRENER PROPONUJE" — gdy są pending decisions.
            // Wyświetlane NA TOP (above Deload Card) bo to wymaga decyzji usera.
            state.pendingDecisions.firstOrNull()?.let { decision ->
                item {
                    AiProposalCard(
                        decision = decision,
                        onAccept = {
                            vm.acceptPendingDecision(decision.id) { msg ->
                                scope.launch { snackbar.showSnackbar(msg) }
                            }
                        },
                        onDismiss = {
                            vm.dismissPendingDecision(decision.id)
                            scope.launch { snackbar.showSnackbar("Propozycja odrzucona") }
                        }
                    )
                }
            }

            // v1.24.26: Karta osiągnięcia celu wagi. Pokazuje się gdy user
            // trzyma target ≥7 dni i nie zareagował jeszcze. 4 akcje co dalej.
            state.goalAchievement?.let { achievement ->
                item {
                    GoalAchievedCard(
                        result = achievement,
                        onMaintain = { vm.onGoalAchievedMaintain() },
                        onContinueCut = { newTarget -> vm.onGoalAchievedContinueCut(newTarget) },
                        onSwitchToBulk = { newTarget -> vm.onGoalAchievedSwitchToBulk(newTarget) },
                        onDismiss = { vm.onGoalAchievedDismiss() }
                    )
                }
            }

            // Deload card — 3 stany: Suggestion (Zastosuj/Wyjaśnij/Anuluj),
            // Active (trwa X dni), Active+isFinished (czas wrócić do oryginalnych wag).
            when (val card = state.deloadCard) {
                is pl.filebit.gymtracker.data.repository.DeloadCardState.Suggestion -> {
                    item {
                        DeloadSuggestionCard(
                            recommendation = card.recommendation,
                            canApply = vm.activePlanIdForDeload() != null,
                            onApply = {
                                vm.applyDeload(card.recommendation.severity) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                        )
                                    }
                                }
                            },
                            onExplain = { showDeloadExplain = card.recommendation },
                            onDismiss = { vm.dismissAlert(pl.filebit.gymtracker.data.repository.AlertType.DELOAD_SUGGESTION) },
                            onPickPlan = onSelectPlanTab,
                            // v1.24.41: dla CUT → CTA prowadzi do zakładki Dieta (refeed) zamiast obniżki wag
                            onPlanRefeed = onOpenDiet
                        )
                    }
                }
                is pl.filebit.gymtracker.data.repository.DeloadCardState.Active -> {
                    item {
                        // v1.24.15: w toku → kompaktowy banner (status, nie alert).
                        // Zakończony → duża karta z CTA "Przywróć plan" (decyzja do podjęcia).
                        if (card.isFinished) {
                            DeloadActiveCard(
                                active = card,
                                onRestore = {
                                    vm.restoreDeload { result ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                "Plan '${result.planName}' wrócił do oryginalnych wag (${result.restoredSets} setów)"
                                            )
                                        }
                                    }
                                },
                                onCancel = { vm.cancelDeloadWithoutRestore() }
                            )
                        } else {
                            DeloadActiveBanner(
                                active = card,
                                onManageRestore = {
                                    vm.restoreDeload { result ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                "Plan '${result.planName}' wrócił do oryginalnych wag (${result.restoredSets} setów)"
                                            )
                                        }
                                    }
                                },
                                onManageCancel = { vm.cancelDeloadWithoutRestore() }
                            )
                        }
                    }
                }
                is pl.filebit.gymtracker.data.repository.DeloadCardState.ReturnAfterBreak -> {
                    item {
                        ReturnAfterBreakCard(
                            recommendation = card.recommendation,
                            onDismiss = { vm.dismissAlert(pl.filebit.gymtracker.data.repository.AlertType.RETURN_AFTER_BREAK) }
                        )
                    }
                }
                is pl.filebit.gymtracker.data.repository.DeloadCardState.ActiveInjury -> {
                    item {
                        ActiveInjuryCard(
                            recommendation = card.recommendation,
                            onDismiss = { vm.dismissAlert(pl.filebit.gymtracker.data.repository.AlertType.ACTIVE_INJURY) }
                        )
                    }
                }
                pl.filebit.gymtracker.data.repository.DeloadCardState.None -> Unit
            }

            // Hero card — 4 stany (A/B/C/D)
            item {
                when {
                    // Stan C: aktywny trening pauzowany
                    state.activeWorkout != null -> ActiveTrainingHeroCard(
                        durationMin = state.activeWorkoutDurationMin,
                        progressPct = state.activeWorkoutProgressPct,
                        currentSetLabel = state.activeWorkoutCurrentSetLabel,
                        planName = state.activePlanName.ifBlank { "Aktywny trening" },
                        onResume = {
                            vm.continueActiveWorkout(
                                onCoach = onStartCoachWorkout,
                                onAdhoc = onStartAdhocWorkout
                            )
                        }
                    )
                    // Stan A: dziś trening z planu
                    state.todaysPlan != null -> TodaysPlanHeroCard(
                        planName = state.todaysPlan!!.name,
                        exerciseCount = state.todaysPlanExerciseCount,
                        daysPerWeek = state.todaysPlan!!.daysOfWeek.size,
                        onStart = {
                            val isoDay = Clock.System
                                .todayIn(TimeZone.currentSystemDefault())
                                .dayOfWeek.isoDayNumber
                            vm.startWorkoutFromPlanForDay(
                                state.todaysPlan!!.id, isoDay, onStartCoachWorkout
                            )
                        },
                        onPostpone = { showPostponeDialog = true }
                    )
                    // Stan B: dziś rest, ale plan istnieje
                    state.nextPlannedDay != null -> DayOffHeroCard(
                        next = state.nextPlannedDay!!,
                        onTrainNow = {
                            vm.startNextPlannedToday(onStartCoachWorkout)
                        },
                        onWeekPlan = { showWeekPlanDialog = true }
                    )
                    // Stan D: brak planu wcale
                    else -> NoPlanHeroCard(onPickPlan = onSelectPlanTab)
                }
            }

            // 2-kolumnowa siatka quick cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        title = "Ad-hoc",
                        subtitle = "Bez planu — dodajesz w trakcie",
                        onClick = { vm.startWorkoutAdhoc(onStartAdhocWorkout) }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.QueryStats,
                        title = "Statystyki",
                        subtitle = "Tydzień · objętość · PR",
                        onClick = onOpenStats
                    )
                }
            }

            // Streak compact card — klik otwiera ekran Odznak (streak to odznaka)
            item {
                StreakCompactCard(
                    weeks = state.streakWeeks,
                    best = state.streakBest,
                    weekCurrent = state.workoutsThisWeek,
                    weekTarget = state.weeklyTarget,
                    onClick = onOpenAchievements
                )
            }

            // v1.9.0: Training Readiness + Muscle Recovery (na samej górze — primary metric)
            state.trainingReadiness?.let { readiness ->
                val readinessDismissed = pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.READINESS in state.dismissedCards
                if (readiness.maturity != DataMaturity.LEARNING && !readinessDismissed) {
                    item {
                        // v1.14.0: unified canApplyDeloadNow zastępuje 3 osobne checki w kartach.
                        // Sprawdza i TrainingPhase, i aktywny mesocykl (MesocyclePhase.DELOAD).
                        TrainingReadinessCard(
                            readiness = readiness,
                            muscleReport = state.muscleRecovery,
                            canApplyDeload = vm.canApplyDeloadNow(),
                            onApplyDeload = { severity ->
                                vm.applyDeload(severity) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                        )
                                    }
                                }
                            },
                            onDismiss = {
                                vm.dismissCard(pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.READINESS)
                            }
                        )
                    }
                }
            }

            // Faza cyklu treningowego (computed z TrainingPhaseAnalyzer)
            state.trainingPhase?.let { phase ->
                val phaseDismissed = pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.PHASE in state.dismissedCards
                // v1.24.40: ukryj fazę z NEEDS_DELOAD jeśli osobna karta DELOAD ZALECANY/ROZWAŻ DELOAD
                // już to mówi — duplikat z 2 systemów (DeloadService + TrainingPhaseAnalyzer) zaśmieca Home.
                val duplicatesDeloadAlert = phase.phase == pl.filebit.gymtracker.ai.TrainingPhase.NEEDS_DELOAD &&
                    state.deloadCard is pl.filebit.gymtracker.data.repository.DeloadCardState.Suggestion
                if (phase.phase != pl.filebit.gymtracker.ai.TrainingPhase.NO_DATA && !phaseDismissed && !duplicatesDeloadAlert) {
                    item {
                        TrainingPhaseCard(
                            status = phase,
                            canApplyDeload = vm.canApplyDeloadNow(),  // v1.14.0: unified check
                            onApplyDeload = {
                                vm.applyDeload(pl.filebit.gymtracker.util.DeloadSeverity.HIGH) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                        )
                                    }
                                }
                            },
                            onDismiss = {
                                vm.dismissCard(pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.PHASE)
                            },
                            periodizationState = state.periodizationState,  // v1.14.0
                            onEndDeloadEarly = {
                                vm.endDeloadEarly {
                                    scope.launch { snackbar.showSnackbar("Deload zakończony — start akumulacji") }
                                }
                            },
                            onExtendPhase = {
                                vm.extendCurrentMesoPhase(addWeeks = 1) {
                                    scope.launch { snackbar.showSnackbar("Faza przedłużona o tydzień") }
                                }
                            }
                        )
                    }
                }
            }

            // v1.7.4 WHOOP-like Recovery Score + ACWR (ukryta gdy user dismissował na dziś)
            state.recoveryScore?.let { score ->
                if (!state.recoveryCardDismissed) {
                    item {
                        WhoopRecoveryCard(
                            score = score,
                            canApplyDeload = vm.canApplyDeloadNow(),  // v1.14.0: unified
                            onApplyDeload = {
                                vm.applyScoreBasedDeload(score) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                        )
                                    }
                                }
                            },
                            onDismiss = { vm.dismissRecoveryCard() }
                        )
                    }
                }
            }
            state.trainingLoad?.let { load ->
                val loadDismissed = pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.LOAD in state.dismissedCards
                if (load.zone != pl.filebit.gymtracker.ai.LoadZone.INSUFFICIENT && !loadDismissed) {
                    item {
                        TrainingLoadCard(
                            load = load,
                            onDismiss = {
                                vm.dismissCard(pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.LOAD)
                            },
                            onApplyDeload = {
                                vm.applyDeload(pl.filebit.gymtracker.util.DeloadSeverity.HIGH) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                        )
                                    }
                                }
                            },
                            onApplyIncrease = {
                                vm.applyLoadIncrease { result ->
                                    scope.launch {
                                        if (result == null) {
                                            snackbar.showSnackbar("Najpierw zakończ aktywny deload")
                                        } else {
                                            snackbar.showSnackbar(
                                                "Plan '${result.planName}': ${result.updatedSets} setów × +${((result.factor - 1) * 100).toInt()}%"
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            // Skan zdrowotny dostępny przez ikonę AI w pasku górnym (FAB → Wyślij screen z zegarka)

            // Section header z linkiem
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ostatnie treningi",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    // 'Zobacz wszystkie →' też nawigujemy do tabu Historia.
                    // Skoro Home nie ma onOpenHistory, używamy onSelectPlanTab? Nie — niech
                    // klik wymusi tab nav do History przez specjalny callback. Na razie tylko
                    // tekst-link bez nawigacji (zachowanie kanonu w wyglądzie).
                    Text(
                        "Zobacz wszystkie →",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AccentOrange,
                        modifier = Modifier.clickable(onClick = onOpenHistory)
                    )
                }
            }

            if (state.recentWorkouts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Jeszcze nie było żadnego treningu.\nKliknij przycisk żeby zacząć.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.recentWorkouts.take(2), key = { it.workout.id }) { item ->
                    RecentWorkoutCard(item = item, onClick = { onOpenWorkout(item.workout.id) })
                }
            }
    }

    if (showPostponeDialog) {
        // Otwórz bottom-sheet zamiast prostego dialogu — user widzi cały tydzień + akcje
        showWeekPlanDialog = true
        showPostponeDialog = false
    }
    if (showWeekPlanDialog) {
        WeekPlanSheet(
            weekSlots = state.weekSlots,
            plansById = state.plansById,
            completedDays = state.completedDaysThisWeek,
            todayDayOfWeek = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber,
            onDismiss = { showWeekPlanDialog = false },
            onTrainNow = { planId, sourceDay ->
                showWeekPlanDialog = false
                vm.startSlotNow(planId, sourceDay, onStartCoachWorkout)
            },
            onPostpone = { planId, originalDay, targetDay ->
                vm.postponeTraining(planId, originalDay, targetDay)
            },
            onSkip = { planId, originalDay ->
                vm.skipTraining(planId, originalDay)
            },
            onClearOverride = { planId, originalDay ->
                vm.clearOverride(planId, originalDay)
            }
        )
    }

    showDeloadExplain?.let { rec ->
        DeloadExplainDialog(
            recommendation = rec,
            onDismiss = { showDeloadExplain = null }
        )
    }

    androidx.compose.material3.SnackbarHost(
        hostState = snackbar,
        modifier = Modifier
    )
}

@Composable
private fun DeloadExplainDialog(
    recommendation: pl.filebit.gymtracker.util.DeloadRecommendation,
    onDismiss: () -> Unit
) {
    val isRefeed = recommendation.recommendsDietBreak
    val pctOff = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> 20
        else -> 10
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // v1.24.44 fix Bug E2E: dla CUT user'a tytuł i treść były skopiowane
            // z deloadu klasycznego — sprzeczne z kartą "REFEED ZALECANY" gdzie
            // trening zostaje BEZ zmian. Teraz osobne treści dla obu wariantów.
            Text(
                if (isRefeed) "Co to jest refeed?" else "Co to jest deload?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isRefeed) {
                        "Refeed to 1-2 dni z kaloriami na maintenance (zamiast deficytu) i większą ilością " +
                            "węglowodanów. Uzupełnia glikogen w mięśniach, daje psychologiczną przerwę " +
                            "od restrykcji, ale NIE niweczy redukcji. Trening zostaje bez zmian — " +
                            "obniżamy tylko kuchnię, nie wagi w planie."
                    } else {
                        "Deload to lżejszy tydzień regeneracyjny — zmniejszasz wagi o $pctOff% " +
                            "ale zachowujesz ten sam plan. Pozwala mięśniom i CNS odpocząć po cyklu " +
                            "intensywnego treningu, żeby wrócić silniejszym."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Dlaczego to sugeruję teraz",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pl.filebit.gymtracker.ui.theme.AccentOrange
                )
                Text(
                    recommendation.reason,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (isRefeed) "Co się stanie po 'Zaplanuj refeed'" else "Co zrobi 'Zastosuj'",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pl.filebit.gymtracker.ui.theme.AccentOrange
                )
                Text(
                    if (isRefeed) {
                        "Przejdziesz do zakładki Dieta, gdzie zaplanujesz 1-2 dni " +
                            "z kaloriami na poziomie maintenance (≈+400-500 kcal vs target) " +
                            "i większą porcją węgli. Trening w planie nie zmienia się — " +
                            "wagi i sety zostają jak są. Po refeedzie wracasz do deficytu."
                    } else {
                        "Wszystkie wagi w aktywnym planie zmniejszą się o $pctOff% (np. 75 kg → " +
                            "${"%.1f".format(75.0 * (1 - pctOff / 100.0))} kg). " +
                            "Po 7 dniach apka przypomni żeby wrócić do oryginalnych wag — " +
                            "snapshot zachowa je dokładnie."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Alternatywa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pl.filebit.gymtracker.ui.theme.AccentOrange
                )
                Text(
                    if (isRefeed) {
                        "Możesz przejść na tydzień maintenance (diet break) — dłuższe odpoczęcie " +
                            "od deficytu, bardziej zauważalny efekt. Albo zignorować — wrócę z " +
                            "sugestią za tydzień jeśli warunki nadal aktualne."
                    } else {
                        "Możesz też zrobić tydzień całkowitej przerwy bez treningu — efekt podobny. " +
                            "Albo zignorować — wrócę z sugestią za tydzień jeśli warunki nadal aktualne."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Rozumiem")
            }
        }
    )
}

@Composable
private fun GreetingHeader(
    displayName: String,
    isActive: Boolean,
    activePlanName: String
) {
    val today = remember {
        SimpleDateFormat("EEEE · d MMM", Locale("pl", "PL")).format(Date())
            .replaceFirstChar { it.titlecase(Locale("pl", "PL")) }
    }
    Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            today.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        // "Cześć[, Maciej]" — imię w żółci. v1.24.16: maxLines=1+ellipsis na imieniu,
        // żeby długie imiona/etykiety nie zawijały layoutu na 2 linie.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                // v1.24.21: "Cześć!" gdy bez imienia (zamiast "Cześć" bez znaku).
                // Z przecinkiem tylko gdy imię jest — wtedy: "Cześć, Maciek".
                if (displayName.isBlank()) "Cześć!" else "Cześć,",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = DarkOnSurface
            )
            if (displayName.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp
                    ),
                    color = AccentOrange,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        if (isActive) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                pl.filebit.gymtracker.ui.theme.PulsingDot(size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "TRENING W TOKU",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    activePlanName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/** Stan A: Plan na dziś — gradient żółty + glow + przyciski Rozpocznij + Przesuń. */
@Composable
private fun TodaysPlanHeroCard(
    planName: String,
    exerciseCount: Int,
    daysPerWeek: Int,
    onStart: () -> Unit,
    onPostpone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.18f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "DZIŚ TRENING · ${planName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                planName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${exerciseCount} ćwiczeń · ~${exerciseCount * 10} min",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            // Row z 2 przyciskami: Rozpocznij (primary, weight 2f) + Przesuń (secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(2f)) {
                    HeroPrimaryButton(
                        text = "Rozpocznij",
                        icon = Icons.Default.PlayArrow,
                        onClick = onStart
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(DarkSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, AccentOrange.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onPostpone),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Przesuń",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

/** Stan C: aktywny trening pauzowany (user wyszedł z apki). */
@Composable
private fun ActiveTrainingHeroCard(
    durationMin: Int,
    progressPct: Int,
    currentSetLabel: String,
    planName: String,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.22f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "TRENING W TRAKCIE · ${durationMin} MIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                planName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (currentSetLabel.isNotBlank()) "$currentSetLabel · $progressPct% ukończone"
                else "Trening rozpoczęty",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            HeroPrimaryButton(
                text = "Wróć do treningu",
                icon = Icons.Default.PlayArrow,
                onClick = onResume
            )
        }
    }
}

@Composable
private fun NoPlanHeroCard(onPickPlan: () -> Unit) {
    Card(
        onClick = onPickPlan,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.12f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                "BRAK PLANU NA DZIŚ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Wybierz plan",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Otwórz zakładkę Plany i ustaw harmonogram",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(AccentOrange, CircleShape)
    )
}

@Composable
private fun MetaItem(num: String, label: String, smallSuffix: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                num,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
            )
            if (smallSuffix != null) {
                Text(
                    smallSuffix,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun HeroPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = AccentOrange),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                color = Color.Black
            )
        }
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = DarkOnSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StreakCompactCard(
    weeks: Int,
    best: Int,
    weekCurrent: Int,
    weekTarget: Int,
    onClick: () -> Unit = {}
) {
    val percent = if (weekTarget > 0) (weekCurrent * 100 / weekTarget).coerceAtMost(100) else 0
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ikona flame w żółtym kwadracie
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = AccentOrange.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Środek: liczba tygodni + label
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$weeks",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AccentOrange
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "tyg. z rzędu",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        "Najlepszy: $best tyg.".uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
                // Po prawej: tygodniowy postęp
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$weekCurrent / $weekTarget",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        "TEN TYDZIEŃ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentOrange,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
private fun RecentWorkoutCard(item: RecentWorkoutItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        // v1.24.35 fix Bug #6 (raport SYM 3tyg): Card(onClick=...) interactive variant
        // nadpisuje LocalContentColor wartością wyciszoną → wszystkie wartości MiniStat
        // (1:00:00, 200kg, 6 setów) były niewidoczne na ciemnym tle. Explicit
        // contentColor = DarkOnSurface przywraca białe wartości.
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface,
            contentColor = DarkOnSurface
        ),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Data
            val dateFmt = remember {
                SimpleDateFormat("EEE · d MMM", Locale("pl", "PL"))
            }
            Text(
                dateFmt.format(Date(item.workout.startedAt)),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(10.dp))
            // 4 stat-y w mono
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                MiniStat(
                    formatDuration(item.workout.durationMillis),
                    "Czas",
                    Modifier.weight(1f)
                )
                MiniStat("${item.exerciseCount}", "Ćwicz.", Modifier.weight(1f))
                MiniStat("${item.totalSets}", "Serie", Modifier.weight(1f))
                MiniStat(
                    formatWeight(item.totalVolumeKg),
                    "Vol.",
                    Modifier.weight(1f),
                    smallSuffix = "kg"
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    smallSuffix: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (smallSuffix != null) {
                Text(
                    smallSuffix,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DeloadSuggestionCard(
    recommendation: pl.filebit.gymtracker.util.DeloadRecommendation,
    canApply: Boolean,
    onApply: () -> Unit,
    onExplain: () -> Unit,
    onDismiss: () -> Unit,
    onPickPlan: () -> Unit = {},
    /** v1.24.41: CTA dla CUT — prowadzi do zakładki Dieta na zaplanowanie refeedu. */
    onPlanRefeed: () -> Unit = {}
) {
    val isRefeed = recommendation.recommendsDietBreak
    val color = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> pl.filebit.gymtracker.ui.theme.ErrorRed
        pl.filebit.gymtracker.util.DeloadSeverity.MED -> pl.filebit.gymtracker.ui.theme.AccentOrange
        pl.filebit.gymtracker.util.DeloadSeverity.LOW -> pl.filebit.gymtracker.ui.theme.AccentOrange
    }
    val bgAlpha = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> 0.18f
        pl.filebit.gymtracker.util.DeloadSeverity.MED -> 0.14f
        pl.filebit.gymtracker.util.DeloadSeverity.LOW -> 0.10f
    }
    val borderAlpha = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> 0.55f
        pl.filebit.gymtracker.util.DeloadSeverity.MED -> 0.40f
        pl.filebit.gymtracker.util.DeloadSeverity.LOW -> 0.30f
    }
    val severityLabel = when {
        // v1.24.41: dla CUT alert mówi o refeedzie, nie o deloadzie wag
        isRefeed -> "REFEED ZALECANY"
        recommendation.severity == pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> "MOCNY SYGNAŁ"
        recommendation.severity == pl.filebit.gymtracker.util.DeloadSeverity.MED -> "DELOAD ZALECANY"
        else -> "ROZWAŻ DELOAD"
    }
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = bgAlpha), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    severityLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                recommendation.reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // v1.24.41 refeed branch: dla CUT klasyczny deload (-10% wag) nie
                // pomoże — zmęczenie wynika z deficytu kcal. CTA prowadzi do Diety.
                // Wagi w planie zostawiamy nietknięte.
                // v1.24.34 fix Bug #3 (raport SYM 3tyg): gdy brak planu, zamiast
                // wyszarzonego "Zastosuj" pokaż enabled "Wybierz plan" — CTA prowadzi
                // usera do działania zamiast zostawiać alert bez wyjścia.
                if (isRefeed) {
                    androidx.compose.material3.Button(
                        onClick = onPlanRefeed,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = color,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🍽 Zaplanuj refeed", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (canApply) {
                    androidx.compose.material3.Button(
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = color,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("✓ Zastosuj", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    androidx.compose.material3.Button(
                        onClick = onPickPlan,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = color,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📋 Wybierz plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onExplain,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = color
                    ),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Wyjaśnij", fontSize = 13.sp)
                }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = DarkOnSurfaceVariant
                    )
                ) {
                    Text("Anuluj", fontSize = 13.sp)
                }
            }
            if (!canApply && !isRefeed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Brak aktywnego planu — wybierz plan, a system automatycznie obniży obciążenia.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveInjuryCard(
    recommendation: pl.filebit.gymtracker.util.ActiveInjuryRecommendation,
    onDismiss: () -> Unit
) {
    // Czerwony — ból to ostry sygnał (większy niż przetrenowanie).
    val color = pl.filebit.gymtracker.ui.theme.ErrorRed
    val bgAlpha = when (recommendation.severity) {
        pl.filebit.gymtracker.util.InjurySeverity.PERSISTENT -> 0.18f
        pl.filebit.gymtracker.util.InjurySeverity.FLAG -> 0.12f
    }
    val severityLabel = when (recommendation.severity) {
        pl.filebit.gymtracker.util.InjurySeverity.PERSISTENT -> "WYKRYTO BÓL — ODPUŚĆ"
        pl.filebit.gymtracker.util.InjurySeverity.FLAG -> "WYKRYTO BÓL — OBSERWUJ"
    }
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = bgAlpha), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    severityLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                recommendation.reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun ReturnAfterBreakCard(
    recommendation: pl.filebit.gymtracker.util.ReturnAfterBreakRecommendation,
    onDismiss: () -> Unit
) {
    // Niebieski "refresh" — to NIE deload (warning), to ostrożny restart (informacja).
    val color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
    val severityLabel = when (recommendation.severity) {
        pl.filebit.gymtracker.util.ReturnSeverity.LONG_BREAK -> "POWRÓT PO DŁUŻSZEJ PRZERWIE"
        pl.filebit.gymtracker.util.ReturnSeverity.SHORT_BREAK -> "POWRÓT PO PRZERWIE"
    }
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "↻",
                    color = color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    severityLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                recommendation.reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun DeloadActiveCard(
    active: pl.filebit.gymtracker.data.repository.DeloadCardState.Active,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    val color = pl.filebit.gymtracker.ui.theme.AccentOrange
    val pctOff = ((1.0 - active.state.factor) * 100).toInt()
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (active.isFinished) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active.isFinished) "DELOAD ZAKOŃCZONY" else "DELOAD TRWA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (active.isFinished) {
                    "Cykl deload (-$pctOff%) trwał ${active.daysElapsed} dni. " +
                        "Czas wrócić do oryginalnych wag w planie '${active.state.planName}'."
                } else {
                    "Plan '${active.state.planName}' z wagami −$pctOff% — pozostało ${active.daysRemaining} " +
                        "${if (active.daysRemaining == 1) "dzień" else "dni"} lżejszego treningu."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = color,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (active.isFinished) "✓ Przywróć plan" else "↩ Wróć teraz",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = DarkOnSurfaceVariant
                    )
                ) {
                    Text("Zamknij bez zmian", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * v1.24.15 — kompaktowy banner deloadu W TOKU (status, nie alert).
 *
 * Filozofia: decyzja o deloadzie podjęta. Nie pytamy o nią codziennie.
 * Pasek informuje "jesteś w deloadzie", a "Wróć teraz" przeniesione
 * do dyskretnego dialogu pod tappable "Zarządzaj".
 *
 * Dla stanu isFinished używamy duża karta DeloadActiveCard — wtedy
 * realnie wymaga decyzji "wrócić do oryginalnych wag?".
 */
/**
 * v1.24.26 — Karta osiągnięcia celu wagi.
 *
 * Pokazuje się gdy user trzyma target ≥7 dni (stabilność wagi).
 * 4 akcje:
 * - Utrzymaj (1-tap, przełącza na MAINTAIN)
 * - Schudnij dalej (dialog z nowym targetem niższym)
 * - Przejdź na masę (dialog z nowym targetem wyższym, BULK)
 * - Zamknij (X — ukryj kartę, decyzja później)
 */
@Composable
private fun GoalAchievedCard(
    result: pl.filebit.gymtracker.data.repository.GoalAchievementResult,
    onMaintain: () -> Unit,
    onContinueCut: (Double) -> Unit,
    onSwitchToBulk: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var showContinueCutDialog by remember { mutableStateOf(false) }
    var showSwitchBulkDialog by remember { mutableStateOf(false) }
    val color = SuccessGreen
    val deltaKg = kotlin.math.abs(result.currentWeight - result.startWeight)
    val direction = if (result.direction == pl.filebit.gymtracker.data.entity.WeightGoalType.CUT) "−" else "+"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "OSIĄGNĄŁEŚ CEL!",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val journey = if (result.daysToAchieve > 0) {
                "Z ${"%.1f".format(result.startWeight)} → ${"%.1f".format(result.currentWeight)} kg " +
                    "($direction${"%.1f".format(deltaKg)} kg w ${result.daysToAchieve} dni). "
            } else {
                "Waga ${"%.1f".format(result.currentWeight)} kg — cel utrzymany. "
            }
            Text(
                journey + "Trzymasz target od ${result.stableDays} dni — gratulacje!",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Co dalej?",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(10.dp))

            // Akcja 1: Utrzymaj (1-tap, primary)
            androidx.compose.material3.Button(
                onClick = onMaintain,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("✓ Utrzymaj wagę — przejdź na maintenance", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Akcja 2: Schudnij dalej (gdy CUT)
                if (result.direction == pl.filebit.gymtracker.data.entity.WeightGoalType.CUT) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showContinueCutDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Schudnij dalej", fontSize = 12.sp)
                    }
                }
                // Akcja 3: Przejdź na masę
                androidx.compose.material3.OutlinedButton(
                    onClick = { showSwitchBulkDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (result.direction == pl.filebit.gymtracker.data.entity.WeightGoalType.CUT)
                            "Przejdź na masę" else "Schudnij teraz",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Dialog: nowy target dla "Schudnij dalej"
    if (showContinueCutDialog) {
        var newTargetText by remember {
            mutableStateOf("%.1f".format(result.currentWeight - 2.0))
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showContinueCutDialog = false },
            title = { Text("Nowy cel redukcji") },
            text = {
                Column {
                    Text("Aktualnie: ${"%.1f".format(result.currentWeight)} kg")
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newTargetText,
                        onValueChange = { newTargetText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Nowy target (kg)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val t = newTargetText.replace(',', '.').toDoubleOrNull()
                        if (t != null && t < result.currentWeight) {
                            onContinueCut(t)
                        }
                        showContinueCutDialog = false
                    }
                ) { Text("Zapisz", fontWeight = FontWeight.Bold, color = SuccessGreen) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showContinueCutDialog = false }) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Dialog: nowy target dla "Przejdź na masę" (CUT→BULK) lub "Schudnij" (BULK→CUT)
    if (showSwitchBulkDialog) {
        val defaultTarget = if (result.direction == pl.filebit.gymtracker.data.entity.WeightGoalType.CUT)
            result.currentWeight + 5.0 else result.currentWeight - 3.0
        var newTargetText by remember { mutableStateOf("%.1f".format(defaultTarget)) }
        val isSwitchToBulk = result.direction == pl.filebit.gymtracker.data.entity.WeightGoalType.CUT
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSwitchBulkDialog = false },
            title = { Text(if (isSwitchToBulk) "Przejdź na budowanie masy" else "Schudnij") },
            text = {
                Column {
                    Text("Aktualnie: ${"%.1f".format(result.currentWeight)} kg")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isSwitchToBulk)
                            "System przełączy się na BULK (+0.3 kg/tydz). Białko obniży się do 1.8 g/kg."
                        else "System przełączy się na FAT_LOSS (−0.5 kg/tydz). Białko podniesie się do 2.2 g/kg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newTargetText,
                        onValueChange = { newTargetText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Nowy target (kg)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val t = newTargetText.replace(',', '.').toDoubleOrNull()
                        if (t != null) {
                            if (isSwitchToBulk && t > result.currentWeight) onSwitchToBulk(t)
                            else if (!isSwitchToBulk && t < result.currentWeight) onContinueCut(t)
                        }
                        showSwitchBulkDialog = false
                    }
                ) { Text("Zapisz", fontWeight = FontWeight.Bold, color = pl.filebit.gymtracker.ui.theme.AccentOrange) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSwitchBulkDialog = false }) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun DeloadActiveBanner(
    active: pl.filebit.gymtracker.data.repository.DeloadCardState.Active,
    onManageRestore: () -> Unit,
    onManageCancel: () -> Unit
) {
    val color = pl.filebit.gymtracker.ui.theme.AccentOrange
    val pctOff = ((1.0 - active.state.factor) * 100).toInt()
    var showDialog by remember { mutableStateOf(false) }

    // v1.24.16: tło z DarkSurface zamiast tinted akcent — żeby banner zlewał się
    // wizualnie z resztą ciemnego Home. Akcent zostaje na ikonie + tekście + linku.
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(pl.filebit.gymtracker.ui.theme.DarkSurface, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // mała pionowa linia akcent po lewej zamiast pełnego tinta
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text("💤", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                "DELOAD · −$pctOff% · ${active.daysRemaining} ${if (active.daysRemaining == 1) "dzień" else "dni"}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp
                ),
                color = color,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(
                onClick = { showDialog = true },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = color
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 0.dp
                )
            ) {
                Text(
                    "Zarządzaj",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Deload w toku") },
            text = {
                Text(
                    "Plan '${active.state.planName}' z wagami −$pctOff%. " +
                        "Pozostało ${active.daysRemaining} ${if (active.daysRemaining == 1) "dzień" else "dni"} lżejszego treningu.\n\n" +
                        "Możesz wrócić wcześniej do oryginalnych wag jeśli czujesz się gotowy."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDialog = false
                        onManageRestore()
                    }
                ) {
                    Text("↩ Wróć teraz", color = color, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDialog = false
                        onManageCancel()
                    }
                ) {
                    Text("Zamknij deload bez przywracania", color = DarkOnSurfaceVariant)
                }
            },
            containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface
        )
    }
}

/** Stan B: dziś rest, ale plan istnieje — przyciski Trenuj dziś + Plan tyg. */
@Composable
private fun DayOffHeroCard(
    next: NextPlannedDay,
    onTrainNow: () -> Unit,
    onWeekPlan: () -> Unit
) {
    val dayName = when (next.dayOfWeek) {
        1 -> "Poniedziałek"
        2 -> "Wtorek"
        3 -> "Środa"
        4 -> "Czwartek"
        5 -> "Piątek"
        6 -> "Sobota"
        else -> "Niedziela"
    }
    val whenText = when (next.daysFromToday) {
        1 -> "Jutro · $dayName"
        2 -> "Pojutrze · $dayName"
        else -> "Za ${next.daysFromToday} dni · $dayName"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.10f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                "NASTĘPNY TRENING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                whenText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${next.planName} · ${next.exerciseCount} ćwiczeń · ~${next.exerciseCount * 10} min",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(2f)) {
                    HeroPrimaryButton(
                        text = "Trenuj dziś",
                        icon = Icons.Default.PlayArrow,
                        onClick = onTrainNow
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(DarkSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, AccentOrange.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onWeekPlan),
                    contentAlignment = Alignment.Center
                ) {
                    // v1.24.16: "Plan tyg." → "Tydzień" — krócej i czytelniej,
                    // bez niejednoznacznego skrótu.
                    Text(
                        "Tydzień",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

// (Dialogi PostponeDialogContent + WeekPlanDialogContent z v0.75.0 zastąpione
//  pełnym WeekPlanSheet w v0.76.0 — patrz ui/home/WeekPlanSheet.kt)


@androidx.compose.runtime.Composable
private fun TrainingPhaseCard(
    status: TrainingPhaseStatus,
    canApplyDeload: Boolean,
    onApplyDeload: () -> Unit,
    onDismiss: () -> Unit,
    // v1.14.0: countdown z datowanego TrainingMesocycle (jeśli istnieje active)
    periodizationState: pl.filebit.gymtracker.data.repository.PeriodizationState =
        pl.filebit.gymtracker.data.repository.PeriodizationState.NoData,
    onEndDeloadEarly: () -> Unit = {},
    onExtendPhase: () -> Unit = {}
) {
    val (accent, emoji) = when (status.phase) {
        TrainingPhase.ACCUMULATION -> SuccessGreen to "📈"
        TrainingPhase.INTENSIFICATION -> AccentOrange to "⚡"
        TrainingPhase.DELOAD -> DarkOnSurfaceVariant to "🛌"
        TrainingPhase.NEEDS_DELOAD -> ErrorRed to "⚠️"
        TrainingPhase.NO_DATA -> DarkOnSurfaceVariant to "❓"
    }
    val showDeloadButton = canApplyDeload && status.phase == TrainingPhase.NEEDS_DELOAD
    // Kontekstowa info-karta (bez akcji) — DELOAD/ACCUM/INTENS to po prostu
    // info "w jakiej fazie jesteś". Wizualnie odróżnij od kart-z-akcją:
    // dyskretne tło + subtle border (zamiast accent).
    val isContextInfo = !showDeloadButton && status.phase != TrainingPhase.NO_DATA
    val cardBg = if (isContextInfo) DarkOnSurfaceVariant.copy(alpha = 0.06f) else accent.copy(alpha = 0.10f)
    val cardBorder = if (isContextInfo) DarkOnSurfaceVariant.copy(alpha = 0.20f) else accent.copy(alpha = 0.4f)
    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { androidx.compose.material3.Text("Zastosować deload?", fontWeight = FontWeight.Bold) },
            text = { androidx.compose.material3.Text("${status.weeksSinceLastDeload} tygodni bez deloadu — algorytm sugeruje tydzień lekki (-30% obciążenie). Możesz w każdej chwili przywrócić oryginalne wagi.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false; onApplyDeload() }) {
                    androidx.compose.material3.Text("Zastosuj", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    emoji,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "FAZA CYKLU",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        status.polishLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        ),
                        color = accent
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Zamknij na dziś",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            pl.filebit.gymtracker.ui.components.AiMarkdown(
                text = status.recommendation,
                contentColor = DarkOnSurface
            )

            // v1.14.0: countdown z datowanego TrainingMesocycle.
            // v1.24.34 fix Bug #2 (raport SYM 3tyg): gdy alert NEEDS_DELOAD aktywny,
            // ukryj countdown poprzedniej fazy (Intensyfikacja/Akumulacja) — była sprzeczność
            // "deload teraz" + "Intensyfikacja (1/3)". User widzi alert + przycisk
            // 'Zastosuj deload'; po zastosowaniu meso.phase=DELOAD i countdown wróci.
            val activeMeso = (periodizationState as? pl.filebit.gymtracker.data.repository.PeriodizationState.Active)
            val suppressCountdown = status.phase == TrainingPhase.NEEDS_DELOAD
            if (activeMeso != null && !suppressCountdown) {
                Spacer(Modifier.height(12.dp))
                MesocycleCountdownBlock(
                    state = activeMeso,
                    accent = accent,
                    onEndDeloadEarly = onEndDeloadEarly,
                    onExtendPhase = onExtendPhase
                )
            }

            if (showDeloadButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text("Zastosuj deload (-30%)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * v1.14.0 — sub-blok TrainingPhaseCard pokazujący countdown datowanego mesocyklu.
 *
 * Dla DELOAD: "Dzień X z 7" + "Akumulacja od pn DD.MM" + [Zakończ wcześniej] [Przedłuż].
 * Dla pozostałych faz: "Tydzień X z Y" + "Kolejna faza: ... od DD.MM" (bez akcji).
 *
 * Rozwiązanie luki ze screenshota Macieja: karta wcześniej była statyczna —
 * teraz pokazuje konkretne daty i co dalej.
 */
@androidx.compose.runtime.Composable
private fun MesocycleCountdownBlock(
    state: pl.filebit.gymtracker.data.repository.PeriodizationState.Active,
    accent: androidx.compose.ui.graphics.Color,
    onEndDeloadEarly: () -> Unit,
    onExtendPhase: () -> Unit
) {
    val meso = state.meso
    val isDeload = meso.phase == pl.filebit.gymtracker.data.entity.MesocyclePhase.DELOAD
    // v1.24.33 fix Bug #1: clamp dayShown żeby nigdy nie pokazywać "Dzień 22 z 21".
    // Sprzeczność z raportu SYM 3tyg — jeśli daysElapsed przekracza totalDaysPlanned,
    // pokaż na max wartość fazy. Stan przekroczony oznaczy się przez daysRemaining=0.
    val dayShown = (state.daysElapsed + 1).coerceAtMost(meso.totalDaysPlanned)
    val cycleEnded = state.daysRemaining == 0
    val dayLabel = if (cycleEnded) {
        "Cykl zakończony"
    } else {
        "Dzień $dayShown z ${meso.totalDaysPlanned}"
    }
    val endDateFmt = java.text.SimpleDateFormat("d MMMM", java.util.Locale("pl", "PL")).format(java.util.Date(meso.plannedEndDateMs))
    val nextPhaseLabel = when (meso.phase) {
        pl.filebit.gymtracker.data.entity.MesocyclePhase.ACCUMULATION -> "Intensyfikacja"
        pl.filebit.gymtracker.data.entity.MesocyclePhase.INTENSIFICATION -> "Deload"
        pl.filebit.gymtracker.data.entity.MesocyclePhase.DELOAD -> "Akumulacja"
        pl.filebit.gymtracker.data.entity.MesocyclePhase.PEAKING -> "Deload"
        pl.filebit.gymtracker.data.entity.MesocyclePhase.RECOVERY -> "Akumulacja"
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = accent
                )
                if (!cycleEnded) {
                    Text(
                        "${state.daysRemaining} ${if (state.daysRemaining == 1) "dzień" else "dni"} zostało",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkOnSurfaceVariant
                    )
                } else {
                    Text(
                        "→ $nextPhaseLabel",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = accent
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (state.progressPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.20f)
            )
            Spacer(Modifier.height(8.dp))
            // v1.24.33 fix Bug #1: gdy cykl się skończył, pokazuj "Czas na transition"
            // zamiast 'Intensyfikacja od 13 maja' (sugestia że NIC SIĘ NIE DZIEJE — bug)
            Text(
                if (cycleEnded) {
                    "Czas na nową fazę — czeka na decyzję"
                } else {
                    "$nextPhaseLabel od $endDateFmt"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = DarkOnSurfaceVariant
            )

            if (isDeload) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onEndDeloadEarly,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = accent
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Zakończ wcześniej", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onExtendPhase,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = DarkOnSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, DarkOnSurfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Przedłuż o tydzień", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}



@androidx.compose.runtime.Composable
private fun RecoveryCard(
    insight: HealthInsight,
    canApplyDeload: Boolean,
    onApplyDeload: () -> Unit
) {
    val (accent, emoji, label) = when (insight.recoveryStatus) {
        RecoveryStatus.EXCELLENT -> Triple(SuccessGreen, "✅", "Doskonała")
        RecoveryStatus.GOOD -> Triple(SuccessGreen, "✅", "Dobra")
        RecoveryStatus.MODERATE -> Triple(AccentOrange, "⚠️", "Umiarkowana")
        RecoveryStatus.POOR -> Triple(ErrorRed, "❌", "Słaba")
        RecoveryStatus.NO_DATA -> Triple(DarkOnSurfaceVariant, "❓", "Brak danych")
    }
    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val deloadPctLabel = when (insight.workoutAdjustment) {
        WorkoutAdjustment.LIGHT_VOLUME -> "lekki deload (-15%)"
        WorkoutAdjustment.DELOAD_TODAY -> "deload (-30%)"
        WorkoutAdjustment.REST_RECOMMENDED -> "deload (-30%)"
        WorkoutAdjustment.AS_PLANNED -> ""
    }
    val showDeloadButton = canApplyDeload && insight.workoutAdjustment != WorkoutAdjustment.AS_PLANNED
    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                androidx.compose.material3.Text(
                    "Zastosować $deloadPctLabel?",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                androidx.compose.material3.Text(
                    "Plan zostanie zmodyfikowany — obciążenia zmniejszone. Możesz w każdej chwili przywrócić oryginalne wagi (kafel 'Aktywny deload')."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showConfirm = false
                    onApplyDeload()
                }) {
                    androidx.compose.material3.Text(
                        "Zastosuj",
                        color = accent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    emoji,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "REGENERACJA",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        label,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        ),
                        color = accent
                    )
                }
            }
            // Liczby — sen + HRV (jeśli są)
            if (insight.lastNightSleepHours != null || insight.avgSleepHours7d != null || insight.avgHrvMs7d != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    insight.lastNightSleepHours?.let {
                        MetricMini(label = "Ostatnia noc", value = "%.1fh".format(it))
                    }
                    insight.avgSleepHours7d?.let {
                        MetricMini(label = "Sen 7d", value = "%.1fh".format(it))
                    }
                    insight.avgHrvMs7d?.let {
                        MetricMini(label = "HRV 7d", value = "%.0f ms".format(it))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            pl.filebit.gymtracker.ui.components.AiMarkdown(
                text = insight.recommendation,
                contentColor = DarkOnSurface
            )
            if (showDeloadButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text(
                        "Zastosuj $deloadPctLabel",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            } else if (canApplyDeload && insight.workoutAdjustment == WorkoutAdjustment.AS_PLANNED) {
                // Brak przycisku, ale daj subtelny hint że plan jest OK
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MetricMini(label: String, value: String) {
    Column {
        androidx.compose.material3.Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )
        androidx.compose.material3.Text(
            value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = DarkOnSurface
        )
    }
}



@androidx.compose.runtime.Composable
private fun ScreenshotImportCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                "📸",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    "Wyślij screen z zegarka",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurface
                )
                androidx.compose.material3.Text(
                    "Huawei / Mi / Garmin / Samsung — AI uzupełni dane",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            androidx.compose.material3.Text(
                "›",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = DarkOnSurfaceVariant
            )
        }
    }
}



@androidx.compose.runtime.Composable
private fun WhoopRecoveryCard(
    score: RecoveryScore,
    canApplyDeload: Boolean,
    onApplyDeload: () -> Unit,
    onDismiss: () -> Unit
) {
    val (accent, label) = when (score.zone) {
        RecoveryZone.GREEN -> SuccessGreen to "Wysokie"
        RecoveryZone.YELLOW -> AccentOrange to "Umiarkowane"
        RecoveryZone.ORANGE -> AccentOrange to "Niskie"
        RecoveryZone.RED -> ErrorRed to "Krytyczne"
    }
    // Pokazuj button gdy AI ma konkretną sugestię — łączymy 3 warunki:
    // 1. Trwały trend: 5+ dni niski w RED/ORANGE
    // 2. Pojedynczy CRITICAL factor (np. sen -23%) — silny sygnał, nie czekaj 5 dni
    // 3. ORANGE od 2+ dni (szybsza eskalacja niż YELLOW)
    val hasCriticalFactor = score.factors.any { it.severity == RecoveryFactorSeverity.CRITICAL }
    val showDeloadButton = canApplyDeload &&
        score.maturity != DataMaturity.LEARNING &&
        (
            (score.daysBelowThreshold >= 5 && (score.zone == RecoveryZone.RED || score.zone == RecoveryZone.ORANGE))
                || (hasCriticalFactor && (score.zone == RecoveryZone.ORANGE || score.zone == RecoveryZone.YELLOW))
                || (score.zone == RecoveryZone.ORANGE && score.daysBelowThreshold >= 2)
        )
    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                androidx.compose.material3.Text(
                    "Zastosować deload?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                androidx.compose.material3.Text(
                    "Recovery Score ${score.score}/100 utrzymuje się nisko od ${score.daysBelowThreshold} dni z rzędu. To trwały sygnał — deload pomoże CNS się zregenerować."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showConfirm = false
                    onApplyDeload()
                }) {
                    androidx.compose.material3.Text(
                        "Zastosuj",
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Quiet mode (LEARNING) — pierwsze 7 dni
            if (score.maturity == DataMaturity.LEARNING) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        "REGENERACJA",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = "Zamknij na dziś",
                            tint = DarkOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    "📊 Zbieram dane (${score.daysOfData}/7 dni)",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    "Algorytm uczy się twojej baseline (sen, HRV, tętno). Pierwsze 7 dni bez sugestii — dopiero potem mogę porównywać z TWOJĄ normą zamiast ogólnych progów.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                return@Card
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "RECOVERY SCORE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        "${score.score} / 100",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = accent
                    )
                }
                androidx.compose.material3.Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Zamknij na dziś",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Personal baselines info
            score.baselines.sleepHours?.let {
                androidx.compose.material3.Text(
                    "Twoja baseline: sen %.1fh".format(it) +
                        (score.baselines.restingHrBpm?.let { hr -> " • tętno %.0f bpm".format(hr) } ?: "") +
                        (score.baselines.hrvMs?.let { hrv -> " • HRV %.0f ms".format(hrv) } ?: ""),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }

            // Czynniki obniżające score
            if (score.factors.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                score.factors.take(3).forEach { f ->
                    val fc = when (f.severity) {
                        RecoveryFactorSeverity.CRITICAL -> ErrorRed
                        RecoveryFactorSeverity.WARNING -> AccentOrange
                        RecoveryFactorSeverity.INFO -> DarkOnSurfaceVariant
                    }
                    androidx.compose.material3.Text(
                        "• ${f.label} (${f.deltaText})",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = fc
                    )
                }
            } else if (score.zone == RecoveryZone.GREEN) {
                androidx.compose.material3.Text(
                    "Wszystkie metryki w normie. Trenuj zgodnie z planem.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface
                )
            }

            // Trwały trend → przycisk deloadu
            if (score.daysBelowThreshold >= 3) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    "Trend: niski score od ${score.daysBelowThreshold} dni z rzędu",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accent
                )
            }
            if (showDeloadButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text(
                        "Zastosuj deload",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TrainingLoadCard(
    load: TrainingLoad,
    onDismiss: () -> Unit,
    onApplyDeload: () -> Unit,
    onApplyIncrease: () -> Unit = {}
) {
    val (accent, label) = when (load.zone) {
        LoadZone.OPTIMAL -> SuccessGreen to "Optymalne"
        LoadZone.DELOAD_PROPER -> SuccessGreen to "Deload (prawidłowy)"
        LoadZone.DETRAINING -> AccentOrange to "Detraining"
        LoadZone.OVERREACHING -> AccentOrange to "Wysokie"
        LoadZone.RISKY -> ErrorRed to "Ryzyko kontuzji"
        LoadZone.INSUFFICIENT -> DarkOnSurfaceVariant to "Mało danych"
    }
    val showReduceButton = load.zone == LoadZone.RISKY || load.zone == LoadZone.OVERREACHING
    // v1.11.68: nie pokazuj "zwiększ obciążenie" gdy zone=DELOAD_PROPER (faza deload aktywna)
    val showIncreaseButton = load.zone == LoadZone.DETRAINING
    var showReduceConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showIncreaseConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showReduceConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReduceConfirm = false },
            title = { androidx.compose.material3.Text("Zastosować redukcję obciążenia?", fontWeight = FontWeight.Bold) },
            text = { androidx.compose.material3.Text("ACWR ${"%.2f".format(load.acwr)} sygnalizuje przeciążenie. Algorytm sugeruje -30% obciążenie. Możesz w każdej chwili przywrócić oryginalne wagi.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showReduceConfirm = false; onApplyDeload() }) {
                    androidx.compose.material3.Text("Zastosuj", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showReduceConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }

    if (showIncreaseConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showIncreaseConfirm = false },
            title = { androidx.compose.material3.Text("Zwiększyć obciążenie?", fontWeight = FontWeight.Bold) },
            text = { androidx.compose.material3.Text("ACWR ${"%.2f".format(load.acwr)} jest niskie (Detraining). Algorytm sugeruje +5% wag w aktywnym planie. Możesz w każdej chwili przywrócić oryginalne wagi.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showIncreaseConfirm = false; onApplyIncrease() }) {
                    androidx.compose.material3.Text("Zastosuj", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showIncreaseConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "OBCIĄŻENIE TRENINGOWE (ACWR)",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        "%.2f".format(load.acwr),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = accent
                    )
                }
                androidx.compose.material3.Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Zamknij na dziś",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                load.recommendation,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            if (showReduceButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showReduceConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text("Zastosuj redukcję obciążenia", fontWeight = FontWeight.Bold)
                }
            }
            if (showIncreaseButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showIncreaseConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text("Zwiększ obciążenie (+5% wag)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



@androidx.compose.runtime.Composable
private fun TrainingReadinessCard(
    readiness: TrainingReadiness,
    muscleReport: MuscleRecoveryReport?,
    canApplyDeload: Boolean,
    onApplyDeload: (pl.filebit.gymtracker.util.DeloadSeverity) -> Unit,
    onDismiss: () -> Unit
) {
    val (accent, label) = when (readiness.zone) {
        ReadinessZone.PEAK -> SuccessGreen to "PEAK — gotów na PR"
        ReadinessZone.GOOD -> SuccessGreen to "GOOD — normalnie"
        ReadinessZone.MODERATE -> AccentOrange to "MODERATE — łagodnie"
        ReadinessZone.REST -> ErrorRed to "REST — odpuść"
    }
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showActionButton = canApplyDeload && (readiness.zone == ReadinessZone.REST || readiness.zone == ReadinessZone.MODERATE)
    val severity = when (readiness.zone) {
        ReadinessZone.REST -> pl.filebit.gymtracker.util.DeloadSeverity.HIGH
        ReadinessZone.MODERATE -> pl.filebit.gymtracker.util.DeloadSeverity.LOW
        else -> pl.filebit.gymtracker.util.DeloadSeverity.LOW
    }
    val severityLabel = when (readiness.zone) {
        ReadinessZone.REST -> "deload (-30%)"
        ReadinessZone.MODERATE -> "lekki deload (-15%)"
        else -> "deload"
    }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { androidx.compose.material3.Text("Zastosować $severityLabel?", fontWeight = FontWeight.Bold) },
            text = { androidx.compose.material3.Text("Training Readiness ${readiness.score}/100. Algorytm sugeruje redukcję obciążenia. Możesz w każdej chwili przywrócić oryginalne wagi.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false; onApplyDeload(severity) }) {
                    androidx.compose.material3.Text("Zastosuj", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm = false }) {
                    androidx.compose.material3.Text("Anuluj")
                }
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "TRAINING READINESS",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        "${readiness.score} / 100",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = accent
                    )
                }
                androidx.compose.material3.Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Zamknij na dziś",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            pl.filebit.gymtracker.ui.components.AiMarkdown(
                text = readiness.recommendation,
                contentColor = DarkOnSurface
            )
            // Komponenty z procentami
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadinessMini("Sen/HRV", readiness.recoveryComponent, "50%")
                ReadinessMini("Tonaż", readiness.loadComponent, "30%")
                ReadinessMini("Mięśnie", readiness.muscleComponent, "20%")
            }

            // Przycisk akcji gdy MODERATE/REST (sugeruje deload)
            if (showActionButton) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text(
                        "Zastosuj $severityLabel",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable: muscle recovery
            if (expanded && muscleReport != null) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = accent.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    "REGENERACJA PER PARTIA",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                muscleReport.statuses.forEach { s ->
                    val (mc, _) = when (s.recommendation) {
                        TrainingRecommendation.TRAIN_HEAVY -> SuccessGreen to "✅"
                        TrainingRecommendation.TRAIN_LIGHT -> AccentOrange to "⚠️"
                        TrainingRecommendation.REST -> AccentOrange to "🔶"
                        TrainingRecommendation.AVOID -> ErrorRed to "❌"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Text(
                            s.polishName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = DarkOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { s.recoveryPct / 100f },
                            color = mc,
                            trackColor = mc.copy(alpha = 0.2f),
                            modifier = Modifier.width(80.dp).height(6.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text(
                            "${s.recoveryPct}%",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = mc,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
                if (muscleReport.todayFocus.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.Text(
                        muscleReport.todayFocus,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else if (muscleReport != null) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    "Klik → szczegóły regeneracji per partia mięśniowa",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RowScope.ReadinessMini(label: String, value: Int, weight: String) {
    Column(modifier = Modifier.weight(1f)) {
        androidx.compose.material3.Text(
            "$label ($weight)",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = DarkOnSurfaceVariant
        )
        androidx.compose.material3.Text(
            "$value",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = DarkOnSurface
        )
    }
}

/**
 * v1.15.0 — karta "AI TRENER PROPONUJE" pokazywana NA TOP Home gdy są pending decisions.
 *
 * Workflow:
 *  1. `ProactiveAiCheckWorker` (lub manual call AI) → tool `propose_periodization_action`
 *     zapisuje `PendingPeriodizationDecision` (status=PENDING).
 *  2. `HomeViewModel` obserwuje `pendingDecisionDao.observePending()` → state.pendingDecisions
 *  3. Ta karta renderuje pierwszą pending decision (najczęściej jest tylko jedna).
 *  4. [Zastosuj] → orchestrator.applyTransition + markAccepted
 *  5. [Pomiń] → markDismissed (status zmienia się, karta znika z observePending)
 *  6. [Wyjaśnij] → dialog z aiReasoning (markdown)
 *
 * Visual: AccentOrange accent (kluczowa akcja), gradient bg, AI emoji.
 */
@androidx.compose.runtime.Composable
private fun AiProposalCard(
    decision: pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var showReasoningDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val accent = pl.filebit.gymtracker.ui.theme.AccentOrange

    // v1.20.1 — parsuj aiDecisionJson, obsłuż 3 typy akcji:
    //  TRANSITION_PHASE / PROPOSE_DELOAD / propose_periodization_action → "Zmiana fazy: <X>"
    //  SCHEDULE_NEXT_CYCLE → "Plan cyklu: N tyg, M faz (cel: GOAL)"
    val (headerLabel, phaseLabel) = androidx.compose.runtime.remember(decision.aiDecisionJson) {
        runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(decision.aiDecisionJson) as kotlinx.serialization.json.JsonObject
            val action = (obj["action"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            if (action == "SCHEDULE_NEXT_CYCLE") {
                val totalWeeks = (obj["total_weeks"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val phasesJson = (obj["phases_json"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "[]"
                val phaseCount = runCatching {
                    (kotlinx.serialization.json.Json.parseToJsonElement(phasesJson) as kotlinx.serialization.json.JsonArray).size
                }.getOrDefault(0)
                val goal = (obj["goal"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                "Plan cyklu" to "$totalWeeks tyg · $phaseCount faz" + if (goal.isNotBlank()) " · $goal" else ""
            } else {
                val phaseStr = (obj["recommended_next_phase"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val phaseDisplay = if (phaseStr != null) {
                    val phase = pl.filebit.gymtracker.data.entity.MesocyclePhase.valueOf(phaseStr)
                    pl.filebit.gymtracker.ai.PeriodizationPromptHelper.phaseLabelPl(phase)
                } else "—"
                "Zmiana fazy cyklu" to phaseDisplay
            }
        }.getOrDefault("Propozycja AI" to "—")
    }

    if (showReasoningDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReasoningDialog = false },
            title = {
                androidx.compose.material3.Text(
                    "🤖 Uzasadnienie AI",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                pl.filebit.gymtracker.ui.components.AiMarkdown(
                    text = decision.aiReasoning,
                    contentColor = DarkOnSurface
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showReasoningDialog = false }) {
                    androidx.compose.material3.Text("Zamknij", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    "🤖",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "AI TRENER PROPONUJE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = accent
                    )
                    androidx.compose.material3.Text(
                        "$headerLabel: $phaseLabel",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = DarkOnSurface
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Pomiń",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Reasoning preview (max 4 linie)
            androidx.compose.material3.Text(
                text = decision.aiReasoning,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(
                "Pewność: ${(decision.confidence * 100).toInt()}%",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1.5f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text("Zastosuj", fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showReasoningDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = accent
                    ),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Text("Wyjaśnij", fontSize = 13.sp)
                }
            }
        }
    }
}

