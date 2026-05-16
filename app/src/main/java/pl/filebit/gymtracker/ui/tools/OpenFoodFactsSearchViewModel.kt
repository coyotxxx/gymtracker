package pl.filebit.gymtracker.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.network.OffSearchResult
import pl.filebit.gymtracker.data.network.OpenFoodFactsClient
import pl.filebit.gymtracker.data.repository.DietRepository
import javax.inject.Inject

/**
 * v1.27.8 — Etap 2: wyszukiwanie produktów w Open Food Facts.
 *
 * AI generujące plany korzysta WYŁĄCZNIE z lokalnej bazy — ten ekran pozwala
 * userowi ją zasilać produktami z zewnętrznej bazy (polski rynek). Wyszukany
 * produkt po dodaniu staje się zwykłym FoodProduct (source="openfoodfacts").
 */
@HiltViewModel
class OpenFoodFactsSearchViewModel @Inject constructor(
    private val offClient: OpenFoodFactsClient,
    private val repo: DietRepository
) : ViewModel() {

    sealed class SearchState {
        object Idle : SearchState()
        object Loading : SearchState()
        data class Results(val products: List<FoodProduct>) : SearchState()
        object Empty : SearchState()
        data class Error(val message: String) : SearchState()
    }

    /** Pozycja wyniku — produkt + czy jest już w lokalnej bazie. */
    data class ResultRow(
        val product: FoodProduct,
        val alreadyInDb: Boolean
    )

    data class UiState(
        val query: String = "",
        val categoryFilter: FoodCategory? = null,
        val loading: Boolean = false,
        val rows: List<ResultRow> = emptyList(),
        /** Komunikat dla stanów innych niż lista wyników (idle/empty/error). */
        val message: String? = null
    )

    private val _query = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<FoodCategory?>(null)
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    private val _addedThisSession = MutableStateFlow<Set<String>>(emptySet())

    private val existingNames: StateFlow<Set<String>> = repo.observeAllProducts()
        .map { list -> list.map { it.name.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val state: StateFlow<UiState> = combine(
        _query,
        _categoryFilter,
        _searchState,
        existingNames,
        _addedThisSession
    ) { query, category, searchState, existing, addedSession ->
        when (searchState) {
            is SearchState.Idle -> UiState(
                query = query, categoryFilter = category,
                message = "Wpisz nazwę produktu i naciśnij Szukaj, albo wybierz " +
                    "kategorię powyżej — pokażemy produkty wartościowe pod trening. " +
                    "Dane z bazy Open Food Facts (polski rynek)."
            )
            is SearchState.Loading -> UiState(
                query = query, categoryFilter = category, loading = true
            )
            is SearchState.Empty -> UiState(
                query = query, categoryFilter = category,
                message = "Brak produktów dla tego zapytania. Spróbuj innej nazwy."
            )
            is SearchState.Error -> UiState(
                query = query, categoryFilter = category,
                message = "Błąd: ${searchState.message}. Sprawdź połączenie z internetem."
            )
            is SearchState.Results -> {
                val rows = searchState.products.map { p ->
                    val inDb = p.name.lowercase() in existing || p.name.lowercase() in addedSession
                    ResultRow(product = p, alreadyInDb = inDb)
                }
                UiState(
                    query = query,
                    categoryFilter = category,
                    rows = rows,
                    message = if (rows.isEmpty()) "Brak wyników." else null
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(q: String) { _query.value = q }

    /** Klik kategorii — wyszukuje w OFF kuratorowane produkty "pod siłownię". */
    fun searchCategory(category: FoodCategory) {
        _query.value = ""
        _categoryFilter.value = category
        _searchState.value = SearchState.Loading
        viewModelScope.launch {
            _searchState.value = when (val r = offClient.searchByCategory(category)) {
                is OffSearchResult.Success -> SearchState.Results(r.products)
                is OffSearchResult.Empty -> SearchState.Empty
                is OffSearchResult.Error -> SearchState.Error(r.message)
            }
        }
    }

    fun search() {
        val q = _query.value.trim()
        if (q.length < 2) return
        _categoryFilter.value = null
        _searchState.value = SearchState.Loading
        viewModelScope.launch {
            _searchState.value = when (val r = offClient.searchByName(q)) {
                is OffSearchResult.Success -> SearchState.Results(r.products)
                is OffSearchResult.Empty -> SearchState.Empty
                is OffSearchResult.Error -> SearchState.Error(r.message)
            }
        }
    }

    fun addProduct(product: FoodProduct) {
        viewModelScope.launch {
            // id=0 → INSERT nowego wiersza (autoGenerate)
            repo.upsertProduct(product.copy(id = 0))
            _addedThisSession.value = _addedThisSession.value + product.name.lowercase()
        }
    }
}
