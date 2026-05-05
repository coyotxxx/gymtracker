package pl.filebit.gymtracker.ui.diet.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.db.dao.RecipeDao
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.Recipe
import pl.filebit.gymtracker.data.repository.DietRepository
import javax.inject.Inject

enum class RecipeFilter { ALL, BREAKFAST, LUNCH, FAVORITES }
enum class CategoryFilter { ALL, SWEET, SAVORY }
enum class SortOrder { NAME, KCAL_ASC, KCAL_DESC, FAV_FIRST }

data class RecipeBrowserUiState(
    val all: List<Recipe> = emptyList(),
    val filter: RecipeFilter = RecipeFilter.ALL,
    val category: CategoryFilter = CategoryFilter.ALL,
    val sort: SortOrder = SortOrder.NAME,
    val search: String = ""
) {
    val filtered: List<Recipe>
        get() {
            val q = search.trim().lowercase()
            return all.asSequence()
                .filter {
                    when (filter) {
                        RecipeFilter.ALL -> true
                        RecipeFilter.BREAKFAST -> it.mealType == MealType.BREAKFAST
                        RecipeFilter.LUNCH -> it.mealType == MealType.LUNCH
                        RecipeFilter.FAVORITES -> it.isFavorite
                    }
                }
                .filter {
                    when (category) {
                        CategoryFilter.ALL -> true
                        CategoryFilter.SWEET -> it.mealCategory == "sweet"
                        CategoryFilter.SAVORY -> it.mealCategory == "savory"
                    }
                }
                .filter { q.isBlank() || it.name.lowercase().contains(q) }
                .sortedWith(
                    when (sort) {
                        SortOrder.NAME -> compareBy { it.name.lowercase() }
                        SortOrder.KCAL_ASC -> compareBy { it.kcalPerServing }
                        SortOrder.KCAL_DESC -> compareByDescending { it.kcalPerServing }
                        SortOrder.FAV_FIRST -> compareByDescending<Recipe> { it.isFavorite }.thenBy { it.name.lowercase() }
                    }
                )
                .toList()
        }
}

@HiltViewModel
class RecipeBrowserViewModel @Inject constructor(
    private val dao: RecipeDao,
    private val productDao: FoodProductDao,
    private val dietRepo: DietRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(RecipeFilter.ALL)
    private val _category = MutableStateFlow(CategoryFilter.ALL)
    private val _sort = MutableStateFlow(SortOrder.NAME)
    private val _search = MutableStateFlow("")

    val state: StateFlow<RecipeBrowserUiState> = combine(
        dao.observeAll(), _filter, _category, _sort, _search
    ) { all, filter, cat, sort, search ->
        RecipeBrowserUiState(
            all = all, filter = filter, category = cat, sort = sort, search = search
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeBrowserUiState())

    fun setFilter(f: RecipeFilter) { _filter.value = f }
    fun setCategory(c: CategoryFilter) { _category.value = c }
    fun setSort(s: SortOrder) { _sort.value = s }
    fun setSearch(s: String) { _search.value = s }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            dao.upsert(recipe.copy(isFavorite = !recipe.isFavorite))
        }
    }

    /**
     * Próbuje dodać przepis do dziennego planu jako MealEntries.
     * Match składników po nazwie z FoodProductDao (case-insensitive + substring).
     * Zwraca info o sukcesie.
     */
    sealed class AddResult {
        data class Success(val matched: Int, val total: Int) : AddResult()
        data class Failed(val reason: String) : AddResult()
    }

    private val _addResult = MutableStateFlow<AddResult?>(null)
    val addResult: StateFlow<AddResult?> = _addResult.asStateFlow()

    fun consumeAddResult() { _addResult.value = null }

    fun addToDiet(recipe: Recipe, mealType: MealType, dateMs: Long) {
        viewModelScope.launch {
            val raw = recipe.rawIngredientsText
            if (raw.isNullOrBlank()) {
                _addResult.value = AddResult.Failed("Przepis nie ma listy składników")
                return@launch
            }
            val products = productDao.getAll()
            val byNameLower = products.associateBy { it.name.lowercase() }

            var matched = 0
            var total = 0
            for (line in raw.split("\n")) {
                val trimmed = line.trim()
                if (trimmed.length < 3) continue
                total++

                // Wyciągnij gramaturę: "150 g skyr naturalny" → 150 + "skyr naturalny"
                val regex = Regex("""^(\d+(?:[,.]\d+)?)\s*(g|ml|szt|łyżk[ai]?|łyżeczk[ai]?)?\s*(.+)$""")
                val m = regex.matchEntire(trimmed) ?: continue
                val (qtyStr, unit, name) = m.destructured
                var qty = qtyStr.replace(",", ".").toDoubleOrNull() ?: continue

                if (unit.contains("łyżk") && !unit.contains("łyżeczk")) qty *= 15
                else if (unit.contains("łyżeczk")) qty *= 5
                else if (unit == "szt") qty *= 50  // bardzo gruba estymacja

                val cleanName = name.lowercase()
                    .replace(Regex("\\b(do smażenia|do podania|opcjonalnie|świeży|świeża|ulubiony|ulubione|naturalny|chudy|biały|jasna|jasny)\\b"), "")
                    .trim()

                // Match
                val product = byNameLower[cleanName]
                    ?: byNameLower.entries.firstOrNull { (k, _) ->
                        k.contains(cleanName) || cleanName.contains(k)
                    }?.value
                    ?: continue

                dietRepo.addMeal(MealEntry(
                    dateMs = dateMs,
                    mealType = mealType,
                    productId = product.id,
                    grams = qty,
                    notes = recipe.name
                ))
                matched++
            }
            _addResult.value = AddResult.Success(matched = matched, total = total)
        }
    }
}
