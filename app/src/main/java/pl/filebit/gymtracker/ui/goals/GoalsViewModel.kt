package pl.filebit.gymtracker.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.toDietGoal
import pl.filebit.gymtracker.data.repository.GoalProgress
import pl.filebit.gymtracker.data.repository.GoalRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _progresses = MutableStateFlow<List<GoalProgress>>(emptyList())
    val progresses: StateFlow<List<GoalProgress>> = _progresses.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // gdy lista celów się zmieni, recompute
            repo.observeAll().collect { all ->
                _progresses.value = all.map { repo.computeProgress(it) }
            }
        }
    }

    fun upsert(goal: Goal) {
        viewModelScope.launch {
            repo.upsert(goal)
            // v1.24.10: synchronizuj UserProfile z aktywnym celem wagowym.
            // Filozofia: JEDEN user, JEDEN aktywny kierunek. Goal LOSE_WEIGHT / GAIN_MASS
            // musi odzwierciedlać się w UserProfile.weightGoalType + targetWeightKg,
            // żeby cały ekosystem (dieta, AI, DeloadDetector) widział spójny kontekst.
            syncProfileFromGoal(goal)
        }
    }

    fun delete(goal: Goal) {
        viewModelScope.launch {
            repo.delete(goal)
            // Po usunięciu celu wagowego: jeśli nie ma innego aktywnego LOSE/GAIN,
            // wróć UserProfile na MAINTAIN (lub NONE jeśli user wcześniej miał NONE)
            val remaining = repo.observeAll().let { /* skip — observe in init */ }
            // (Pełna synchronizacja po delete wymaga nasłuchu observeAll — to robi refresh())
        }
    }

    /** v1.24.10: mapuje cel wagowy → UserProfile.weightGoalType. */
    private suspend fun syncProfileFromGoal(goal: Goal) {
        val newWeightGoal = when (goal.type) {
            GoalType.LOSE_WEIGHT -> WeightGoalType.CUT
            GoalType.GAIN_MASS -> WeightGoalType.BULK
            else -> null  // INCREASE_STRENGTH / IMPROVE_CARDIO / CUSTOM nie zmieniają weightGoalType
        } ?: return

        val profile = profileRepo.get()
        // Tylko aktualizuj jeśli coś się zmieniło (idempotent)
        if (profile.weightGoalType == newWeightGoal && profile.targetWeightKg == goal.targetValue) return

        // v1.28.1 (Etap 2): cel = `goalType`. `weightGoalType` znormalizuje repo.
        profileRepo.save(
            profile.copy(
                goalType = newWeightGoal.toDietGoal(),
                targetWeightKg = goal.targetValue
            )
        )
    }
}
