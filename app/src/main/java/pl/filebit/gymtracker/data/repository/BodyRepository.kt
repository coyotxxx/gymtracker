package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyRepository @Inject constructor(
    private val dao: BodyMeasurementDao
) {
    fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll()
    suspend fun getAllAsc(): List<BodyMeasurement> = dao.getAllAsc()
    suspend fun getLatest(): BodyMeasurement? = dao.getLatest()
    suspend fun upsert(m: BodyMeasurement): Long = dao.upsert(m)
    suspend fun delete(m: BodyMeasurement) = dao.delete(m)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
