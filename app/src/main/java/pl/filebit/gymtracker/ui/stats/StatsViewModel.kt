package pl.filebit.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.MuscleRecovery
import pl.filebit.gymtracker.data.repository.OverviewStats
import pl.filebit.gymtracker.data.repository.PersonalRecordRow
import pl.filebit.gymtracker.data.repository.StagnationAlert
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class StatsUiState(
    val loading: Boolean = true,
    val overview: OverviewStats? = null,
    val volumeWeek: Double = 0.0,
    val volumeWeekDelta: Double = 0.0,    // % zmiana vs poprzedni tydzień
    val avgWorkoutDurationMillis: Long = 0L,
    val volumePerWeek8: List<Double> = emptyList(),
    val volumePerWeek26: List<Double> = emptyList(),
    val muscleVolumeReport: List<pl.filebit.gymtracker.util.MuscleVolumeReport> = emptyList(),
    val personalRecords: List<PersonalRecordRow> = emptyList(),
    val recovery: List<MuscleRecovery> = emptyList(),
    val stagnations: List<StagnationAlert> = emptyList(),
    val calendarHeatmap: Map<Long, Double> = emptyMap()  // epochDay → volume
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val volumeService: pl.filebit.gymtracker.data.repository.VolumeService
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.value = StatsUiState(loading = true)
            val o = statsRepo.overview()
            val vol26 = runCatching { statsRepo.volumePerWeek(26) }.getOrDefault(emptyList())
            val vol8 = vol26.takeLast(8)
            val volWeek = vol26.lastOrNull() ?: 0.0
            val volPrev = vol26.dropLast(1).lastOrNull() ?: 0.0
            val delta = if (volPrev > 0) ((volWeek - volPrev) / volPrev * 100.0) else 0.0

            val avgDuration = if (o.totalWorkouts > 0) o.totalDurationMillis / o.totalWorkouts else 0L

            val muscleReport = runCatching { volumeService.currentWeekReport() }.getOrDefault(emptyList())
            val prs = runCatching { statsRepo.allPersonalRecords() }.getOrDefault(emptyList())
            val recovery = runCatching { statsRepo.recoveryByMuscle() }.getOrDefault(emptyList())
            val stagnations = runCatching { statsRepo.allStagnations() }.getOrDefault(emptyList())
            val heatmap = runCatching { statsRepo.calendarHeatmap(84) }.getOrDefault(emptyMap())

            _state.value = StatsUiState(
                loading = false,
                overview = o,
                volumeWeek = volWeek,
                volumeWeekDelta = delta,
                avgWorkoutDurationMillis = avgDuration,
                volumePerWeek8 = vol8,
                volumePerWeek26 = vol26,
                muscleVolumeReport = muscleReport,
                personalRecords = prs,
                recovery = recovery,
                stagnations = stagnations,
                calendarHeatmap = heatmap
            )
        }
    }
}
