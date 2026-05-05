package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.repository.MealFeedbackRepository
import javax.inject.Inject

@HiltViewModel
class MealPreferencesViewModel @Inject constructor(
    private val repo: MealFeedbackRepository
) : ViewModel() {
    val items: StateFlow<List<MealFeedback>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
