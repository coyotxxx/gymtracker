package pl.filebit.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import pl.filebit.gymtracker.ai.HealthInsight
import pl.filebit.gymtracker.ai.HealthInsightAnalyzer
import pl.filebit.gymtracker.ai.MuscleRecoveryAnalyzer
import pl.filebit.gymtracker.ai.MuscleRecoveryReport
import pl.filebit.gymtracker.ai.RecoveryScore
import pl.filebit.gymtracker.ai.RecoveryScoreCalculator
import pl.filebit.gymtracker.ai.RecoveryStatus
import pl.filebit.gymtracker.ai.TrainingLoad
import pl.filebit.gymtracker.ai.TrainingLoadAnalyzer
import pl.filebit.gymtracker.ai.TrainingReadiness
import pl.filebit.gymtracker.ai.TrainingReadinessAnalyzer
import pl.filebit.gymtracker.ai.ReadinessZone
import pl.filebit.gymtracker.ai.WorkoutAdjustment
import pl.filebit.gymtracker.util.DeloadSeverity
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseAnalyzer
import pl.filebit.gymtracker.ai.TrainingPhaseStatus
import pl.filebit.gymtracker.data.db.dao.PendingPeriodizationDecisionDao
import pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.repository.PeriodizationOrchestrator
import pl.filebit.gymtracker.data.repository.PeriodizationState
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class HomeUiState(
    val displayName: String = "",
    val activeWorkout: Workout? = null,
    val activePlanName: String = "",
    val todaysPlan: TrainingPlan? = null,
    val todaysPlanExerciseCount: Int = 0,
    val recentWorkouts: List<RecentWorkoutItem> = emptyList(),
    val streakWeeks: Int = 0,
    val streakBest: Int = 0,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 3,
    val deloadCard: pl.filebit.gymtracker.data.repository.DeloadCardState =
        pl.filebit.gymtracker.data.repository.DeloadCardState.None,
    val nextPlannedDay: NextPlannedDay? = null,
    val activeWorkoutDurationMin: Int = 0,
    val activeWorkoutProgressPct: Int = 0,            // % ukończonych setów (Stan C)
    val activeWorkoutCurrentSetLabel: String = "",    // "Seria 8 z 19"
    val weekSlots: Map<Int, List<pl.filebit.gymtracker.util.ScheduleSlot>> = emptyMap(),
    val plansById: Map<Long, pl.filebit.gymtracker.data.entity.TrainingPlan> = emptyMap(),
    val completedDaysThisWeek: Set<Int> = emptySet(),  // dni Pn-Nd z ukończonym treningiem
    val trainingPhase: TrainingPhaseStatus? = null,    // null = jeszcze nie obliczone
    val healthInsight: HealthInsight? = null,           // null = jeszcze nie obliczone (Health Connect)
    val recoveryScore: RecoveryScore? = null,           // v1.7.4 — WHOOP-like 0-100
    val trainingLoad: TrainingLoad? = null,             // v1.7.4 — ACWR
    val recoveryCardDismissed: Boolean = false,          // v1.7.5 — user zamknął kartę na dziś
    val trainingReadiness: TrainingReadiness? = null,   // v1.9.0 — kompozyt
    val muscleRecovery: MuscleRecoveryReport? = null,   // v1.9.0 — per partia
    val dismissedCards: Set<String> = emptySet(),        // v1.10 — generyczny dismiss per cardKey
    val periodizationState: PeriodizationState = PeriodizationState.NoData,  // v1.14.0 — datowany mesocykl
    val pendingDecisions: List<PendingPeriodizationDecision> = emptyList(),    // v1.15.0 — AI propozycje
    // v1.24.26: osiągnięcie celu wagi — karta proaktywna gdy waga w target ≥7 dni.
    val goalAchievement: pl.filebit.gymtracker.data.repository.GoalAchievementResult? = null
)

data class NextPlannedDay(
    val planId: Long,
    val planName: String,
    val dayOfWeek: Int,         // 1=Pn..7=Nd
    val daysFromToday: Int,     // 1=jutro, 7=za tydzień
    val exerciseCount: Int
)

