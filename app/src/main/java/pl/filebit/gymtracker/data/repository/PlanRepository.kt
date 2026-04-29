package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: TrainingPlanDao,
    private val planExerciseDao: PlanExerciseDao
) {

    fun observeAllPlans(): Flow<List<TrainingPlan>> = planDao.observeAll()

    suspend fun getPlan(id: Long): TrainingPlan? = planDao.getById(id)

    suspend fun upsertPlan(plan: TrainingPlan): Long = planDao.upsert(plan)

    suspend fun updatePlan(plan: TrainingPlan) = planDao.update(plan)

    suspend fun deletePlan(plan: TrainingPlan) = planDao.delete(plan)

    suspend fun deletePlanById(id: Long) = planDao.deleteById(id)

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

    suspend fun getMaxOrderIndex(planId: Long): Int? =
        planExerciseDao.getMaxOrderIndex(planId)

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
