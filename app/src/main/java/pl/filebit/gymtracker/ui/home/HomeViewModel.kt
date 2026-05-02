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
    val deloadAlert: pl.filebit.gymtracker.util.DeloadRecommendation? = null,
    val weekDays: List<DayMarker> = emptyList(),     // 7 dni Pn-Nd dla mini-paska
    val nextPlannedDay: NextPlannedDay? = null       // najbliższy dzień z planu (jeśli dziś wolne)
)

/**
 * Status jednego dnia tygodnia w mini-pasku Home.
 */
data class DayMarker(
    val dayOfWeek: Int,        // 1=Pn..7=Nd
    val isToday: Boolean,
    val isPlanned: Boolean,    // dzień ma ćwiczenia w którymś planie
    val isCompleted: Boolean   // ten dzień bieżącego tygodnia ma ukończony trening
)

data class NextPlannedDay(
    val dayOfWeek: Int,        // 1=Pn..7=Nd
    val daysFromToday: Int,    // 1=jutro, 7=za tydzień
    val planName: String,
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
    private val deloadService: pl.filebit.gymtracker.data.repository.DeloadService
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        workoutRepo.observeActive(),
        workoutRepo.observeRecent(10),  // potrzebujemy do tygodnia + 4 ostatnich
        planRepo.observeAllPlans()
    ) { active, recent, plans ->
        val isoDay = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        // pierwszy plan który ma JAKIEKOLWIEK ćwiczenia na dzisiejszy dzień
        var todaysPlan: pl.filebit.gymtracker.data.entity.TrainingPlan? = null
        var todaysCount = 0
        for (plan in plans) {
            val exesForToday = planRepo.getPlanExercisesForDay(plan.id, isoDay)
            if (exesForToday.isNotEmpty()) {
                todaysPlan = plan
                todaysCount = exesForToday.size
                break
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

        val deloadAlert = runCatching { deloadService.check() }.getOrNull()

        // === Pasek 7 dni tygodnia (Pn-Nd) ===
        // Dla każdego dnia: planowany (jakikolwiek plan ma na ten dzień)
        // + ukończony (ten dzień bieżącego tygodnia ma sesję finished)
        val plannedDays: Set<Int> = run {
            val planned = mutableSetOf<Int>()
            for (plan in plans) {
                for (d in 1..7) {
                    if (planRepo.getPlanExercisesForDay(plan.id, d).isNotEmpty()) planned.add(d)
                }
            }
            planned
        }
        val nowMs = System.currentTimeMillis()
        // Początek bieżącego tygodnia (Pn 00:00)
        val cal = java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val daysToMonday = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysToMonday)
        val mondayMs = cal.timeInMillis
        val sundayEndMs = mondayMs + 7L * 86_400_000L
        val completedDays: Set<Int> = recent
            .filter { it.finishedAt != null && it.startedAt in mondayMs until sundayEndMs }
            .map { w ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = w.startedAt }
                ((c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            }.toSet()

        val weekDays = (1..7).map { d ->
            DayMarker(
                dayOfWeek = d,
                isToday = d == isoDay,
                isPlanned = d in plannedDays,
                isCompleted = d in completedDays
            )
        }

        // Najbliższy dzień z planu (>= jutro, max 7 dni do przodu)
        val nextPlannedDay: NextPlannedDay? = if (todaysPlan == null) {
            // Szukamy następnego planowanego dnia
            var found: NextPlannedDay? = null
            for (offset in 1..7) {
                val targetDay = ((isoDay - 1 + offset) % 7) + 1
                if (targetDay in plannedDays) {
                    // Znajdź pierwszy plan z tym dniem
                    for (plan in plans) {
                        val exes = planRepo.getPlanExercisesForDay(plan.id, targetDay)
                        if (exes.isNotEmpty()) {
                            found = NextPlannedDay(
                                dayOfWeek = targetDay,
                                daysFromToday = offset,
                                planName = plan.name,
                                exerciseCount = exes.size
                            )
                            break
                        }
                    }
                    if (found != null) break
                }
            }
            found
        } else null

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
            deloadAlert = deloadAlert,
            weekDays = weekDays,
            nextPlannedDay = nextPlannedDay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

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
