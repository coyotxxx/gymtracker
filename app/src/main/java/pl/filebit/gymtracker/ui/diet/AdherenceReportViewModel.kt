package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import javax.inject.Inject

data class AdherenceReportState(
    val loading: Boolean = true,
    val last7Days: AdherenceSummary = AdherenceSummary(),
    val last14Days: AdherenceSummary = AdherenceSummary(),
    val recentDays: List<AdherenceLog> = emptyList()
)

@HiltViewModel
class AdherenceReportViewModel @Inject constructor(
    private val calc: AdherenceCalculator
) : ViewModel() {

    private val _state = MutableStateFlow(AdherenceReportState())
    val state: StateFlow<AdherenceReportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Najpierw upewniamy się że dziś jest policzone
            runCatching { calc.computeForToday() }
            val last7 = calc.avgAdherenceLastDays(7)
            val last14 = calc.avgAdherenceLastDays(14)
            val recent = calc.getRecent(14)
            _state.value = AdherenceReportState(
                loading = false,
                last7Days = last7,
                last14Days = last14,
                recentDays = recent
            )
        }
    }
}
