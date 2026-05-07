package pl.filebit.gymtracker.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AppNotification
import pl.filebit.gymtracker.ai.NotificationCenter
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val center: NotificationCenter,
    private val readPrefs: pl.filebit.gymtracker.data.repository.ReadNotificationsPrefs
) : ViewModel() {

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init { reload() }

    fun reload() {
        _loading.value = true
        viewModelScope.launch {
            val items = runCatching { center.computeNotifications() }.getOrDefault(emptyList())
            _items.value = items
            _unreadCount.value = readPrefs.unreadCount(items.map { it.id })
            _loading.value = false
        }
    }

    /** Wywoływane gdy user otwiera ekran NotificationsScreen — oznacza wszystkie jako przeczytane. */
    fun markAllAsRead() {
        val ids = _items.value.map { it.id }
        readPrefs.markAllRead(ids)
        _unreadCount.value = 0
    }
}
