package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealConsumption
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType

@Dao
interface MealConsumptionDao {

    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs AND mealType = :mealType LIMIT 1")
    suspend fun get(dateMs: Long, mealType: MealType): MealConsumption?

    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs")
    fun observeForDate(dateMs: Long): Flow<List<MealConsumption>>

    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs")
    suspend fun getForDate(dateMs: Long): List<MealConsumption>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: MealConsumption): Long

    @Update
    suspend fun update(c: MealConsumption)

    @Query("DELETE FROM meal_consumptions WHERE dateMs = :dateMs AND mealType = :mealType")
    suspend fun delete(dateMs: Long, mealType: MealType)

    @Query("DELETE FROM meal_consumptions")
    suspend fun deleteAll()
}
