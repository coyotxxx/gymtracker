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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.NewPr
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class NewPrWithName(val pr: NewPr, val exerciseName: String)

data class ExerciseGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val lastSessionWeight: Double? = null,
    val lastSessionReps: Int? = null,
    val previousSessionSummary: String? = null,   // np. "Ostatnio: 80 kg × 8 / 8 / 6 (RPE 9)"
    val suggestion: pl.filebit.gymtracker.data.repository.NextSetSuggestion? = null
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
    private val statsRepo: StatsRepository,
    private val trainingDietBridge: pl.filebit.gymtracker.data.repository.TrainingDietBridge,
    private val aiSummaryService: pl.filebit.gymtracker.ai.WorkoutAiSummaryService,
    profileRepo: UserProfileRepository
) : ViewModel() {

    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing.asStateFlow()

    private val _pendingPRs = MutableStateFlow<List<NewPrWithName>>(emptyList())
    val pendingPRs: StateFlow<List<NewPrWithName>> = _pendingPRs.asStateFlow()

    private val _pendingTips = MutableStateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>>(emptyList())
    val pendingTips: StateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>> = _pendingTips.asStateFlow()

    private val _pendingStagnation = MutableStateFlow<List<pl.filebit.gymtracker.data.repository.StagnationAlert>>(emptyList())
    val pendingStagnation: StateFlow<List<pl.filebit.gymtracker.data.repository.StagnationAlert>> = _pendingStagnation.asStateFlow()

    private val _pendingFeedbackId = MutableStateFlow<Long?>(null)
    val pendingFeedbackId: StateFlow<Long?> = _pendingFeedbackId.asStateFlow()

    // v2.7.0: analiza po treningu leci w tle PO feedbacku; flaga blokuje
    // przedwczesne wyjście z ekranu.
    private val _analysisInFlight = MutableStateFlow(false)

    private var pendingOnDoneCallback: (() -> Unit)? = null

    fun consumePendingPRs() { _pendingPRs.value = emptyList(); tryFinishCallback() }
    fun consumePendingTips() { _pendingTips.value = emptyList(); tryFinishCallback() }
    fun consumePendingStagnation() { _pendingStagnation.value = emptyList(); tryFinishCallback() }

    fun consumePendingFeedback(
        save: Boolean,
        wellbeingRating: Int? = null,
        painArea: String? = null,
        painNotes: String? = null
    ) {
        val id = _pendingFeedbackId.value
        viewModelScope.launch {
            if (save && id != null) {
                workoutRepo.setPostWorkoutFeedback(id, wellbeingRating, painArea, painNotes)
            }
            _pendingFeedbackId.value = null
            tryFinishCallback()
        }
    }

    private fun tryFinishCallback() {
        if (_pendingPRs.value.isEmpty() &&
            _pendingTips.value.isEmpty() &&
            _pendingStagnation.value.isEmpty() &&
            _pendingFeedbackId.value == null &&
            !_analysisInFlight.value
        ) {
            pendingOnDoneCallback?.invoke()
            pendingOnDoneCallback = null
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepo.observeActive(),
        profileRepo.observe()
    ) { workout, profile -> workout to profile }
        .flatMapLatest { (workout, profile) ->
            if (workout == null) {
                flowOf(ActiveWorkoutUiState(workout = null, profile = profile))
            } else {
                // v1.29.13: obserwujemy też tabelę ćwiczeń — gdy seeder naprawi
                // metricType (np. cardio WEIGHT_REPS -> DISTANCE_DURATION),
                // trwający trening od razu przeładuje układ, bez restartu.
                combine(
                    workoutRepo.observeSetsForWorkout(workout.id),
                    exerciseRepo.observeAll()
                ) { sets, exercises -> sets to exercises }
                    .map { (sets, allExercises) ->
                        val exById = allExercises.associateBy { it.id }
                        val groups = sets.groupBy { it.exerciseId }
                                .toList()
                                .sortedBy { (_, list) -> list.minOf { it.orderIndex } }
                                .mapNotNull { (exerciseId, list) ->
                                    val ex = exById[exerciseId] ?: return@mapNotNull null
                                    val last = workoutRepo.getLastSetForExercise(exerciseId)
                                    val prevSession = statsRepo.getPreviousSessionForExercise(
                                        exerciseId, excludeWorkoutId = workout.id
                                    )
                                    val prevSummary = prevSession?.let {
                                        val sessionSets = it.sets
                                        if (sessionSets.isEmpty()) null
                                        else {
                                            val maxW = sessionSets.maxOf { s -> s.weightKg }
                                            val repsList = sessionSets
                                                .filter { s -> s.weightKg == maxW }
                                                .joinToString(" / ") { s -> s.reps.toString() }
                                            val rpeAvg = sessionSets.mapNotNull { s -> s.rpe }
                                                .takeIf { l -> l.isNotEmpty() }?.average()
                                            val rpePart = rpeAvg?.let { v -> " (RPE ${"%.1f".format(v)})" } ?: ""
                                            "${pl.filebit.gymtracker.util.formatWeight(maxW)} kg × $repsList$rpePart"
                                        }
                                    }
                                    val suggestion = statsRepo.suggestNextSet(
                                        exerciseId,
                                        excludeWorkoutId = workout.id,
                                        goal = profile.goal
                                    )
                                    ExerciseGroup(
                                        exercise = ex,
                                        sets = list.sortedBy { it.setNumber },
                                        lastSessionWeight = last?.weightKg,
                                        lastSessionReps = last?.reps,
                                        previousSessionSummary = prevSummary,
                                        suggestion = suggestion
                                    )
                                }
                        ActiveWorkoutUiState(workout = workout, groups = groups, profile = profile)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActiveWorkoutUiState()
        )

    fun addSet(
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        setType: pl.filebit.gymtracker.data.entity.SetType = pl.filebit.gymtracker.data.entity.SetType.NORMAL
    ) {
        val workoutId = state.value.workout?.id ?: return
        viewModelScope.launch {
            workoutRepo.addSet(
                workoutId = workoutId,
                exerciseId = exerciseId,
                reps = reps,
                weightKg = weightKg,
                setType = setType
            )
        }
    }

    fun setNotes(notes: String) {
        val w = state.value.workout ?: return
        viewModelScope.launch {
            workoutRepo.updateWorkout(w.copy(notes = notes))
        }
    }

    /**
     * Tworzy nowy aktywny trening jako kopia podanego treningu (placeholdery isCompleted=false).
     * Używane przy "Powtórz trening".
     */
    fun cloneFromWorkout(sourceWorkoutId: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            val src = workoutRepo.getWorkout(sourceWorkoutId) ?: return@launch
            val srcSets = workoutRepo.getSetsForWorkout(sourceWorkoutId)
            val active = workoutRepo.startOrResume()
            val ordered = srcSets.sortedWith(compareBy({ it.orderIndex }, { it.setNumber }))
            for (s in ordered) {
                workoutRepo.addPlannedSet(
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
        pendingOnDoneCallback = onDone
        viewModelScope.launch {
            // 1) NAJPIERW zapis treningu + most do diety
            workoutRepo.finish(id)
            runCatching { trainingDietBridge.recomputeFromWorkout(id) }
            _isFinishing.value = false
            // 2) Feedback NATYCHMIAST — nie czekamy na ciężkie analizy historii
            val stillExists = workoutRepo.getWorkout(id)?.finishedAt != null
            if (stillExists) _pendingFeedbackId.value = id
            // 3) AI summary w tle
            launch {
                aiSummaryService.generate(id).onSuccess { text ->
                    workoutRepo.setAiSummary(id, text)
                }
            }
            // 4) Analiza po treningu (PR/tipsy/stagnacja) — jednoprzebiegowo, w tle.
            //    Pokaże się dopiero PO zamknięciu feedbacku (gating w ekranie).
            _analysisInFlight.value = true
            launch {
                val analysis = runCatching { statsRepo.analyzePostWorkout(id) }.getOrNull()
                if (analysis != null) {
                    _pendingPRs.value = analysis.prs.map { p ->
                        NewPrWithName(p, exerciseRepo.get(p.exerciseId)?.name ?: "?")
                    }
                    _pendingTips.value = analysis.tips
                    _pendingStagnation.value = analysis.stagnations
                }
                _analysisInFlight.value = false
                tryFinishCallback()
            }
            if (!stillExists) tryFinishCallback()
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
