package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.UserDietProfile

@Dao
interface UserDietProfileDao {

    @Query("SELECT * FROM user_diet_profile WHERE id = 1")
    fun observe(): Flow<UserDietProfile?>

    @Query("SELECT * FROM user_diet_profile WHERE id = 1")
    suspend fun get(): UserDietProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserDietProfile)
}
