package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.WeeklyReportService
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.entity.AiWeeklyReport
import javax.inject.Inject

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val service: WeeklyReportService,
    private val reportDao: AiWeeklyReportDao
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyReportUiState())
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    val reports: StateFlow<List<AiWeeklyReport>> = reportDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun generate() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = service.generate()
            result.fold(
                onSuccess = {
                    // Wynik trafia do bazy automatycznie w service (onSuccess insert).
                    // Flow z reportDao.observeAll() podchwyci zmiany.
                    _state.update { it.copy(isLoading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Nieznany błąd"
                        )
                    }
                }
            )
        }
    }

    fun deleteReport(id: Long) {
        viewModelScope.launch { reportDao.delete(id) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
