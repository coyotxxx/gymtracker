package pl.filebit.gymtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class HistoryItem(
    val workout: Workout,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val exerciseCount: Int
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: WorkoutRepository
) : ViewModel() {

    val workouts: StateFlow<List<HistoryItem>> = repo.observeAll()
        .map { list ->
            list.filter { !it.isActive }.map { w ->
                val sets = repo.getSetsForWorkout(w.id)
                HistoryItem(
                    workout = w,
                    totalSets = sets.size,
                    totalVolumeKg = sets.sumOf { it.reps * it.weightKg },
                    exerciseCount = sets.map { it.exerciseId }.distinct().size
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
