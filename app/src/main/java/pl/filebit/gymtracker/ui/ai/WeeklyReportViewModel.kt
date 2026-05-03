package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiPlanApplier
import pl.filebit.gymtracker.ai.AiPlanProposal
import pl.filebit.gymtracker.ai.ReportAction
import pl.filebit.gymtracker.ai.WeeklyReportService
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.entity.AiWeeklyReport
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import javax.inject.Inject

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedActions: Set<Int> = emptySet(),
    val targetPlanId: Long? = null,
    val followUpMessage: String = ""
)

sealed class WeeklyImprovementState {
    object Idle : WeeklyImprovementState()
    object Loading : WeeklyImprovementState()
    data class Preview(val proposal: AiPlanProposal, val basedOnReportId: Long) : WeeklyImprovementState()
    object Applying : WeeklyImprovementState()
    data class Error(val message: String) : WeeklyImprovementState()
}

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val service: WeeklyReportService,
    private val reportDao: AiWeeklyReportDao,
    private val planRepo: PlanRepository,
    private val exerciseRepo: ExerciseRepository,
    private val planApplier: AiPlanApplier
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyReportUiState())
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    private val _improvement = MutableStateFlow<WeeklyImprovementState>(WeeklyImprovementState.Idle)
    val improvement: StateFlow<WeeklyImprovementState> = _improvement.asStateFlow()

    val reports: StateFlow<List<AiWeeklyReport>> = reportDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plans: StateFlow<List<TrainingPlan>> = planRepo.observeAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun generate() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = service.generate()
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            // Wyczyść stan zaznaczeń przy nowym raporcie
                            selectedActions = emptySet(),
                            followUpMessage = ""
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Nieznany błąd") }
                }
            )
        }
    }

    fun deleteReport(id: Long) {
        viewModelScope.launch { reportDao.delete(id) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Parsuje akcje z treści raportu (bez side-effectu — UI używa tego do listy). */
    fun parseActions(reportContent: String): List<ReportAction> =
        service.parseActions(reportContent)

    fun toggleAction(actionId: Int) {
        _state.update { st ->
            val newSet = if (actionId in st.selectedActions) st.selectedActions - actionId
            else st.selectedActions + actionId
            st.copy(selectedActions = newSet)
        }
    }

    fun setTargetPlan(planId: Long?) {
        _state.update { it.copy(targetPlanId = planId) }
    }

    fun setFollowUpMessage(msg: String) {
        _state.update { it.copy(followUpMessage = msg) }
    }

    fun requestImprovement(report: AiWeeklyReport) {
        val st = _state.value
        val planId = st.targetPlanId ?: plans.value.firstOrNull()?.id ?: run {
            _improvement.value = WeeklyImprovementState.Error("Najpierw utwórz przynajmniej jeden plan treningowy.")
            return
        }
        val all = service.parseActions(report.content)
        val selected = all.filter { it.id in st.selectedActions }
        if (selected.isEmpty() && st.followUpMessage.isBlank()) {
            _improvement.value = WeeklyImprovementState.Error("Zaznacz akcje lub doprecyzuj prośbą.")
            return
        }
        _improvement.value = WeeklyImprovementState.Loading
        viewModelScope.launch {
            val result = service.improvePlanFromReport(
                planId = planId,
                reportContent = report.content,
                selectedActions = selected,
                userMessage = st.followUpMessage.takeIf { it.isNotBlank() }
            )
            result.fold(
                onSuccess = { proposal ->
                    _improvement.value = WeeklyImprovementState.Preview(proposal, report.id)
                },
                onFailure = { e ->
                    _improvement.value = WeeklyImprovementState.Error(e.message ?: "Błąd AI")
                }
            )
        }
    }

    fun applyImprovement(asCopy: Boolean) {
        val state = _improvement.value
        if (state !is WeeklyImprovementState.Preview) return
        val planId = _state.value.targetPlanId ?: plans.value.firstOrNull()?.id ?: return
        _improvement.value = WeeklyImprovementState.Applying
        viewModelScope.launch {
            val outcome = if (asCopy) {
                planApplier.applyAsCopy(planId, state.proposal).map { Unit }
            } else {
                planApplier.replaceExistingPlan(planId, state.proposal)
            }
            outcome.fold(
                onSuccess = {
                    _improvement.value = WeeklyImprovementState.Idle
                    _state.update {
                        it.copy(selectedActions = emptySet(), followUpMessage = "")
                    }
                },
                onFailure = {
                    _improvement.value = WeeklyImprovementState.Error(it.message ?: "Błąd zapisu")
                }
            )
        }
    }

    fun dismissImprovement() {
        _improvement.value = WeeklyImprovementState.Idle
    }

    /** Snapshot ćwiczeń planu (per dzień → lista nazw) — do diff w preview. */
    suspend fun loadPlanExerciseNamesByDay(planId: Long): Map<Int, List<String>> {
        val exes = planRepo.getPlanExercises(planId)
        return exes.groupBy { it.dayOfWeek }
            .mapValues { (_, list) ->
                list.sortedBy { it.orderIndex }
                    .mapNotNull { exerciseRepo.get(it.exerciseId)?.name }
            }
    }
}
