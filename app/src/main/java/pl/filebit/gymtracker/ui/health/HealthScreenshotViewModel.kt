package pl.filebit.gymtracker.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.HealthScreenshotAnalyzer
import pl.filebit.gymtracker.ai.HealthScreenshotData
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.RecoveryLogDao
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.RecoveryLog
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class AnalyzedScreenshot(
    val index: Int,                 // pozycja w liście wgranych
    val data: HealthScreenshotData,
    val errorMessage: String? = null
)

sealed class HealthScreenshotState {
    object Idle : HealthScreenshotState()
    data class Analyzing(val current: Int, val total: Int) : HealthScreenshotState()
    data class Reviewing(val results: List<AnalyzedScreenshot>) : HealthScreenshotState()
    data class Error(val message: String) : HealthScreenshotState()
    data class Saved(val savedFields: List<String>) : HealthScreenshotState()
}

@HiltViewModel
class HealthScreenshotViewModel @Inject constructor(
    private val analyzer: HealthScreenshotAnalyzer,
    private val recoveryLogDao: RecoveryLogDao,
    private val bodyDao: BodyMeasurementDao
) : ViewModel() {

    private val _state = MutableStateFlow<HealthScreenshotState>(HealthScreenshotState.Idle)
    val state: StateFlow<HealthScreenshotState> = _state.asStateFlow()

    /**
     * Analizuje listę zrzutów ekranu PO KOLEI (sekwencyjnie — żeby nie przeciążyć API).
     * Wynik = lista AnalyzedScreenshot (z opcjonalnym błędem per zdjęcie).
     */
    fun analyzeImages(images: List<Pair<ByteArray, String>>) {
        if (_state.value is HealthScreenshotState.Analyzing) return
        if (images.isEmpty()) return
        viewModelScope.launch {
            val results = mutableListOf<AnalyzedScreenshot>()
            for ((idx, pair) in images.withIndex()) {
                _state.value = HealthScreenshotState.Analyzing(idx + 1, images.size)
                val (bytes, mime) = pair
                val result = analyzer.analyze(bytes, mime)
                result.fold(
                    onSuccess = { data ->
                        results.add(AnalyzedScreenshot(idx, data))
                    },
                    onFailure = { err ->
                        // Zapisz placeholder z błędem — user widzi które zdjęcie nie wyszło
                        results.add(AnalyzedScreenshot(idx, HealthScreenshotData(), err.message))
                    }
                )
            }
            _state.value = HealthScreenshotState.Reviewing(results)
        }
    }

    /**
     * Zapisuje WSZYSTKIE zrecenzowane wyniki (po confirm). Per data — merge do
     * istniejącego RecoveryLog. Wagę aktualizujemy zawsze (nadpisujemy datę).
     */
    fun saveAll(results: List<AnalyzedScreenshot>) {
        viewModelScope.launch {
            val saved = mutableListOf<String>()
            for (r in results) {
                if (r.errorMessage != null) continue
                val data = r.data
                val dateMs = parseDateOrToday(data.detectedDate)

                val hasRecoveryData = data.sleepHours != null || data.stressLevel1to5 != null
                if (hasRecoveryData) {
                    val existing = recoveryLogDao.getForDate(dateMs)
                    val merged = (existing ?: RecoveryLog(dateMs = dateMs)).copy(
                        sleepHours = data.sleepHours ?: existing?.sleepHours,
                        stressLevel = data.stressLevel1to5 ?: existing?.stressLevel,
                        updatedAt = System.currentTimeMillis()
                    )
                    recoveryLogDao.insert(merged)
                    if (data.sleepHours != null) saved += "sen ${"%.1f".format(data.sleepHours)}h"
                    if (data.stressLevel1to5 != null) saved += "stres ${data.stressLevel1to5}/5"
                }

                if (data.weightKg != null && data.weightKg > 0) {
                    bodyDao.upsert(BodyMeasurement(
                        date = dateMs,
                        weightKg = data.weightKg
                    ))
                    saved += "waga ${"%.1f".format(data.weightKg)}kg"
                }
            }
            _state.value = HealthScreenshotState.Saved(saved)
        }
    }

    fun reset() { _state.value = HealthScreenshotState.Idle }

    private fun parseDateOrToday(detectedDate: String?): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        if (detectedDate != null) {
            try {
                val parts = detectedDate.split("-")
                if (parts.size == 3) {
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            } catch (_: Exception) { /* fallback do dzisiaj */ }
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
