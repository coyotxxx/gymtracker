package pl.filebit.gymtracker.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.repository.GoalProgress
import pl.filebit.gymtracker.data.repository.GoalRepository
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository
) : ViewModel() {

    private val _progresses = MutableStateFlow<List<GoalProgress>>(emptyList())
    val progresses: StateFlow<List<GoalProgress>> = _progresses.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // gdy lista celów się zmieni, recompute
            repo.observeAll().collect { all ->
                _progresses.value = all.map { repo.computeProgress(it) }
            }
        }
    }

    fun upsert(goal: Goal) {
        viewModelScope.launch {
            repo.upsert(goal)
        }
    }

    fun delete(goal: Goal) {
        viewModelScope.launch {
            repo.delete(goal)
        }
    }
}
