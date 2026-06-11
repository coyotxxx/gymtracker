package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.filebit.gymtracker.data.db.dao.DietPhaseDao
import pl.filebit.gymtracker.data.db.dao.UserProfileDao
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.toWeightGoal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val dao: UserProfileDao,
    // v2.16.0 (P1-5): nullable-default — Hilt wstrzykuje realny DAO, testy biorą null
    // (konstruują UserProfileRepository(dao) i nie potrzebują synchronizacji fazy).
    private val dietPhaseDao: DietPhaseDao? = null
) {
    fun observe(): Flow<UserProfile> = dao.observe().map { it ?: UserProfile() }
    suspend fun get(): UserProfile = dao.get() ?: UserProfile().also { dao.upsert(it) }

    /**
     * v1.28.1 (Etap 2): `goalType` jest jedynym źródłem prawdy o celu.
     * Każdy zapis NORMALIZUJE legacy `weightGoalType` z `goalType` — dzięki temu
     * dwa pola nie mogą się rozjechać niezależnie od tego, który ekran zapisuje.
     */
    suspend fun save(profile: UserProfile) {
        val normalized = profile.copy(id = 1, weightGoalType = profile.goalType.toWeightGoal())
        dao.upsert(normalized)
        // v2.16.0 (P1-5): zmiana kierunku celu (CUT→BULK itd.) zamyka starą fazę diety,
        // żeby getCurrent() nie zwracał nieaktualnej (np. wciąż "CUT" po przejściu na masę).
        runCatching { closeStaleDietPhaseIfGoalChanged(normalized.weightGoalType) }
    }

    private suspend fun closeStaleDietPhaseIfGoalChanged(goal: WeightGoalType) {
        val phaseDao = dietPhaseDao ?: return
        if (goal == WeightGoalType.NONE) return  // cel nieokreślony — nie ruszamy faz
        val active = phaseDao.getCurrent() ?: return
        val phaseDirection = when (active.type) {
            DietPhaseType.CUT -> WeightGoalType.CUT
            DietPhaseType.BULK -> WeightGoalType.BULK
            DietPhaseType.MAINTENANCE -> WeightGoalType.MAINTAIN
            // REFEED_DAY / DIET_BREAK — krótkie, neutralne kierunkowo: wygasają same
            else -> return
        }
        if (phaseDirection != goal) {
            phaseDao.closePhase(active.id)
        }
    }
}
