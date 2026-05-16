package pl.filebit.gymtracker.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.ProgressPhotoDao
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.PhotoType
import pl.filebit.gymtracker.data.entity.ProgressPhoto
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.toDietGoal
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.util.TrendInfo
import pl.filebit.gymtracker.util.computeTrend
import pl.filebit.gymtracker.util.estimatedWeeksToGoal
import pl.filebit.gymtracker.util.progressToGoal
import javax.inject.Inject

enum class ChartRange(val days: Int?, val label: String) {
    D30(30, "30D"),
    D90(90, "90D"),
    D180(180, "180D"),
    D360(360, "360D"),
    All(null, "Wszystko")
}

data class MeasurementsUiState(
    val isLoading: Boolean = true,
    val measurements: List<BodyMeasurement> = emptyList(),
    val photos: Map<PhotoType, ProgressPhoto?> = emptyMap(),
    val latestWeight: Double? = null,
    val latestWaist: Double? = null,
    val latestBf: Double? = null,
    val daysSinceLastWeight: Int? = null,
    val weightTrend30d: TrendInfo? = null,
    val bfTrend30d: TrendInfo? = null,
    val waistTrend30d: TrendInfo? = null,
    val selectedRange: ChartRange = ChartRange.D90,
    val targetWeightKg: Double? = null,
    val goalType: WeightGoalType = WeightGoalType.NONE,
    val goalProgressPct: Double? = null,
    val etaWeeks: Int? = null,
    val savedToast: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MeasurementsViewModel @Inject constructor(
    private val bodyDao: BodyMeasurementDao,
    private val photoDao: ProgressPhotoDao,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MeasurementsUiState())
    val state: StateFlow<MeasurementsUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            bodyDao.observeAll().collect { all ->
                refreshFromMeasurements(all)
            }
        }
        viewModelScope.launch {
            photoDao.observeAll().collect { photos ->
                val map = PhotoType.entries.associateWith { type ->
                    photos.firstOrNull { it.photoType == type }
                }
                _state.update { it.copy(photos = map) }
            }
        }
    }

    private suspend fun refreshFromMeasurements(all: List<BodyMeasurement>) {
        val profile = profileRepo.get()
        val sortedDesc = all.sortedByDescending { it.date }

        val latestWithWeight = sortedDesc.firstOrNull { it.weightKg != null }
        val latestWithWaist = sortedDesc.firstOrNull { it.waistCm != null }
        val latestWithBf = sortedDesc.firstOrNull { it.bodyFatPercent != null }

        val now = System.currentTimeMillis()
        val daysSinceLastWeight = latestWithWeight?.let {
            ((now - it.date) / 86_400_000L).toInt()
        }

        val weightTrend = computeTrend(all, { it.weightKg }, rangeDays = 30, now = now)
        val bfTrend = computeTrend(all, { it.bodyFatPercent }, rangeDays = 30, now = now)
        val waistTrend = computeTrend(all, { it.waistCm }, rangeDays = 30, now = now)

        // Goal — start = pierwsza waga w okresie celu (najstarsza waga), aktualne = ostatnia
        val target = profile.targetWeightKg
        val current = latestWithWeight?.weightKg
        val (progressPct, eta) = if (target != null && current != null && profile.weightGoalType != WeightGoalType.NONE) {
            val firstWeight = sortedDesc.lastOrNull { it.weightKg != null }?.weightKg ?: current
            val pct = progressToGoal(startWeight = firstWeight, current = current, target = target)
            val deltaPerWeek = weightTrend?.deltaPerWeek ?: 0.0
            val weeks = estimatedWeeksToGoal(current, target, deltaPerWeek)
            pct to weeks
        } else {
            null to null
        }

        _state.update {
            it.copy(
                isLoading = false,
                measurements = sortedDesc,
                latestWeight = latestWithWeight?.weightKg,
                latestWaist = latestWithWaist?.waistCm,
                latestBf = latestWithBf?.bodyFatPercent,
                daysSinceLastWeight = daysSinceLastWeight,
                weightTrend30d = weightTrend,
                bfTrend30d = bfTrend,
                waistTrend30d = waistTrend,
                targetWeightKg = target,
                goalType = profile.weightGoalType,
                goalProgressPct = progressPct,
                etaWeeks = eta
            )
        }
    }

    fun setRange(range: ChartRange) {
        _state.update { it.copy(selectedRange = range) }
    }

    /**
     * Quick-add: zapisuje wagę z dzisiejszą datą. Jeśli już istnieje pomiar
     * z dziś (cokolwiek innego niż waga, np. obwody), aktualizuje wagę
     * w istniejącym wpisie zamiast tworzyć duplikat.
     */
    fun saveQuickWeight(kg: Double) {
        if (kg <= 0 || kg > 500) {
            _state.update { it.copy(errorMessage = "Nieprawidłowa waga: $kg kg") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val startOfDay = startOfTodayMillis()
            val endOfDay = startOfDay + 86_400_000L
            val existing = _state.value.measurements
                .firstOrNull { it.date in startOfDay until endOfDay }
            val updated = if (existing != null) {
                existing.copy(weightKg = kg)
            } else {
                BodyMeasurement(date = now, weightKg = kg)
            }
            bodyDao.upsert(updated)
            _state.update { it.copy(savedToast = "Zapisano $kg kg") }
        }
    }

    fun saveFullMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch {
            bodyDao.upsert(measurement)
            _state.update { it.copy(savedToast = "Pomiar zapisany") }
        }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            bodyDao.deleteById(id)
        }
    }

    fun setWeightGoal(target: Double?, type: WeightGoalType) {
        viewModelScope.launch {
            val cur = profileRepo.get()
            // v1.28.1 (Etap 2): cel = `goalType`. `weightGoalType` znormalizuje repo.
            profileRepo.save(cur.copy(targetWeightKg = target, goalType = type.toDietGoal()))
        }
    }

    fun consumeToast() = _state.update { it.copy(savedToast = null, errorMessage = null) }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
