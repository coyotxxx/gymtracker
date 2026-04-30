package pl.filebit.gymtracker.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import pl.filebit.gymtracker.data.template.PlanTemplate
import javax.inject.Inject

data class PlanListItem(
    val plan: TrainingPlan,
    val exerciseCount: Int,
    val daysWithExercises: List<Int> = emptyList()
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository
) : ViewModel() {

    /** Plan ID aktywnego treningu (jeśli z planu) — używane do badge "AKTYWNY". */
    val activePlanId: StateFlow<Long?> = workoutRepo.observeActive()
        .map { it?.fromPlanId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val plans: StateFlow<List<PlanListItem>> = planRepo.observeAllPlans()
        .mapLatest { list ->
            list.map { plan ->
                val exes = planRepo.getPlanExercises(plan.id)
                PlanListItem(
                    plan = plan,
                    exerciseCount = exes.size,
                    daysWithExercises = exes.map { it.dayOfWeek }.distinct().sorted()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun duplicatePlan(planId: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val src = planRepo.getPlan(planId) ?: return@launch
            val newId = planRepo.upsertPlan(
                src.copy(
                    id = 0L,
                    name = src.name + " (kopia)",
                    createdAt = System.currentTimeMillis()
                )
            )
            val srcExercises = planRepo.getPlanExercises(planId)
            for (pe in srcExercises) {
                val newPeId = planRepo.upsertPlanExercise(
                    pe.copy(id = 0L, planId = newId)
                )
                val srcSets = planRepo.getSetsForPlanExercise(pe.id)
                for (s in srcSets) {
                    planRepo.upsertPlanSet(s.copy(id = 0L, planExerciseId = newPeId))
                }
            }
            onDone(newId)
        }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            planRepo.deletePlanById(planId)
        }
    }

    fun createFromTemplate(template: PlanTemplate, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val planId = planRepo.upsertPlan(
                TrainingPlan(
                    name = template.name,
                    daysOfWeek = template.days.map { it.dayOfWeek }.distinct().sorted(),
                    notes = template.description
                )
            )
            for (day in template.days) {
                day.exercises.forEachIndexed { idx, te ->
                    // znajdź ćwiczenie po nazwie; jeśli nie ma — utwórz custom
                    val ex = exerciseRepo.findByName(te.exerciseName) ?: run {
                        val newId = exerciseRepo.upsert(
                            pl.filebit.gymtracker.data.entity.Exercise(
                                name = te.exerciseName,
                                primaryMuscle = pl.filebit.gymtracker.data.entity.MuscleGroup.OTHER,
                                equipment = pl.filebit.gymtracker.data.entity.Equipment.OTHER,
                                isCustom = true
                            )
                        )
                        exerciseRepo.get(newId)!!
                    }
                    val peId = planRepo.upsertPlanExercise(
                        PlanExercise(
                            planId = planId,
                            exerciseId = ex.id,
                            dayOfWeek = day.dayOfWeek,
                            orderIndex = idx
                        )
                    )
                    repeat(te.sets) { setIdx ->
                        planRepo.upsertPlanSet(
                            PlanExerciseSet(
                                planExerciseId = peId,
                                setNumber = setIdx + 1,
                                reps = te.reps,
                                weightKg = null,
                                restSeconds = te.restSeconds
                            )
                        )
                    }
                }
            }
            onDone(planId)
        }
    }

    fun startWorkoutFromPlanForDay(planId: Long, day: Int, onReady: () -> Unit) {
        viewModelScope.launch {
            val active = workoutRepo.startOrResume(fromPlanId = planId, fromDayOfWeek = day)
            val planExercises = planRepo.getPlanExercisesForDay(planId, day)
            planExercises.forEach { pe ->
                val setSpecs = planRepo.getSetsForPlanExercise(pe.id)
                setSpecs.forEach { spec ->
                    workoutRepo.addPlannedSet(
                        workoutId = active.id,
                        exerciseId = pe.exerciseId,
                        reps = spec.reps,
                        weightKg = spec.weightKg ?: 0.0
                    )
                }
            }
            onReady()
        }
    }
}
