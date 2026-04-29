package pl.filebit.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.OverviewStats
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject

data class StatsUiState(
    val loading: Boolean = true,
    val overview: OverviewStats? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepo: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.value = StatsUiState(loading = true)
            val o = statsRepo.overview()
            _state.value = StatsUiState(loading = false, overview = o)
        }
    }
}
