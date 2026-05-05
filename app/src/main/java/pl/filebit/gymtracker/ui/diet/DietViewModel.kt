package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ai.DietAiService
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.MealEntryWithMacros
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.macrosFor
import pl.filebit.gymtracker.service.DietReminderScheduler
import pl.filebit.gymtracker.util.DailyMacroGoal
import pl.filebit.gymtracker.util.computeDailyGoal
import javax.inject.Inject

/** Sumy dnia: kcal i 3 makro. */
data class DayTotals(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
) {
    operator fun plus(m: MealEntryWithMacros) = DayTotals(
        kcal + m.kcal, protein + m.protein, carbs + m.carbs, fat + m.fat
    )
}

data class MealGroup(
    val type: MealType,
    val entries: List<MealEntryWithMacros>,
    val totals: DayTotals,
    /** Godzina posiłku z DietConfig (np. "12:00"). Pusty gdy slot nieaktywny. */
    val timeLabel: String = "",
    val customLabel: String = ""    // np. "Drugie śniadanie" gdy 4 posiłki
)

data class DietUiState(
    val loading: Boolean = true,
    val dateMs: Long = 0L,            // start dnia (00:00 lokalny)
    val goal: DailyMacroGoal = DailyMacroGoal(
        kcal = 0,
        proteinG = 0,
        carbsG = 0,
        fatG = 0,
        breakdown = pl.filebit.gymtracker.util.GoalBreakdown(
            weightKg = 0.0,
            genderLabel = "",
            tdeeKcal = 0,
            tdeeFormulaText = "",
            deficitOrSurplus = 0,
            deficitLabel = "",
            isManualOverride = false,
            proteinPerKg = 0.0,
            fatPerKg = 0.0,
            carbsCalculation = ""
        )
    ),
    val totals: DayTotals = DayTotals(),
    val groups: List<MealGroup> = emptyList(),
    val productsAll: List<FoodProduct> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: FoodCategory? = null,
    val filteredProducts: List<FoodProduct> = emptyList(),
    val config: DietConfig = DietConfig(),
    val perMealKcal: Int = 0          // cel kcal podzielony przez liczbę posiłków
)

sealed class AiPlanState {
    object Idle : AiPlanState()
    object Loading : AiPlanState()
    data class Success(val message: String) : AiPlanState()
    data class Error(val message: String) : AiPlanState()
}

