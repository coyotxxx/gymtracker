package pl.filebit.gymtracker.ui.exercises

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
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.ExercisePr
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repo: ExerciseRepository,
    private val statsRepo: StatsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _muscleFilter = MutableStateFlow<MuscleGroup?>(null)
    val muscleFilter: StateFlow<MuscleGroup?> = _muscleFilter.asStateFlow()

    private val _equipmentFilter = MutableStateFlow<Equipment?>(null)
    val equipmentFilter: StateFlow<Equipment?> = _equipmentFilter.asStateFlow()

    private val _prs = MutableStateFlow<Map<Long, ExercisePr>>(emptyMap())
    val prs: StateFlow<Map<Long, ExercisePr>> = _prs.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseExercises: kotlinx.coroutines.flow.Flow<List<Exercise>> = _query.flatMapLatest { q ->
        if (q.isBlank()) {
            _muscleFilter.flatMapLatest { muscle ->
                if (muscle == null) repo.observeAll()
                else repo.observeByMuscle(muscle)
            }
        } else {
            repo.search(q)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercises: StateFlow<List<Exercise>> = _equipmentFilter.flatMapLatest { eq ->
        baseExercises.flatMapLatest { list ->
            kotlinx.coroutines.flow.flowOf(
                if (eq == null) list else list.filter { it.equipment == eq }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Pre-load PRs przy starcie. Aktualizuje się gdy lista zmieni.
        viewModelScope.launch {
            exercises.collect { list ->
                val map = mutableMapOf<Long, ExercisePr>()
                for (ex in list) {
                    statsRepo.prForExercise(ex.id)?.let { pr -> map[ex.id] = pr }
                }
                _prs.value = map
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setMuscleFilter(m: MuscleGroup?) { _muscleFilter.value = m }
    fun setEquipmentFilter(e: Equipment?) { _equipmentFilter.value = e }

    fun saveExerciseNotes(exerciseId: Long, notes: String) {
        viewModelScope.launch {
            val ex = repo.get(exerciseId) ?: return@launch
            repo.upsert(ex.copy(notes = notes))
        }
    }
}
