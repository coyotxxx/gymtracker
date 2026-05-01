package pl.filebit.gymtracker.util

import kotlin.math.roundToInt

data class ProgressionResult(
    val weightKg: Double,
    val reps: Int,
    val rationale: String
)

/**
 * Pure function: oblicza sugestię na następną sesję na podstawie
 * referencyjnej serii (najcięższa z poprzedniej sesji) + średniego RPE.
 *
 * Heurystyka (zgodna z literaturą — RP, MASS Research Review):
 *  - RPE ≤ 7 (lub brak RPE) → +delta kg, te same powtórzenia
 *  - RPE 7..9               → utrzymaj wagę, +1 powt.
 *  - RPE > 9                → utrzymaj (nie progresuj)
 *
 * Waga zaokrąglana do 0.25 kg (najmniejszy krok obciążnika).
 *
 * @param refWeight waga z referencyjnej serii poprzedniej sesji (kg)
 * @param refReps   powtórzenia z referencyjnej serii
 * @param avgRpe    średnie RPE z poprzedniej sesji (null = brak danych)
 * @param delta     przyrost wagi dla celu treningowego
 *                  (STRENGTH=2.5, HYPERTROPHY/MIX=1.25, FITNESS/CARDIO=1.0)
 */
fun computeProgression(
    refWeight: Double,
    refReps: Int,
    avgRpe: Double?,
    delta: Double
): ProgressionResult {
    val (newWeight, newReps, rationale) = when {
        avgRpe == null -> Triple(
            refWeight + delta, refReps,
            "Brak RPE — sugestia +$delta kg"
        )
        avgRpe <= 7.0 -> Triple(
            refWeight + delta, refReps,
            "Ostatni RPE ${"%.1f".format(avgRpe)} (lekko) → +$delta kg"
        )
        avgRpe <= 9.0 -> Triple(
            refWeight, refReps + 1,
            "Ostatni RPE ${"%.1f".format(avgRpe)} → utrzymaj wagę, +1 powt."
        )
        else -> Triple(
            refWeight, refReps,
            "Ostatni RPE ${"%.1f".format(avgRpe)} (max) → utrzymaj"
        )
    }
    val rounded = (newWeight * 4).roundToInt() / 4.0
    return ProgressionResult(rounded, newReps, rationale)
}
