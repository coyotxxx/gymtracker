package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao
) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    fun observeByMuscle(muscle: MuscleGroup): Flow<List<Exercise>> = dao.observeByMuscle(muscle)
    fun search(query: String): Flow<List<Exercise>> = dao.search(query)
    suspend fun get(id: Long): Exercise? = dao.getById(id)
    suspend fun findByName(name: String): Exercise? = dao.findByName(name)
    suspend fun findBySlug(slug: String): Exercise? = dao.findBySlug(slug)
    suspend fun upsert(exercise: Exercise): Long = dao.upsert(exercise)

    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun getFavorites(): List<Exercise> = dao.getFavorites()
    fun observeFavorites(): Flow<List<Exercise>> = dao.observeFavorites()

    suspend fun setAvoided(id: Long, avoided: Boolean) = dao.setAvoided(id, avoided)
    suspend fun getAvoided(): List<Exercise> = dao.getAvoided()
}
