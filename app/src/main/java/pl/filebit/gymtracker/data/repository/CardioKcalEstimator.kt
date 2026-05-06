package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Szacuje średni dzienny dodatek kcal z cardio (bieżnia/rower/HIIT) na bazie
 * TrainingDaySummary z ostatnich 7 dni.
 *
 * Współczynnik: ~10 kcal/min cardio dla osoby ~75 kg w średniej intensywności.
 * Zakres typowy: 6-15 kcal/min (zależy od HR, intensywności, masy).
 *
 * Dlaczego: aktywny user który robi 3× w tygodniu po 30 min biegu ma +900 kcal/tydz
 * = 130 kcal/dzień extra. Bez tego TDEE jest zaniżone.
 */
@Singleton
class CardioKcalEstimator @Inject constructor(
    private val bridge: TrainingDietBridge
) {
    /** Średni dzienny kcal cardio z ostatnich 7 dni. */
    suspend fun avgDailyKcalLast7Days(): Int {
        val summaries = bridge.getRecent(days = 7)
        if (summaries.isEmpty()) return 0
        val totalCardioMin = summaries.sumOf { it.cardioMinutes }
        if (totalCardioMin <= 0) return 0
        val totalKcal = totalCardioMin * KCAL_PER_MIN_CARDIO
        return totalKcal / 7   // rozłożone na 7 dni jako średni dzienny dodatek
    }

    companion object {
        const val KCAL_PER_MIN_CARDIO = 10
    }
}
