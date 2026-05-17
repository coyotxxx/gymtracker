package pl.filebit.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.HydrationLog
import pl.filebit.gymtracker.data.entity.HydrationSource
import pl.filebit.gymtracker.data.repository.ActivityRepository
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.HydrationCalculator
import pl.filebit.gymtracker.data.repository.HydrationRepository
import pl.filebit.gymtracker.data.repository.RecoveryRepository
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

/**
 * v1.28.5 — ViewModel dziennych kafelków WODA / KROKI / REGEN.
 *
 * Kafelki przeniesione z ekranu Dieta na Home — to codzienne logi i powinny być
 * na wierzchu. Wydzielone jako samodzielny komponent: logika logowania (woda,
 * kroki, ankieta regeneracji) żyje TYLKO tutaj. Dane trzymane w repozytoriach,
 * więc dieta liczy TDEE/cel nawodnienia bez zmian — niezależnie od ekranu.
 */
@HiltViewModel
class DailyTilesViewModel @Inject constructor(
    private val hydrationRepo: HydrationRepository,
    private val hydrationCalc: HydrationCalculator,
    private val activityRepo: ActivityRepository,
    private val recoveryRepo: RecoveryRepository,
    private val profileRepo: UserProfileRepository,
    private val trainingDietBridge: TrainingDietBridge,
    private val dietPrefs: DietPreferences
) : ViewModel() {

    /** Początek dzisiejszego dnia — repozytoria bucketują po dniu. */
    private fun today(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // === KROKI ===
    private val _stepsToday = MutableStateFlow(0)
    val stepsToday: StateFlow<Int> = _stepsToday.asStateFlow()
    private val _showStepsDialog = MutableStateFlow(false)
    val showStepsDialog: StateFlow<Boolean> = _showStepsDialog.asStateFlow()
    private val _hcConnected = MutableStateFlow(false)
    val hcConnected: StateFlow<Boolean> = _hcConnected.asStateFlow()

    fun openStepsDialog() { _showStepsDialog.value = true }
    fun dismissStepsDialog() { _showStepsDialog.value = false }

    fun setSteps(steps: Int) {
        viewModelScope.launch {
            activityRepo.setSteps(today(), steps)
            refreshSteps()
        }
    }

    private suspend fun refreshSteps() {
        _stepsToday.value = activityRepo.getForDate(today())?.steps ?: 0
    }

    // === WODA ===
    private val _hydrationToday = MutableStateFlow(0)
    val hydrationToday: StateFlow<Int> = _hydrationToday.asStateFlow()
    private val _hydrationGoal = MutableStateFlow(2400)
    val hydrationGoal: StateFlow<Int> = _hydrationGoal.asStateFlow()
    private val _hydrationLogs = MutableStateFlow<List<HydrationLog>>(emptyList())
    val hydrationLogs: StateFlow<List<HydrationLog>> = _hydrationLogs.asStateFlow()
    private val _showHydrationDialog = MutableStateFlow(false)
    val showHydrationDialog: StateFlow<Boolean> = _showHydrationDialog.asStateFlow()

    fun openHydrationDialog() {
        _showHydrationDialog.value = true
        viewModelScope.launch { refreshHydrationLogs() }
    }
    fun dismissHydrationDialog() { _showHydrationDialog.value = false }

    fun addHydration(ml: Int, source: HydrationSource = HydrationSource.WATER) {
        viewModelScope.launch {
            hydrationRepo.add(today(), ml, source)
            refreshHydration()
            refreshHydrationLogs()
        }
    }

    fun deleteHydration(id: Long) {
        viewModelScope.launch {
            hydrationRepo.delete(id)
            refreshHydration()
            refreshHydrationLogs()
        }
    }

    private suspend fun refreshHydrationLogs() {
        _hydrationLogs.value = hydrationRepo.observeForDate(today()).first()
            .sortedByDescending { it.createdAt }
    }

    private suspend fun refreshHydration() {
        val date = today()
        _hydrationToday.value = hydrationRepo.sumForDate(date)
        val weight = profileRepo.get().bodyweightKg ?: 75.0
        val summary = runCatching { trainingDietBridge.getForDate(date) }.getOrNull()
        val goal = hydrationCalc.computeTarget(
            weightKg = weight,
            hadTrainingToday = summary?.isTrainingDay == true,
            proteinGramsToday = 0.0,
            usesCreatine = false,
            trainingDurationMin = summary?.durationMinutes?.takeIf { it > 0 }
        )
        _hydrationGoal.value = goal.totalMl
    }

    // === REGENERACJA (ankieta) ===
    private val _showRecoveryDialog = MutableStateFlow(false)
    val showRecoveryDialog: StateFlow<Boolean> = _showRecoveryDialog.asStateFlow()

    fun openRecoveryDialog() { _showRecoveryDialog.value = true }
    fun dismissRecoveryDialog() { _showRecoveryDialog.value = false }

    fun saveRecoveryLog(
        sleepHours: Double?,
        sleepQuality: Int?,
        stressLevel: Int?,
        hungerLevel: Int?,
        energyLevel: Int?,
        sorenessLevel: Int?,
        difficultyAdherence: Int?
    ) {
        viewModelScope.launch {
            recoveryRepo.upsert(
                dateMs = today(),
                sleepHours = sleepHours,
                sleepQuality = sleepQuality,
                stressLevel = stressLevel,
                hungerLevel = hungerLevel,
                energyLevel = energyLevel,
                sorenessLevel = sorenessLevel,
                difficultyAdherence = difficultyAdherence
            )
        }
    }

    init {
        viewModelScope.launch {
            runCatching { refreshSteps() }
            runCatching { refreshHydration() }
            _hcConnected.value =
                runCatching { dietPrefs.load().healthConnectSyncEnabled }.getOrDefault(false)
        }
    }
}
