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
    val nextPlannedDay: NextPlannedDay? = null,
    val activeWorkoutDurationMin: Int = 0,
    val activeWorkoutProgressPct: Int = 0,            // % ukończonych setów (Stan C)
    val activeWorkoutCurrentSetLabel: String = ""     // "Seria 8 z 19"
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
    private val deloadService: pl.filebit.gymtracker.data.repository.DeloadService
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        workoutRepo.observeActive(),
        workoutRepo.observeRecent(4),
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

        // Stan B: Next planned day — gdy dziś brak treningu, znajdź najbliższy w tygodniu
        val nextPlannedDay: NextPlannedDay? = if (todaysPlan == null) {
            var found: NextPlannedDay? = null
            for (offset in 1..7) {
                val targetDay = ((isoDay - 1 + offset) % 7) + 1
                for (plan in plans) {
                    val exes = planRepo.getPlanExercisesForDay(plan.id, targetDay)
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
                if (found != null) break
            }
            found
        } else null

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
            deloadAlert = deloadAlert,
            nextPlannedDay = nextPlannedDay,
            activeWorkoutDurationMin = durationMin,
            activeWorkoutProgressPct = progressPct,
            activeWorkoutCurrentSetLabel = currentSetLabel
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
