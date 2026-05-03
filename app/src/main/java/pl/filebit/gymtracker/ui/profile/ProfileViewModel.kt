package pl.filebit.gymtracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: UserProfileRepository,
    private val proactiveScheduler: pl.filebit.gymtracker.service.ProactiveAiCheckScheduler
) : ViewModel() {

    val profile: StateFlow<UserProfile> = repo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    fun save(profile: UserProfile, onDone: () -> Unit) {
        viewModelScope.launch {
            val before = repo.get()
            repo.save(profile)
            // Sync proactive AI check scheduler z toggle
            if (before.aiProactiveChecksEnabled != profile.aiProactiveChecksEnabled) {
                if (profile.aiProactiveChecksEnabled) proactiveScheduler.schedulePeriodic()
                else proactiveScheduler.cancel()
            }
            onDone()
        }
    }

    /** Resetuje onboardingCompleted = false, by user mógł przejść kreator od nowa. */
    fun restartOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = repo.get()
            repo.save(current.copy(onboardingCompleted = false))
            onDone()
        }
    }
}
