package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.HydrationLogDao
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.HydrationLog
import pl.filebit.gymtracker.data.entity.HydrationSource
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HydrationRepository @Inject constructor(
    private val dao: HydrationLogDao,
    // v2.29.0: nullable-default — Hilt wstrzykuje realny logger, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    fun observeForDate(dateMs: Long): Flow<List<HydrationLog>> {
        val (start, end) = dayBounds(dateMs)
        return dao.observeForDayRange(start, end)
    }

    suspend fun sumForDate(dateMs: Long): Int {
        val (start, end) = dayBounds(dateMs)
        return dao.sumMlForRange(start, end)
    }

    suspend fun add(dateMs: Long, ml: Int, source: HydrationSource = HydrationSource.WATER) {
        val (start, _) = dayBounds(dateMs)
        dao.insert(HydrationLog(dateMs = start, ml = ml, source = source))
        diag?.info(DiagnosticCategory.DIET, "HydrationRepository", "hydration_logged",
            "Zalogowano nawodnienie: ${ml}ml (${source.name})",
            dataJson = """{"ml":$ml,"source":"${source.name}"}""", success = true)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Adherence średnia z ostatnich N dni — dla silnika korekt. */
    suspend fun avgAdherenceLastNDays(weightKg: Double, days: Int = 14, calc: HydrationCalculator): Int {
        if (days <= 0) return 0
        val now = System.currentTimeMillis()
        val (todayStart, _) = dayBounds(now)
        val ms = days * 24L * 3600 * 1000
        val sinceStart = todayStart - ms

        val logs = dao.getSince(sinceStart)
        val byDay = logs.groupBy { dayBounds(it.dateMs).first }

        var sumPct = 0
        var sampleDays = 0
        for ((_, dayLogs) in byDay) {
            val sum = dayLogs.sumOf { it.ml }
            val target = calc.computeTarget(weightKg, hadTrainingToday = false, proteinGramsToday = 0.0, usesCreatine = false).totalMl
            sumPct += calc.adherencePct(sum, target)
            sampleDays++
        }
        return if (sampleDays > 0) sumPct / sampleDays else 0
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
