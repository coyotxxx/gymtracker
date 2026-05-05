package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.FastingWindowDao
import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.db.dao.MealEntryDao
import pl.filebit.gymtracker.data.db.dao.RecipeDao
import pl.filebit.gymtracker.data.entity.FastingWindow
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.Recipe
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

class DietRepository @Inject constructor(
    private val foodDao: FoodProductDao,
    private val mealDao: MealEntryDao,
    private val fastingDao: FastingWindowDao,
    private val recipeDao: RecipeDao
) {
    // ===== Food products =====
    fun observeAllProducts(): Flow<List<FoodProduct>> = foodDao.observeAll()
    fun observeProductsByCategory(c: FoodCategory): Flow<List<FoodProduct>> = foodDao.observeByCategory(c)
    fun searchProducts(q: String): Flow<List<FoodProduct>> = foodDao.search(q)
    suspend fun getProduct(id: Long): FoodProduct? = foodDao.getById(id)
    suspend fun upsertProduct(p: FoodProduct): Long = foodDao.upsert(p)
    suspend fun deleteProduct(id: Long) = foodDao.delete(id)
    suspend fun setProductFavorite(id: Long, fav: Boolean) = foodDao.setFavorite(id, fav)
    suspend fun getFavoriteProducts(): List<FoodProduct> = foodDao.getFavorites()

    // ===== Meal entries =====
    fun observeMealsForDate(dateMs: Long): Flow<List<MealEntry>> {
        val (start, end) = dayBounds(dateMs)
        return mealDao.observeForDateRange(start, end)
    }

    suspend fun getMealsForDate(dateMs: Long): List<MealEntry> {
        val (start, end) = dayBounds(dateMs)
        return mealDao.getForDateRange(start, end)
    }

    suspend fun addMeal(entry: MealEntry): Long = mealDao.upsert(entry)
    suspend fun updateMeal(entry: MealEntry) { mealDao.upsert(entry) }
    suspend fun deleteMeal(id: Long) = mealDao.delete(id)

    // ===== Fasting window =====
    fun observeActiveFasting(): Flow<FastingWindow?> = fastingDao.observeActive()
    suspend fun getActiveFasting(): FastingWindow? = fastingDao.getActive()
    suspend fun openFastingWindow(durationHours: Int = 8): Long = fastingDao.upsert(
        FastingWindow(eatingStartMs = System.currentTimeMillis(), plannedEatingHours = durationHours)
    )
    suspend fun closeFastingWindow(window: FastingWindow) {
        fastingDao.upsert(window.copy(eatingEndMs = System.currentTimeMillis()))
    }
    suspend fun getRecentFasting(limit: Int = 30) = fastingDao.getRecent(limit)

    // ===== Recipes =====
    fun observeRecipes(): Flow<List<Recipe>> = recipeDao.observeAll()
    fun observeRecipesByMealType(m: MealType): Flow<List<Recipe>> = recipeDao.observeByMealType(m)
    fun observeFavoriteRecipes(): Flow<List<Recipe>> = recipeDao.observeFavorites()
    suspend fun getRecipe(id: Long): Recipe? = recipeDao.getById(id)
    suspend fun upsertRecipe(r: Recipe): Long = recipeDao.upsert(r)
    suspend fun deleteRecipe(id: Long) = recipeDao.delete(id)

    /** Granice dnia w epoch ms (lokalna strefa czasowa). */
    private fun dayBounds(dateMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = dateMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }
}

/**
 * Wyliczone makro/kcal z MealEntry × FoodProduct.
 */
data class MealEntryWithMacros(
    val entry: MealEntry,
    val product: FoodProduct,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

fun MealEntry.macrosFor(product: FoodProduct): MealEntryWithMacros {
    val factor = grams / 100.0
    return MealEntryWithMacros(
        entry = this,
        product = product,
        kcal = product.kcalPer100g * factor,
        protein = product.proteinPer100g * factor,
        carbs = product.carbsPer100g * factor,
        fat = product.fatPer100g * factor
    )
}
