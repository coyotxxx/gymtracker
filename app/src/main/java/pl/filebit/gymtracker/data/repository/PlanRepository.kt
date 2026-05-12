package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: TrainingPlanDao,
    private val planExerciseDao: PlanExerciseDao,
    private val planExerciseSetDao: PlanExerciseSetDao,
    private val weeklyOverrideDao: pl.filebit.gymtracker.data.db.dao.WeeklyPlanOverrideDao,
    private val eventDetectorService: pl.filebit.gymtracker.ai.EventDetectorService
) {

    /**
     * Efektywny harmonogram dla bieżącego tygodnia (z uwzględnieniem overrides).
     * Zwraca: dayOfWeek (1-7) → lista ScheduleSlot
     */
    suspend fun getEffectiveScheduleForCurrentWeek(): Map<Int, List<pl.filebit.gymtracker.util.ScheduleSlot>> {
        val plans = planDao.getAll()
        val baseSlots = plans.associate { plan ->
            val days = (1..7).filter { d ->
                planExerciseDao.getForPlanAndDay(plan.id, d).isNotEmpty()
            }.toSet()
            plan.id to days
        }
        val weekStart = pl.filebit.gymtracker.util.currentWeekStartMillis()
        val overrides = weeklyOverrideDao.getForWeek(weekStart)
        return pl.filebit.gymtracker.util.resolveWeekSchedule(baseSlots, overrides)
    }

    /**
     * Zapisuje przesunięcie treningu w bieżącym tygodniu.
     * @param targetDay 1-7 lub WeeklyPlanOverride.SKIPPED (-1) gdy pomijamy
     */
    suspend fun postponeTraining(planId: Long, originalDay: Int, targetDay: Int) {
        val weekStart = pl.filebit.gymtracker.util.currentWeekStartMillis()
        weeklyOverrideDao.deleteForOrigin(weekStart, planId, originalDay)
        if (targetDay != originalDay) {
            weeklyOverrideDao.insert(
                pl.filebit.gymtracker.data.entity.WeeklyPlanOverride(
                    weekStartMillis = weekStart,
                    planId = planId,
                    originalDayOfWeek = originalDay,
                    targetDayOfWeek = targetDay
                )
            )
        }
        // Sprzątaj overrides starsze niż 6 tygodni
        weeklyOverrideDao.deleteOldOverrides(weekStart - 6L * 7 * 24 * 60 * 60 * 1000)
    }

    /**
     * Cofa override (przywraca oryginalną pozycję z planu).
     */
    suspend fun clearTrainingOverride(planId: Long, originalDay: Int) {
        val weekStart = pl.filebit.gymtracker.util.currentWeekStartMillis()
        weeklyOverrideDao.deleteForOrigin(weekStart, planId, originalDay)
    }

    fun observeAllPlans(): Flow<List<TrainingPlan>> = planDao.observeAll()

    /** v1.11.50 — bulk fetch wszystkich planow (do uniknięcia N+1 w HistoryViewModel). */
    suspend fun getAll(): List<TrainingPlan> = planDao.getAll()

    suspend fun getPlan(id: Long): TrainingPlan? = planDao.getById(id)

    // === v1.24.12: aktywny plan (filozofia jeden user, jeden aktywny stan) ===

    /** Plan oznaczony jako aktywny (isActive=true). Null gdy user nie ma żadnego. */
    suspend fun getActivePlan(): TrainingPlan? = planDao.getActive()

    /** Reactive observe aktywnego planu — Home/PlanList nasłuchują zmian. */
    fun observeActivePlan(): Flow<TrainingPlan?> = planDao.observeActive()

    /** Atomowo ustawia plan jako jedyny aktywny (zeruje innym isActive). */
    suspend fun setActivePlan(planId: Long) {
        planDao.clearActive()
        planDao.markActive(planId)
    }

    suspend fun upsertPlan(plan: TrainingPlan): Long {
        val isNew = plan.id == 0L
        val resultId = planDao.upsert(plan)
        // v1.11.63: PLAN_START event tylko dla NOWYCH planow (insert, nie update)
        if (isNew) {
            runCatching {
                eventDetectorService.onPlanCreated(resultId, plan.copy(id = resultId).name)
            }
            // v1.24.12: jeśli to pierwszy plan w bazie (lub żaden nie był aktywny),
            // ustaw nowy jako aktywny — żeby user nie zostawał z pustym stanem.
            if (planDao.getActive() == null) {
                setActivePlan(resultId)
            }
        }
        return resultId
    }

    suspend fun updatePlan(plan: TrainingPlan) = planDao.update(plan)

    suspend fun deletePlan(plan: TrainingPlan) {
        val wasActive = plan.isActive
        planDao.delete(plan)
        // v1.11.63: PLAN_END event po usunieciu planu
        runCatching { eventDetectorService.onPlanDeleted(plan.id, plan.name) }
        // v1.24.12: jeśli usunęliśmy aktywny plan, promuj najnowszy pozostały
        if (wasActive) promoteNewestAsActive()
    }

    suspend fun deletePlanById(id: Long) {
        val plan = planDao.getById(id)
        val wasActive = plan?.isActive == true
        planDao.deleteById(id)
        if (plan != null) {
            runCatching { eventDetectorService.onPlanDeleted(plan.id, plan.name) }
        }
        if (wasActive) promoteNewestAsActive()
    }

    /** v1.24.12: po usunięciu aktywnego planu wybierz najnowszy z pozostałych. */
    private suspend fun promoteNewestAsActive() {
        val newest = planDao.getAll().maxByOrNull { it.createdAt } ?: return
        setActivePlan(newest.id)
    }

    fun observePlanExercises(planId: Long): Flow<List<PlanExercise>> =
        planExerciseDao.observeForPlan(planId)

    suspend fun getPlanExercises(planId: Long): List<PlanExercise> =
        planExerciseDao.getForPlan(planId)

    suspend fun getPlanExercisesForDay(planId: Long, day: Int): List<PlanExercise> =
        planExerciseDao.getForPlanAndDay(planId, day)

    suspend fun getDaysWithExercises(planId: Long): List<Int> =
        planExerciseDao.getDaysWithExercises(planId)

    suspend fun upsertPlanExercise(pe: PlanExercise): Long = planExerciseDao.upsert(pe)

    suspend fun updatePlanExercise(pe: PlanExercise) = planExerciseDao.update(pe)

    suspend fun deletePlanExercise(pe: PlanExercise) = planExerciseDao.delete(pe)

    suspend fun deleteAllPlanExercises(planId: Long) =
        planExerciseDao.deleteAllForPlan(planId)

    // ===== Per-set w planie =====
    suspend fun getSetsForPlanExercise(planExerciseId: Long): List<PlanExerciseSet> =
        planExerciseSetDao.getForPlanExercise(planExerciseId)

    suspend fun upsertPlanSet(set: PlanExerciseSet): Long = planExerciseSetDao.upsert(set)

    suspend fun updatePlanSet(set: PlanExerciseSet) = planExerciseSetDao.update(set)

    suspend fun deletePlanSet(set: PlanExerciseSet) = planExerciseSetDao.delete(set)

    suspend fun deleteAllSetsForPlanExercise(planExerciseId: Long) =
        planExerciseSetDao.deleteAllForPlanExercise(planExerciseId)

    suspend fun getMaxOrderIndex(planId: Long): Int? =
        planExerciseDao.getMaxOrderIndex(planId)

    /**
     * Aplikuje sugestię progresji do wszystkich serii danego ćwiczenia w planie.
     * Wywoływane po treningu z dialogu post-workout (auto-update planu).
     * Zwraca liczbę zaktualizowanych setów.
     */
    suspend fun applyProgressionToPlan(
        planId: Long,
        exerciseId: Long,
        newWeightKg: Double,
        newReps: Int
    ): Int {
        val planExercises = planExerciseDao.getForPlan(planId)
            .filter { it.exerciseId == exerciseId }
        var updated = 0
        for (pe in planExercises) {
            val sets = planExerciseSetDao.getForPlanExercise(pe.id)
            for (set in sets) {
                planExerciseSetDao.update(
                    set.copy(weightKg = newWeightKg, reps = newReps)
                )
                updated++
            }
        }
        return updated
    }

    /**
     * Pobiera wszystkie plany i filtruje te które mają w daysOfWeek wskazany dzień.
     */
    suspend fun getPlansForDay(day: Int): List<TrainingPlan> =
        planDao.getAll().filter { it.daysOfWeek.contains(day) }

    fun observePlanWithExercises(planId: Long): Flow<Pair<TrainingPlan?, List<PlanExercise>>> =
        combine(
            planDao.observeAll(),
            planExerciseDao.observeForPlan(planId)
        ) { plans, exercises ->
            val plan = plans.firstOrNull { it.id == planId }
            plan to exercises
        }
}
