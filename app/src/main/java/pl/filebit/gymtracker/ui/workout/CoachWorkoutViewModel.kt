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
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class CoachExerciseGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

sealed class AiOpinionState {
    object Idle : AiOpinionState()
    object Loading : AiOpinionState()
    data class Result(val opinion: pl.filebit.gymtracker.ai.RpeOpinion) : AiOpinionState()
    data class Error(val message: String) : AiOpinionState()
}

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
    val previousSessionSummaryForCurrent: String? = null,
    val suggestionForCurrent: pl.filebit.gymtracker.data.repository.NextSetSuggestion? = null,
    val defaultRestSeconds: Int = 90,
    val flashOnTimerEnd: Boolean = false
)

@HiltViewModel
class CoachWorkoutViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val exerciseRepo: ExerciseRepository,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val trainingDietBridge: TrainingDietBridge,
    private val aiSummaryService: pl.filebit.gymtracker.ai.WorkoutAiSummaryService,
    private val rpeOpinionService: pl.filebit.gymtracker.ai.RpeOpinionService
) : ViewModel() {

    private val _pendingPRs = MutableStateFlow<List<NewPrWithName>>(emptyList())
    val pendingPRs: StateFlow<List<NewPrWithName>> = _pendingPRs.asStateFlow()

    private val _pendingTips = MutableStateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>>(emptyList())
    val pendingTips: StateFlow<List<pl.filebit.gymtracker.data.repository.ProgressionTip>> = _pendingTips.asStateFlow()

    private val _pendingStagnation = MutableStateFlow<List<pl.filebit.gymtracker.data.repository.StagnationAlert>>(emptyList())
    val pendingStagnation: StateFlow<List<pl.filebit.gymtracker.data.repository.StagnationAlert>> = _pendingStagnation.asStateFlow()

    // AI opinion (per-set drugie zdanie) — odpalane na żądanie ikonką w UI
    private val _aiOpinion = MutableStateFlow<AiOpinionState>(AiOpinionState.Idle)
    val aiOpinion: StateFlow<AiOpinionState> = _aiOpinion.asStateFlow()

    private val _pendingFeedbackId = MutableStateFlow<Long?>(null)
    val pendingFeedbackId: StateFlow<Long?> = _pendingFeedbackId.asStateFlow()

    private var pendingOnDoneCallback: (() -> Unit)? = null

    fun consumePendingPRs() { _pendingPRs.value = emptyList(); tryFinishCallback() }
    fun consumePendingTips() { _pendingTips.value = emptyList(); tryFinishCallback() }

    /**
     * Aplikuje pojedynczą sugestię progresji do planu z którego startował trening.
     * Po wywołaniu tip znika z listy pendingTips. Gdy lista pusta — zamykamy dialog.
     */
    fun applyTip(tip: pl.filebit.gymtracker.data.repository.ProgressionTip) {
        val planId = state.value.workout?.fromPlanId ?: return
        viewModelScope.launch {
            planRepo.applyProgressionToPlan(
                planId = planId,
                exerciseId = tip.exerciseId,
                newWeightKg = tip.suggestedWeightKg,
                newReps = tip.suggestedReps.takeIf { it > 0 } ?: tip.currentReps
            )
            _pendingTips.value = _pendingTips.value.filterNot { it.exerciseId == tip.exerciseId }
            tryFinishCallback()
        }
    }

    fun applyAllTips() {
        val planId = state.value.workout?.fromPlanId ?: return
        val tips = _pendingTips.value
        viewModelScope.launch {
            for (tip in tips) {
                planRepo.applyProgressionToPlan(
                    planId = planId,
                    exerciseId = tip.exerciseId,
                    newWeightKg = tip.suggestedWeightKg,
                    newReps = tip.suggestedReps.takeIf { it > 0 } ?: tip.currentReps
                )
            }
            _pendingTips.value = emptyList()
            tryFinishCallback()
        }
    }
    fun consumePendingStagnation() { _pendingStagnation.value = emptyList(); tryFinishCallback() }
    fun dismissAiOpinion() { _aiOpinion.value = AiOpinionState.Idle }

    /**
     * Zapisuje post-workout feedback i (jeśli wszystko inne consumed) wywołuje
     * onDone z finishWorkout. Wywoływane z PostWorkoutFeedbackSheet.
     */
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
            _pendingFeedbackId.value == null
        ) {
            pendingOnDoneCallback?.invoke()
            pendingOnDoneCallback = null
        }
    }

    fun askAiOpinion(followUpMessage: String? = null) {
        val st = state.value
        val sug = st.suggestionForCurrent ?: return
        val ex = st.currentExercise ?: return
        val workoutId = st.workout?.id
        _aiOpinion.value = AiOpinionState.Loading
        viewModelScope.launch {
            val result = rpeOpinionService.ask(
                exerciseId = ex.id,
                suggestion = sug,
                excludeWorkoutId = workoutId,
                followUpMessage = followUpMessage
            )
            result.fold(
                onSuccess = { _aiOpinion.value = AiOpinionState.Result(it) },
                onFailure = { _aiOpinion.value = AiOpinionState.Error(it.message ?: "Błąd AI") }
            )
        }
    }

    /**
     * Aplikuje sugestię AI (waga + reps) na aktualną serię w coach mode.
     * Po zastosowaniu zamyka dialog opinii.
     */
    fun applyAiSuggestion(weightKg: Double, reps: Int) {
        val st = state.value
        val current = st.currentSet ?: return
        viewModelScope.launch {
            workoutRepo.updateSet(current.copy(weightKg = weightKg, reps = reps))
            _aiOpinion.value = AiOpinionState.Idle
        }
    }

    /**
     * v2.4.0 — ręczna edycja wartości bieżącej serii (tap na wielki blok).
     * Aktualizuje tylko podane (non-null) pola. Pozwala poprawić ciężar/powt./czas/
     * prędkość przed oznaczeniem serii jako wykonanej, bez wchodzenia w osobny ekran.
     */
    fun updateCurrentSetValues(
        weightKg: Double? = null,
        reps: Int? = null,
        durationSec: Int? = null,
        distanceM: Double? = null
    ) {
        val current = state.value.currentSet ?: return
        viewModelScope.launch {
            workoutRepo.updateSet(
                current.copy(
                    weightKg = weightKg ?: current.weightKg,
                    reps = reps ?: current.reps,
                    durationSec = durationSec ?: current.durationSec,
                    distanceM = distanceM ?: current.distanceM
                )
            )
        }
    }

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
                flashOnTimerEnd = profile.flashOnTimerEnd,
                totalExercises = groups.size
            )
        }

        val currentExerciseIndex = groups.indexOfFirst { it.exercise.id == currentSet.exerciseId }
        val currentGroup = groups.getOrNull(currentExerciseIndex)
        val currentSetIndexInExercise = currentGroup?.sets?.indexOfFirst { it.id == currentSet.id } ?: 0
        val lastSet = workoutRepo.getLastSetForExercise(currentSet.exerciseId)

        val prevSession = statsRepo.getPreviousSessionForExercise(
            currentSet.exerciseId, excludeWorkoutId = workout.id
        )
        val prevSummary = prevSession?.sets?.takeIf { it.isNotEmpty() }?.let { ss ->
            val maxW = ss.maxOf { it.weightKg }
            val repsList = ss.filter { it.weightKg == maxW }.joinToString(" / ") { it.reps.toString() }
            val rpeAvg = ss.mapNotNull { it.rpe }.takeIf { l -> l.isNotEmpty() }?.average()
            val rpePart = rpeAvg?.let { v -> " (RPE ${"%.1f".format(v)})" } ?: ""
            "${pl.filebit.gymtracker.util.formatWeight(maxW)} kg × $repsList$rpePart"
        }
        val suggestion = statsRepo.suggestNextSet(
            currentSet.exerciseId,
            excludeWorkoutId = workout.id,
            goal = profile.goal
        )

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
            previousSessionSummaryForCurrent = prevSummary,
            suggestionForCurrent = suggestion,
            defaultRestSeconds = rest,
            flashOnTimerEnd = profile.flashOnTimerEnd
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

    /**
     * Coach mode — wariant cardio: zatwierdza bieżącą serię czasem i/lub dystansem
     * (DURATION / DISTANCE_DURATION) zamiast powtórzeń.
     */
    fun confirmCurrentSetCardio(
        durationSec: Int?,
        distanceM: Double?,
        rpe: Int? = null,
        onDone: () -> Unit = {}
    ) {
        val setId = state.value.currentSet?.id ?: return
        viewModelScope.launch {
            workoutRepo.confirmSetCardio(setId, durationSec, distanceM)
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
        pendingOnDoneCallback = onDone
        viewModelScope.launch {
            val prs = statsRepo.detectNewPRs(id)
            val withNames = prs.map { p ->
                NewPrWithName(p, exerciseRepo.get(p.exerciseId)?.name ?: "?")
            }
            val tips = statsRepo.progressionTipsForWorkout(id)
            val stagnation = statsRepo.detectStagnation(id)
            workoutRepo.finish(id)
            // Aktualizuj TrainingDaySummary — most do modułu DIETA
            runCatching { trainingDietBridge.recomputeFromWorkout(id) }
            // AI summary w tle — nie blokuje wyjścia z ekranu
            generateAiSummaryInBackground(id)
            // Workout istnieje (miał sety) → pokaż feedback sheet
            val stillExists = workoutRepo.getWorkout(id)?.finishedAt != null
            if (stillExists) {
                _pendingFeedbackId.value = id
            }
            _pendingPRs.value = withNames
            _pendingTips.value = tips
            _pendingStagnation.value = stagnation
            tryFinishCallback()
        }
    }

    private fun generateAiSummaryInBackground(workoutId: Long) {
        viewModelScope.launch {
            aiSummaryService.generate(workoutId).onSuccess { text ->
                workoutRepo.setAiSummary(workoutId, text)
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