@HiltViewModel
class DietViewModel @Inject constructor(
    private val repo: DietRepository,
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val dietPrefs: DietPreferences,
    private val reminderScheduler: DietReminderScheduler,
    private val dietAi: DietAiService,
    private val trainingDietBridge: pl.filebit.gymtracker.data.repository.TrainingDietBridge,
    private val adherenceCalc: pl.filebit.gymtracker.data.repository.AdherenceCalculator,
    private val autoAdjust: pl.filebit.gymtracker.data.repository.AutoAdjustmentService,
    private val dietAdjustmentScheduler: pl.filebit.gymtracker.service.DietAutoAdjustmentScheduler,
    private val mealFeedbackRepo: pl.filebit.gymtracker.data.repository.MealFeedbackRepository,
    private val substituteService: pl.filebit.gymtracker.data.repository.SubstituteService,
    private val hydrationRepo: pl.filebit.gymtracker.data.repository.HydrationRepository,
    private val hydrationCalc: pl.filebit.gymtracker.data.repository.HydrationCalculator,
    private val recoveryRepo: pl.filebit.gymtracker.data.repository.RecoveryRepository,
    private val qualityScorer: pl.filebit.gymtracker.data.repository.DailyQualityScorer,
    private val weeklyBudgetCalc: pl.filebit.gymtracker.data.repository.WeeklyBudgetCalculator,
    private val activityRepo: pl.filebit.gymtracker.data.repository.ActivityRepository,
    private val phaseRepo: pl.filebit.gymtracker.data.repository.DietPhaseRepository,
    private val phaseManager: pl.filebit.gymtracker.data.repository.PhaseManager,
    private val recoveryAnalyzer: pl.filebit.gymtracker.data.repository.RecoveryAnalyzer,
    private val emergencyMealGen: pl.filebit.gymtracker.ai.EmergencyMealGenerator,
    private val damageControl: pl.filebit.gymtracker.data.repository.DamageControl,
    private val healthConnect: pl.filebit.gymtracker.data.health.HealthConnectManager,
    private val healthConnectScheduler: pl.filebit.gymtracker.service.HealthConnectSyncScheduler,
    private val consumptionRepo: pl.filebit.gymtracker.data.repository.MealConsumptionRepository
) : ViewModel() {

    // === MEAL CONSUMPTION STATUS ===
    private val _consumptions = MutableStateFlow<Map<MealType, pl.filebit.gymtracker.data.entity.MealConsumptionStatus>>(emptyMap())
    val consumptions: StateFlow<Map<MealType, pl.filebit.gymtracker.data.entity.MealConsumptionStatus>> = _consumptions.asStateFlow()

    fun cycleConsumption(mealType: MealType) {
        viewModelScope.launch {
            consumptionRepo.cycleStatus(_selectedDateMs.value, mealType)
            refreshConsumptions()
        }
    }

    fun setConsumption(mealType: MealType, status: pl.filebit.gymtracker.data.entity.MealConsumptionStatus) {
        viewModelScope.launch {
            consumptionRepo.setStatus(_selectedDateMs.value, mealType, status)
            refreshConsumptions()
        }
    }

    private suspend fun refreshConsumptions() {
        val list = consumptionRepo.getForDate(_selectedDateMs.value)
        _consumptions.value = list.associate { it.mealType to it.status }
    }

    // === HEALTH CONNECT ===
    private val _hcAvailability = MutableStateFlow(pl.filebit.gymtracker.data.health.HealthConnectAvailability.NOT_SUPPORTED)
    val hcAvailability: StateFlow<pl.filebit.gymtracker.data.health.HealthConnectAvailability> = _hcAvailability.asStateFlow()

    private val _hcHasPermission = MutableStateFlow(false)
    val hcHasPermission: StateFlow<Boolean> = _hcHasPermission.asStateFlow()

    fun refreshHealthConnect() {
        viewModelScope.launch {
            _hcAvailability.value = healthConnect.checkAvailability()
            _hcHasPermission.value = healthConnect.hasAllPermissions()

            // Jeśli włączony sync + permissions OK → pobierz kroki na dziś
            val cfg = dietPrefs.load()
            if (cfg.healthConnectSyncEnabled && _hcHasPermission.value) {
                runCatching {
                    val steps = healthConnect.readStepsForDate(_selectedDateMs.value)
                    if (steps > 0) {
                        activityRepo.setSteps(_selectedDateMs.value, steps,
                            source = pl.filebit.gymtracker.data.entity.ActivitySource.HEALTH_CONNECT)
                        refreshSteps()
                    }
                }
            }
        }
    }

    fun toggleHealthConnectSync(enabled: Boolean) {
        viewModelScope.launch {
            val cfg = dietPrefs.load()
            dietPrefs.save(cfg.copy(healthConnectSyncEnabled = enabled))
            if (enabled) healthConnectScheduler.schedule()
            else healthConnectScheduler.cancel()
            refreshHealthConnect()
        }
    }

    /**
     * Compose triggery dla permission flow + install Health Connect app.
     */
    private val _hcPermissionRequest = MutableStateFlow(false)
    val hcPermissionRequest: StateFlow<Boolean> = _hcPermissionRequest.asStateFlow()

    private val _hcInstallNeeded = MutableStateFlow(false)
    val hcInstallNeeded: StateFlow<Boolean> = _hcInstallNeeded.asStateFlow()

    /**
     * User kliknął "Włącz HC" — sprawdź dostępność, jeśli OK uruchom permission flow.
     */
    fun startHealthConnectEnableFlow() {
        viewModelScope.launch {
            val avail = healthConnect.checkAvailability()
            when (avail) {
                pl.filebit.gymtracker.data.health.HealthConnectAvailability.NOT_INSTALLED -> {
                    _hcInstallNeeded.value = true
                }
                pl.filebit.gymtracker.data.health.HealthConnectAvailability.NOT_SUPPORTED -> {
                    _hcInstallNeeded.value = true // pokażemy dialog z info
                }
                pl.filebit.gymtracker.data.health.HealthConnectAvailability.INSTALLED -> {
                    if (healthConnect.hasAllPermissions()) {
                        // Już mamy permission — od razu zapis + sync
                        toggleHealthConnectSync(true)
                        syncHealthConnectNow()
                    } else {
                        _hcPermissionRequest.value = true
                    }
                }
            }
        }
    }

    fun consumeHcPermissionRequest() { _hcPermissionRequest.value = false }
    fun consumeHcInstallNeeded() { _hcInstallNeeded.value = false }

    /** Po zwrocie z permission launcher — sprawdź wynik i zapisz/sync. */
    fun onHealthConnectPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val hasIt = healthConnect.hasAllPermissions()
            if (hasIt) {
                toggleHealthConnectSync(true)
                val steps = syncHealthConnectNow()
                _hcSyncMessage.value = when {
                    steps == 0 -> "✓ Permission OK, ale Health Connect zwrócił 0 kroków na dziś.\n\nMożliwe przyczyny:\n• Brak providera kroków (Google Fit / krokomierz)\n• Telefon nie liczył dziś kroków\n• Sync wewnętrzny HC jeszcze nie zaszedł\n\nKliknij '🔄 Sync HC' w kaflu kroków po zsync'owaniu się Twojego krokomierza."
                    else -> "✓ Pobrano $steps kroków z Health Connect"
                }
            } else {
                _hcSyncMessage.value = "✗ Brak zgody na READ_STEPS — Health Connect wyłączony"
                toggleHealthConnectSync(false)
            }
        }
    }

    fun openHealthConnectInstall(context: android.content.Context) {
        try {
            context.startActivity(healthConnect.providerInstallIntent())
        } catch (_: Exception) { /* brak Play Store — ignore */ }
        consumeHcInstallNeeded()
    }

    /** Permission contract dla Compose launcher. */
    fun healthConnectPermissionContract() = healthConnect.permissionContract()
    fun healthConnectPermissions() = healthConnect.permissionsToRequest()

    suspend fun syncHealthConnectNow(): Int {
        val steps = healthConnect.readStepsForDate(_selectedDateMs.value)
        if (steps > 0) {
            activityRepo.setSteps(_selectedDateMs.value, steps,
                source = pl.filebit.gymtracker.data.entity.ActivitySource.HEALTH_CONNECT)
            refreshSteps()
        }
        return steps
    }

    /**
     * Status sync HC z komunikatem dla usera.
     */
    private val _hcSyncMessage = MutableStateFlow<String?>(null)
    val hcSyncMessage: StateFlow<String?> = _hcSyncMessage.asStateFlow()

    fun consumeHcSyncMessage() { _hcSyncMessage.value = null }

    /**
     * Manualne uruchomienie sync z UI (przycisk "Sync HC").
     * Pokazuje Snackbar z wynikiem.
     */
    fun manualHealthConnectSync() {
        viewModelScope.launch {
            val avail = healthConnect.checkAvailability()
            if (avail != pl.filebit.gymtracker.data.health.HealthConnectAvailability.INSTALLED) {
                _hcSyncMessage.value = "Health Connect niedostępny na tym urządzeniu"
                return@launch
            }
            if (!healthConnect.hasAllPermissions()) {
                _hcSyncMessage.value = "Brak permission. Włącz toggle ponownie."
                return@launch
            }
            val steps = runCatching { healthConnect.readStepsForDate(_selectedDateMs.value) }.getOrNull() ?: -1
            _hcSyncMessage.value = when {
                steps < 0 -> "Błąd sync z Health Connect"
                steps == 0 -> "Health Connect zwrócił 0 kroków na dziś.\n\nMożliwe przyczyny:\n• Brak aplikacji która zapisuje kroki do HC (Google Fit / krokomierz / Samsung Health)\n• Telefon nie liczył dziś kroków\n• Provider nie zsync'ował się jeszcze\n\nW Health Connect sprawdź zakładkę 'Połączone aplikacje'."
                else -> {
                    activityRepo.setSteps(_selectedDateMs.value, steps,
                        source = pl.filebit.gymtracker.data.entity.ActivitySource.HEALTH_CONNECT)
                    refreshSteps()
                    "✓ Pobrano $steps kroków z Health Connect"
                }
            }
        }
    }

    // === EMERGENCY ===
    private val _showEmergencyDialog = MutableStateFlow(false)
    val showEmergencyDialog: StateFlow<Boolean> = _showEmergencyDialog.asStateFlow()

    private val _showDamageControlDialog = MutableStateFlow(false)
    val showDamageControlDialog: StateFlow<Boolean> = _showDamageControlDialog.asStateFlow()

    private val _damageControlResult = MutableStateFlow<pl.filebit.gymtracker.data.repository.DamageControlResult?>(null)
    val damageControlResult: StateFlow<pl.filebit.gymtracker.data.repository.DamageControlResult?> = _damageControlResult.asStateFlow()

    fun openEmergencyDialog() { _showEmergencyDialog.value = true }
    fun dismissEmergencyDialog() { _showEmergencyDialog.value = false }

    // === EMERGENCY MEAL GENERATION (AI) ===
    sealed class EmergencyMealState {
        object Idle : EmergencyMealState()
        object Loading : EmergencyMealState()
        data class Success(
            val recipe: pl.filebit.gymtracker.ai.AiMealRecipe,
            val mealType: MealType,
            val modeLabel: String
        ) : EmergencyMealState()
        data class Error(val message: String) : EmergencyMealState()
        data class Saved(val mealName: String) : EmergencyMealState()
    }

    private val _emergencyMealState = MutableStateFlow<EmergencyMealState>(EmergencyMealState.Idle)
    val emergencyMealState: StateFlow<EmergencyMealState> = _emergencyMealState.asStateFlow()

    fun generateEmergencyMeal(mode: pl.filebit.gymtracker.ai.EmergencyMode) {
        if (_emergencyMealState.value is EmergencyMealState.Loading) return
        val mealType = when (mode) {
            pl.filebit.gymtracker.ai.EmergencyMode.QUICK_5MIN,
            pl.filebit.gymtracker.ai.EmergencyMode.NO_COOKING,
            pl.filebit.gymtracker.ai.EmergencyMode.STORE_SHOP -> MealType.SNACK
            pl.filebit.gymtracker.ai.EmergencyMode.AT_WORK -> MealType.LUNCH
            pl.filebit.gymtracker.ai.EmergencyMode.LATE_NIGHT -> MealType.DINNER
        }
        val modeLabel = when (mode) {
            pl.filebit.gymtracker.ai.EmergencyMode.QUICK_5MIN -> "⏱ 5 min"
            pl.filebit.gymtracker.ai.EmergencyMode.NO_COOKING -> "🥗 Bez gotowania"
            pl.filebit.gymtracker.ai.EmergencyMode.AT_WORK -> "🏢 W pracy"
            pl.filebit.gymtracker.ai.EmergencyMode.STORE_SHOP -> "🛒 Ze sklepu"
            pl.filebit.gymtracker.ai.EmergencyMode.LATE_NIGHT -> "🌙 Późna kolacja"
        }
        val targetKcal = state.value.perMealKcal.takeIf { it > 0 } ?: 400

        viewModelScope.launch {
            _emergencyMealState.value = EmergencyMealState.Loading
            val result = emergencyMealGen.generate(mode, targetKcal, mealType)
            _emergencyMealState.value = result.fold(
                onSuccess = { recipe -> EmergencyMealState.Success(recipe, mealType, modeLabel) },
                onFailure = { err -> EmergencyMealState.Error(err.message ?: "Nieznany błąd AI") }
            )
        }
    }

    fun acceptEmergencyMeal() {
        val s = _emergencyMealState.value as? EmergencyMealState.Success ?: return
        viewModelScope.launch {
            val products = state.value.productsAll
            val byName = products.associateBy { it.name.lowercase() }
            var added = 0
            for (ing in s.recipe.ingredients) {
                val key = ing.productName.lowercase()
                val product = byName[key]
                    ?: byName.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value
                    ?: continue
                repo.addMeal(pl.filebit.gymtracker.data.entity.MealEntry(
                    dateMs = _selectedDateMs.value,
                    mealType = s.mealType,
                    productId = product.id,
                    grams = ing.grams.toDouble(),
                    notes = s.recipe.name
                ))
                added++
            }
            runCatching { adherenceCalc.computeForDate(_selectedDateMs.value) }
            _emergencyMealState.value = EmergencyMealState.Saved(s.recipe.name)
        }
    }

    fun dismissEmergencyMeal() { _emergencyMealState.value = EmergencyMealState.Idle }

    fun openDamageControlDialog() { _showDamageControlDialog.value = true }
    fun dismissDamageControlDialog() { _showDamageControlDialog.value = false }
    fun dismissDamageControlResult() { _damageControlResult.value = null }

    /**
     * User wybrał z listy popularnych dań / wpisał kcal ręcznie.
     * Liczymy DamageControl dla pozostałego dnia.
     */
    fun applyDamageControl(unplannedKcal: Int) {
        viewModelScope.launch {
            val s = state.value
            val nowSum = s.totals.kcal.toInt()
            val totalAfter = nowSum + unplannedKcal
            // Pozostałe sloty = liczba slotów per config minus już logged groups
            val groupsWithEntries = s.groups.count { it.entries.isNotEmpty() }
            val totalSlots = s.config.mealsPerDay
            val remaining = (totalSlots - groupsWithEntries).coerceAtLeast(0)

            val res = damageControl.recommend(
                unplannedKcal = unplannedKcal,
                dailyGoalKcal = s.goal.kcal,
                alreadyConsumedKcalIncludingUnplanned = totalAfter,
                remainingSlots = remaining,
                totalSlotsToday = totalSlots
            )
            _damageControlResult.value = res
        }
    }

    // === DIET PHASE ===
    private val _currentPhase = MutableStateFlow<pl.filebit.gymtracker.data.entity.DietPhase?>(null)
    val currentPhase: StateFlow<pl.filebit.gymtracker.data.entity.DietPhase?> = _currentPhase.asStateFlow()

    private val _phaseSuggestion = MutableStateFlow<pl.filebit.gymtracker.data.repository.PhaseSuggestion?>(null)
    val phaseSuggestion: StateFlow<pl.filebit.gymtracker.data.repository.PhaseSuggestion?> = _phaseSuggestion.asStateFlow()

    fun checkPhaseSuggestion() {
        viewModelScope.launch {
            val profile = profileRepo.get()
            val current = phaseRepo.getCurrent()
            // Waga przy starcie cut — uproszczenie: jeśli mamy DietPhase typu CUT, jego startDate
            // to moment rozpoczęcia. Bierzemy aktualną wagę profile (precyzja: 1 punkt zamiast 2).
            // Pełniejsza implementacja wymagałaby BodyMeasurement w startDate.
            val cutStartWeight = profile.bodyweightKg
            val recoveryLogs = recoveryRepo.getLast7Days()
            val recovery = recoveryAnalyzer.analyze(recoveryLogs)
            val adherence = adherenceCalc.avgAdherenceLastDays(14).avgKcalPct
            val trend = pl.filebit.gymtracker.util.WeightTrend.NO_DATA

            val suggestion = phaseManager.suggest(
                currentPhase = current,
                weightGoalType = profile.weightGoalType,
                weightAtCutStartKg = cutStartWeight,
                currentWeightKg = profile.bodyweightKg,
                weightTrend = trend,
                adherenceKcalPct = adherence,
                recovery = recovery,
                isTodayHeavyTraining = false
            )
            if (suggestion.proposedType != null) {
                _phaseSuggestion.value = suggestion
            }
        }
    }

    fun acceptPhaseSuggestion() {
        val s = _phaseSuggestion.value ?: return
        val type = s.proposedType ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val end = if (s.durationDays > 0) {
                now + s.durationDays * 24L * 3600 * 1000
            } else null
            phaseRepo.startPhase(pl.filebit.gymtracker.data.entity.DietPhase(
                type = type,
                startDateMs = now,
                endDateMs = end,
                kcalAdjustment = s.kcalAdjustment,
                reason = s.reason,
                createdBySystem = true,
                accepted = true
            ))
            _currentPhase.value = phaseRepo.getCurrent()
            _phaseSuggestion.value = null
        }
    }

    fun dismissPhaseSuggestion() { _phaseSuggestion.value = null }

    private suspend fun refreshCurrentPhase() {
        _currentPhase.value = phaseRepo.getCurrent()
    }

    // === DAILY ACTIVITY (steps) ===
    private val _stepsToday = MutableStateFlow(0)
    val stepsToday: StateFlow<Int> = _stepsToday.asStateFlow()

    private val _showStepsDialog = MutableStateFlow(false)
    val showStepsDialog: StateFlow<Boolean> = _showStepsDialog.asStateFlow()

    fun openStepsDialog() { _showStepsDialog.value = true }
    fun dismissStepsDialog() { _showStepsDialog.value = false }

    fun setSteps(steps: Int) {
        viewModelScope.launch {
            activityRepo.setSteps(_selectedDateMs.value, steps)
            refreshSteps()
        }
    }

    private suspend fun refreshSteps() {
        val log = activityRepo.getForDate(_selectedDateMs.value)
        _stepsToday.value = log?.steps ?: 0
    }

    // === HYDRATION ===
    private val _hydrationToday = MutableStateFlow(0)
    val hydrationToday: StateFlow<Int> = _hydrationToday.asStateFlow()

    private val _hydrationGoal = MutableStateFlow(2400)
    val hydrationGoal: StateFlow<Int> = _hydrationGoal.asStateFlow()

    fun addHydration(ml: Int) {
        viewModelScope.launch {
            val date = _selectedDateMs.value
            hydrationRepo.add(date, ml)
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

    private val _hydrationLogs = MutableStateFlow<List<pl.filebit.gymtracker.data.entity.HydrationLog>>(emptyList())
    val hydrationLogs: StateFlow<List<pl.filebit.gymtracker.data.entity.HydrationLog>> = _hydrationLogs.asStateFlow()

    private val _showHydrationDialog = MutableStateFlow(false)
    val showHydrationDialog: StateFlow<Boolean> = _showHydrationDialog.asStateFlow()

    fun openHydrationLogDialog() {
        _showHydrationDialog.value = true
        viewModelScope.launch { refreshHydrationLogs() }
    }

    fun dismissHydrationDialog() { _showHydrationDialog.value = false }

    private suspend fun refreshHydrationLogs() {
        val date = _selectedDateMs.value
        val (start, end) = run {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = date
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val s = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            s to cal.timeInMillis
        }
        // Pobranie z DAO bezpośrednio (przez repository nie ma) — używamy publicznego API
        val logs = hydrationRepo.observeForDate(date).first()
        _hydrationLogs.value = logs.sortedByDescending { it.createdAt }
    }

    private suspend fun refreshHydration() {
        val date = _selectedDateMs.value
        val sum = hydrationRepo.sumForDate(date)
        _hydrationToday.value = sum
        // Cel z Calculator
        val profile = profileRepo.get()
        val weight = profile.bodyweightKg ?: 75.0
        val goal = hydrationCalc.computeTarget(
            weightKg = weight,
            hadTrainingToday = false, // TODO: integracja z TrainingDietBridge
            proteinGramsToday = 0.0,
            usesCreatine = false
        )
        _hydrationGoal.value = goal.totalMl
    }

    // === RECOVERY ===
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
                dateMs = _selectedDateMs.value,
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

    data class SubstitutePrompt(
        val entry: pl.filebit.gymtracker.data.entity.MealEntry,
        val original: FoodProduct,
        val substitutes: List<pl.filebit.gymtracker.data.repository.Substitute>,
        val tolerance: pl.filebit.gymtracker.data.repository.MatchTolerance
    )

    private val _substitutePrompt = MutableStateFlow<SubstitutePrompt?>(null)
    val substitutePrompt: StateFlow<SubstitutePrompt?> = _substitutePrompt.asStateFlow()

    fun openSubstitutes(
        entry: pl.filebit.gymtracker.data.entity.MealEntry,
        product: FoodProduct,
        tolerance: pl.filebit.gymtracker.data.repository.MatchTolerance =
            pl.filebit.gymtracker.data.repository.MatchTolerance.STRICT
    ) {
        val all = state.value.productsAll
        val subs = substituteService.findSubstitutes(
            original = product,
            originalGrams = entry.grams,
            allProducts = all,
            tolerance = tolerance
        )
        _substitutePrompt.value = SubstitutePrompt(entry, product, subs, tolerance)
    }

    fun changeSubstituteTolerance(tolerance: pl.filebit.gymtracker.data.repository.MatchTolerance) {
        val cur = _substitutePrompt.value ?: return
        val all = state.value.productsAll
        val subs = substituteService.findSubstitutes(
            original = cur.original,
            originalGrams = cur.entry.grams,
            allProducts = all,
            tolerance = tolerance
        )
        _substitutePrompt.value = cur.copy(substitutes = subs, tolerance = tolerance)
    }

    fun applySubstitute(newProduct: FoodProduct, newGrams: Double) {
        val cur = _substitutePrompt.value ?: return
        viewModelScope.launch {
            // Usuń starą pozycję, dodaj nową z tą samą datą i typem posiłku
            repo.deleteMeal(cur.entry.id)
            repo.addMeal(
                pl.filebit.gymtracker.data.entity.MealEntry(
                    dateMs = cur.entry.dateMs,
                    mealType = cur.entry.mealType,
                    productId = newProduct.id,
                    grams = newGrams,
                    notes = cur.entry.notes
                )
            )
            runCatching { adherenceCalc.computeForDate(cur.entry.dateMs) }
            _substitutePrompt.value = null
        }
    }

    fun dismissSubstitute() {
        _substitutePrompt.value = null
    }

    /**
     * Lista nazw potraw z ostatnio wygenerowanego planu AI — do oceny przez usera.
     * Czyszczone po użyciu (consumeAiPlanRatingPrompt).
     */
    private val _aiPlanRatingPrompt = MutableStateFlow<List<Pair<MealType, String>>>(emptyList())
    val aiPlanRatingPrompt: StateFlow<List<Pair<MealType, String>>> = _aiPlanRatingPrompt.asStateFlow()

    /**
     * Pełne recipe per slot z ostatniego planu AI (z instrukcjami).
     * Używane do "Pokaż przepis" w MealGroupCard. Transient — żyje od generacji do
     * wygenerowania nowego planu lub zamknięcia VM.
     */
    private val _slotRecipes = MutableStateFlow<Map<MealType, pl.filebit.gymtracker.ai.AiMealRecipe>>(emptyMap())
    val slotRecipes: StateFlow<Map<MealType, pl.filebit.gymtracker.ai.AiMealRecipe>> = _slotRecipes.asStateFlow()

    private val _shownRecipeFor = MutableStateFlow<MealType?>(null)
    val shownRecipeFor: StateFlow<MealType?> = _shownRecipeFor.asStateFlow()

    fun showRecipeFor(type: MealType) { _shownRecipeFor.value = type }
    fun dismissRecipe() { _shownRecipeFor.value = null }

    fun rateMeal(displayName: String, rating: Int, tags: String = "", notes: String = "") {
        viewModelScope.launch {
            runCatching { mealFeedbackRepo.rate(displayName, rating, tags, notes) }
        }
    }

    fun consumeAiPlanRatingPrompt() {
        _aiPlanRatingPrompt.value = emptyList()
    }

    /**
     * Alternatywy do każdego slotu z OSTATNIO wygenerowanego planu AI.
     * Map<MealType, List<AiAlternative>>. Czyszczone przy nowej generacji.
     */
    private val _slotAlternatives =
        MutableStateFlow<Map<MealType, List<pl.filebit.gymtracker.ai.AiAlternative>>>(emptyMap())
    val slotAlternatives: StateFlow<Map<MealType, List<pl.filebit.gymtracker.ai.AiAlternative>>> =
        _slotAlternatives.asStateFlow()

    /**
     * Wybór alternatywy dla danego slotu — zastępuje WSZYSTKIE entries tego slotu
     * w bieżącym dniu zawartością alternatywy.
     */
    fun selectAlternative(mealType: MealType, alternative: pl.filebit.gymtracker.ai.AiAlternative) {
        viewModelScope.launch {
            val products = state.value.productsAll
            val byNameLower = products.associateBy { it.name.lowercase() }
            val dateMs = _selectedDateMs.value

            // Usuń istniejące entries tego slotu w dniu
            state.value.groups
                .firstOrNull { it.type == mealType }
                ?.entries
                ?.forEach { e -> repo.deleteMeal(e.entry.id) }

            // Wstaw nową kompozycję
            alternative.ingredients.forEach { ing ->
                val product = byNameLower[ing.productName.lowercase()]
                if (product != null) {
                    repo.addMeal(
                        MealEntry(
                            dateMs = dateMs,
                            mealType = mealType,
                            productId = product.id,
                            grams = ing.grams.toDouble(),
                            notes = alternative.name
                        )
                    )
                }
            }
            runCatching { adherenceCalc.computeForDate(dateMs) }
            // Zastąp recipe w slotRecipes wybraną alternatywą (z toRecipe — zachowuje instructions jeśli były)
            _slotRecipes.value = _slotRecipes.value.toMutableMap().apply {
                put(mealType, alternative.toRecipe())
            }
        }
    }

    data class AdjustmentPreview(
        val id: Long,
        val decision: pl.filebit.gymtracker.util.AdjustmentDecision,
        val aiExplanation: String?
    )

    private val _adjustmentPreview = MutableStateFlow<AdjustmentPreview?>(null)
    val adjustmentPreview: StateFlow<AdjustmentPreview?> = _adjustmentPreview.asStateFlow()

    fun checkForAdjustment() {
        viewModelScope.launch {
            val decision = autoAdjust.analyzeNow()
            // Save preview do DietAdjustment (z AI explainer) — zwraca id
            val adjId = autoAdjust.savePreview(decision)
            val saved = autoAdjust.getRecent(1).firstOrNull { it.id == adjId }
            _adjustmentPreview.value = AdjustmentPreview(
                id = adjId,
                decision = decision,
                aiExplanation = saved?.aiExplanation
            )
        }
    }

    fun applyAdjustment() {
        val preview = _adjustmentPreview.value ?: return
        viewModelScope.launch {
            autoAdjust.applyDecision(preview.id)
            _adjustmentPreview.value = null
        }
    }

    fun dismissAdjustmentPreview() {
        val preview = _adjustmentPreview.value
        viewModelScope.launch {
            preview?.id?.let { autoAdjust.dismissAdjustment(it) }
        }
        _adjustmentPreview.value = null
    }

    private val _onboardingChecked = MutableStateFlow(false)
    private val _needsOnboarding = MutableStateFlow(false)
    val needsOnboarding: StateFlow<Boolean> = _needsOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val done = dietProfileRepo.isOnboardingDone()
            _needsOnboarding.value = !done
            _onboardingChecked.value = true
            // Ensure today's TrainingDaySummary istnieje (lazy)
            runCatching { trainingDietBridge.ensureForToday() }
            // Refresh hydration today
            runCatching { refreshHydration() }
            // Refresh steps today
            runCatching { refreshSteps() }
            // Refresh diet phase
            runCatching { refreshCurrentPhase() }
            // Refresh Health Connect availability + steps
            runCatching { refreshHealthConnect() }
            runCatching { refreshConsumptions() }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<FoodCategory?>(null)
    private val _selectedDateMs = MutableStateFlow(todayStartMs())
    private val _aiPlanState = MutableStateFlow<AiPlanState>(AiPlanState.Idle)
    val aiPlanState: StateFlow<AiPlanState> = _aiPlanState.asStateFlow()

    private data class FilterTuple(
        val dateMs: Long,
        val query: String,
        val cat: FoodCategory?,
        val config: DietConfig
    )

    val state: StateFlow<DietUiState> = combine(
        _selectedDateMs,
        _searchQuery,
        _categoryFilter,
        dietPrefs.state
    ) { dateMs, query, cat, config ->
        FilterTuple(dateMs, query, cat, config)
    }
        .let { tuple ->
            @OptIn(ExperimentalCoroutinesApi::class)
            tuple.flatMapLatest { t ->
                val dateMs = t.dateMs
                val query = t.query
                val cat = t.cat
                val config = t.config
                combine(
                    repo.observeMealsForDate(dateMs),
                    repo.observeAllProducts()
                ) { meals, allProducts ->
                    val productMap = allProducts.associateBy { it.id }
                    val withMacros = meals.mapNotNull { e ->
                        productMap[e.productId]?.let { p -> e.macrosFor(p) }
                    }
                    val byType = withMacros.groupBy { it.entry.mealType }
                    val cfg = config
                    val mealHours = cfg.mealHoursDecimal()
                    // Mapowanie slot index → MealType (max 4 — bo enum ma 4 wartości).
                    // Dla 5-6 posiłków SNACK się powtarza wizualnie ale w bazie wszystkie
                    // dodatkowe są SNACK.
                    val typesForSlots: List<MealType> = when (cfg.mealsPerDay) {
                        2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
                        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
                        4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
                        5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
                        6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
                        else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
                    }
                    val groups = typesForSlots.mapIndexed { idx, type ->
                        val entries = byType[type].orEmpty()
                        val tot = entries.fold(DayTotals()) { acc, m -> acc + m }
                        MealGroup(
                            type = type,
                            entries = entries,
                            totals = tot,
                            timeLabel = mealHours.getOrNull(idx)?.let { cfg.formatTime(it) } ?: "",
                            customLabel = labelForSlot(idx + 1, cfg.mealsPerDay)
                        )
                    }
                    val totals = groups.fold(DayTotals()) { acc, g ->
                        DayTotals(
                            acc.kcal + g.totals.kcal,
                            acc.protein + g.totals.protein,
                            acc.carbs + g.totals.carbs,
                            acc.fat + g.totals.fat
                        )
                    }

                    val profile = runCatching { profileRepo.get() }.getOrNull()
                    val dietProfile = runCatching { dietProfileRepo.get() }.getOrNull()
                    val goal = if (profile != null) computeDailyGoal(
                        profile,
                        manualKcalOverride = cfg.manualKcal,
                        customDeficit = cfg.customDeficit,
                        dietProfile = dietProfile
                    ) else DailyMacroGoal(
                        2200, 150, 250, 70,
                        pl.filebit.gymtracker.util.GoalBreakdown(
                            75.0, "—", 2200, "fallback", 0, "fallback",
                            false, 2.0, 1.0, "fallback"
                        )
                    )

                    val filtered = allProducts.let { list ->
                        val byCat = if (cat == null) list else list.filter { it.category == cat }
                        if (query.isBlank()) byCat
                        else byCat.filter { it.name.contains(query, ignoreCase = true) }
                    }

                    DietUiState(
                        loading = false,
                        dateMs = dateMs,
                        goal = goal,
                        totals = totals,
                        groups = groups,
                        productsAll = allProducts,
                        searchQuery = query,
                        categoryFilter = cat,
                        filteredProducts = filtered,
                        config = cfg,
                        perMealKcal = if (cfg.mealsPerDay > 0) goal.kcal / cfg.mealsPerDay else 0
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setCategoryFilter(c: FoodCategory?) { _categoryFilter.value = c }

    fun addMeal(productId: Long, grams: Double, mealType: MealType) {
        viewModelScope.launch {
            repo.addMeal(
                MealEntry(
                    dateMs = _selectedDateMs.value,
                    mealType = mealType,
                    productId = productId,
                    grams = grams
                )
            )
            // Update adherence po każdej zmianie posiłków
            runCatching { adherenceCalc.computeForDate(_selectedDateMs.value) }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            repo.deleteMeal(id)
            runCatching { adherenceCalc.computeForDate(_selectedDateMs.value) }
        }
    }

    /**
     * Generuje plan dnia AI: woła DietAiService → mapuje productName → productId
     * (case-insensitive) → zapisuje MealEntry per posiłek + Recipe.
     * Stan AI loading/success/error eksponuje przez aiPlanState.
     */
    fun generateAiDayPlan() {
        if (_aiPlanState.value is AiPlanState.Loading) return
        _aiPlanState.value = AiPlanState.Loading
        viewModelScope.launch {
            val config = dietPrefs.load()
            val result = dietAi.generateDayPlan(config)
            result.fold(
                onSuccess = { plan ->
                    val products = state.value.productsAll
                    val byNameLower = products.associateBy { it.name.lowercase() }
                    var addedMeals = 0
                    var skippedIngredients = 0

                    // Wyczyść istniejące posiłki dnia (start od czystej karty)
                    val dateMs = _selectedDateMs.value
                    state.value.groups.forEach { g ->
                        g.entries.forEach { e -> repo.deleteMeal(e.entry.id) }
                    }

                    plan.mealsForSlots.forEach { (mealType, recipe) ->
                        var anyAdded = false
                        recipe.ingredients.forEach { ing ->
                            val product = byNameLower[ing.productName.lowercase()]
                            if (product != null) {
                                repo.addMeal(
                                    MealEntry(
                                        dateMs = dateMs,
                                        mealType = mealType,
                                        productId = product.id,
                                        grams = ing.grams.toDouble(),
                                        notes = recipe.name
                                    )
                                )
                                anyAdded = true
                            } else {
                                skippedIngredients++
                            }
                        }
                        if (anyAdded) addedMeals++
                    }

                    runCatching { adherenceCalc.computeForDate(_selectedDateMs.value) }
                    // Zachowaj alternatywy per slot (transient — do następnej generacji)
                    _slotAlternatives.value = plan.mealsForSlots
                        .associate { (type, recipe) -> type to recipe.alternatives }
                        .filterValues { it.isNotEmpty() }
                    // Recipe per slot — do wyświetlenia "Pokaż przepis"
                    _slotRecipes.value = plan.mealsForSlots.associate { (type, recipe) -> type to recipe }
                    // Zaproponuj userowi ocenę wygenerowanych potraw (MealFeedback)
                    _aiPlanRatingPrompt.value = plan.mealsForSlots
                        .map { (type, recipe) -> type to recipe.name }
                        .filter { it.second.isNotBlank() }
                    _aiPlanState.value = AiPlanState.Success(
                        if (skippedIngredients > 0)
                            "Plan dnia gotowy ($addedMeals posiłków, $skippedIngredients składników pominiętych — brak w bazie)"
                        else
                            "Plan dnia gotowy — $addedMeals posiłków dodanych do dziennika"
                    )
                },
                onFailure = { err ->
                    _aiPlanState.value = AiPlanState.Error(err.message ?: "Nieznany błąd AI")
                }
            )
        }
    }

    fun consumeAiPlanState() {
        _aiPlanState.value = AiPlanState.Idle
    }

    fun saveConfig(config: DietConfig) {
        dietPrefs.save(config)
        viewModelScope.launch {
            reminderScheduler.rescheduleAll(config)
            if (config.autoCheckAdjustments) dietAdjustmentScheduler.schedulePeriodic()
            else dietAdjustmentScheduler.cancel()
            if (config.healthConnectSyncEnabled) healthConnectScheduler.schedule()
            else healthConnectScheduler.cancel()
            refreshHealthConnect()
        }
    }

    fun rescheduleReminders() {
        viewModelScope.launch { reminderScheduler.rescheduleAll(dietPrefs.load()) }
    }

    private fun labelForSlot(slot: Int, total: Int): String = when {
        total == 2 && slot == 1 -> "Śniadanie"
        total == 2 -> "Kolacja"
        total == 3 && slot == 1 -> "Śniadanie"
        total == 3 && slot == 2 -> "Obiad"
        total == 3 -> "Kolacja"
        total == 4 && slot == 1 -> "Śniadanie"
        total == 4 && slot == 2 -> "Drugie śniadanie"
        total == 4 && slot == 3 -> "Obiad"
        total == 4 -> "Kolacja"
        total == 5 && slot == 1 -> "Śniadanie"
        total == 5 && slot == 2 -> "Drugie śniadanie"
        total == 5 && slot == 3 -> "Obiad"
        total == 5 && slot == 4 -> "Podwieczorek"
        total == 5 -> "Kolacja"
        total >= 6 && slot == 1 -> "Śniadanie"
        total >= 6 && slot == total -> "Kolacja"
        else -> "Posiłek $slot"
    }

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
