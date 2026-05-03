package pl.filebit.gymtracker.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

sealed class OnboardingNavState {
    object Loading : OnboardingNavState()
    object Needed : OnboardingNavState()
    object NotNeeded : OnboardingNavState()
}

data class ActiveWorkoutShellState(
    val workout: Workout? = null,
    val planName: String = "",      // pusty gdy ad-hoc
    val durationMillis: Long = 0L
) {
    val hasActive: Boolean get() = workout != null
    val isFromPlan: Boolean get() = workout?.fromPlanId != null
}

/**
 * Lekki VM dla globalnego shellu (mini-bar) — tickuje co sekundę gdy jest
 * aktywny trening, w innym wypadku siedzi cicho.
 */
@HiltViewModel
class ActiveWorkoutShellViewModel @Inject constructor(
    workoutRepo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _onboardingState = MutableStateFlow<OnboardingNavState>(OnboardingNavState.Loading)
    val onboardingState: StateFlow<OnboardingNavState> = _onboardingState.asStateFlow()

    init {
        viewModelScope.launch {
            val p = profileRepo.get()
            _onboardingState.value = if (p.onboardingCompleted)
                OnboardingNavState.NotNeeded
            else
                OnboardingNavState.Needed
        }
    }

    /** Wywoływane po zakończeniu onboardingu — przełącza state żeby UI mogło nawigować. */
    fun markOnboardingDone() {
        _onboardingState.value = OnboardingNavState.NotNeeded
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActiveWorkoutShellState> = workoutRepo.observeActive()
        .flatMapLatest { workout ->
            if (workout == null) {
                flowOf(ActiveWorkoutShellState())
            } else {
                flow {
                    val planName = workout.fromPlanId?.let { planRepo.getPlan(it)?.name } ?: ""
                    while (true) {
                        val now = System.currentTimeMillis()
                        emit(
                            ActiveWorkoutShellState(
                                workout = workout,
                                planName = planName,
                                durationMillis = now - workout.startedAt
                            )
                        )
                        delay(1000)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutShellState())
}
