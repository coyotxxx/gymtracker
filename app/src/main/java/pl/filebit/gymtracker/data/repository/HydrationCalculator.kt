package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wynik kalkulacji celu nawodnienia + adherence.
 */
data class HydrationGoal(
    val baseMl: Int,                    // 30ml × kg
    val workoutBonusMl: Int,            // 500ml × treningi
    val proteinBonusMl: Int,            // +300ml gdy białko >2g/kg
    val creatineBonusMl: Int,           // +500ml gdy kreatyna
    val temperatureBonusMl: Int,        // +500ml gdy >25°C
    val totalMl: Int,
    val explanation: String
)

/**
 * Liczy cel nawodnienia per dzień.
 * Pure function — testowalne.
 *
 * Bazowa formuła: 30ml × kg masy ciała (norma EFSA).
 * Modyfikatory:
 *  - workout bonus rośnie z czasem treningu (15-44min=300ml, 45-74=500ml, 75-104=750ml, 105+=1000ml)
 *    Fallback: jeśli `trainingDurationMin == null` ale `hadTrainingToday=true`, to 500ml (backward compat).
 *  - +300ml gdy białko spożyte >2g/kg (potrzeba na metabolizm białka)
 *  - +500ml gdy kreatyna w suplementacji (zatrzymuje wodę w mięśniach)
 *  - +500ml gdy temperatura >25°C
 *
 * v1.17.0: dodano `trainingDurationMin` — phase-aware via TrainingDaySummary.durationMinutes.
 */
@Singleton
class HydrationCalculator @Inject constructor() {

    fun computeTarget(
        weightKg: Double,
        hadTrainingToday: Boolean,
        proteinGramsToday: Double,
        usesCreatine: Boolean,
        temperatureC: Double? = null,
        trainingDurationMin: Int? = null
    ): HydrationGoal {
        val base = (weightKg * 30).toInt()
        val (workout, workoutLabel) = computeWorkoutBonus(hadTrainingToday, trainingDurationMin)
        val proteinPerKg = if (weightKg > 0) proteinGramsToday / weightKg else 0.0
        val proteinBonus = if (proteinPerKg > 2.0) 300 else 0
        val creatineBonus = if (usesCreatine) 500 else 0
        val tempBonus = if (temperatureC != null && temperatureC > 25.0) 500 else 0
        val total = base + workout + proteinBonus + creatineBonus + tempBonus

        val parts = buildList {
            add("Baza: ${base} ml (30 ml × ${weightKg.toInt()} kg)")
            if (workout > 0) add(workoutLabel)
            if (proteinBonus > 0) add("+ wysokie białko (>2g/kg): 300 ml")
            if (creatineBonus > 0) add("+ kreatyna: 500 ml")
            if (tempBonus > 0) add("+ wysoka temp: 500 ml")
        }

        return HydrationGoal(
            baseMl = base,
            workoutBonusMl = workout,
            proteinBonusMl = proteinBonus,
            creatineBonusMl = creatineBonus,
            temperatureBonusMl = tempBonus,
            totalMl = total,
            explanation = parts.joinToString(" · ")
        )
    }

    /** Zwraca % spełnienia celu (0-100+). */
    fun adherencePct(consumedMl: Int, goalMl: Int): Int {
        if (goalMl <= 0) return 0
        return (consumedMl.toDouble() / goalMl * 100).toInt()
    }

    /**
     * Workout bonus progresywny w funkcji czasu (literatura ACSM: ~150-250ml na 15min).
     * Bez treningu → 0. Krótki (<15 min) → 0 (rozgrzewka tylko). Backward compat (durationMin=null) → 500ml.
     */
    private fun computeWorkoutBonus(hadTrainingToday: Boolean, durationMin: Int?): Pair<Int, String> {
        if (!hadTrainingToday) return 0 to ""
        if (durationMin == null) return 500 to "+ trening: 500 ml"
        return when {
            durationMin < 15 -> 0 to ""
            durationMin < 45 -> 300 to "+ trening (${durationMin} min): 300 ml"
            durationMin < 75 -> 500 to "+ trening (${durationMin} min): 500 ml"
            durationMin < 105 -> 750 to "+ długi trening (${durationMin} min): 750 ml"
            else -> 1000 to "+ bardzo długi trening (${durationMin} min): 1000 ml"
        }
    }
}
