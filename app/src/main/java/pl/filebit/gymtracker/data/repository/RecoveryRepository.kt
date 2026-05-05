package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.RecoveryLogDao
import pl.filebit.gymtracker.data.entity.RecoveryLog
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryRepository @Inject constructor(
    private val dao: RecoveryLogDao
) {

    fun observeForDate(dateMs: Long): Flow<RecoveryLog?> {
        val (start, _) = dayBounds(dateMs)
        return dao.observeForDate(start)
    }

    suspend fun getForDate(dateMs: Long): RecoveryLog? {
        val (start, _) = dayBounds(dateMs)
        return dao.getForDate(start)
    }

    suspend fun upsert(
        dateMs: Long,
        sleepHours: Double? = null,
        sleepQuality: Int? = null,
        stressLevel: Int? = null,
        hungerLevel: Int? = null,
        energyLevel: Int? = null,
        sorenessLevel: Int? = null,
        difficultyAdherence: Int? = null,
        notes: String = ""
    ) {
        val (start, _) = dayBounds(dateMs)
        val existing = dao.getForDate(start)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.update(existing.copy(
                sleepHours = sleepHours ?: existing.sleepHours,
                sleepQuality = sleepQuality ?: existing.sleepQuality,
                stressLevel = stressLevel ?: existing.stressLevel,
                hungerLevel = hungerLevel ?: existing.hungerLevel,
                energyLevel = energyLevel ?: existing.energyLevel,
                sorenessLevel = sorenessLevel ?: existing.sorenessLevel,
                difficultyAdherence = difficultyAdherence ?: existing.difficultyAdherence,
                notes = notes.ifBlank { existing.notes },
                updatedAt = now
            ))
        } else {
            dao.insert(RecoveryLog(
                dateMs = start,
                sleepHours = sleepHours,
                sleepQuality = sleepQuality,
                stressLevel = stressLevel,
                hungerLevel = hungerLevel,
                energyLevel = energyLevel,
                sorenessLevel = sorenessLevel,
                difficultyAdherence = difficultyAdherence,
                notes = notes,
                createdAt = now,
                updatedAt = now
            ))
        }
    }

    suspend fun getLast7Days(): List<RecoveryLog> {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        return dao.getSince(sevenDaysAgo, limit = 7)
    }

    fun observeRecent(limit: Int = 14): Flow<List<RecoveryLog>> = dao.observeRecent(limit)

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
