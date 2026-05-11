package pl.filebit.gymtracker.ui.periodization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import javax.inject.Inject

/**
 * UI state karty mesocyklu — denormalized z entity dla łatwego rendera.
 */
data class MesocycleCardUi(
    val id: Long,
    val phase: MesocyclePhase,
    val statusLabel: String,       // "TRWA" / "UKOŃCZONY" / "POMINIĘTY"
    val statusColor: Color,
    val subtitle: String,          // "Tydzień 2/3 — 5 dni zostało" lub "3 tyg zakończony"
    val dateRangeText: String,     // "12.05 — 02.06" lub "Start: 12.05 (trwa)"
    val progressPct: Int,          // 0-100 (tylko dla ACTIVE)
    val daysRemainingLabel: String,// "5 dni" / "1 dzień"
    val notes: String              // z entity.notes
)

data class PeriodizationPlanUiState(
    val isLoading: Boolean = true,
    val activeCard: MesocycleCardUi? = null,
    val historyCards: List<MesocycleCardUi> = emptyList(),
    val cycles: List<TrainingMesocycle> = emptyList()
)

@HiltViewModel
class PeriodizationPlanViewModel @Inject constructor(
    private val mesoDao: TrainingMesocycleDao
) : ViewModel() {

    val state: StateFlow<PeriodizationPlanUiState> = mesoDao.observeRecent(12)
        .map { cycles ->
            mapToUiState(cycles, System.currentTimeMillis())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PeriodizationPlanUiState(isLoading = true)
        )
}

/**
 * Pure function — mapping listy mesocykli do UI state.
 * Top-level dla testowalności bez DAO.
 */
internal fun mapToUiState(
    cycles: List<TrainingMesocycle>,
    nowMs: Long
): PeriodizationPlanUiState {
    val active = cycles.firstOrNull { it.status == MesocycleStatus.ACTIVE }
    val history = cycles.filter { it.status != MesocycleStatus.ACTIVE }
        .sortedByDescending { it.startDateMs }

    return PeriodizationPlanUiState(
        isLoading = false,
        activeCard = active?.let { mapToCardUi(it, nowMs, emphasized = true) },
        historyCards = history.map { mapToCardUi(it, nowMs, emphasized = false) },
        cycles = cycles
    )
}

internal fun mapToCardUi(
    meso: TrainingMesocycle,
    nowMs: Long,
    emphasized: Boolean
): MesocycleCardUi {
    val df = java.text.SimpleDateFormat("d.MM", java.util.Locale("pl", "PL"))
    val startStr = df.format(java.util.Date(meso.startDateMs))
    val endStr = (meso.endDateMs ?: meso.plannedEndDateMs).let {
        df.format(java.util.Date(it))
    }

    val (statusLabel, statusColor) = when (meso.status) {
        MesocycleStatus.ACTIVE -> "TRWA" to AccentOrange
        MesocycleStatus.COMPLETED -> "UKOŃCZONY" to SuccessGreen
        MesocycleStatus.SKIPPED -> "POMINIĘTY" to DarkOnSurfaceVariant
        MesocycleStatus.PLANNED -> "PLANOWANY" to DarkOnSurfaceVariant
    }

    val daysRemaining = meso.daysRemaining(nowMs)
    val daysElapsed = meso.daysSinceStart(nowMs)
    val totalDays = meso.totalDaysPlanned
    val progressPct = if (meso.status == MesocycleStatus.ACTIVE && totalDays > 0) {
        ((daysElapsed.toDouble() / totalDays) * 100).toInt().coerceIn(0, 100)
    } else 100

    val subtitle = when (meso.status) {
        MesocycleStatus.ACTIVE -> "Tydzień ${meso.weekInPhase}/${meso.phaseLengthWeeks} • ${meso.phase.shortDesc()}"
        MesocycleStatus.COMPLETED -> "${meso.phaseLengthWeeks} tyg • ${meso.phase.shortDesc()}"
        else -> meso.phase.shortDesc()
    }

    val dateRangeText = if (meso.status == MesocycleStatus.ACTIVE) {
        "Start: $startStr → planowany koniec: $endStr"
    } else {
        "$startStr — $endStr"
    }

    val daysRemainingLabel = when {
        daysRemaining == 1 -> "1 dzień"
        daysRemaining in 2..4 -> "$daysRemaining dni"
        else -> "$daysRemaining dni"
    }

    return MesocycleCardUi(
        id = meso.id,
        phase = meso.phase,
        statusLabel = statusLabel,
        statusColor = statusColor,
        subtitle = subtitle,
        dateRangeText = dateRangeText,
        progressPct = progressPct,
        daysRemainingLabel = daysRemainingLabel,
        notes = meso.notes
    )
}
