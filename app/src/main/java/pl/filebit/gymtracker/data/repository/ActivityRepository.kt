package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.DailyActivityLogDao
import pl.filebit.gymtracker.data.entity.ActivitySource
import pl.filebit.gymtracker.data.entity.DailyActivityLog
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val dao: DailyActivityLogDao
) {

    fun observeForDate(dateMs: Long): Flow<DailyActivityLog?> {
        val (start, _) = dayBounds(dateMs)
        return dao.observeForDate(start)
    }

    suspend fun getForDate(dateMs: Long): DailyActivityLog? {
        val (start, _) = dayBounds(dateMs)
        return dao.getForDate(start)
    }

    /**
     * Update kroków dla danego dnia. Auto-licz estimatedKcalNeat (~0.04 kcal/krok dla 70-80 kg).
     */
    suspend fun setSteps(dateMs: Long, steps: Int, source: ActivitySource = ActivitySource.MANUAL) {
        val (start, _) = dayBounds(dateMs)
        val existing = dao.getForDate(start)
        val now = System.currentTimeMillis()
        val kcalNeat = estimateKcalFromSteps(steps)
        if (existing != null) {
            dao.update(existing.copy(steps = steps, estimatedKcalNeat = kcalNeat, source = source, updatedAt = now))
        } else {
            dao.insert(DailyActivityLog(
                dateMs = start, steps = steps,
                estimatedKcalNeat = kcalNeat, source = source,
                createdAt = now, updatedAt = now
            ))
        }
    }

    suspend fun setActiveMinutes(dateMs: Long, minutes: Int, source: ActivitySource = ActivitySource.MANUAL) {
        val (start, _) = dayBounds(dateMs)
        val existing = dao.getForDate(start)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.update(existing.copy(activeMinutes = minutes, source = source, updatedAt = now))
        } else {
            dao.insert(DailyActivityLog(
                dateMs = start, activeMinutes = minutes,
                source = source, createdAt = now, updatedAt = now
            ))
        }
    }

    suspend fun deleteForDate(dateMs: Long) {
        val (start, _) = dayBounds(dateMs)
        dao.getForDate(start)?.let { dao.delete(it.id) }
    }

    suspend fun avgStepsLastNDays(days: Int): Int {
        val now = System.currentTimeMillis()
        val (todayStart, _) = dayBounds(now)
        val ms = days * 24L * 3600 * 1000
        val start = todayStart - ms
        return dao.avgStepsForRange(start, todayStart).toInt()
    }

    suspend fun getRecent(days: Int = 30): List<DailyActivityLog> {
        val sinceMs = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return dao.getSince(sinceMs, limit = days)
    }

    fun observeRecent(limit: Int = 30): Flow<List<DailyActivityLog>> = dao.observeRecent(limit)

    /** ~0.04 kcal/krok (70-80 kg). Skaluje się z masą — uproszczenie. */
    private fun estimateKcalFromSteps(steps: Int): Int = (steps * 0.04).toInt()

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
