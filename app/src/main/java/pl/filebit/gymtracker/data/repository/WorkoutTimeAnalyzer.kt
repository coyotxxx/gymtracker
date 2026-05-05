package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.WorkoutContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

/**
 * Klasyfikuje posiłki w slotach jako PRE/POST/NORMAL na podstawie godziny treningu.
 *
 * Reguły:
 *  - posiłek najbliższy `trainingHour - 2h ± 30min` → PRE_WORKOUT
 *  - posiłek najbliższy `trainingHour + 1h ± 30min` → POST_WORKOUT
 *  - pozostałe → NORMAL
 */
@Singleton
class WorkoutTimeAnalyzer @Inject constructor() {

    data class SlotContext(
        val mealType: MealType,
        val mealHourDecimal: Double,
        val workoutContext: WorkoutContext
    )

    /**
     * Klasyfikuje N slotów (mealsForSlots z konfiguracji DietConfig) per WorkoutContext.
     *
     * @param mealHoursDecimal lista godzin posiłków (np. 8.0, 12.0, 16.0, 20.0)
     * @param mealTypesForSlots typy posiłków per slot (BREAKFAST, LUNCH, ...)
     * @param trainingHour godzina treningu (0-23) lub null jeśli nie wiadomo
     * @return mapa slot index → kontekst
     */
    fun classifySlots(
        mealHoursDecimal: List<Double>,
        mealTypesForSlots: List<MealType>,
        trainingHour: Int?
    ): List<SlotContext> {
        val n = mealHoursDecimal.size
        if (trainingHour == null || n == 0) {
            return mealHoursDecimal.zip(mealTypesForSlots).map { (h, t) ->
                SlotContext(t, h, WorkoutContext.NORMAL)
            }
        }

        val preTarget = trainingHour - 2.0   // pre 2h przed
        val postTarget = trainingHour + 1.0  // post 1h po

        // Najbliższy slot do preTarget w oknie ±1.5h
        val preIdx = nearestSlotInWindow(mealHoursDecimal, preTarget, window = 1.5)
        // Najbliższy slot do postTarget w oknie ±1.5h
        val postIdx = nearestSlotInWindow(mealHoursDecimal, postTarget, window = 1.5)

        return mealHoursDecimal.mapIndexed { idx, hour ->
            val ctx = when (idx) {
                preIdx -> WorkoutContext.PRE_WORKOUT
                postIdx -> WorkoutContext.POST_WORKOUT
                else -> WorkoutContext.NORMAL
            }
            SlotContext(mealTypesForSlots[idx], hour, ctx)
        }
    }

    private fun nearestSlotInWindow(
        slots: List<Double>,
        target: Double,
        window: Double
    ): Int? {
        var bestIdx: Int? = null
        var bestDiff = Double.MAX_VALUE
        slots.forEachIndexed { idx, h ->
            val diff = kotlin.math.abs(h - target)
            if (diff <= window && diff < bestDiff) {
                bestIdx = idx
                bestDiff = diff
            }
        }
        return bestIdx
    }

    /**
     * Wykrywa modalną godzinę treningu z listy timestampów Workout.startedAt.
     * Zwraca null jeśli brak danych lub mniej niż 5 treningów.
     */
    fun detectUsualTrainingHour(workoutStartTimestamps: List<Long>): Int? {
        if (workoutStartTimestamps.size < 5) return null
        val cal = Calendar.getInstance()
        val hourCounts = IntArray(24)
        for (ts in workoutStartTimestamps) {
            cal.timeInMillis = ts
            val h = cal.get(Calendar.HOUR_OF_DAY)
            hourCounts[h]++
        }
        val maxIdx = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: return null
        // Pokrycie >= 30% jest sensowne (rozproszone treningi → null)
        return if (hourCounts[maxIdx] * 100 / workoutStartTimestamps.size >= 30) maxIdx else null
    }
}
