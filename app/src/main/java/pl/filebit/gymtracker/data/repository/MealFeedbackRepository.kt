package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.MealFeedbackDao
import pl.filebit.gymtracker.data.entity.MealFeedback
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealFeedbackRepository @Inject constructor(
    private val dao: MealFeedbackDao
) {
    fun observeAll(): Flow<List<MealFeedback>> = dao.observeAll()

    suspend fun getByName(name: String): MealFeedback? =
        dao.getByKey(MealFeedback.normalizeKey(name))

    suspend fun getTopFavorites(limit: Int = 10): List<MealFeedback> =
        dao.getTopFavorites(minRating = 4, limit = limit)

    suspend fun getTopDisliked(limit: Int = 5): List<MealFeedback> =
        dao.getTopDisliked(maxRating = 2, limit = limit)

    /**
     * Zapisuje ocenę. Jeśli istnieje wcześniejsza ocena tego dania:
     *  - aktualizuje rating + tags + notes
     *  - inkrementuje timesEaten
     *  - aktualizuje updatedAt
     */
    suspend fun rate(displayName: String, rating: Int, tags: String = "", notes: String = "") {
        require(rating in 1..5) { "rating poza zakresem 1..5: $rating" }
        val key = MealFeedback.normalizeKey(displayName)
        val now = System.currentTimeMillis()
        val existing = dao.getByKey(key)
        if (existing != null) {
            dao.update(
                existing.copy(
                    displayName = displayName.trim().ifBlank { existing.displayName },
                    rating = rating,
                    tags = tags.ifBlank { existing.tags },
                    notes = notes.ifBlank { existing.notes },
                    timesEaten = existing.timesEaten + 1,
                    updatedAt = now
                )
            )
        } else {
            dao.insert(
                MealFeedback(
                    dishKey = key,
                    displayName = displayName.trim().ifBlank { "Posiłek" },
                    rating = rating,
                    tags = tags,
                    notes = notes,
                    timesEaten = 1,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
