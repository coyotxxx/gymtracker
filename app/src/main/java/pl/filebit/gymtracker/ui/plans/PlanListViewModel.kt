package pl.filebit.gymtracker.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.PlanRepository
import javax.inject.Inject

data class PlanListItem(
    val plan: TrainingPlan,
    val exerciseCount: Int
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val planRepo: PlanRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val plans: StateFlow<List<PlanListItem>> = planRepo.observeAllPlans()
        .mapLatest { list ->
            list.map { plan ->
                PlanListItem(plan, planRepo.getPlanExercises(plan.id).size)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
