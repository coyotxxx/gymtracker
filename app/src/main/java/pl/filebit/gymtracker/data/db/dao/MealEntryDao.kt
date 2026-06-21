package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealEntry

@Dao
interface MealEntryDao {

    @Query("SELECT * FROM meal_entries WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY mealType ASC, createdAt ASC")
    fun observeForDateRange(startMs: Long, endMs: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY mealType ASC, createdAt ASC")
    suspend fun getForDateRange(startMs: Long, endMs: Long): List<MealEntry>

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getById(id: Long): MealEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MealEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<MealEntry>)

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_entries WHERE dateMs >= :startMs AND dateMs < :endMs")
    suspend fun deleteForDateRange(startMs: Long, endMs: Long)

    /**
     * v2.72.0: atomowy zapis całego planu dnia — czyści dzień i wstawia nowe wpisy
     * w JEDNEJ transakcji. Używane przez AI tool save_diet_plan (zapis planu, np. ze zdjęcia),
     * żeby uniknąć lawiny pojedynczych add_meal/delete_meal i połowicznego zapisu.
     */
    @Transaction
    suspend fun replaceForDateRange(startMs: Long, endMs: Long, entries: List<MealEntry>) {
        deleteForDateRange(startMs, endMs)
        upsertAll(entries)
    }
}
