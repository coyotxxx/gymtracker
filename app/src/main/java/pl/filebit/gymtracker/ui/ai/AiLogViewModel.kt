package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.entity.AiLog
import pl.filebit.gymtracker.data.repository.AiLogRepository
import javax.inject.Inject

@HiltViewModel
class AiLogViewModel @Inject constructor(
    private val repo: AiLogRepository,
    private val prefs: AiPreferences
) : ViewModel() {

    val logs: StateFlow<List<AiLog>> = repo.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loggingEnabled = MutableStateFlow(prefs.isLoggingEnabled())
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled.asStateFlow()

    private val _selectedLog = MutableStateFlow<AiLog?>(null)
    val selectedLog: StateFlow<AiLog?> = _selectedLog.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        prefs.setLoggingEnabled(enabled)
        _loggingEnabled.value = enabled
    }

    fun selectLog(log: AiLog?) {
        _selectedLog.value = log
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.deleteAll()
        }
    }

    fun deleteOlderThanDays(days: Int) {
        viewModelScope.launch {
            repo.deleteOlderThanDays(days)
        }
    }
}
