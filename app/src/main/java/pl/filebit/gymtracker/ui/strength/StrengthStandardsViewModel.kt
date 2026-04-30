package pl.filebit.gymtracker.ui.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.StrengthEvaluation
import pl.filebit.gymtracker.data.repository.StrengthRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class StrengthStandardsViewModel @Inject constructor(
    private val repo: StrengthRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _evaluations = MutableStateFlow<List<StrengthEvaluation>>(emptyList())
    val evaluations: StateFlow<List<StrengthEvaluation>> = _evaluations.asStateFlow()

    val profile: StateFlow<UserProfile> = profileRepo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    init {
        viewModelScope.launch { recompute() }
    }

    fun recompute() {
        viewModelScope.launch {
            _evaluations.value = repo.evaluateAll()
        }
    }

    fun saveBodyweightAndGender(bodyweightKg: Double?, gender: pl.filebit.gymtracker.data.entity.Gender) {
        viewModelScope.launch {
            val current = profileRepo.get()
            profileRepo.save(current.copy(bodyweightKg = bodyweightKg, gender = gender))
            recompute()
        }
    }
}
