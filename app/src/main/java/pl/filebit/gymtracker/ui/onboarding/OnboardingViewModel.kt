package pl.filebit.gymtracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class OnboardingUiState(
    val displayName: String = "",
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    val gender: Gender = Gender.MALE,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val bodyweightKg: Double? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setName(s: String) = _state.update { it.copy(displayName = s) }
    fun setGoal(g: TrainingGoal) = _state.update { it.copy(goal = g) }
    fun setExperience(e: ExperienceLevel) = _state.update { it.copy(experience = e) }
    fun setGender(g: Gender) = _state.update { it.copy(gender = g) }
    fun setDaysPerWeek(d: Int) = _state.update { it.copy(daysPerWeek = d.coerceIn(1, 7)) }
    fun setSessionMinutes(m: Int) = _state.update { it.copy(sessionMinutes = m.coerceIn(15, 240)) }
    fun setBodyweight(kg: Double?) = _state.update { it.copy(bodyweightKg = kg) }

    /**
     * Zapisuje profil + ustawia onboardingCompleted = true.
     * @param onDone wywołuje się po zapisie (UI nawiguje do Home)
     */
    fun complete(onDone: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val current = profileRepo.get()
            val s = _state.value
            profileRepo.save(
                current.copy(
                    displayName = s.displayName.trim(),
                    goal = s.goal,
                    experience = s.experience,
                    gender = s.gender,
                    daysPerWeek = s.daysPerWeek,
                    sessionMinutes = s.sessionMinutes,
                    bodyweightKg = s.bodyweightKg,
                    onboardingCompleted = true
                )
            )
            _state.update { it.copy(isSaving = false) }
            onDone()
        }
    }

    /** Skip — zapisuje tylko flagę onboardingCompleted, reszta defaults. */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = profileRepo.get()
            profileRepo.save(current.copy(onboardingCompleted = true))
            onDone()
        }
    }
}

