package pl.filebit.gymtracker.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.repository.DietRepository
import javax.inject.Inject

/**
 * v1.27.7 — ekran "Baza produktów" w Narzędziach.
 *
 * Podgląd całej lokalnej bazy produktów: wyszukiwarka, filtr kategorii +
 * ulubionych, sortowanie po makro. AI generujące plany korzysta WYŁĄCZNIE
 * z tej bazy — ekran pozwala userowi nią zarządzać (oznaczanie ulubionych).
 */
@HiltViewModel
class FoodDatabaseViewModel @Inject constructor(
    private val repo: DietRepository
) : ViewModel() {

    /** Kryterium sortowania listy produktów. */
    enum class ProductSort(val label: String) {
        NAME("Nazwa"),
        KCAL("Kalorie"),
        PROTEIN("Białko"),
        CARBS("Węgle"),
        FAT("Tłuszcz")
    }

    data class UiState(
        val products: List<FoodProduct> = emptyList(),
        val query: String = "",
        val category: FoodCategory? = null,
        val favoritesOnly: Boolean = false,
        val sort: ProductSort = ProductSort.NAME,
        /** Liczba wszystkich produktów w bazie (przed filtrami). */
        val totalCount: Int = 0
    )

    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow<FoodCategory?>(null)
    private val _favoritesOnly = MutableStateFlow(false)
    private val _sort = MutableStateFlow(ProductSort.NAME)

    val state: StateFlow<UiState> = combine(
        repo.observeAllProducts(),
        _query,
        _category,
        _favoritesOnly,
        _sort
    ) { all, query, category, favoritesOnly, sort ->
        val filtered = all
            .let { list -> if (category == null) list else list.filter { it.category == category } }
            .let { list -> if (favoritesOnly) list.filter { it.isFavorite } else list }
            .let { list ->
                if (query.isBlank()) list
                else list.filter { it.name.contains(query, ignoreCase = true) }
            }
        // Ulubione zawsze na górze, w obrębie sortowania wybranego przez usera.
        val sorted = when (sort) {
            ProductSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            ProductSort.KCAL -> filtered.sortedByDescending { it.kcalPer100g }
            ProductSort.PROTEIN -> filtered.sortedByDescending { it.proteinPer100g }
            ProductSort.CARBS -> filtered.sortedByDescending { it.carbsPer100g }
            ProductSort.FAT -> filtered.sortedByDescending { it.fatPer100g }
        }
        UiState(
            products = sorted.sortedByDescending { it.isFavorite },
            query = query,
            category = category,
            favoritesOnly = favoritesOnly,
            sort = sort,
            totalCount = all.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(q: String) { _query.value = q }
    fun setCategory(c: FoodCategory?) { _category.value = c }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }
    fun setSort(s: ProductSort) { _sort.value = s }

    fun toggleFavorite(product: FoodProduct) {
        viewModelScope.launch {
            repo.setProductFavorite(product.id, !product.isFavorite)
        }
    }
}
