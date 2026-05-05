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
import pl.filebit.gymtracker.data.db.dao.MealPrepPlanDao
import pl.filebit.gymtracker.data.entity.MealPrepPlan
import pl.filebit.gymtracker.data.entity.MealPrepStep
import pl.filebit.gymtracker.data.repository.MealPrepPlanner
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPrepViewModel @Inject constructor(
    private val dao: MealPrepPlanDao,
    private val planner: MealPrepPlanner
) : ViewModel() {

    private val _plan = MutableStateFlow<MealPrepPlan?>(null)
    val plan: StateFlow<MealPrepPlan?> = _plan.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val steps: StateFlow<List<MealPrepStep>> = _plan
        .flatMapLatest { p ->
            if (p == null) flowOf(emptyList())
            else dao.observeSteps(p.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _plan.value = dao.getLatest()
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
                val name = "Meal prep na $daysAhead ${if (daysAhead == 1) "dzień" else "dni"}"
                val newId = planner.generate(from, to, name)
                _plan.value = dao.getById(newId)
                _statusMessage.value = "Plan wygenerowany"
            } finally {
                _generating.value = false
            }
        }
    }

    fun toggleStep(step: MealPrepStep) {
        viewModelScope.launch {
            dao.updateStep(step.copy(isCompleted = !step.isCompleted))
        }
    }

    fun consumeStatusMessage() { _statusMessage.value = null }

    suspend fun exportText(): String {
        val p = _plan.value ?: return ""
        val s = dao.getSteps(p.id)
        return planner.exportAsText(p, s)
    }
}
