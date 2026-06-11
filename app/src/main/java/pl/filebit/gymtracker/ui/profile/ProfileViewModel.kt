package pl.filebit.gymtracker.ui.profile

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
import pl.filebit.gymtracker.data.repository.PeriodizationPreferences
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

/** v1.19.0 — preferencje periodyzacji w UI (3 pola). */
data class PeriodizationUiPrefs(
    val mesocycleWeeks: Int = PeriodizationPreferences.DEFAULT_CYCLE_WEEKS,
    val deloadDays: Int = PeriodizationPreferences.DEFAULT_DELOAD_DAYS,
    val autoDeloadEnabled: Boolean = PeriodizationPreferences.DEFAULT_AUTO_DELOAD
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: UserProfileRepository,
    private val proactiveScheduler: pl.filebit.gymtracker.service.ProactiveAiCheckScheduler,
    private val weeklyReportScheduler: pl.filebit.gymtracker.service.WeeklyReportScheduler,
    private val periodizationPrefs: PeriodizationPreferences
) : ViewModel() {

    val profile: StateFlow<UserProfile> = repo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    private val _periodization = MutableStateFlow(loadPrefs())
    val periodization: StateFlow<PeriodizationUiPrefs> = _periodization.asStateFlow()

    private fun loadPrefs() = PeriodizationUiPrefs(
        mesocycleWeeks = periodizationPrefs.mesocycleWeeks(),
        deloadDays = periodizationPrefs.deloadDays(),
        autoDeloadEnabled = periodizationPrefs.autoDeloadEnabled()
    )

    fun setMesocycleWeeks(value: Int) {
        periodizationPrefs.setMesocycleWeeks(value)
        _periodization.value = loadPrefs()
    }

    fun setDeloadDays(value: Int) {
        periodizationPrefs.setDeloadDays(value)
        _periodization.value = loadPrefs()
    }

    fun setAutoDeloadEnabled(value: Boolean) {
        periodizationPrefs.setAutoDeloadEnabled(value)
        _periodization.value = loadPrefs()
    }

    fun save(profile: UserProfile, onDone: () -> Unit) {
        viewModelScope.launch {
            val before = repo.get()
            // v1.28.1 (Etap 2): cel = jedno pole `goalType`. UserProfileRepository.save
            // normalizuje legacy `weightGoalType` — żadnej ręcznej synchronizacji nie trzeba.
            repo.save(profile)
            // Sync proactive AI check scheduler z toggle
            if (before.aiProactiveChecksEnabled != profile.aiProactiveChecksEnabled) {
                if (profile.aiProactiveChecksEnabled) proactiveScheduler.schedulePeriodic()
                else proactiveScheduler.cancel()
            }
            // v2.13.0 — sync auto-raport tygodniowy
            if (before.aiAutoGenerateWeeklyReports != profile.aiAutoGenerateWeeklyReports) {
                if (profile.aiAutoGenerateWeeklyReports) weeklyReportScheduler.schedulePeriodic()
                else weeklyReportScheduler.cancel()
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
