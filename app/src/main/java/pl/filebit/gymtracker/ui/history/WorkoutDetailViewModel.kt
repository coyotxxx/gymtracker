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
import pl.filebit.gymtracker.data.repository.PreviousSession
import pl.filebit.gymtracker.data.repository.StatsRepository
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
    val planDayOfWeek: Int? = null, // 1=Pon..7=Nd
    val loading: Boolean = true,
    /**
     * Mapa exerciseId → poprzednia sesja (przed startedAt aktualnego treningu).
     * Używana do pokazania "ostatnio" pod każdym setem w detalu.
     */
    val previousByExercise: Map<Long, PreviousSession?> = emptyMap()
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val statsRepo: StatsRepository
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
            // Poprzednie sesje per ćwiczenie — klucz: exerciseId. Pobierane chronologicznie
            // PRZED tym workoutem (nie najnowszy w bazie).
            val previousMap = if (workout != null) {
                groups.associate { g ->
                    g.exercise.id to statsRepo.getSessionBefore(g.exercise.id, workout.startedAt)
                }
            } else emptyMap()
            _state.value = WorkoutDetailUiState(
                workout = workout,
                groups = groups,
                planName = planName,
                planDayOfWeek = workout?.fromDayOfWeek,
                loading = false,
                previousByExercise = previousMap
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
     * Tworzy nowy aktywny ad-hoc trening jako kopia tego (placeholdery isCompleted=false).
     * Po utworzeniu callback nawigacji do ActiveWorkout.
     */
    fun repeatWorkout(onReady: () -> Unit) {
        val srcId = _state.value.workout?.id ?: return
        viewModelScope.launch {
            val srcSets = repo.getSetsForWorkout(srcId).sortedWith(
                compareBy({ it.orderIndex }, { it.setNumber })
            )
            if (srcSets.isEmpty()) { onReady(); return@launch }
            val active = repo.startOrResume()
            for (s in srcSets) {
                repo.addPlannedSet(
                    workoutId = active.id,
                    exerciseId = s.exerciseId,
                    reps = s.reps,
                    weightKg = s.weightKg,
                    setType = s.setType,
                    durationSec = s.durationSec,
                    distanceM = s.distanceM
                )
            }
            onReady()
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
        val srcWorkout = _state.value.workout
        val day = srcWorkout?.fromDayOfWeek ?: 1
        viewModelScope.launch {
            val planId = planRepo.upsertPlan(
                TrainingPlan(name = "", daysOfWeek = emptyList(), notes = "")
            )
            groups.forEachIndexed { idx, group ->
                val newPeId = planRepo.upsertPlanExercise(
                    PlanExercise(
                        planId = planId,
                        exerciseId = group.exercise.id,
                        dayOfWeek = day,
                        orderIndex = idx
                    )
                )
                // jeden plan-set per workout-set (zachowuje rzeczywiste wagi/powt)
                group.sets.forEachIndexed { setIdx, ws ->
                    planRepo.upsertPlanSet(
                        pl.filebit.gymtracker.data.entity.PlanExerciseSet(
                            planExerciseId = newPeId,
                            setNumber = setIdx + 1,
                            reps = ws.reps,
                            weightKg = if (ws.weightKg > 0.0) ws.weightKg else null,
                            restSeconds = null
                        )
                    )
                }
            }
            onCreated(planId)
        }
    }
}
