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
    suspend fun upsert(exercise: Exercise): Long = dao.upsert(exercise)
}
