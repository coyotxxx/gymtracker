package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.ProgressPhoto

@Dao
interface ProgressPhotoDao {

    @Query("SELECT * FROM progress_photos ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ProgressPhoto>>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    suspend fun getById(id: Long): ProgressPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProgressPhoto): Long

    @Delete
    suspend fun delete(p: ProgressPhoto)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
