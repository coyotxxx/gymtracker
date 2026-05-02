package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.WeeklyReportService
import javax.inject.Inject

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val report: String? = null,
    val error: String? = null,
    val generatedAt: Long? = null
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val service: WeeklyReportService
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyReportUiState())
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    fun generate() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = service.generate()
            result.fold(
                onSuccess = { md ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            report = md,
                            error = null,
                            generatedAt = System.currentTimeMillis()
                        )
                    }
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

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
