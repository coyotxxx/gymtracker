package pl.filebit.gymtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsCacheService
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import javax.inject.Inject

data class HistoryItem(
    val workout: Workout,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val exerciseCount: Int,
    val planName: String? = null,
    val hasPR: Boolean = false,
    /** v1.19.0 — faza mesocyklu w której wykonano trening (do PhaseBadge w UI). */
    val mesocyclePhase: MesocyclePhase? = null
)

/**
 * v1.19.0 — pure function: dla listy mesocykli + timestamp znajduje fazę aktywną w tamtym czasie.
 * Lista mesocykli musi być posortowana DESC po startDateMs (tak jak `mesoDao.getRecent`).
 */
internal fun resolveMesocyclePhase(
    mesocycles: List<TrainingMesocycle>,
    workoutStartMs: Long
): MesocyclePhase? {
    // Znajdź mesocykl który zawiera workoutStartMs (startDate <= ts < endDate lub null=trwa).
    return mesocycles.firstOrNull { m ->
        val end = m.endDateMs ?: m.plannedEndDateMs
        m.startDateMs <= workoutStartMs && workoutStartMs < end
    }?.phase
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val statsRepo: StatsRepository,
    private val statsCacheService: StatsCacheService,
    // v1.19.0 — phase badge w historii
    private val mesoDao: TrainingMesocycleDao
) : ViewModel() {

    val workouts: StateFlow<List<HistoryItem>> = repo.observeAll()
        .map { list ->
            // v1.11.50: Pre-fetch RAZ — eliminuje N+1 queries:
            // - snapshot (1 query setów + workouts + exercises)
            // - allPlans (1 query)
            // Stara wersja robila ~270 queries dla 30 treningów → teraz 2 queries.
            val snapshot = runCatching { statsCacheService.snapshot() }
                .getOrDefault(pl.filebit.gymtracker.data.repository.StatsSnapshot.EMPTY)
            val plansById = runCatching { planRepo.getAll().associateBy { it.id } }
                .getOrDefault(emptyMap())
            // v1.19.0 — pobierz wszystkie mesocykle RAZ (do mapowania workoutu na fazę)
            val mesocycles = runCatching { mesoDao.getRecent(limit = 200) }.getOrNull().orEmpty()

            list.filter { !it.isActive }.map { w ->
                val sets = snapshot.setsByWorkoutId[w.id] ?: emptyList()
                val planName = w.fromPlanId?.let { plansById[it]?.name }
                val prs = runCatching { statsRepo.detectNewPRsFast(w.id, snapshot) }
                    .getOrDefault(emptyList())
                HistoryItem(
                    workout = w,
                    totalSets = sets.size,
                    totalVolumeKg = sets.sumOf { it.reps * it.weightKg },
                    exerciseCount = sets.map { it.exerciseId }.distinct().size,
                    planName = planName,
                    hasPR = prs.isNotEmpty(),
                    mesocyclePhase = resolveMesocyclePhase(mesocycles, w.startedAt)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
