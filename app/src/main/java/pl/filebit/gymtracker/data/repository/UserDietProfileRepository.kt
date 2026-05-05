package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.UserDietProfileDao
import pl.filebit.gymtracker.data.entity.UserDietProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDietProfileRepository @Inject constructor(
    private val dao: UserDietProfileDao
) {
    fun observe(): Flow<UserDietProfile?> = dao.observe()
    suspend fun get(): UserDietProfile? = dao.get()
    suspend fun upsert(profile: UserDietProfile) = dao.upsert(profile)

    /** Sprawdza czy onboarding diety jest ukończony (do redirectu w DietScreen). */
    suspend fun isOnboardingDone(): Boolean = dao.get()?.isOnboardingDone == true
}
