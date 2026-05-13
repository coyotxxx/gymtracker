package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.GoalDao
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * v1.24.26 — Detekcja osiągnięcia celu wagi.
 *
 * Filozofia Macieja: "jeżeli mam cel redukcji z 87 do 75 — co system zrobi
 * jak osiągnę cel? Powinniśmy mieć opcje wyboru co dalej."
 *
 * Detektor sprawdza:
 * - User ma weightGoalType = CUT lub BULK
 * - User ma targetWeightKg ustawione
 * - latestBodyMeasurement osiągnęło target (dla CUT: <=target, dla BULK: >=target)
 * - Stabilność: utrzymanie >= 7 dni (żeby nie reagować na pojedynczy wpis wagi
 *   który może być losowy: woda, czas dnia, ubranie, etc.)
 *
 * Po wykrytym osiągnięciu zwraca [GoalAchievementResult] z opcjami co dalej.
 */
data class GoalAchievementResult(
    val isReached: Boolean,
    val direction: WeightGoalType,  // CUT albo BULK gdy isReached
    val startWeight: Double,
    val targetWeight: Double,
    val currentWeight: Double,
    val daysToAchieve: Int,           // ile dni od goal.startDate do reach
    val stableDays: Int,              // ile dni utrzymuje target (≥7 dla isReached=true)
    val achievedAt: Long              // timestamp osiągnięcia (najnowszy pomiar w target)
)

@Singleton
class GoalAchievementService @Inject constructor(
    private val bodyDao: BodyMeasurementDao,
    private val goalDao: GoalDao
) {
    /**
     * Sprawdza czy user osiągnął target.
     * @return GoalAchievementResult.isReached = true gdy 7+ dni utrzymuje target
     *         (zapobiega false-positive od wahań wagi)
     */
    suspend fun checkAchieved(profile: UserProfile): GoalAchievementResult? {
        val target = profile.targetWeightKg ?: return null
        val goal = profile.weightGoalType
        if (goal != WeightGoalType.CUT && goal != WeightGoalType.BULK) return null

        // Pobierz wszystkie pomiary wagi posortowane chronologicznie
        val measurements = bodyDao.getAllAsc()
            .filter { it.weightKg != null && it.weightKg > 0 }
        if (measurements.isEmpty()) return null

        val latest = measurements.last()
        val latestWeight = latest.weightKg!!
        val startWeight = measurements.first().weightKg ?: latestWeight

        // Czy target osiągnięty?
        val targetReached = when (goal) {
            WeightGoalType.CUT -> latestWeight <= target + 0.2   // ±0.2 kg tolerancja
            WeightGoalType.BULK -> latestWeight >= target - 0.2
            else -> false
        }
        if (!targetReached) return null

        // Sprawdź stabilność — od którego dnia user utrzymuje target?
        val dayMs = 24L * 3600 * 1000
        val nowMs = System.currentTimeMillis()
        val firstReachDate = measurements.firstOrNull { m ->
            val w = m.weightKg ?: return@firstOrNull false
            when (goal) {
                WeightGoalType.CUT -> w <= target + 0.2
                WeightGoalType.BULK -> w >= target - 0.2
                else -> false
            }
        }?.date ?: return null

        val stableDays = ((nowMs - firstReachDate) / dayMs).toInt().coerceAtLeast(0)
        val isReached = stableDays >= 7  // 7 dni stabilności — best practice ISSN

        val daysToAchieve = ((firstReachDate - measurements.first().date) / dayMs).toInt().coerceAtLeast(0)

        return GoalAchievementResult(
            isReached = isReached,
            direction = goal,
            startWeight = startWeight,
            targetWeight = target,
            currentWeight = latestWeight,
            daysToAchieve = daysToAchieve,
            stableDays = stableDays,
            achievedAt = firstReachDate
        )
    }

    /**
     * Sprawdza czy istnieje aktywny Goal entity (LOSE_WEIGHT / GAIN_MASS)
     * i czy jest oznaczony jako osiągnięty.
     */
    suspend fun isGoalDismissedByUser(profile: UserProfile): Boolean {
        // Jeśli user już zareagował (np. wybrał Maintain), Goal.achieved=true
        // i nie pokazujemy karty ponownie.
        val goals = goalDao.getAll()
        val relevantGoalType = when (profile.weightGoalType) {
            WeightGoalType.CUT -> pl.filebit.gymtracker.data.entity.GoalType.LOSE_WEIGHT
            WeightGoalType.BULK -> pl.filebit.gymtracker.data.entity.GoalType.GAIN_MASS
            else -> return true
        }
        return goals.any { it.type == relevantGoalType && it.achieved }
    }
}
