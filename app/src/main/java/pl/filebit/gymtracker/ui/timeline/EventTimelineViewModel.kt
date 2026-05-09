package pl.filebit.gymtracker.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType
import javax.inject.Inject

/**
 * v1.11.72 — Event Timeline ViewModel.
 *
 * Pokazuje wszystkie eventy z `training_events` table chronologicznie
 * (od najnowszych). User może filtrować po typie.
 */
data class EventTimelineUiState(
    val isLoading: Boolean = true,
    val allEvents: List<TrainingEvent> = emptyList(),
    val filterType: TrainingEventType? = null,    // null = wszystkie
    val filteredEvents: List<TrainingEvent> = emptyList(),
    val groupedByMonth: Map<String, List<TrainingEvent>> = emptyMap(),
    val totalCount: Int = 0,
    val countByType: Map<TrainingEventType, Int> = emptyMap()
)

@HiltViewModel
class EventTimelineViewModel @Inject constructor(
    private val eventDao: TrainingEventDao
) : ViewModel() {
    private val _state = MutableStateFlow(EventTimelineUiState())
    val state: StateFlow<EventTimelineUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val all = eventDao.getRecent(limit = 1000)  // 1000 eventów wystarczy dla typowego usera 3 lata+
            val countByType = all.groupBy { it.type }.mapValues { it.value.size }
            applyFilter(all, _state.value.filterType, countByType)
        }
    }

    fun setFilter(type: TrainingEventType?) {
        val all = _state.value.allEvents.takeIf { it.isNotEmpty() } ?: return
        applyFilter(all, type, _state.value.countByType)
    }

    private fun applyFilter(
        all: List<TrainingEvent>,
        type: TrainingEventType?,
        countByType: Map<TrainingEventType, Int>
    ) {
        val filtered = if (type == null) all else all.filter { it.type == type }
        val df = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val grouped = filtered.groupBy { df.format(java.util.Date(it.date)) }
        _state.value = _state.value.copy(
            isLoading = false,
            allEvents = all,
            filterType = type,
            filteredEvents = filtered,
            groupedByMonth = grouped,
            totalCount = all.size,
            countByType = countByType
        )
    }
}
