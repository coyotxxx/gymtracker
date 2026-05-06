package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.AiLogDao
import pl.filebit.gymtracker.data.entity.AiLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiLogRepository @Inject constructor(
    private val dao: AiLogDao
) {
    fun observeRecent(limit: Int = 200): Flow<List<AiLog>> = dao.observeRecent(limit)
    suspend fun getRecent(limit: Int = 200): List<AiLog> = dao.getRecent(limit)
    suspend fun getById(id: Long): AiLog? = dao.getById(id)
    suspend fun count(): Int = dao.count()

    /** Zapisz log + automatycznie usuwa najstarsze (keep max 200). */
    suspend fun log(log: AiLog): Long {
        val id = dao.insert(log)
        runCatching { dao.trimToMax(MAX_LOGS) }
        return id
    }

    suspend fun deleteAll() = dao.deleteAll()
    suspend fun deleteOlderThanDays(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600 * 1000
        dao.deleteOlderThan(cutoff)
    }

    companion object {
        const val MAX_LOGS = 200
    }
}