data class RecentWorkoutItem(
    val workout: Workout,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val exerciseCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val deloadService: pl.filebit.gymtracker.data.repository.DeloadService,
    private val loadIncreaseService: pl.filebit.gymtracker.data.repository.LoadIncreaseService,
    private val phaseAnalyzer: TrainingPhaseAnalyzer,
    private val healthAnalyzer: HealthInsightAnalyzer,
    private val recoveryScoreCalculator: RecoveryScoreCalculator,
    private val trainingLoadAnalyzer: TrainingLoadAnalyzer,
    private val recoveryCardPrefs: pl.filebit.gymtracker.data.repository.RecoveryCardPrefs,
    private val dismissedCardsPrefs: pl.filebit.gymtracker.data.repository.DismissedCardsPrefs,
    private val muscleRecoveryAnalyzer: MuscleRecoveryAnalyzer,
    private val readinessAnalyzer: TrainingReadinessAnalyzer,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService,
    private val mesoDao: TrainingMesocycleDao,
    private val periodizationOrchestrator: PeriodizationOrchestrator,
    private val pendingDecisionDao: PendingPeriodizationDecisionDao,  // v1.15.0
    private val homeAlertNotifier: pl.filebit.gymtracker.service.HomeAlertNotifier,  // v1.24.0
    private val goalAchievementService: pl.filebit.gymtracker.data.repository.GoalAchievementService,  // v1.24.26
    private val goalRepo: pl.filebit.gymtracker.data.repository.GoalRepository,  // v1.24.26
    private val dietProfileRepo: pl.filebit.gymtracker.data.repository.UserDietProfileRepository  // v1.24.26
) : ViewModel() {

    init {
        // v1.14.0: utrzymaj aktywny mesocykl przy wejściu na Home (idempotentne).
        // Tworzy initial cykl gdy brak + updateuje weekInPhase.
        viewModelScope.launch {
            runCatching { periodizationOrchestrator.pulse() }
        }
    }

    private val recoveryCardRefresh = kotlinx.coroutines.flow.MutableStateFlow(0L)

    // Trigger do wymuszania rebuild state po akcjach deload (Apply/Dismiss/Restore/Cancel).
    // Bez tego SharedPrefs się zmienia ale combine() nie wie o tym — kafel zostaje na ekranie.
    private val deloadRefresh = kotlinx.coroutines.flow.MutableStateFlow(0L)

    // v1.14.0/v1.15.0: combine has typed overloads up to arity 5. Wrapping 4 flows w jedno żeby
    // zostać w typowanym combine (zamiast vararg z Flow<*> i castów).
    private data class SecondaryFlows(
        val deloadTrigger: Long,
        val recoveryTrigger: Long,
        val activeMeso: TrainingMesocycle?,
        val pendingDecisions: List<PendingPeriodizationDecision>
    )

    private val secondaryFlows = combine(
        deloadRefresh,
        recoveryCardRefresh,
        mesoDao.observeActive(),
        pendingDecisionDao.observePending()
    ) { d, rc, m, pd -> SecondaryFlows(d, rc, m, pd) }

    val state: StateFlow<HomeUiState> = combine(
        workoutRepo.observeActive(),
        workoutRepo.observeRecent(4),
        planRepo.observeAllPlans(),
        secondaryFlows
    ) { active, recent, plans, secondary ->
        val activeMeso: TrainingMesocycle? = secondary.activeMeso
        val pendingDecisions: List<PendingPeriodizationDecision> = secondary.pendingDecisions
        val isoDay = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        // Effective schedule = oryginalne dni planów + overrides per-tygodniowe
        val schedule = runCatching { planRepo.getEffectiveScheduleForCurrentWeek() }.getOrDefault(emptyMap())
        // v1.24.12: aktywny plan z bazy (TrainingPlan.isActive). Gdy istnieje,
        // bierzemy slot WYŁĄCZNIE z tego planu — żeby przy 2+ planach Home nie
        // chwytał innego planu (filozofia: jeden user, jeden aktywny plan).
        // Bez aktywnego planu (np. po wipe) fallback do firstOrNull żeby nie
        // pokazać pustego ekranu.
        val activePlanFromDb = plans.firstOrNull { it.isActive }
        fun pickSlot(slots: List<pl.filebit.gymtracker.util.ScheduleSlot>?): pl.filebit.gymtracker.util.ScheduleSlot? {
            if (slots.isNullOrEmpty()) return null
            return if (activePlanFromDb != null) {
                slots.firstOrNull { it.planId == activePlanFromDb.id }
            } else {
                slots.first()
            }
        }
        var todaysPlan: pl.filebit.gymtracker.data.entity.TrainingPlan? = null
        var todaysCount = 0
        var todaysSourceDay = isoDay
        pickSlot(schedule[isoDay])?.let { slot ->
            val plan = plans.firstOrNull { it.id == slot.planId }
            if (plan != null) {
                val exes = planRepo.getPlanExercisesForDay(plan.id, slot.sourceDayOfWeek)
                if (exes.isNotEmpty()) {
                    todaysPlan = plan
                    todaysCount = exes.size
                    todaysSourceDay = slot.sourceDayOfWeek
                }
            }
        }
        val items = recent.map { w ->
            val sets = workoutRepo.getSetsForWorkout(w.id)
            RecentWorkoutItem(
                workout = w,
                totalSets = sets.size,
                totalVolumeKg = sets.sumOf { it.reps * it.weightKg },
                exerciseCount = sets.map { it.exerciseId }.distinct().size
            )
        }
        // Streak + week progress (best-effort, błędy ignorujemy)
        val streak = runCatching { statsRepo.streakInfo() }.getOrNull()
        val profile = runCatching { profileRepo.get() }.getOrNull()
        val weeklyTarget = (profile?.daysPerWeek ?: 3).coerceAtLeast(1)
        val weekProgress = runCatching { statsRepo.weekProgress(weeklyTarget) }.getOrNull()
        val activePlanName = active?.fromPlanId?.let { planId ->
            plans.firstOrNull { it.id == planId }?.name.orEmpty()
        }.orEmpty()

        val deloadCard = runCatching { deloadService.cardState() }
            .getOrNull() ?: pl.filebit.gymtracker.data.repository.DeloadCardState.None
        // v1.24.0: wyślij notyfikację gdy alert się pojawia / zmienia
        // (anti-spam przez hash w SharedPreferences — nie powtórzymy tej samej)
        runCatching { homeAlertNotifier.maybeNotify(deloadCard) }

        // v1.11.46: snapshot RAZ dla 3 analyzerów (zero N+1)
        val analyzerSnapshot = runCatching { statsCacheService.snapshot() }
            .getOrDefault(pl.filebit.gymtracker.data.repository.StatsSnapshot.EMPTY)

        // Faza cyklu treningowego.
        // v1.24.45 fix E2E Bug #2: priorytet aktywny mesocykl (Plan cyklu) > heurystyka analyzera.
        // Bez tego Home pokazywał "Intensyfikacja 1/3" gdy Plan cyklu miał "Akumulacja 3/3"
        // — dwa różne źródła prawdy. Po backfillu mesocycle istnieje, więc prawda jest tam.
        val mesoBasedPhase = secondary.activeMeso?.let { meso ->
            val mappedPhase = when (meso.phase) {
                pl.filebit.gymtracker.data.entity.MesocyclePhase.ACCUMULATION -> TrainingPhase.ACCUMULATION
                pl.filebit.gymtracker.data.entity.MesocyclePhase.INTENSIFICATION -> TrainingPhase.INTENSIFICATION
                pl.filebit.gymtracker.data.entity.MesocyclePhase.DELOAD -> TrainingPhase.DELOAD
                // PEAKING/RECOVERY — mapuj na INTENSIFICATION/DELOAD jako najbliższe odpowiedniki UI
                pl.filebit.gymtracker.data.entity.MesocyclePhase.PEAKING -> TrainingPhase.INTENSIFICATION
                pl.filebit.gymtracker.data.entity.MesocyclePhase.RECOVERY -> TrainingPhase.DELOAD
            }
            // polishLabel używa weeksSinceLastDeload jako tygodnia w cyklu —
            // dla INTENSIFICATION odejmuje 3 (akumulacja=1-3, intens=4-6).
            val weeksField = when (meso.phase) {
                pl.filebit.gymtracker.data.entity.MesocyclePhase.INTENSIFICATION -> meso.weekInPhase + 3
                pl.filebit.gymtracker.data.entity.MesocyclePhase.PEAKING -> meso.weekInPhase + 3
                else -> meso.weekInPhase
            }
            pl.filebit.gymtracker.ai.TrainingPhaseStatus(
                phase = mappedPhase,
                weeksSinceLastDeload = weeksField,
                recommendation = "Faza z aktywnego mesocyklu (zobacz Plan cyklu)."
            )
        }
        val heuristicPhase = runCatching { phaseAnalyzer.analyzeWithSnapshot(analyzerSnapshot) }.getOrNull()
        val trainingPhase = mesoBasedPhase ?: heuristicPhase
        // v1.11.68: faza cyklu jest passthrough do innych analyzerów - żeby ich
        // konkluzje były spójne (np. ACWR <0.8 podczas deloadu = OK, nie "dodaj").
        val currentPhase = trainingPhase?.phase ?: TrainingPhase.NO_DATA
        // Regeneracja — sen + HRV (v1.11.69: phase-aware - nie sugeruje deload gdy juz deload)
        val healthInsight = runCatching { healthAnalyzer.analyze(currentPhase) }.getOrNull()
        // v1.7.4 WHOOP-like Recovery Score 0-100 z personal baseline (nie używa StatsSnapshot)
        val recoveryScore = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        // v1.7.4 ACWR — Acute:Chronic Workload Ratio (v1.11.68: phase-aware,
        // v1.24.2: hasGlobalAlert — żeby Sweet spot nie myliło gdy aktywny inny alert)
        val hasGlobalAlert = deloadCard !is pl.filebit.gymtracker.data.repository.DeloadCardState.None &&
            deloadCard !is pl.filebit.gymtracker.data.repository.DeloadCardState.Active
        val trainingLoad = runCatching {
            trainingLoadAnalyzer.analyzeWithSnapshot(analyzerSnapshot, currentPhase, hasGlobalAlert)
        }.getOrNull()
        // v1.9.0 Recovery per partia + Training Readiness (v1.11.68: phase-aware)
        val muscleRecovery = runCatching { muscleRecoveryAnalyzer.analyzeWithSnapshot(analyzerSnapshot) }.getOrNull()
        val rawReadiness = runCatching { readinessAnalyzer.analyze(currentPhase) }.getOrNull()
        // v1.11.69: gdy Readiness=GOOD/PEAK ale HealthInsight=REST/DELOAD_TODAY,
        // dopisz ostrzezenie. Powod: Readiness uzywa 28d baseline, HealthInsight ma
        // swieze dane (1-2 noce) - swieze dane powinny byc widoczne nawet przy wysokim score.
        val trainingReadiness = if (
            rawReadiness != null && healthInsight != null &&
            (rawReadiness.zone == ReadinessZone.PEAK || rawReadiness.zone == ReadinessZone.GOOD) &&
            (healthInsight.workoutAdjustment == WorkoutAdjustment.REST_RECOMMENDED ||
                healthInsight.workoutAdjustment == WorkoutAdjustment.DELOAD_TODAY)
        ) {
            rawReadiness.copy(
                recommendation = rawReadiness.recommendation +
                    "\n\n⚠ ALE świeże dane (sen/HRV) sygnalizują niską regenerację — dziś lżej niż score sugeruje."
            )
        } else rawReadiness

        // Stan B: Next planned day — używa effective schedule (z overrides)
        val nextPlannedDay: NextPlannedDay? = if (todaysPlan == null) {
            var found: NextPlannedDay? = null
            for (offset in 1..7) {
                val targetDay = ((isoDay - 1 + offset) % 7) + 1
                val slot = pickSlot(schedule[targetDay])
                if (slot != null) {
                    val plan = plans.firstOrNull { it.id == slot.planId }
                    if (plan != null) {
                        val exes = planRepo.getPlanExercisesForDay(plan.id, slot.sourceDayOfWeek)
                        if (exes.isNotEmpty()) {
                            found = NextPlannedDay(
                                planId = plan.id,
                                planName = plan.name,
                                dayOfWeek = targetDay,
                                daysFromToday = offset,
                                exerciseCount = exes.size
                            )
                            break
                        }
                    }
                }
            }
            found
        } else null

        // Dni w tym tygodniu z ukończonym treningiem
        val weekStart = pl.filebit.gymtracker.util.currentWeekStartMillis()
        val weekEnd = weekStart + 7L * 86_400_000L
        val completedDays: Set<Int> = recent
            .filter { it.finishedAt != null && it.startedAt in weekStart until weekEnd }
            .map { w ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = w.startedAt }
                ((c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            }.toSet()

        // v1.14.0: mapuj aktywny mesocykl na PeriodizationState (bez wywoływania orchestrator.pulse —
        // to robi się w init {} oraz w GymTrackerApp.onCreate, tutaj tylko read-only).
        val now = System.currentTimeMillis()
        val periodizationState: PeriodizationState = activeMeso?.let { m ->
            if (now >= m.plannedEndDateMs) {
                // plannedEnd minął — pokażemy "transition due" gdy w pulse() zostanie
                // wygenerowany proposal. Na razie pokazuję Active z daysRemaining=0.
                PeriodizationState.Active(
                    meso = m,
                    daysElapsed = m.daysSinceStart(now),
                    daysRemaining = 0
                )
            } else {
                PeriodizationState.Active(
                    meso = m,
                    daysElapsed = m.daysSinceStart(now),
                    daysRemaining = m.daysRemaining(now)
                )
            }
        } ?: PeriodizationState.NoData

        // Stan C: dla aktywnego treningu — czas trwania + progress setów
        val durationMin: Int
        val progressPct: Int
        val currentSetLabel: String
        if (active != null) {
            val now = System.currentTimeMillis()
            val elapsed = (now - active.startedAt).coerceAtLeast(0)
            durationMin = (elapsed / 60_000).toInt()
            val sets = workoutRepo.getSetsForWorkout(active.id)
            val total = sets.size.coerceAtLeast(1)
            val done = sets.count { it.isCompleted }
            progressPct = (done * 100 / total).coerceIn(0, 100)
            currentSetLabel = if (sets.isNotEmpty()) "Seria ${(done + 1).coerceAtMost(total)} z $total"
            else "Trening rozpoczęty"
        } else {
            durationMin = 0
            progressPct = 0
            currentSetLabel = ""
        }

        // v1.24.26: detekcja osiągnięcia celu wagi. Pokazujemy gdy user
        // trzyma target ≥7 dni i nie zareagował już (Goal.achieved=false).
        val goalAchievement = runCatching {
            profile?.let { p ->
                val result = goalAchievementService.checkAchieved(p)
                if (result != null && result.isReached &&
                    !goalAchievementService.isGoalDismissedByUser(p)) result else null
            }
        }.getOrNull()

        HomeUiState(
            displayName = profile?.displayName.orEmpty(),
            activeWorkout = active,
            activePlanName = activePlanName,
            todaysPlan = todaysPlan,
            todaysPlanExerciseCount = todaysCount,
            recentWorkouts = items,
            streakWeeks = streak?.current ?: 0,
            streakBest = streak?.best ?: 0,
            workoutsThisWeek = weekProgress?.current ?: 0,
            weeklyTarget = weeklyTarget,
            deloadCard = deloadCard,
            nextPlannedDay = nextPlannedDay,
            activeWorkoutDurationMin = durationMin,
            activeWorkoutProgressPct = progressPct,
            activeWorkoutCurrentSetLabel = currentSetLabel,
            weekSlots = schedule,
            plansById = plans.associateBy { it.id },
            completedDaysThisWeek = completedDays,
            trainingPhase = trainingPhase,
            healthInsight = healthInsight,
            recoveryScore = recoveryScore,
            trainingLoad = trainingLoad,
            recoveryCardDismissed = recoveryCardPrefs.isDismissedForToday(),
            trainingReadiness = trainingReadiness,
            muscleRecovery = muscleRecovery,
            dismissedCards = setOf(
                pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.RECOVERY,
                pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.PHASE,
                pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.READINESS,
                pl.filebit.gymtracker.data.repository.DismissedCardsPrefs.CardKeys.LOAD
            ).filter { dismissedCardsPrefs.isDismissedToday(it) }.toSet(),
            periodizationState = periodizationState,
            pendingDecisions = pendingDecisions,  // v1.15.0
            goalAchievement = goalAchievement  // v1.24.26
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    // ===== DELOAD HANDLERS =====

    /** Aplikuje deload do aktywnego planu (todaysPlan lub nextPlannedDay). Zwraca info dla UI. */
    fun applyDeload(
        severity: pl.filebit.gymtracker.util.DeloadSeverity,
        onApplied: (pl.filebit.gymtracker.data.repository.DeloadService.ApplyResult) -> Unit
    ) {
        val planId = state.value.todaysPlan?.id
            ?: state.value.nextPlannedDay?.planId
            ?: return
        viewModelScope.launch {
            val result = deloadService.apply(planId, severity)
            deloadRefresh.value = System.currentTimeMillis()
            onApplied(result)
        }
    }

    fun dismissDeload() {
        viewModelScope.launch {
            deloadService.dismiss()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    // === v1.24.26: AKCJE PO OSIĄGNIĘCIU CELU WAGI ===
    // User osiągnął target (np. 87→75 kg). System pyta "co dalej?".
    // Filozofia: nie zostawiaj usera w nieokreślonym stanie po sukcesie.

    /** Utrzymuj wagę — kontynuuj na tej co teraz. */
    fun onGoalAchievedMaintain() {
        viewModelScope.launch {
            val profile = profileRepo.get()
            val currentWeight = profile.bodyweightKg ?: 75.0
            profileRepo.save(
                profile.copy(
                    weightGoalType = pl.filebit.gymtracker.data.entity.WeightGoalType.MAINTAIN,
                    targetWeightKg = currentWeight
                )
            )
            dietProfileRepo.get()?.let { dp ->
                dietProfileRepo.save(
                    dp.copy(
                        goalType = pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN,
                        paceKgPerWeek = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            markAchievedGoalsDone()
            deloadRefresh.value = System.currentTimeMillis()  // force state recompute
        }
    }

    /** Przejdź na masę — wpisz nowy target wyższy + zmień goalType BULK. */
    fun onGoalAchievedSwitchToBulk(newTargetKg: Double) {
        viewModelScope.launch {
            val profile = profileRepo.get()
            profileRepo.save(
                profile.copy(
                    weightGoalType = pl.filebit.gymtracker.data.entity.WeightGoalType.BULK,
                    targetWeightKg = newTargetKg
                )
            )
            dietProfileRepo.get()?.let { dp ->
                dietProfileRepo.save(
                    dp.copy(
                        goalType = pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN,
                        paceKgPerWeek = 0.3,  // lean bulk default
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            markAchievedGoalsDone()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    /** Schudnij dalej — nowy niższy target. */
    fun onGoalAchievedContinueCut(newTargetKg: Double) {
        viewModelScope.launch {
            val profile = profileRepo.get()
            profileRepo.save(profile.copy(targetWeightKg = newTargetKg))
            markAchievedGoalsDone()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    /** X close — user nie chce decydować teraz, ukryj kartę. */
    fun onGoalAchievedDismiss() {
        viewModelScope.launch {
            markAchievedGoalsDone()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    private suspend fun markAchievedGoalsDone() {
        val profile = profileRepo.get()
        val relevantType = when (profile.weightGoalType) {
            pl.filebit.gymtracker.data.entity.WeightGoalType.CUT ->
                pl.filebit.gymtracker.data.entity.GoalType.LOSE_WEIGHT
            pl.filebit.gymtracker.data.entity.WeightGoalType.BULK ->
                pl.filebit.gymtracker.data.entity.GoalType.GAIN_MASS
            else -> return
        }
        val now = System.currentTimeMillis()
        goalRepo.observeAll().first()
            .filter { it.type == relevantType && !it.achieved }
            .forEach { g ->
                goalRepo.upsert(g.copy(achieved = true, achievedAt = now))
            }
    }

    /** v1.24.0: zamknięcie konkretnego typu alertu — nie blokuje innych typów. */
    fun dismissAlert(type: pl.filebit.gymtracker.data.repository.AlertType) {
        viewModelScope.launch {
            deloadService.dismiss(type)
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    fun restoreDeload(onRestored: (pl.filebit.gymtracker.data.repository.DeloadService.RestoreResult) -> Unit) {
        viewModelScope.launch {
            val result = deloadService.restore()
            deloadRefresh.value = System.currentTimeMillis()
            onRestored(result)
        }
    }

    fun cancelDeloadWithoutRestore() {
        viewModelScope.launch {
            deloadService.cancelWithoutRestore()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    // ===== LOAD INCREASE HANDLERS (ACWR DETRAINING) =====

    /** Aplikuje zwiększenie obciążenia +5% do aktywnego planu. */
    fun applyLoadIncrease(
        onApplied: (pl.filebit.gymtracker.data.repository.LoadIncreaseService.ApplyResult?) -> Unit
    ) {
        val planId = state.value.todaysPlan?.id
            ?: state.value.nextPlannedDay?.planId
            ?: return
        viewModelScope.launch {
            val result = loadIncreaseService.apply(planId, factor = 1.05)
            deloadRefresh.value = System.currentTimeMillis()
            onApplied(result)
        }
    }

    fun dismissLoadIncrease() {
        viewModelScope.launch {
            loadIncreaseService.dismiss()
            deloadRefresh.value = System.currentTimeMillis()
        }
    }

    fun restoreLoadIncrease(
        onRestored: (pl.filebit.gymtracker.data.repository.LoadIncreaseService.RestoreResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = loadIncreaseService.restore()
            deloadRefresh.value = System.currentTimeMillis()
            onRestored(result)
        }
    }

    /**
     * Aplikuje deload bazując na statusie regeneracji (HealthInsight).
     * Mapowanie:
     *  - LIGHT_VOLUME (sen 5-6h) → DeloadSeverity.LOW (-15%)
     *  - DELOAD_TODAY (HRV -15%) → DeloadSeverity.HIGH (-30%)
     *  - REST_RECOMMENDED (sen <5h ≥2 noce) → DeloadSeverity.HIGH (-30%) z odrobiną
     *    sugestii rest, ale plan modyfikujemy "na maks redukcji" — user sam zdecyduje
     *    czy w ogóle pójdzie trenować.
     */
    fun applyHealthBasedDeload(
        adjustment: WorkoutAdjustment,
        onApplied: (pl.filebit.gymtracker.data.repository.DeloadService.ApplyResult) -> Unit
    ) {
        val planId = state.value.todaysPlan?.id
            ?: state.value.nextPlannedDay?.planId
            ?: return
        val severity = when (adjustment) {
            WorkoutAdjustment.LIGHT_VOLUME -> DeloadSeverity.LOW
            WorkoutAdjustment.DELOAD_TODAY -> DeloadSeverity.HIGH
            WorkoutAdjustment.REST_RECOMMENDED -> DeloadSeverity.HIGH
            WorkoutAdjustment.AS_PLANNED -> return  // brak akcji — plan OK
        }
        viewModelScope.launch {
            val result = deloadService.apply(planId, severity)
            deloadRefresh.value = System.currentTimeMillis()
            onApplied(result)
        }
    }

    /** v1.7.5 — user zamyka kartę REGENERACJA na bieżący dzień. Następny dzień wraca. */
    fun dismissRecoveryCard() {
        recoveryCardPrefs.dismissForToday()
        recoveryCardRefresh.value = System.currentTimeMillis()
    }

    /** v1.10 — generyczny dismiss dla dowolnej karty alertowej (Phase/Readiness/Load). */
    fun dismissCard(cardKey: String) {
        dismissedCardsPrefs.dismissToday(cardKey)
        recoveryCardRefresh.value = System.currentTimeMillis()
    }

    /**
     * v1.14.0 — zakończ bieżący deload wcześniej.
     * Wywoływane z przycisku "Zakończ wcześniej" w TrainingPhaseCard.
     * Zamyka deload + tworzy nowy cykl ACCUMULATION. mesoDao.observeActive emituje
     * automatycznie, UI się odświeży.
     */
    fun endDeloadEarly(onDone: () -> Unit = {}) {
        val mesoId = (state.value.periodizationState as? PeriodizationState.Active)?.meso?.id ?: return
        viewModelScope.launch {
            runCatching { periodizationOrchestrator.endDeloadEarly(mesoId) }
            onDone()
        }
    }

    /**
     * v1.14.0 — przedłuż bieżącą fazę o N tygodni.
     * Wywoływane z przycisku "Przedłuż o tydzień" w TrainingPhaseCard.
     */
    fun extendCurrentMesoPhase(addWeeks: Int = 1, onDone: () -> Unit = {}) {
        val mesoId = (state.value.periodizationState as? PeriodizationState.Active)?.meso?.id ?: return
        viewModelScope.launch {
            runCatching { periodizationOrchestrator.extendCurrentPhase(mesoId, addWeeks) }
            onDone()
        }
    }

    /**
     * v1.15.0 — user kliknął [Zastosuj] w karcie "AI TRENER PROPONUJE".
     * Parsuje AI decision JSON, wykonuje orchestrator.applyTransition + markAccepted.
     */
    fun acceptPendingDecision(decisionId: Long, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val decision = pendingDecisionDao.getById(decisionId)
                    ?: throw IllegalStateException("Decyzja nie istnieje")

                // Parsuj AI decision JSON żeby wyciągnąć parametry
                val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(decision.aiDecisionJson) as? kotlinx.serialization.json.JsonObject
                    ?: throw IllegalStateException("Niepoprawny JSON decyzji AI")

                val phaseStr = (jsonObj["recommended_next_phase"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: throw IllegalStateException("Brak recommended_next_phase")
                val phase = MesocyclePhase.valueOf(phaseStr)

                val startDateStr = (jsonObj["planned_start_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: throw IllegalStateException("Brak planned_start_date")
                val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val plannedStartMs = df.parse(startDateStr)?.time
                    ?: throw IllegalStateException("Niepoprawna data: $startDateStr")

                val durationWeeks = (jsonObj["planned_duration_weeks"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 3

                // fromPhase = obecna faza aktywnego mesocyklu (z bazy, nie z JSON-a)
                val currentMeso = mesoDao.getActive()
                    ?: throw IllegalStateException("Brak aktywnego mesocyklu")

                val proposal = pl.filebit.gymtracker.data.repository.TransitionProposal(
                    fromPhase = currentMeso.phase,
                    recommendedNext = phase,
                    plannedStartDate = plannedStartMs,
                    plannedDurationWeeks = durationWeeks,
                    reasoning = decision.aiReasoning,
                    confidence = decision.confidence
                )
                periodizationOrchestrator.applyTransition(
                    proposal = proposal,
                    currentMesoId = decision.currentMesoId,
                    aiDecision = decision.aiReasoning.take(200)
                )
                pendingDecisionDao.markAccepted(decisionId, System.currentTimeMillis())
                onDone("Faza zmieniona na ${pl.filebit.gymtracker.ai.PeriodizationPromptHelper.phaseLabelPl(phase)}")
            }.onFailure {
                onDone("Błąd: ${it.message}")
            }
        }
    }

    /**
     * v1.15.0 — user kliknął X / "Pomiń" w karcie. Nie zmienia cyklu, tylko ukrywa propozycję.
     */
    fun dismissPendingDecision(decisionId: Long) {
        viewModelScope.launch {
            pendingDecisionDao.markDismissed(decisionId, System.currentTimeMillis())
        }
    }

    /**
     * v1.14.0 — unified check czy mogę aplikować deload TERAZ.
     * Naprawia inkonsystencję: TrainingReadinessCard (z `phaseIsDeload`),
     * WhoopRecoveryCard i TrainingLoadCard miały różne checki. Teraz jedno źródło prawdy.
     */
    fun canApplyDeloadNow(): Boolean {
        val phase = state.value.trainingPhase?.phase
        val mesoState = state.value.periodizationState
        val mesoPhase = (mesoState as? PeriodizationState.Active)?.meso?.phase
        return activePlanIdForDeload() != null &&
                phase != TrainingPhase.DELOAD &&
                phase != TrainingPhase.NEEDS_DELOAD &&
                mesoPhase != MesocyclePhase.DELOAD
    }

    /**
     * v1.7.4 — deload na bazie Recovery Score (jeśli trwale niskie).
     * Severity bazuje na zone (RED/ORANGE/YELLOW).
     */
    fun applyScoreBasedDeload(
        score: RecoveryScore,
        onApplied: (pl.filebit.gymtracker.data.repository.DeloadService.ApplyResult) -> Unit
    ) {
        val planId = state.value.todaysPlan?.id
            ?: state.value.nextPlannedDay?.planId
            ?: return
        val severity = when (score.zone) {
            pl.filebit.gymtracker.ai.RecoveryZone.RED -> DeloadSeverity.HIGH
            pl.filebit.gymtracker.ai.RecoveryZone.ORANGE -> DeloadSeverity.MED
            pl.filebit.gymtracker.ai.RecoveryZone.YELLOW -> DeloadSeverity.LOW
            pl.filebit.gymtracker.ai.RecoveryZone.GREEN -> return
        }
        viewModelScope.launch {
            val result = deloadService.apply(planId, severity)
            deloadRefresh.value = System.currentTimeMillis()
            onApplied(result)
        }
    }

    /** Plan id do podglądu wag w dialogu confirm Apply. */
    fun activePlanIdForDeload(): Long? =
        state.value.todaysPlan?.id ?: state.value.nextPlannedDay?.planId

    fun startWorkoutAdhoc(onReady: () -> Unit) {
        viewModelScope.launch {
            workoutRepo.startOrResume(fromPlanId = null)
            onReady()
        }
    }

    fun continueActiveWorkout(onCoach: () -> Unit, onAdhoc: () -> Unit) {
        viewModelScope.launch {
            val w = workoutRepo.startOrResume()
            if (w.fromPlanId != null) onCoach() else onAdhoc()
        }
    }

    /**
     * Bottom-sheet: przesuń trening na konkretny dzień bieżącego tygodnia.
     */
    fun postponeTraining(planId: Long, originalDay: Int, targetDay: Int) {
        viewModelScope.launch {
            planRepo.postponeTraining(planId, originalDay, targetDay)
        }
    }

    fun skipTraining(planId: Long, originalDay: Int) {
        viewModelScope.launch {
            planRepo.postponeTraining(planId, originalDay, pl.filebit.gymtracker.data.entity.WeeklyPlanOverride.SKIPPED)
        }
    }

    fun clearOverride(planId: Long, originalDay: Int) {
        viewModelScope.launch {
            planRepo.clearTrainingOverride(planId, originalDay)
        }
    }

    /**
     * Bottom-sheet: 'Trenuj teraz' z konkretnego slotu (Pn-Nd) bieżącego tygodnia.
     * Najpierw przesuwa override żeby bazowy dzień przeszedł na dziś, potem startuje.
     */
    fun startSlotNow(planId: Long, sourceDay: Int, onCoach: () -> Unit) {
        val isoDay = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        viewModelScope.launch {
            if (sourceDay != isoDay) {
                planRepo.postponeTraining(planId, sourceDay, isoDay)
            }
            val active = workoutRepo.startOrResume(fromPlanId = planId, fromDayOfWeek = sourceDay)
            val planExercises = planRepo.getPlanExercisesForDay(planId, sourceDay)
            planExercises.forEach { pe ->
                val setSpecs = planRepo.getSetsForPlanExercise(pe.id)
                setSpecs.forEach { spec ->
                    workoutRepo.addPlannedSet(
                        workoutId = active.id,
                        exerciseId = pe.exerciseId,
                        reps = spec.reps,
                        weightKg = spec.weightKg ?: 0.0,
                        durationSec = spec.durationSec,
                        distanceM = spec.distanceM
                    )
                }
            }
            onCoach()
        }
    }

    /**
     * Stan B: 'Trenuj dziś' — uruchamia plan z najbliższego planowanego dnia,
     * ale używając ćwiczeń z TARGET dnia (nie dzisiejszego). To jest 'przesunięcie'
     * — robisz dziś to co miało być jutro/za 2 dni.
     */
    fun startNextPlannedToday(onCoach: () -> Unit) {
        val next = state.value.nextPlannedDay ?: return
        viewModelScope.launch {
            val active = workoutRepo.startOrResume(fromPlanId = next.planId, fromDayOfWeek = next.dayOfWeek)
            val planExercises = planRepo.getPlanExercisesForDay(next.planId, next.dayOfWeek)
            planExercises.forEach { pe ->
                val setSpecs = planRepo.getSetsForPlanExercise(pe.id)
                setSpecs.forEach { spec ->
                    workoutRepo.addPlannedSet(
                        workoutId = active.id,
                        exerciseId = pe.exerciseId,
                        reps = spec.reps,
                        weightKg = spec.weightKg ?: 0.0,
                        durationSec = spec.durationSec,
                        distanceM = spec.distanceM
                    )
                }
            }
            onCoach()
        }
    }

    fun startWorkoutFromPlanForDay(planId: Long, day: Int, onReady: () -> Unit) {
        viewModelScope.launch {
            val active = workoutRepo.startOrResume(fromPlanId = planId, fromDayOfWeek = day)
            val planExercises = planRepo.getPlanExercisesForDay(planId, day)
            planExercises.forEach { pe ->
                val setSpecs = planRepo.getSetsForPlanExercise(pe.id)
                setSpecs.forEach { spec ->
                    workoutRepo.addPlannedSet(
                        workoutId = active.id,
                        exerciseId = pe.exerciseId,
                        reps = spec.reps,
                        weightKg = spec.weightKg ?: 0.0,
                        durationSec = spec.durationSec,
                        distanceM = spec.distanceM
                    )
                }
            }
            onReady()
        }
    }
}
