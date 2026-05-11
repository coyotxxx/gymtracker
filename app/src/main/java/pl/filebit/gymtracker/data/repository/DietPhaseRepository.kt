package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.DietPhaseDao
import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DietPhaseRepository @Inject constructor(
    private val dao: DietPhaseDao
) {
    suspend fun getCurrent(): DietPhase? = dao.getCurrent()

    fun observeCurrent(): Flow<DietPhase?> = dao.observeCurrent()

    fun observeAll(): Flow<List<DietPhase>> = dao.observeAll()

    suspend fun getRecent(limit: Int = 50): List<DietPhase> = dao.getRecent(limit)

    suspend fun getLastCut(): DietPhase? = dao.getLastCut()

    /** v1.17.0 — startDateMs ostatniego REFEED_DAY (lub null gdy brak). */
    suspend fun getLastEndedRefeedStartMs(): Long? = dao.getLastRefeedStartMs()

    /**
     * Rozpoczyna nową fazę. Zamyka aktualną (jeśli istnieje) ustawiając endDateMs.
     * Zwraca id nowej fazy.
     */
    suspend fun startPhase(phase: DietPhase): Long {
        val now = phase.startDateMs
        // Zamknij aktualną fazę
        dao.getCurrent(now)?.let { current ->
            if (current.endDateMs == null || current.endDateMs > now) {
                dao.closePhase(current.id, endMs = now)
            }
        }
        return dao.insert(phase)
    }

    /**
     * Kończy aktualną fazę (np. user kliknie "zakończ MAINTENANCE").
     */
    suspend fun closeCurrent() {
        dao.getCurrent()?.let { dao.closePhase(it.id) }
    }

    /**
     * Aktualna długość bieżącej fazy CUT w dniach (do reguł 8-12 tyg).
     * Jeśli nie ma aktywnej CUT — zwraca 0.
     */
    suspend fun currentCutDurationDays(): Int {
        val current = dao.getCurrent() ?: return 0
        if (current.type != DietPhaseType.CUT) return 0
        return current.durationDays()
    }
}
