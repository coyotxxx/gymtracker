package pl.filebit.gymtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class DetailGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val groups: List<DetailGroup> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repo: WorkoutRepository
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
            _state.value = WorkoutDetailUiState(workout = workout, groups = groups, loading = false)
        }
    }

    fun deleteWorkout(onDone: () -> Unit) {
        val id = _state.value.workout?.id ?: return
        viewModelScope.launch {
            repo.deleteWorkout(id)
            onDone()
        }
    }
}
