package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealConsumption
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus

@Dao
interface MealConsumptionDao {

    // v2.73.0 (POSIŁKI N): status kluczowany po numerze slotu (dateMs, mealSlot), nie mealType.
    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs AND mealSlot = :mealSlot LIMIT 1")
    suspend fun get(dateMs: Long, mealSlot: Int): MealConsumption?

    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs")
    fun observeForDate(dateMs: Long): Flow<List<MealConsumption>>

    @Query("SELECT * FROM meal_consumptions WHERE dateMs = :dateMs")
    suspend fun getForDate(dateMs: Long): List<MealConsumption>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: MealConsumption): Long

    @Update
    suspend fun update(c: MealConsumption)

    @Query("DELETE FROM meal_consumptions WHERE dateMs = :dateMs AND mealSlot = :mealSlot")
    suspend fun delete(dateMs: Long, mealSlot: Int)

    @Query("DELETE FROM meal_consumptions")
    suspend fun deleteAll()
}
