package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.MealConsumptionDao
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.DiagnosticLevel
import pl.filebit.gymtracker.data.entity.MealConsumption
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.73.0 (POSIŁKI N): status konsumpcji kluczowany po NUMERZE slotu (dateMs, mealSlot),
 * nie po mealType — dzięki temu każdy posiłek (także wiele przekąsek) ma niezależny status.
 */
@Singleton
class MealConsumptionRepository @Inject constructor(
    private val dao: MealConsumptionDao,
    // v2.24.0: nullable-default — Hilt wstrzykuje realny logger w apce, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    suspend fun get(dateMs: Long, mealSlot: Int): MealConsumption? {
        val (start, _) = dayBounds(dateMs)
        return dao.get(start, mealSlot)
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
    suspend fun cycleStatus(dateMs: Long, mealSlot: Int) {
        val (start, _) = dayBounds(dateMs)
        val current = dao.get(start, mealSlot)
        val nextStatus = when (current?.status) {
            null, MealConsumptionStatus.PLANNED -> MealConsumptionStatus.CONSUMED
            MealConsumptionStatus.CONSUMED -> MealConsumptionStatus.SKIPPED
            MealConsumptionStatus.SKIPPED -> MealConsumptionStatus.PLANNED
        }
        dao.insert(MealConsumption(
            id = current?.id ?: 0,
            dateMs = start,
            mealSlot = mealSlot,
            status = nextStatus,
            notedAt = System.currentTimeMillis()
        ))
        logStatus("meal_status_cycle", mealSlot, current?.status, nextStatus, start)
    }

    suspend fun setStatus(dateMs: Long, mealSlot: Int, status: MealConsumptionStatus) {
        val (start, _) = dayBounds(dateMs)
        val current = dao.get(start, mealSlot)
        dao.insert(MealConsumption(
            id = current?.id ?: 0,
            dateMs = start,
            mealSlot = mealSlot,
            status = status,
            notedAt = System.currentTimeMillis()
        ))
        logStatus("meal_status_set", mealSlot, current?.status, status, start)
    }

    /**
     * v2.24.0: jeden punkt logujący KAŻDĄ zmianę statusu posiłku (UI, notyfikacja, AI).
     * Dzięki temu w logu widać np. „Posiłek 3 → SKIPPED w piątek".
     */
    private fun logStatus(
        eventCode: String,
        mealSlot: Int,
        from: MealConsumptionStatus?,
        to: MealConsumptionStatus,
        dayStart: Long
    ) {
        val level = if (to == MealConsumptionStatus.SKIPPED) DiagnosticLevel.WARN else DiagnosticLevel.INFO
        diag?.event(
            category = DiagnosticCategory.DIET,
            level = level,
            source = "MealConsumptionRepository",
            event = eventCode,
            message = "Posiłek $mealSlot: ${from?.name ?: "brak"} → ${to.name}",
            dataJson = """{"mealSlot":$mealSlot,"from":${from?.let { "\"${it.name}\"" } ?: "null"},"to":"${to.name}","dayStartMs":$dayStart}""",
            success = true
        )
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
