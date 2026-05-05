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
    private val dietAi: DietAiService
) : ViewModel() {

    private val _onboardingChecked = MutableStateFlow(false)
    private val _needsOnboarding = MutableStateFlow(false)
    val needsOnboarding: StateFlow<Boolean> = _needsOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val done = dietProfileRepo.isOnboardingDone()
            _needsOnboarding.value = !done
            _onboardingChecked.value = true
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
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch { repo.deleteMeal(id) }
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
        viewModelScope.launch { reminderScheduler.rescheduleAll(config) }
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
