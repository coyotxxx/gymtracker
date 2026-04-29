package pl.filebit.gymtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class DetailGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val groups: List<DetailGroup> = emptyList(),
    val planName: String? = null, // null = ad-hoc lub plan usunięty
    val loading: Boolean = true
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val planRepo: PlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutDetailUiState())
    val state: StateFlow<WorkoutDetailUiState> = _state.asStateFlow()

    fun load(workoutId: Long) {
        viewModelScope.launch {
            val workout = repo.getWorkout(workoutId)
            val sets = repo.getSetsForWorkout(workoutId)
            val groups = sets.groupBy { it.exerciseId }
                .toList()
                .sortedBy { (_, list) -> list.minOf { it.orderIndex } }
                .mapNotNull { (exerciseId, list) ->
                    val ex = repo.getExercise(exerciseId) ?: return@mapNotNull null
                    DetailGroup(exercise = ex, sets = list.sortedBy { it.setNumber })
                }
            val planName = workout?.fromPlanId?.let { planRepo.getPlan(it)?.name }
            _state.value = WorkoutDetailUiState(
                workout = workout,
                groups = groups,
                planName = planName,
                loading = false
            )
        }
    }

    fun deleteWorkout(onDone: () -> Unit) {
        val id = _state.value.workout?.id ?: return
        viewModelScope.launch {
            repo.deleteWorkout(id)
            onDone()
        }
    }

    /**
     * Tworzy nowy plan z ćwiczeń tego treningu. Każda grupa ćwiczeń → PlanExercise.
     * Wagę bierzemy najwyższą (working set), reps z najcięższego setu, liczbę serii = wszystkie sety.
     * User dopracuje nazwę i dni tygodnia w PlanEdit.
     */
    fun saveAsPlan(onCreated: (Long) -> Unit) {
        val groups = _state.value.groups
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val planId = planRepo.upsertPlan(
                TrainingPlan(
                    name = "",
                    daysOfWeek = emptyList(),
                    notes = ""
                )
            )
            groups.forEachIndexed { idx, group ->
                val maxWeight = group.sets.maxOfOrNull { it.weightKg } ?: 0.0
                val workingSet = group.sets.firstOrNull { it.weightKg == maxWeight }
                    ?: group.sets.firstOrNull()
                val typicalReps = workingSet?.reps ?: 8
                planRepo.upsertPlanExercise(
                    PlanExercise(
                        planId = planId,
                        exerciseId = group.exercise.id,
                        orderIndex = idx,
                        plannedSets = group.sets.size,
                        plannedReps = typicalReps,
                        plannedWeightKg = if (maxWeight > 0.0) maxWeight else null,
                        restSeconds = null
                    )
                )
            }
            onCreated(planId)
        }
    }
}
