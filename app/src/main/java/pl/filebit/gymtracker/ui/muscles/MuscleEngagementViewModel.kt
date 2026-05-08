package pl.filebit.gymtracker.ui.muscles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.MuscleAnalysisReport
import pl.filebit.gymtracker.data.repository.MuscleEngagement
import pl.filebit.gymtracker.data.repository.StatsCacheService
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject

enum class EngagementPeriod(val days: Int, val labelRes: Int) {
    WEEK(7, pl.filebit.gymtracker.R.string.muscles_week),
    MONTH(30, pl.filebit.gymtracker.R.string.muscles_month),
    QUARTER(90, pl.filebit.gymtracker.R.string.muscles_quarter),
    ALL(0, pl.filebit.gymtracker.R.string.muscles_all)
}

data class MuscleUiState(
    val loading: Boolean = true,
    val period: EngagementPeriod = EngagementPeriod.WEEK,
    val engagement: List<MuscleEngagement> = emptyList(),
    val analysis: MuscleAnalysisReport? = null
)

@HiltViewModel
class MuscleEngagementViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val statsCacheService: StatsCacheService
) : ViewModel() {

    private val _state = MutableStateFlow(MuscleUiState())
    val state: StateFlow<MuscleUiState> = _state.asStateFlow()

    init { reload() }

    fun setPeriod(p: EngagementPeriod) {
        _state.value = _state.value.copy(period = p, loading = true)
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val period = _state.value.period.days
            // v1.11.41: oba wywołania używają tego samego snapshot — 3 queries dla
            // obu metod zamiast osobnych N+1 per metoda (~1140 queries → 3 queries).
            val snapshot = statsCacheService.snapshot()
            val list = statsRepo.muscleEngagementFast(period, snapshot)
            val analysis = statsRepo.muscleAnalysisFast(period, snapshot)
            _state.value = _state.value.copy(
                loading = false,
                engagement = list,
                analysis = analysis
            )
        }
    }
}
