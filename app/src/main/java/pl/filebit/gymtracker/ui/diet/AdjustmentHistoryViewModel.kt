package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.DietAdjustment
import pl.filebit.gymtracker.data.repository.AutoAdjustmentService
import javax.inject.Inject

@HiltViewModel
class AdjustmentHistoryViewModel @Inject constructor(
    private val autoAdjust: AutoAdjustmentService
) : ViewModel() {

    private val _items = MutableStateFlow<List<DietAdjustment>>(emptyList())
    val items: StateFlow<List<DietAdjustment>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            _items.value = autoAdjust.getRecent(50)
        }
    }
}
