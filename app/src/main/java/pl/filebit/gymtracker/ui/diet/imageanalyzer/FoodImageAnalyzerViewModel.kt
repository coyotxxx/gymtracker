package pl.filebit.gymtracker.ui.diet.imageanalyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.FoodAnalysis
import pl.filebit.gymtracker.ai.FoodImageAnalyzer
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.DietRepository
import javax.inject.Inject

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Analyzing : AnalysisState()
    data class Success(val analysis: FoodAnalysis) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
    data class Saved(val mealsAdded: Int) : AnalysisState()
}

@HiltViewModel
class FoodImageAnalyzerViewModel @Inject constructor(
    private val analyzer: FoodImageAnalyzer,
    private val dietRepo: DietRepository,
    private val foodProductDao: pl.filebit.gymtracker.data.db.dao.FoodProductDao,
    private val dietPrefs: pl.filebit.gymtracker.data.repository.DietPreferences,
    private val productResolver: pl.filebit.gymtracker.data.repository.ProductResolver
) : ViewModel() {

    /** v2.73.0: liczba posiłków/dzień — do pickera „Posiłek N". */
    fun mealsPerDay(): Int = dietPrefs.load().mealsPerDay

    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    fun analyzeImage(imageBytes: ByteArray, mimeType: String = "image/jpeg") {
        if (_state.value is AnalysisState.Analyzing) return
        viewModelScope.launch {
            _state.value = AnalysisState.Analyzing
            val result = analyzer.analyze(imageBytes, mimeType)
            _state.value = result.fold(
                onSuccess = { AnalysisState.Success(it) },
                onFailure = { AnalysisState.Error(it.message ?: "Nieznany błąd AI") }
            )
        }
    }

    /**
     * Zapisuje analizowane składniki jako MealEntry w wybranym slocie.
     * Każdy składnik staje się oddzielnym MealEntry. Brakujący produkt:
     *   v2.74.0 (ETAP 3) → najpierw dociągamy z OpenFoodFacts (ProductResolver),
     *   a dopiero gdy OFF nic nie ma — placeholder z 0 makro (user uzupełni).
     */
    fun saveAsMealEntries(analysis: FoodAnalysis, slot: Int, dateMs: Long) {
        viewModelScope.launch {
            val tag = pl.filebit.gymtracker.util.MealSlots.mealTypeForSlot(slot, dietPrefs.load().mealsPerDay)
            var added = 0
            // Cel: jeden zbiorczy notes per posiłek
            val dishNotes = analysis.dishName

            for (ing in analysis.ingredients) {
                // 1) lokalnie → 2) OpenFoodFacts (dodaje realny produkt do bazy)
                val resolved = runCatching { productResolver.resolveOrNull(ing.productName) }.getOrNull()
                val productId = resolved?.id ?: foodProductDao.upsert(FoodProduct(
                    // 3) fallback: OFF też nie zna → placeholder z 0 makro
                    name = ing.productName,
                    category = FoodCategory.OTHER,
                    kcalPer100g = 0.0,
                    proteinPer100g = 0.0,
                    carbsPer100g = 0.0,
                    fatPer100g = 0.0,
                    isCustom = true,
                    source = "image_analysis",
                    notes = "Z analizy zdjęcia — zaktualizuj makro ręcznie"
                ))
                dietRepo.addMeal(MealEntry(
                    dateMs = dateMs,
                    mealType = tag,
                    mealSlot = slot,
                    productId = productId,
                    grams = ing.grams.toDouble(),
                    notes = dishNotes
                ))
                added++
            }
            _state.value = AnalysisState.Saved(added)
        }
    }

    fun reset() { _state.value = AnalysisState.Idle }
}
