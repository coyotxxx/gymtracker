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
    val countByType: Map<TrainingEventType, Int> = emptyMap(),
    // v1.11.74: expanded card details (lazy load po tapie)
    val expandedEventId: Long? = null,
    val expandedDetails: ExpandedPrDetails? = null,
    val isExpanding: Boolean = false,
    // v1.11.75: poprzedni PR per event (do highlight chip w collapsed view)
    val previousPrByEventId: Map<Long, PreviousPr> = emptyMap()
)

/**
 * v1.11.74: szczegóły rozwinięte dla PR_SET event.
 */
data class ExpandedPrDetails(
    val eventId: Long,
    val workoutDurationMin: Int,
    val totalVolumeKg: Double,
    val wellbeing: Int?,
    val avgRpe: Double?,
    val previousPr: PreviousPr?,         // poprzedni PR dla porównania (zmiana, dni temu)
    val sets: List<SetInfo>,             // wszystkie sety ćwiczenia z tego workoutu
    val prSetIndex: Int,                 // index PR setu (do highlight)
    val trendPoints: List<TrendPoint>    // 12 tyg, top e1RM per workout
)

data class PreviousPr(val weightKg: Double, val reps: Int, val daysAgo: Int)
data class SetInfo(val setNumber: Int, val weightKg: Double, val reps: Int, val rpe: Int?)
data class TrendPoint(val date: Long, val e1rmKg: Double)

@HiltViewModel
class EventTimelineViewModel @Inject constructor(
    private val eventDao: TrainingEventDao,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService
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
            // v1.11.75: dla każdego PR_SET znajdź poprzedni PR tego samego ćwiczenia
            val previousPrMap = computePreviousPrMap(all)
            _state.value = _state.value.copy(previousPrByEventId = previousPrMap)
            applyFilter(all, _state.value.filterType, countByType)
        }
    }

    /**
     * v1.11.75: dla każdego PR_SET buduje mapę eventId → PreviousPr.
     * Iteruje po PR-ach pogrupowanych per exerciseId, posortowanych chronologicznie.
     */
    private fun computePreviousPrMap(events: List<TrainingEvent>): Map<Long, PreviousPr> {
        val result = mutableMapOf<Long, PreviousPr>()
        events.filter { it.type == TrainingEventType.PR_SET }
            .groupBy { it.exerciseId }
            .forEach { (_, prs) ->
                val sorted = prs.sortedBy { it.date }
                for (i in 1 until sorted.size) {
                    val current = sorted[i]
                    val previous = sorted[i - 1]
                    val pw = previous.weightKg ?: continue
                    val pr = previous.reps ?: continue
                    val daysAgo = ((current.date - previous.date) / (24L * 3600_000)).toInt()
                    result[current.id] = PreviousPr(pw, pr, daysAgo)
                }
            }
        return result
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

    /**
     * v1.11.74: toggle expand. Jeśli ten sam event już rozwinięty → collapse.
     * Inaczej: load szczegóły z snapshot i ustaw expanded.
     */
    fun toggleExpand(eventId: Long) {
        val current = _state.value
        if (current.expandedEventId == eventId) {
            _state.value = current.copy(expandedEventId = null, expandedDetails = null)
            return
        }
        _state.value = current.copy(expandedEventId = eventId, isExpanding = true, expandedDetails = null)
        viewModelScope.launch {
            val event = current.allEvents.firstOrNull { it.id == eventId }
            if (event == null || event.type != TrainingEventType.PR_SET) {
                _state.value = _state.value.copy(isExpanding = false)
                return@launch
            }
            val details = loadPrDetails(event)
            _state.value = _state.value.copy(isExpanding = false, expandedDetails = details)
        }
    }

    private suspend fun loadPrDetails(event: TrainingEvent): ExpandedPrDetails? {
        val workoutId = event.workoutId ?: return null
        val exerciseId = event.exerciseId ?: return null
        val snapshot = statsCacheService.snapshot()
        val workout = snapshot.workoutsById[workoutId] ?: return null

        // Wszystkie sety z tego workoutu, dla tego ćwiczenia
        val setsForWorkout = (snapshot.completedSetsByWorkoutId[workoutId] ?: emptyList())
            .filter { it.exerciseId == exerciseId &&
                it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP }
            .sortedBy { it.setNumber }

        if (setsForWorkout.isEmpty()) return null

        val totalVolumeFromAll = (snapshot.completedSetsByWorkoutId[workoutId] ?: emptyList())
            .filter { it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP }
            .sumOf { it.weightKg * it.reps }
        val avgRpe = (snapshot.completedSetsByWorkoutId[workoutId] ?: emptyList())
            .mapNotNull { it.rpe?.toDouble() }
            .takeIf { it.isNotEmpty() }?.average()
        val durationMin = workout.finishedAt?.let { ((it - workout.startedAt) / 60_000).toInt() } ?: 0

        // Index setu który był PR (najwyższe e1RM)
        val prIndex = setsForWorkout.indices.maxByOrNull { idx ->
            val s = setsForWorkout[idx]
            s.weightKg * (1 + s.reps / 30.0)
        } ?: 0

        // Poprzedni PR dla tego ćwiczenia — szukamy event PR_SET wcześniej, z tym samym exerciseId
        val previousPrEvent = _state.value.allEvents.filter {
            it.type == TrainingEventType.PR_SET &&
                it.exerciseId == exerciseId &&
                it.date < event.date
        }.maxByOrNull { it.date }
        val previousPr = previousPrEvent?.let {
            val daysAgo = ((event.date - it.date) / (24L * 3600_000)).toInt()
            if (it.weightKg != null && it.reps != null) PreviousPr(it.weightKg, it.reps, daysAgo) else null
        }

        // Trend e1RM: top set per workout dla tego ćwiczenia, ostatnie 12 tyg
        val msPerWeek = 7L * 24 * 3600_000
        val cutoff = event.date - 12 * msPerWeek
        val trendPoints = snapshot.finishedWorkouts
            .filter { it.startedAt in cutoff..event.date }
            .mapNotNull { w ->
                val sets = (snapshot.completedSetsByWorkoutId[w.id] ?: emptyList())
                    .filter { it.exerciseId == exerciseId &&
                        it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP &&
                        it.weightKg > 0.0 && it.reps > 0 }
                if (sets.isEmpty()) return@mapNotNull null
                val topE1rm = sets.maxOf { it.weightKg * (1 + it.reps / 30.0) }
                TrendPoint(w.startedAt, topE1rm)
            }
            .sortedBy { it.date }

        return ExpandedPrDetails(
            eventId = event.id,
            workoutDurationMin = durationMin,
            totalVolumeKg = totalVolumeFromAll,
            wellbeing = workout.wellbeingRating,
            avgRpe = avgRpe,
            previousPr = previousPr,
            sets = setsForWorkout.map { SetInfo(it.setNumber, it.weightKg, it.reps, it.rpe) },
            prSetIndex = prIndex,
            trendPoints = trendPoints
        )
    }
}
