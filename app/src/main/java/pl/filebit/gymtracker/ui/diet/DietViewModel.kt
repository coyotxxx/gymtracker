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
    private val weeklyBudgetCalc: pl.filebit.gymtracker.data.repository.WeeklyBudgetCalculator
) : ViewModel() {

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
