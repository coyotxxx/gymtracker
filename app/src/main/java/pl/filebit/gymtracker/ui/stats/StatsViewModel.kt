package pl.filebit.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.Achievement
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
    val muscleVolumePeriod: VolumePeriod = VolumePeriod.WEEK_1,
    val daysToWeekEnd: Int = 0,
    val personalRecords: List<PersonalRecordRow> = emptyList(),
    val recovery: List<MuscleRecovery> = emptyList(),
    val stagnations: List<StagnationAlert> = emptyList(),
    val calendarHeatmap: Map<Long, Double> = emptyMap(),  // epochDay → volume
    val achievements: List<Achievement> = emptyList()    // dla osobnego ekranu Odznaki
)

enum class VolumePeriod(val weeks: Int, val label: String) {
    WEEK_1(1, "Ten tydzień"),
    WEEK_2(2, "Ostatnie 2 tyg"),
    WEEK_4(4, "Ostatnie 4 tyg")
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val volumeService: pl.filebit.gymtracker.data.repository.VolumeService
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun setVolumePeriod(p: VolumePeriod) {
        if (_state.value.muscleVolumePeriod == p) return
        viewModelScope.launch {
            val report = runCatching { volumeService.reportForWeeks(p.weeks) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                muscleVolumePeriod = p,
                muscleVolumeReport = report
            )
        }
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = StatsUiState(loading = true)

            val period = _state.value.muscleVolumePeriod
            val cal = java.util.Calendar.getInstance().apply { firstDayOfWeek = java.util.Calendar.MONDAY }
            val isoDay = (cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7 + 1
            val daysLeft = (7 - isoDay).coerceAtLeast(0)

            // === RÓWNOLEGLE — async × 9 (zamiast sekwencyjnie). ~3-5x szybsze. ===
            coroutineScope {
                val overviewD = async { statsRepo.overview() } // może rzucić — propaguje (jak w oryginale)
                val vol26D = async { runCatching { statsRepo.volumePerWeek(26) }.getOrDefault(emptyList()) }
                val muscleD = async { runCatching { volumeService.reportForWeeks(period.weeks) }.getOrDefault(emptyList()) }
                val prsD = async { runCatching { statsRepo.allPersonalRecords() }.getOrDefault(emptyList()) }
                val recoveryD = async { runCatching { statsRepo.recoveryByMuscle() }.getOrDefault(emptyList()) }
                val stagnD = async { runCatching { statsRepo.allStagnations() }.getOrDefault(emptyList()) }
                val heatmapD = async { runCatching { statsRepo.calendarHeatmap(84) }.getOrDefault(emptyMap()) }
                val profileD = async { profileRepo.get() }

                val o = overviewD.await()
                val vol26 = vol26D.await()
                val profile = profileD.await()
                val target = profile.daysPerWeek.coerceAtLeast(1)

                // achievements zależy od target — uruchom dopiero teraz, ale to też async OK
                val achievementsD = async { runCatching { statsRepo.unlockedAchievements(target) }.getOrDefault(emptyList()) }

                val vol8 = vol26.takeLast(8)
                val volWeek = vol26.lastOrNull() ?: 0.0
                val volPrev = vol26.dropLast(1).lastOrNull() ?: 0.0
                val delta = if (volPrev > 0) ((volWeek - volPrev) / volPrev * 100.0) else 0.0
                val avgDuration = if (o.totalWorkouts > 0) o.totalDurationMillis / o.totalWorkouts else 0L

                _state.value = StatsUiState(
                    loading = false,
                    overview = o,
                    volumeWeek = volWeek,
                    volumeWeekDelta = delta,
                    avgWorkoutDurationMillis = avgDuration,
                    volumePerWeek8 = vol8,
                    volumePerWeek26 = vol26,
                    muscleVolumeReport = muscleD.await(),
                    muscleVolumePeriod = period,
                    daysToWeekEnd = daysLeft,
                    personalRecords = prsD.await(),
                    recovery = recoveryD.await(),
                    stagnations = stagnD.await(),
                    calendarHeatmap = heatmapD.await(),
                    achievements = achievementsD.await()
                )
            }
        }
    }
}
