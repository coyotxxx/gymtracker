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
        val todaysPlan = plans.firstOrNull { it.daysOfWeek.contains(isoDay) }
        val todaysCount = todaysPlan?.let { planRepo.getPlanExercises(it.id).size } ?: 0
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

    fun startOrResumeWorkout(onReady: () -> Unit) {
        viewModelScope.launch {
            workoutRepo.startOrResume()
            onReady()
        }
    }

    fun startWorkoutFromPlan(planId: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            val active = workoutRepo.startOrResume()
            val planExercises = planRepo.getPlanExercises(planId)
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
