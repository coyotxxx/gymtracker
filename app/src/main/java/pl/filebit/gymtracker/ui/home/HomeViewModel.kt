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
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class HomeUiState(
    val activeWorkout: Workout? = null,
    val todaysPlan: TrainingPlan? = null,
    val todaysPlanExerciseCount: Int = 0,
    val recentWorkouts: List<RecentWorkoutItem> = emptyList()
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
    private val planRepo: PlanRepository
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        workoutRepo.observeActive(),
        workoutRepo.observeRecent(10),
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
        HomeUiState(
            activeWorkout = active,
            todaysPlan = todaysPlan,
            todaysPlanExerciseCount = todaysCount,
            recentWorkouts = items
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
                repeat(pe.plannedSets) {
                    workoutRepo.addPlannedSet(
                        workoutId = active.id,
                        exerciseId = pe.exerciseId,
                        reps = pe.plannedReps,
                        weightKg = pe.plannedWeightKg ?: 0.0
                    )
                }
            }
            onReady()
        }
    }
}
