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
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.MealEntryWithMacros
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.macrosFor
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
    val totals: DayTotals
)

data class DietUiState(
    val loading: Boolean = true,
    val dateMs: Long = 0L,            // start dnia (00:00 lokalny)
    val goal: DailyMacroGoal = DailyMacroGoal(0, 0, 0, 0),
    val totals: DayTotals = DayTotals(),
    val groups: List<MealGroup> = emptyList(),
    val productsAll: List<FoodProduct> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: FoodCategory? = null,
    val filteredProducts: List<FoodProduct> = emptyList()
)

@HiltViewModel
class DietViewModel @Inject constructor(
    private val repo: DietRepository,
    private val profileRepo: UserProfileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<FoodCategory?>(null)
    private val _selectedDateMs = MutableStateFlow(todayStartMs())

    val state: StateFlow<DietUiState> = combine(
        _selectedDateMs,
        _searchQuery,
        _categoryFilter
    ) { dateMs, query, cat -> Triple(dateMs, query, cat) }
        .let { triple ->
            @OptIn(ExperimentalCoroutinesApi::class)
            triple.flatMapLatest { (dateMs, query, cat) ->
                combine(
                    repo.observeMealsForDate(dateMs),
                    repo.observeAllProducts()
                ) { meals, allProducts ->
                    val productMap = allProducts.associateBy { it.id }
                    val withMacros = meals.mapNotNull { e ->
                        productMap[e.productId]?.let { p -> e.macrosFor(p) }
                    }
                    val byType = withMacros.groupBy { it.entry.mealType }
                    val groups = listOf(
                        MealType.BREAKFAST,
                        MealType.LUNCH,
                        MealType.DINNER,
                        MealType.SNACK
                    ).map { type ->
                        val entries = byType[type].orEmpty()
                        val tot = entries.fold(DayTotals()) { acc, m -> acc + m }
                        MealGroup(type, entries, tot)
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
                    val goal = if (profile != null) computeDailyGoal(profile)
                    else DailyMacroGoal(2200, 150, 250, 70)

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
                        filteredProducts = filtered
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

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
