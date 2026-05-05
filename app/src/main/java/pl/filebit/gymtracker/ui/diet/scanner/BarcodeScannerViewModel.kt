package pl.filebit.gymtracker.ui.diet.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.network.OpenFoodFactsClient
import pl.filebit.gymtracker.data.network.OpenFoodFactsResult
import javax.inject.Inject

sealed class ScanState {
    object Scanning : ScanState()
    object Loading : ScanState()
    data class FoundExisting(val product: FoodProduct) : ScanState()
    data class FoundNew(val product: FoodProduct) : ScanState()
    data class Partial(val product: FoodProduct, val missing: List<String>) : ScanState()
    data class Error(val message: String) : ScanState()
    object NotFound : ScanState()
}

@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    private val openFoodFacts: OpenFoodFactsClient,
    private val productDao: FoodProductDao
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _selectedProductId = MutableStateFlow<Long?>(null)
    val selectedProductId: StateFlow<Long?> = _selectedProductId.asStateFlow()

    private var lastScannedBarcode: String? = null

    fun onBarcodeDetected(barcode: String) {
        // Debounce — żeby ten sam barkod nie odpalał setki razy
        if (barcode == lastScannedBarcode) return
        if (_state.value is ScanState.Loading) return
        lastScannedBarcode = barcode

        viewModelScope.launch {
            _state.value = ScanState.Loading
            // Najpierw sprawdź lokalną bazę
            val existing = productDao.getByBarcode(barcode)
            if (existing != null) {
                _state.value = ScanState.FoundExisting(existing)
                return@launch
            }
            // Potem OpenFoodFacts
            when (val result = openFoodFacts.lookup(barcode)) {
                is OpenFoodFactsResult.Found -> {
                    _state.value = ScanState.FoundNew(result.product)
                }
                is OpenFoodFactsResult.PartialData -> {
                    _state.value = ScanState.Partial(result.partialProduct, result.missingFields)
                }
                is OpenFoodFactsResult.NotFound -> {
                    _state.value = ScanState.NotFound
                }
                is OpenFoodFactsResult.Error -> {
                    _state.value = ScanState.Error(result.message)
                }
            }
        }
    }

    fun acceptAndAddToBase(product: FoodProduct) {
        viewModelScope.launch {
            // Sprawdź duplikat po nazwie (case insensitive) — żeby nie zaśmiecać bazy
            val existingByName = productDao.getAll().firstOrNull {
                it.name.equals(product.name, ignoreCase = true)
            }
            val finalId = if (existingByName != null) {
                existingByName.id
            } else {
                productDao.upsert(product)
            }
            _selectedProductId.value = finalId
        }
    }

    fun acceptExisting(product: FoodProduct) {
        _selectedProductId.value = product.id
    }

    fun resetScan() {
        lastScannedBarcode = null
        _state.value = ScanState.Scanning
    }

    fun manualBarcodeEntry(barcode: String) {
        if (barcode.isBlank()) return
        onBarcodeDetected(barcode.trim())
    }
}
