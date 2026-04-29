package pl.filebit.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.data.repository.OverviewStats
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.StreakInfo
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WeekProgress
import javax.inject.Inject

data class StatsUiState(
    val loading: Boolean = true,
    val overview: OverviewStats? = null,
    val streak: StreakInfo? = null,
    val weekProgress: WeekProgress? = null,
    val achievements: List<Achievement> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.value = StatsUiState(loading = true)
            val o = statsRepo.overview()
            val streak = statsRepo.streakInfo()
            val profile = profileRepo.get()
            val target = profile.daysPerWeek.coerceAtLeast(1)
            val week = statsRepo.weekProgress(target)
            val achievements = statsRepo.unlockedAchievements(target)
            _state.value = StatsUiState(
                loading = false,
                overview = o,
                streak = streak,
                weekProgress = week,
                achievements = achievements
            )
        }
    }
}
