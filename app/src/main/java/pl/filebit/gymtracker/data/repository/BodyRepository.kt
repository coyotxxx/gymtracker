package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyRepository @Inject constructor(
    private val dao: BodyMeasurementDao,
    // v2.28.0: nullable-default — Hilt wstrzykuje realny logger, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll()
    suspend fun getAllAsc(): List<BodyMeasurement> = dao.getAllAsc()
    suspend fun getLatest(): BodyMeasurement? = dao.getLatest()
    suspend fun upsert(m: BodyMeasurement): Long {
        val id = dao.upsert(m)
        diag?.info(DiagnosticCategory.USER_ACTION, "BodyRepository", "measurement_saved",
            "Zapisano pomiar ciała" + (m.weightKg?.let { " — waga $it kg" } ?: ""),
            dataJson = """{"id":$id,"weightKg":${m.weightKg ?: "null"}}""", success = true)
        return id
    }
    suspend fun delete(m: BodyMeasurement) {
        dao.delete(m)
        diag?.info(DiagnosticCategory.USER_ACTION, "BodyRepository", "measurement_deleted",
            "Usunięto pomiar ciała #${m.id}", dataJson = """{"id":${m.id}}""")
    }
    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
        diag?.info(DiagnosticCategory.USER_ACTION, "BodyRepository", "measurement_deleted",
            "Usunięto pomiar ciała #$id", dataJson = """{"id":$id}""")
    }
}
