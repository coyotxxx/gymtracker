package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.ShoppingListDao
import pl.filebit.gymtracker.data.entity.ShoppingList
import pl.filebit.gymtracker.data.entity.ShoppingListItem
import pl.filebit.gymtracker.data.repository.ShoppingListGenerator
import javax.inject.Inject
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val dao: ShoppingListDao,
    private val generator: ShoppingListGenerator
) : ViewModel() {

    private val _list = MutableStateFlow<ShoppingList?>(null)
    val list: StateFlow<ShoppingList?> = _list.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val items: StateFlow<List<ShoppingListItem>> = _list
        .flatMapLatest { l ->
            if (l == null) flowOf(emptyList())
            else dao.observeItems(l.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _list.value = dao.getLatest()
        }
    }

    fun generate(daysAhead: Int) {
        if (_generating.value) return
        viewModelScope.launch {
            _generating.value = true
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val from = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, daysAhead)
                val to = cal.timeInMillis
                val name = "Plan zakupów na $daysAhead dni"
                val newId = generator.generate(from, to, name)
                _list.value = dao.getById(newId)
                _statusMessage.value = "Lista wygenerowana"
            } finally {
                _generating.value = false
            }
        }
    }

    fun togglePurchased(item: ShoppingListItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(isPurchased = !item.isPurchased))
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    suspend fun exportText(): String {
        val l = _list.value ?: return ""
        val items = dao.getItems(l.id)
        return generator.exportAsText(l, items)
    }
}
