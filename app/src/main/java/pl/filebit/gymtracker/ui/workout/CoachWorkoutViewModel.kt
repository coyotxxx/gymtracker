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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class CoachExerciseGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

data class CoachUiState(
    val workout: Workout? = null,
    val planName: String = "",
    val groups: List<CoachExerciseGroup> = emptyList(),
    val currentSet: WorkoutSet? = null,
    val currentExercise: Exercise? = null,
    val currentExerciseIndex: Int = 0,        // 0-based
    val currentSetIndexInExercise: Int = 0,   // 0-based
    val totalExercises: Int = 0,
    val totalSetsInCurrentExercise: Int = 0,
    val completedSets: Int = 0,
    val totalSets: Int = 0,
    val isComplete: Boolean = false,          // wszystkie sety zrobione
    val lastSetForCurrent: WorkoutSet? = null, // dla "ostatnio" w UI
    val defaultRestSeconds: Int = 90
)

@HiltViewModel
class CoachWorkoutViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val exerciseRepo: ExerciseRepository,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _pendingPRs = MutableStateFlow<List<NewPrWithName>>(emptyList())
    val pendingPRs: StateFlow<List<NewPrWithName>> = _pendingPRs.asStateFlow()

    private val _pendingTips = MutableStateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>>(emptyList())
    val pendingTips: StateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>> = _pendingTips.asStateFlow()

    fun consumePendingPRs() { _pendingPRs.value = emptyList() }
    fun consumePendingTips() { _pendingTips.value = emptyList() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CoachUiState> = workoutRepo.observeActive()
        .flatMapLatest { workout ->
            if (workout == null) {
                flowOf(CoachUiState(isComplete = true))
            } else {
                workoutRepo.observeSetsForWorkout(workout.id).map { sets ->
                    buildState(workout, sets)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachUiState())

    private suspend fun buildState(workout: Workout, sets: List<WorkoutSet>): CoachUiState {
        val planName = workout.fromPlanId?.let { planRepo.getPlan(it)?.name } ?: ""
        val profile = profileRepo.get()
        val rest = profile.defaultRestSeconds

        val orderedExerciseIds = sets
            .sortedBy { it.orderIndex }
            .map { it.exerciseId }
            .distinct()
        val exerciseMap = orderedExerciseIds
            .mapNotNull { exerciseRepo.get(it)?.let { ex -> it to ex } }
            .toMap()

        val groups = orderedExerciseIds.mapNotNull { eid ->
            exerciseMap[eid]?.let { ex ->
                CoachExerciseGroup(
                    exercise = ex,
                    sets = sets.filter { it.exerciseId == eid }.sortedBy { it.setNumber }
                )
            }
        }

        val totalSets = sets.size
        val completedSets = sets.count { it.isCompleted }

        // Pierwsza niezakończona seria w kolejności (po orderIndex/setNumber)
        val currentSet = groups
            .flatMap { it.sets }
            .firstOrNull { !it.isCompleted }

        val isComplete = currentSet == null && totalSets > 0

        if (currentSet == null) {
            return CoachUiState(
                workout = workout,
                planName = planName,
                groups = groups,
                completedSets = completedSets,
                totalSets = totalSets,
                isComplete = isComplete,
                defaultRestSeconds = rest,
                totalExercises = groups.size
            )
        }

        val currentExerciseIndex = groups.indexOfFirst { it.exercise.id == currentSet.exerciseId }
        val currentGroup = groups.getOrNull(currentExerciseIndex)
        val currentSetIndexInExercise = currentGroup?.sets?.indexOfFirst { it.id == currentSet.id } ?: 0
        val lastSet = workoutRepo.getLastSetForExercise(currentSet.exerciseId)

        return CoachUiState(
            workout = workout,
            planName = planName,
            groups = groups,
            currentSet = currentSet,
            currentExercise = currentGroup?.exercise,
            currentExerciseIndex = currentExerciseIndex,
            currentSetIndexInExercise = currentSetIndexInExercise,
            totalExercises = groups.size,
            totalSetsInCurrentExercise = currentGroup?.sets?.size ?: 0,
            completedSets = completedSets,
            totalSets = totalSets,
            isComplete = false,
            lastSetForCurrent = lastSet,
            defaultRestSeconds = rest
        )
    }

    fun confirmCurrentSet(actualReps: Int, rpe: Int? = null, onDone: () -> Unit = {}) {
        val setId = state.value.currentSet?.id ?: return
        viewModelScope.launch {
            workoutRepo.confirmSet(setId, actualReps)
            if (rpe != null) {
                val updated = workoutRepo.getSet(setId)
                if (updated != null) {
                    workoutRepo.updateSet(updated.copy(rpe = rpe))
                }
            }
            onDone()
        }
    }

    fun skipCurrentSet() {
        val set = state.value.currentSet ?: return
        viewModelScope.launch {
            workoutRepo.deleteSet(set)
        }
    }

    fun finishWorkout(onDone: () -> Unit) {
        val id = state.value.workout?.id ?: run { onDone(); return }
        viewModelScope.launch {
            val prs = statsRepo.detectNewPRs(id)
            val withNames = prs.map { p ->
                NewPrWithName(p, exerciseRepo.get(p.exerciseId)?.name ?: "?")
            }
            val tips = statsRepo.progressionTipsForWorkout(id)
            workoutRepo.finish(id)
            if (withNames.isNotEmpty() || tips.isNotEmpty()) {
                _pendingPRs.value = withNames
                _pendingTips.value = tips
            } else {
                onDone()
            }
        }
    }

    fun setNotes(notes: String) {
        val w = state.value.workout ?: return
        viewModelScope.launch {
            workoutRepo.updateWorkout(w.copy(notes = notes))
        }
    }
}
