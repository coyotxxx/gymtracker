package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.filebit.gymtracker.data.db.dao.UserProfileDao
import pl.filebit.gymtracker.data.entity.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val dao: UserProfileDao
) {
    fun observe(): Flow<UserProfile> = dao.observe().map { it ?: UserProfile() }
    suspend fun get(): UserProfile = dao.get() ?: UserProfile().also { dao.upsert(it) }
    suspend fun save(profile: UserProfile) = dao.upsert(profile.copy(id = 1))
}
