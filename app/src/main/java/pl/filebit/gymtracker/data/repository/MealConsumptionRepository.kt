package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.MealConsumptionDao
import pl.filebit.gymtracker.data.entity.MealConsumption
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealConsumptionRepository @Inject constructor(
    private val dao: MealConsumptionDao
) {
    suspend fun get(dateMs: Long, mealType: MealType): MealConsumption? {
        val (start, _) = dayBounds(dateMs)
        return dao.get(start, mealType)
    }

    fun observeForDate(dateMs: Long): Flow<List<MealConsumption>> {
        val (start, _) = dayBounds(dateMs)
        return dao.observeForDate(start)
    }

    suspend fun getForDate(dateMs: Long): List<MealConsumption> {
        val (start, _) = dayBounds(dateMs)
        return dao.getForDate(start)
    }

    /**
     * Cykl statusu: brak → CONSUMED → SKIPPED → PLANNED → CONSUMED ...
     * Trzy kliki = pełny cykl.
     */
    suspend fun cycleStatus(dateMs: Long, mealType: MealType) {
        val (start, _) = dayBounds(dateMs)
        val current = dao.get(start, mealType)
        val nextStatus = when (current?.status) {
            null, MealConsumptionStatus.PLANNED -> MealConsumptionStatus.CONSUMED
            MealConsumptionStatus.CONSUMED -> MealConsumptionStatus.SKIPPED
            MealConsumptionStatus.SKIPPED -> MealConsumptionStatus.PLANNED
        }
        dao.insert(MealConsumption(
            id = current?.id ?: 0,
            dateMs = start,
            mealType = mealType,
            status = nextStatus,
            notedAt = System.currentTimeMillis()
        ))
    }

    suspend fun setStatus(dateMs: Long, mealType: MealType, status: MealConsumptionStatus) {
        val (start, _) = dayBounds(dateMs)
        val current = dao.get(start, mealType)
        dao.insert(MealConsumption(
            id = current?.id ?: 0,
            dateMs = start,
            mealType = mealType,
            status = status,
            notedAt = System.currentTimeMillis()
        ))
    }

    private fun dayBounds(dateMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}
