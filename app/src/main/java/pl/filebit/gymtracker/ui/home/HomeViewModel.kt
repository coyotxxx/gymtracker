package pl.filebit.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import pl.filebit.gymtracker.ai.WorkoutAdjustment
import pl.filebit.gymtracker.util.DeloadSeverity
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseAnalyzer
import pl.filebit.gymtracker.ai.TrainingPhaseStatus
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.Workout
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
    val dismissedCards: Set<String> = emptySet()         // v1.10 — generyczny dismiss per cardKey
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
    private val phaseAnalyzer: TrainingPhaseAnalyzer,
    private val healthAnalyzer: HealthInsightAnalyzer,
    private val recoveryScoreCalculator: RecoveryScoreCalculator,
    private val trainingLoadAnalyzer: TrainingLoadAnalyzer,
    private val recoveryCardPrefs: pl.filebit.gymtracker.data.repository.RecoveryCardPrefs,
    private val dismissedCardsPrefs: pl.filebit.gymtracker.data.repository.DismissedCardsPrefs,
    private val muscleRecoveryAnalyzer: MuscleRecoveryAnalyzer,
    private val readinessAnalyzer: TrainingReadinessAnalyzer
) : ViewModel() {

    private val recoveryCardRefresh = kotlinx.coroutines.flow.MutableStateFlow(0L)

    // Trigger do wymuszania rebuild state po akcjach deload (Apply/Dismiss/Restore/Cancel).
    // Bez tego SharedPrefs się zmienia ale combine() nie wie o tym — kafel zostaje na ekranie.
    private val deloadRefresh = kotlinx.coroutines.flow.MutableStateFlow(0L)

    val state: StateFlow<HomeUiState> = combine(
        workoutRepo.observeActive(),
        workoutRepo.observeRecent(4),
        planRepo.observeAllPlans(),
        deloadRefresh,
        recoveryCardRefresh
    ) { active, recent, plans, _, _ ->
        val isoDay = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        // Effective schedule = oryginalne dni planów + overrides per-tygodniowe
        val schedule = runCatching { planRepo.getEffectiveScheduleForCurrentWeek() }.getOrDefault(emptyMap())
        var todaysPlan: pl.filebit.gymtracker.data.entity.TrainingPlan? = null
        var todaysCount = 0
        var todaysSourceDay = isoDay
        schedule[isoDay]?.firstOrNull()?.let { slot ->
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

        // Faza cyklu treningowego — z TrainingPhaseAnalyzer (deterministic)
        val trainingPhase = runCatching { phaseAnalyzer.analyze() }.getOrNull()
        // Regeneracja — sen + HRV z Health Connect
        val healthInsight = runCatching { healthAnalyzer.analyze() }.getOrNull()
        // v1.7.4 WHOOP-like Recovery Score 0-100 z personal baseline
        val recoveryScore = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        // v1.7.4 ACWR — Acute:Chronic Workload Ratio
        val trainingLoad = runCatching { trainingLoadAnalyzer.analyze() }.getOrNull()
        // v1.9.0 Recovery per partia + Training Readiness
        val muscleRecovery = runCatching { muscleRecoveryAnalyzer.analyze() }.getOrNull()
        val trainingReadiness = runCatching { readinessAnalyzer.analyze() }.getOrNull()

        // Stan B: Next planned day — używa effective schedule (z overrides)
        val nextPlannedDay: NextPlannedDay? = if (todaysPlan == null) {
            var found: NextPlannedDay? = null
            for (offset in 1..7) {
                val targetDay = ((isoDay - 1 + offset) % 7) + 1
                val slot = schedule[targetDay]?.firstOrNull()
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
            ).filter { dismissedCardsPrefs.isDismissedToday(it) }.toSet()
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

    /** v1.10 — uniwersalny apply deload z konkretnym severity (z dowolnej karty alertowej). */
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
                        weightKg = spec.weightKg ?: 0.0
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
                        weightKg = spec.weightKg ?: 0.0
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
                        weightKg = spec.weightKg ?: 0.0
                    )
                }
            }
            onReady()
        }
    }
}
