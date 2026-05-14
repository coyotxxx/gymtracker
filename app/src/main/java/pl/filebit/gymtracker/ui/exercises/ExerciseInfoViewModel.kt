package pl.filebit.gymtracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import javax.inject.Inject

/**
 * v1.25.6 — ViewModel dla [ExerciseInfoBottomSheet]: pozwala usunąć błędne
 * dopasowanie z bazy ExerciseDB (GIF/instrukcje/CSV) bez utraty user data
 * (name, primaryMuscle, equipment, isFavorite, etc.).
 */
@HiltViewModel
class ExerciseInfoViewModel @Inject constructor(
    private val repo: ExerciseRepository
) : ViewModel() {

    fun detach(exerciseId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.detachFromExerciseDb(exerciseId)
            onDone()
        }
    }
}
