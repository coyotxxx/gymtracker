package pl.filebit.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExercisePr
import pl.filebit.gymtracker.data.repository.ExerciseProgressionPoint
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val pr: ExercisePr? = null,
    val progression: List<ExerciseProgressionPoint> = emptyList(),
    val history: List<WorkoutSet> = emptyList()
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val statsRepo: StatsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L

    private val _state = MutableStateFlow(ExerciseDetailUiState())
    val state: StateFlow<ExerciseDetailUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val ex = exerciseRepo.get(exerciseId)
            val pr = statsRepo.prForExercise(exerciseId)
            val progression = statsRepo.progressionForExercise(exerciseId)
            val history = statsRepo.historyForExercise(exerciseId)
            _state.value = ExerciseDetailUiState(
                loading = false,
                exercise = ex,
                pr = pr,
                progression = progression,
                history = history
            )
        }
    }

    fun saveNotes(notes: String) {
        val ex = _state.value.exercise ?: return
        viewModelScope.launch {
            exerciseRepo.upsert(ex.copy(notes = notes))
            _state.value = _state.value.copy(exercise = ex.copy(notes = notes))
        }
    }
}
