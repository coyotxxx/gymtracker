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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class PlanExerciseWithDetail(
    val planEx: PlanExercise,
    val exercise: Exercise?,
    val sets: List<PlanExerciseSet> = emptyList()
)

data class PlanEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val daysOfWeek: Set<Int> = emptySet(),
    val notes: String = "",
    val exercises: List<PlanExerciseWithDetail> = emptyList(),
    val selectedDay: Int = 1, // 1=Pon..7=Nd, currently active tab
    val showAdvancedFields: Boolean = false,
    val isLoading: Boolean = true
) {
    val exercisesForSelectedDay: List<PlanExerciseWithDetail>
        get() = exercises.filter { it.planEx.dayOfWeek == selectedDay }
}

@HiltViewModel
class PlanEditViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val planId: Long = savedStateHandle.get<Long>("planId") ?: 0L
    private val createdAt: Long
    private var nextLocalId: Long = -1L // ujemne ID dla niezapisanych
    private var nextLocalSetId: Long = -1L

    private val _state = MutableStateFlow(PlanEditUiState(isNew = planId == 0L))
    val state: StateFlow<PlanEditUiState> = _state.asStateFlow()

    init {
        createdAt = System.currentTimeMillis()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .dayOfWeek.isoDayNumber
        _state.update { it.copy(selectedDay = today) }
        viewModelScope.launch {
            val showAdvanced = profileRepo.get().showAdvancedSetFields
            _state.update { it.copy(showAdvancedFields = showAdvanced) }
        }
        load()
    }

    fun updatePlanSetAdvanced(
        planExerciseId: Long,
        setId: Long,
        rpe: Int? = null,
        rir: Int? = null,
        tempo: String? = null,
        clearRpe: Boolean = false,
        clearRir: Boolean = false,
        clearTempo: Boolean = false
    ) = _state.update { st ->
        val newList = st.exercises.map { ped ->
            if (ped.planEx.id != planExerciseId) ped
            else ped.copy(sets = ped.sets.map { s ->
                if (s.id != setId) s
                else s.copy(
                    rpe = when {
                        clearRpe -> null
                        rpe != null -> rpe
                        else -> s.rpe
                    },
                    rir = when {
                        clearRir -> null
                        rir != null -> rir
                        else -> s.rir
                    },
                    tempo = when {
                        clearTempo -> null
                        tempo != null -> tempo
                        else -> s.tempo
                    }
                )
            })
        }
        st.copy(exercises = newList)
    }

    fun setSelectedDay(day: Int) = _state.update { it.copy(selectedDay = day) }

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
                val ex = exerciseRepo.get(pe.exerciseId)
                val sets = planRepo.getSetsForPlanExercise(pe.id)
                PlanExerciseWithDetail(pe, ex, sets)
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

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            val ex = exerciseRepo.get(exerciseId) ?: return@launch
            val day = _state.value.selectedDay
            val orderInDay = _state.value.exercises.count { it.planEx.dayOfWeek == day }
            val peId = nextLocalId--
            val newPe = PlanExercise(
                id = peId,
                planId = planId,
                exerciseId = exerciseId,
                dayOfWeek = day,
                orderIndex = orderInDay
            )
            // 3 default sety: 8 powt, brak wagi, brak odp
            val defaultSets = (1..3).map { setNum ->
                PlanExerciseSet(
                    id = nextLocalSetId--,
                    planExerciseId = peId,
                    setNumber = setNum,
                    reps = 8,
                    weightKg = null,
                    restSeconds = null
                )
            }
            _state.update {
                it.copy(
                    exercises = it.exercises + PlanExerciseWithDetail(newPe, ex, defaultSets),
                    daysOfWeek = it.daysOfWeek + day
                )
            }
        }
    }

    fun addSetToExercise(planExerciseId: Long) = _state.update { st ->
        val newList = st.exercises.map { ped ->
            if (ped.planEx.id != planExerciseId) ped
            else {
                val nextNum = (ped.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
                // smart fill — kopiuj z ostatniej serii
                val template = ped.sets.lastOrNull()
                val newSet = PlanExerciseSet(
                    id = nextLocalSetId--,
                    planExerciseId = planExerciseId,
                    setNumber = nextNum,
                    reps = template?.reps ?: 8,
                    weightKg = template?.weightKg,
                    restSeconds = template?.restSeconds
                )
                ped.copy(sets = ped.sets + newSet)
            }
        }
        st.copy(exercises = newList)
    }

    fun removeSetFromExercise(planExerciseId: Long, setId: Long) = _state.update { st ->
        val newList = st.exercises.map { ped ->
            if (ped.planEx.id != planExerciseId) ped
            else {
                val filtered = ped.sets.filter { it.id != setId }
                // przenumeruj
                val renumbered = filtered.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) }
                ped.copy(sets = renumbered)
            }
        }
        st.copy(exercises = newList)
    }

    fun updatePlanSet(
        planExerciseId: Long,
        setId: Long,
        reps: Int? = null,
        weightKg: Double? = null,
        restSeconds: Int? = null,
        clearWeight: Boolean = false,
        clearRest: Boolean = false
    ) = _state.update { st ->
        val newList = st.exercises.map { ped ->
            if (ped.planEx.id != planExerciseId) ped
            else {
                val newSets = ped.sets.map { s ->
                    if (s.id != setId) s
                    else s.copy(
                        reps = reps ?: s.reps,
                        weightKg = when {
                            clearWeight -> null
                            weightKg != null -> weightKg
                            else -> s.weightKg
                        },
                        restSeconds = when {
                            clearRest -> null
                            restSeconds != null -> restSeconds
                            else -> s.restSeconds
                        }
                    )
                }
                ped.copy(sets = newSets)
            }
        }
        st.copy(exercises = newList)
    }

    fun removeExercise(id: Long) = _state.update { st ->
        st.copy(exercises = st.exercises.filter { it.planEx.id != id })
    }

    fun toggleSupersetWithPrev(id: Long) = _state.update { st ->
        val day = st.selectedDay
        val sameDay = st.exercises
            .filter { it.planEx.dayOfWeek == day }
            .sortedBy { it.planEx.orderIndex }
        val idx = sameDay.indexOfFirst { it.planEx.id == id }
        if (idx <= 0) return@update st  // pierwsze ćwiczenie nie może być "z poprzednim"
        val current = sameDay[idx]
        val prev = sameDay[idx - 1]

        // Jeśli current jest w tej samej grupie co prev — wyłącz superseria current
        if (current.planEx.supersetGroup != null && current.planEx.supersetGroup == prev.planEx.supersetGroup) {
            val newList = st.exercises.map {
                if (it.planEx.id == current.planEx.id)
                    it.copy(planEx = it.planEx.copy(supersetGroup = null))
                else it
            }
            return@update st.copy(exercises = newList)
        }

        // W przeciwnym razie — dołącz current do grupy prev
        val targetGroup = prev.planEx.supersetGroup ?: run {
            // Generuj nową literę A/B/C... niewystępującą w tym dniu
            val existing = sameDay.mapNotNull { it.planEx.supersetGroup }.toSet()
            ('A'..'Z').map { it.toString() }.firstOrNull { it !in existing } ?: "A"
        }
        val newList = st.exercises.map {
            when (it.planEx.id) {
                current.planEx.id -> it.copy(planEx = it.planEx.copy(supersetGroup = targetGroup))
                prev.planEx.id -> it.copy(planEx = it.planEx.copy(supersetGroup = targetGroup))
                else -> it
            }
        }
        st.copy(exercises = newList)
    }

    fun moveExerciseUp(id: Long) = _state.update { st ->
        val day = st.selectedDay
        val sameDay = st.exercises.filter { it.planEx.dayOfWeek == day }
        val idx = sameDay.indexOfFirst { it.planEx.id == id }
        if (idx <= 0) return@update st
        val swapWith = sameDay[idx - 1]
        val current = sameDay[idx]
        val newList = st.exercises.map {
            when (it.planEx.id) {
                current.planEx.id -> it.copy(planEx = it.planEx.copy(orderIndex = swapWith.planEx.orderIndex))
                swapWith.planEx.id -> it.copy(planEx = it.planEx.copy(orderIndex = current.planEx.orderIndex))
                else -> it
            }
        }.sortedWith(compareBy({ it.planEx.dayOfWeek }, { it.planEx.orderIndex }))
        st.copy(exercises = newList)
    }

    fun moveExerciseDown(id: Long) = _state.update { st ->
        val day = st.selectedDay
        val sameDay = st.exercises.filter { it.planEx.dayOfWeek == day }
        val idx = sameDay.indexOfFirst { it.planEx.id == id }
        if (idx < 0 || idx >= sameDay.size - 1) return@update st
        val swapWith = sameDay[idx + 1]
        val current = sameDay[idx]
        val newList = st.exercises.map {
            when (it.planEx.id) {
                current.planEx.id -> it.copy(planEx = it.planEx.copy(orderIndex = swapWith.planEx.orderIndex))
                swapWith.planEx.id -> it.copy(planEx = it.planEx.copy(orderIndex = current.planEx.orderIndex))
                else -> it
            }
        }.sortedWith(compareBy({ it.planEx.dayOfWeek }, { it.planEx.orderIndex }))
        st.copy(exercises = newList)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val st = _state.value
            val derivedDays = st.exercises.map { it.planEx.dayOfWeek }.distinct().sorted()
            val plan = TrainingPlan(
                id = if (st.isNew) 0L else planId,
                name = st.name.trim(),
                daysOfWeek = derivedDays,
                notes = st.notes.trim(),
                createdAt = createdAt
            )
            val savedPlanId = planRepo.upsertPlan(plan)
            planRepo.deleteAllPlanExercises(savedPlanId)
            // grupuj per dzień, zachowaj kolejność
            val groupedByDay = st.exercises.groupBy { it.planEx.dayOfWeek }
            for ((day, list) in groupedByDay) {
                list.forEachIndexed { idx, ped ->
                    val newPeId = planRepo.upsertPlanExercise(
                        ped.planEx.copy(
                            id = 0L,
                            planId = savedPlanId,
                            dayOfWeek = day,
                            orderIndex = idx
                        )
                    )
                    // wstaw sety do nowo utworzonego planExerciseId
                    ped.sets.forEachIndexed { setIdx, s ->
                        planRepo.upsertPlanSet(
                            s.copy(
                                id = 0L,
                                planExerciseId = newPeId,
                                setNumber = setIdx + 1
                            )
                        )
                    }
                }
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
