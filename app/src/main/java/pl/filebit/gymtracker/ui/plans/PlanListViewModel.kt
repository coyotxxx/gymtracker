package pl.filebit.gymtracker.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class PlanListItem(
    val plan: TrainingPlan,
    val exerciseCount: Int,
    val daysWithExercises: List<Int> = emptyList()
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val plans: StateFlow<List<PlanListItem>> = planRepo.observeAllPlans()
        .mapLatest { list ->
            list.map { plan ->
                val exes = planRepo.getPlanExercises(plan.id)
                PlanListItem(
                    plan = plan,
                    exerciseCount = exes.size,
                    daysWithExercises = exes.map { it.dayOfWeek }.distinct().sorted()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
