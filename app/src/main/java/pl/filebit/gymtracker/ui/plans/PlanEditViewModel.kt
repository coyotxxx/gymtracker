package pl.filebit.gymtracker.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import javax.inject.Inject

data class PlanExerciseWithDetail(
    val planEx: PlanExercise,
    val exercise: Exercise?
)

data class PlanEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val daysOfWeek: Set<Int> = emptySet(),
    val notes: String = "",
    val exercises: List<PlanExerciseWithDetail> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlanEditViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val exerciseRepo: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val planId: Long = savedStateHandle.get<Long>("planId") ?: 0L
    private val createdAt: Long
    private var nextLocalId: Long = -1L // ujemne ID dla niezapisanych

    private val _state = MutableStateFlow(PlanEditUiState(isNew = planId == 0L))
    val state: StateFlow<PlanEditUiState> = _state.asStateFlow()

    init {
        createdAt = System.currentTimeMillis()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (planId == 0L) {
                _state.update { it.copy(isLoading = false, isNew = true) }
                return@launch
            }
            val plan = planRepo.getPlan(planId)
            if (plan == null) {
                _state.update { it.copy(isLoading = false, isNew = true) }
                return@launch
            }
            val exes = planRepo.getPlanExercises(planId)
            val withDetails = exes.map { pe ->
                val ex = exerciseRepo.getById(pe.exerciseId)
                PlanExerciseWithDetail(pe, ex)
            }
            _state.update {
                it.copy(
                    isNew = false,
                    name = plan.name,
                    daysOfWeek = plan.daysOfWeek.toSet(),
                    notes = plan.notes,
                    exercises = withDetails,
                    isLoading = false
                )
            }
        }
    }

    fun setName(s: String) = _state.update { it.copy(name = s) }
    fun setNotes(s: String) = _state.update { it.copy(notes = s) }

    fun toggleDay(day: Int) = _state.update {
        val days = it.daysOfWeek.toMutableSet()
        if (days.contains(day)) days.remove(day) else days.add(day)
        it.copy(daysOfWeek = days)
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            val ex = exerciseRepo.getById(exerciseId) ?: return@launch
            val newPe = PlanExercise(
                id = nextLocalId--, // unikalne tymczasowe id
                planId = planId,
                exerciseId = exerciseId,
                orderIndex = _state.value.exercises.size,
                plannedSets = 3,
                plannedReps = 8
            )
            _state.update {
                it.copy(exercises = it.exercises + PlanExerciseWithDetail(newPe, ex))
            }
        }
    }

    fun updatePlanExercise(
        id: Long,
        sets: Int? = null,
        reps: Int? = null,
        weightKg: Double? = null,
        restSeconds: Int? = null,
        clearWeight: Boolean = false,
        clearRest: Boolean = false
    ) = _state.update { st ->
        val newList = st.exercises.map { pe ->
            if (pe.planEx.id != id) pe
            else pe.copy(
                planEx = pe.planEx.copy(
                    plannedSets = sets ?: pe.planEx.plannedSets,
                    plannedReps = reps ?: pe.planEx.plannedReps,
                    plannedWeightKg = when {
                        clearWeight -> null
                        weightKg != null -> weightKg
                        else -> pe.planEx.plannedWeightKg
                    },
                    restSeconds = when {
                        clearRest -> null
                        restSeconds != null -> restSeconds
                        else -> pe.planEx.restSeconds
                    }
                )
            )
        }
        st.copy(exercises = newList)
    }

    fun removeExercise(id: Long) = _state.update { st ->
        st.copy(exercises = st.exercises.filter { it.planEx.id != id })
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val st = _state.value
            val plan = TrainingPlan(
                id = if (st.isNew) 0L else planId,
                name = st.name.trim(),
                daysOfWeek = st.daysOfWeek.toList().sorted(),
                notes = st.notes.trim(),
                createdAt = createdAt
            )
            val savedId = planRepo.upsertPlan(plan)
            // wyczyść stare i wstaw nowe w nowej kolejności
            planRepo.deleteAllPlanExercises(savedId)
            st.exercises.forEachIndexed { idx, pe ->
                val toInsert = pe.planEx.copy(
                    id = 0L,
                    planId = savedId,
                    orderIndex = idx
                )
                planRepo.upsertPlanExercise(toInsert)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (_state.value.isNew) {
            onDone()
            return
        }
        viewModelScope.launch {
            planRepo.deletePlanById(planId)
            onDone()
        }
    }
}
