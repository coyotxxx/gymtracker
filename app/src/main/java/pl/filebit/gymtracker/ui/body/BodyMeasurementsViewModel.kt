package pl.filebit.gymtracker.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class BodyMeasurementsViewModel @Inject constructor(
    private val repo: BodyRepository,
    profileRepo: UserProfileRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val measurements: StateFlow<List<BodyMeasurement>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<UserProfile> = profileRepo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    fun upsert(m: BodyMeasurement) {
        viewModelScope.launch { repo.upsert(m) }
    }

    fun delete(m: BodyMeasurement) {
        viewModelScope.launch { repo.delete(m) }
    }
}
