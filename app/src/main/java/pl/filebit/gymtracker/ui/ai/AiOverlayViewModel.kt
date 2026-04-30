package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class AiOverlayState(
    val enabled: Boolean = false,
    val connected: Boolean = false
)

@HiltViewModel
class AiOverlayViewModel @Inject constructor(
    profileRepo: UserProfileRepository,
    private val aiPrefs: AiPreferences
) : ViewModel() {

    val state: StateFlow<AiOverlayState> = profileRepo.observe()
        .map { profile ->
            AiOverlayState(
                enabled = profile.aiOverlayEnabled,
                connected = aiPrefs.load().isConnected
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiOverlayState())
}
