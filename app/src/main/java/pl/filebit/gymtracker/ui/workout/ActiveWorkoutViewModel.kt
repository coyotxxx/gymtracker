package pl.filebit.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class ExerciseGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val lastSessionWeight: Double? = null,
    val lastSessionReps: Int? = null
)

data class ActiveWorkoutUiState(
    val workout: Workout? = null,
    val groups: List<ExerciseGroup> = emptyList(),
    val profile: UserProfile = UserProfile()
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val planRepo: PlanRepository,
    profileRepo: UserProfileRepository
) : ViewModel() {

    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepo.observeActive(),
        profileRepo.observe()
    ) { workout, profile -> workout to profile }
        .flatMapLatest { (workout, profile) ->
            if (workout == null) {
                flowOf(ActiveWorkoutUiState(workout = null, profile = profile))
            } else {
                workoutRepo.observeSetsForWorkout(workout.id).let { setsFlow ->
                    kotlinx.coroutines.flow.flow {
                        setsFlow.collect { sets ->
                            val groups = sets.groupBy { it.exerciseId }
                                .toList()
                                .sortedBy { (_, list) -> list.minOf { it.orderIndex } }
                                .mapNotNull { (exerciseId, list) ->
                                    val ex = exerciseRepo.get(exerciseId) ?: return@mapNotNull null
                                    val last = workoutRepo.getLastSetForExercise(exerciseId)
                                    ExerciseGroup(
                                        exercise = ex,
                                        sets = list.sortedBy { it.setNumber },
                                        lastSessionWeight = last?.weightKg,
                                        lastSessionReps = last?.reps
                                    )
                                }
                            emit(ActiveWorkoutUiState(workout = workout, groups = groups, profile = profile))
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActiveWorkoutUiState()
        )

    fun addSet(exerciseId: Long, reps: Int, weightKg: Double, isWarmup: Boolean = false) {
        val workoutId = state.value.workout?.id ?: return
        viewModelScope.launch {
            workoutRepo.addSet(
                workoutId = workoutId,
                exerciseId = exerciseId,
                reps = reps,
                weightKg = weightKg,
                isWarmup = isWarmup
            )
        }
    }

    fun updateSet(set: WorkoutSet) {
        viewModelScope.launch { workoutRepo.updateSet(set) }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch { workoutRepo.deleteSet(set) }
    }

    fun removeExercise(exerciseId: Long) {
        val workoutId = state.value.workout?.id ?: return
        viewModelScope.launch {
            workoutRepo.removeExerciseFromWorkout(workoutId, exerciseId)
        }
    }

    fun finishWorkout(onDone: () -> Unit) {
        val id = state.value.workout?.id ?: return
        _isFinishing.value = true
        viewModelScope.launch {
            workoutRepo.finish(id)
            _isFinishing.value = false
            onDone()
        }
    }

    fun discardWorkout(onDone: () -> Unit) {
        viewModelScope.launch {
            workoutRepo.discardActive()
            onDone()
        }
    }

    /**
     * Zapisuje obecny trening jako szablon planu (TrainingPlan + PlanExercise).
     * Plan nie ma jeszcze nazwy ani dni — user uzupełni w PlanEdit.
     */
    fun saveAsPlan(onCreated: (Long) -> Unit) {
        val groups = state.value.groups
        if (groups.isEmpty()) return
        val srcWorkout = state.value.workout
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
