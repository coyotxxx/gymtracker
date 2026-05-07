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

sealed class HealthScreenshotState {
    object Idle : HealthScreenshotState()
    object Analyzing : HealthScreenshotState()
    data class Success(val data: HealthScreenshotData) : HealthScreenshotState()
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

    fun analyzeImage(imageBytes: ByteArray, mimeType: String = "image/jpeg") {
        if (_state.value is HealthScreenshotState.Analyzing) return
        viewModelScope.launch {
            _state.value = HealthScreenshotState.Analyzing
            val result = analyzer.analyze(imageBytes, mimeType)
            _state.value = result.fold(
                onSuccess = { HealthScreenshotState.Success(it) },
                onFailure = { HealthScreenshotState.Error(it.message ?: "Nieznany błąd AI") }
            )
        }
    }

    /**
     * Zapisuje wyciągnięte dane do RecoveryLog (sen/stres/kalorie) i BodyMeasurement (waga).
     * Data: detectedDate jeśli AI ją wyciągnął, inaczej dzisiaj.
     */
    fun save(data: HealthScreenshotData) {
        viewModelScope.launch {
            val dateMs = parseDateOrToday(data.detectedDate)
            val saved = mutableListOf<String>()

            // RecoveryLog — upsert: scal z istniejącym jeśli już istnieje dla tej daty
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

            // BodyMeasurement (waga) — tylko jeśli waga widoczna
            if (data.weightKg != null && data.weightKg > 0) {
                bodyDao.upsert(BodyMeasurement(
                    date = dateMs,
                    weightKg = data.weightKg
                ))
                saved += "waga ${"%.1f".format(data.weightKg)}kg"
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
