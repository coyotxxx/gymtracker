package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.BodyMeasurement

@Dao
interface BodyMeasurementDao {

    @Query("SELECT * FROM body_measurements ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurements ORDER BY date ASC")
    suspend fun getAllAsc(): List<BodyMeasurement>

    @Query("SELECT * FROM body_measurements WHERE id = :id")
    suspend fun getById(id: Long): BodyMeasurement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: BodyMeasurement): Long

    @Delete
    suspend fun delete(m: BodyMeasurement)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM body_measurements ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): BodyMeasurement?
}
