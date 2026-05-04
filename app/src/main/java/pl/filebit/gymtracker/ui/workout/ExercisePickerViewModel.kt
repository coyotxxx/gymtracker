package pl.filebit.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class PickerUiState(
    val query: String = "",
    val muscleFilter: MuscleGroup? = null,
    val exercises: List<Exercise> = emptyList()
)

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _muscleFilter = MutableStateFlow<MuscleGroup?>(null)
    val muscleFilter: StateFlow<MuscleGroup?> = _muscleFilter.asStateFlow()

    private val _equipmentFilter = MutableStateFlow<Equipment?>(null)
    val equipmentFilter: StateFlow<Equipment?> = _equipmentFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseExercises: kotlinx.coroutines.flow.Flow<List<Exercise>> = _query.flatMapLatest { q ->
        if (q.isBlank()) {
            _muscleFilter.flatMapLatest { muscle ->
                if (muscle == null) exerciseRepo.observeAll()
                else exerciseRepo.observeByMuscle(muscle)
            }
        } else {
            exerciseRepo.search(q)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercises: StateFlow<List<Exercise>> = _equipmentFilter.flatMapLatest { eq ->
        baseExercises.flatMapLatest { list ->
            flowOf(if (eq == null) list else list.filter { it.equipment == eq })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setMuscleFilter(m: MuscleGroup?) { _muscleFilter.value = m }
    fun setEquipmentFilter(e: Equipment?) { _equipmentFilter.value = e }

    /**
     * Tryb WORKOUT: dodaje pierwszą serię z auto-fill z ostatniej sesji
     * (lub sensowny default). Picker zamyka się i wraca do Active Workout.
     */
    fun pickForWorkout(exercise: Exercise, onDone: () -> Unit) {
        viewModelScope.launch {
            val active = workoutRepo.startOrResume()
            val last = workoutRepo.getLastSetForExercise(exercise.id)
            val (w, r) = when {
                last != null -> last.weightKg to last.reps
                exercise.equipment == pl.filebit.gymtracker.data.entity.Equipment.BODYWEIGHT -> 0.0 to 8
                else -> 20.0 to 8
            }
            workoutRepo.addSet(
                workoutId = active.id,
                exerciseId = exercise.id,
                reps = r,
                weightKg = w
            )
            onDone()
        }
    }

    /**
     * Tryb PLAN: tylko zwraca exerciseId callbackiem, NIE dodaje serii.
     */
    fun pickForPlan(exercise: Exercise, onPicked: (Long) -> Unit) {
        onPicked(exercise.id)
    }
}
