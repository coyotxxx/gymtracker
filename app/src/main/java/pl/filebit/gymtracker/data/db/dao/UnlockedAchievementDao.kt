package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.UnlockedAchievement

@Dao
interface UnlockedAchievementDao {

    @Query("SELECT * FROM unlocked_achievements ORDER BY unlockedAt DESC")
    fun observeAll(): Flow<List<UnlockedAchievement>>

    @Query("SELECT * FROM unlocked_achievements")
    suspend fun getAll(): List<UnlockedAchievement>

    @Query("SELECT * FROM unlocked_achievements WHERE code = :code")
    suspend fun getByCode(code: String): UnlockedAchievement?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(item: UnlockedAchievement): Long
}
