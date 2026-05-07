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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import pl.filebit.gymtracker.ai.RecoveryFactorSeverity
import pl.filebit.gymtracker.ai.RecoveryScore
import pl.filebit.gymtracker.ai.RecoveryStatus
import pl.filebit.gymtracker.ai.RecoveryZone
import pl.filebit.gymtracker.ai.TrainingLoad
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseStatus
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
                            onDismiss = { vm.dismissDeload() }
                        )
                    }
                }
                is pl.filebit.gymtracker.data.repository.DeloadCardState.Active -> {
                    item {
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

            // Faza cyklu treningowego (computed z TrainingPhaseAnalyzer)
            state.trainingPhase?.let { phase ->
                if (phase.phase != pl.filebit.gymtracker.ai.TrainingPhase.NO_DATA) {
                    item { TrainingPhaseCard(status = phase) }
                }
            }

            // v1.7.4 WHOOP-like Recovery Score + ACWR
            state.recoveryScore?.let { score ->
                item {
                    WhoopRecoveryCard(
                        score = score,
                        canApplyDeload = vm.activePlanIdForDeload() != null,
                        onApplyDeload = {
                            vm.applyScoreBasedDeload(score) { result ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        "Plan '${result.planName}': ${result.updatedSets} setów × ${(result.factor * 100).toInt()}%"
                                    )
                                }
                            }
                        }
                    )
                }
            }
            state.trainingLoad?.let { load ->
                if (load.zone != pl.filebit.gymtracker.ai.LoadZone.INSUFFICIENT) {
                    item { TrainingLoadCard(load = load) }
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
    val pctOff = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> 20
        else -> 10
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Co to jest deload?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Deload to lżejszy tydzień regeneracyjny — zmniejszasz wagi o $pctOff% " +
                        "ale zachowujesz ten sam plan. Pozwala mięśniom i CNS odpocząć po cyklu " +
                        "intensywnego treningu, żeby wrócić silniejszym.",
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
                    "Co zrobi 'Zastosuj'",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pl.filebit.gymtracker.ui.theme.AccentOrange
                )
                Text(
                    "Wszystkie wagi w aktywnym planie zmniejszą się o $pctOff% (np. 75 kg → " +
                        "${"%.1f".format(75.0 * (1 - pctOff / 100.0))} kg). " +
                        "Po 7 dniach apka przypomni żeby wrócić do oryginalnych wag — " +
                        "snapshot zachowa je dokładnie.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Alternatywa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pl.filebit.gymtracker.ui.theme.AccentOrange
                )
                Text(
                    "Możesz też zrobić tydzień całkowitej przerwy bez treningu — efekt podobny. " +
                        "Albo zignorować — wrócę z sugestią za tydzień jeśli warunki nadal aktualne.",
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
        // "Cześć[, Maciej]" — imię w żółci
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (displayName.isBlank()) "Cześć" else "Cześć,",
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
                    color = AccentOrange
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
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
    onDismiss: () -> Unit
) {
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
    val severityLabel = when (recommendation.severity) {
        pl.filebit.gymtracker.util.DeloadSeverity.HIGH -> "MOCNY SYGNAŁ"
        pl.filebit.gymtracker.util.DeloadSeverity.MED -> "DELOAD ZALECANY"
        pl.filebit.gymtracker.util.DeloadSeverity.LOW -> "ROZWAŻ DELOAD"
    }
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = bgAlpha), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    severityLabel,
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
                recommendation.reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = onApply,
                    enabled = canApply,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = color,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("✓ Zastosuj", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            if (!canApply) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Brak aktywnego planu — Zastosuj wymaga planu z wagami.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
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
                    Text(
                        "Plan tyg.",
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
private fun TrainingPhaseCard(status: TrainingPhaseStatus) {
    val (accent, emoji) = when (status.phase) {
        TrainingPhase.ACCUMULATION -> SuccessGreen to "📈"
        TrainingPhase.INTENSIFICATION -> AccentOrange to "⚡"
        TrainingPhase.DELOAD -> DarkOnSurfaceVariant to "🛌"
        TrainingPhase.NEEDS_DELOAD -> ErrorRed to "⚠️"
        TrainingPhase.NO_DATA -> DarkOnSurfaceVariant to "❓"
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
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                status.recommendation,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
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
            androidx.compose.material3.Text(
                insight.recommendation,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
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
    onApplyDeload: () -> Unit
) {
    val (accent, label) = when (score.zone) {
        RecoveryZone.GREEN -> SuccessGreen to "Wysokie"
        RecoveryZone.YELLOW -> AccentOrange to "Umiarkowane"
        RecoveryZone.ORANGE -> AccentOrange to "Niskie"
        RecoveryZone.RED -> ErrorRed to "Krytyczne"
    }
    val showDeloadButton = canApplyDeload &&
        score.maturity != DataMaturity.LEARNING &&
        score.daysBelowThreshold >= 5 &&
        (score.zone == RecoveryZone.RED || score.zone == RecoveryZone.ORANGE)
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
                androidx.compose.material3.Text(
                    "REGENERACJA",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
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
private fun TrainingLoadCard(load: TrainingLoad) {
    val (accent, label) = when (load.zone) {
        LoadZone.OPTIMAL -> SuccessGreen to "Optymalne"
        LoadZone.DETRAINING -> DarkOnSurfaceVariant to "Detraining"
        LoadZone.OVERREACHING -> AccentOrange to "Wysokie"
        LoadZone.RISKY -> ErrorRed to "Ryzyko kontuzji"
        LoadZone.INSUFFICIENT -> DarkOnSurfaceVariant to "Mało danych"
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
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                load.recommendation,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
        }
    }
}

