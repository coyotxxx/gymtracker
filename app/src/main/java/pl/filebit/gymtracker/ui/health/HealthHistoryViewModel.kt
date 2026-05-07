package pl.filebit.gymtracker.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.RecoveryLogDao
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.RecoveryLog
import javax.inject.Inject

/** Wpis historii — RecoveryLog + opcjonalna waga z BodyMeasurement dla tej samej daty. */
data class HealthHistoryEntry(
    val log: RecoveryLog,
    val weightKg: Double?
)

@HiltViewModel
class HealthHistoryViewModel @Inject constructor(
    recoveryDao: RecoveryLogDao,
    private val bodyDao: BodyMeasurementDao
) : ViewModel() {

    val entries: StateFlow<List<HealthHistoryEntry>> = combine(
        recoveryDao.observeRecent(60),
        bodyDao.observeAll()
    ) { logs, weights ->
        // Dla każdego loga znajdź wagę z tego samego dnia
        val dayMs = 24L * 3600 * 1000
        val validWeights = weights.filter { it.weightKg != null }
        logs.map { log ->
            val weight = validWeights.firstOrNull { m ->
                m.date >= log.dateMs && m.date < log.dateMs + dayMs
            }?.weightKg
            HealthHistoryEntry(log, weight)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
}
