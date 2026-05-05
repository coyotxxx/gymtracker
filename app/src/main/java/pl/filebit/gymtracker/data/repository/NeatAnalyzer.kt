package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot aktywności pozatreningowej (NEAT) dla silnika korekt.
 *
 * Filozofia: większość stagnacji w cut to spadek kroków (NEAT), nie wolniejszy
 * metabolizm. Jeśli avg14d kroków spadł >30% vs avg30d → silnik NIE TNIE kcal,
 * tylko sugeruje "wróć do kroków najpierw".
 */
data class NeatSnapshot(
    val avg14dSteps: Int,
    val avg30dSteps: Int,
    val baselineStepsPerDay: Int,        // z UserDietProfile
    val sampleDays14: Int,
    val sampleDays30: Int,
    /** True gdy avg14d <= 0.7 × avg30d (spadek o 30%+) i jest wystarczająco danych. */
    val significantStepsDrop: Boolean,
    /** True gdy avg14d <= 0.7 × baseline (z onboardingu) i jest wystarczająco danych. */
    val belowBaseline: Boolean
) {
    companion object {
        val EMPTY = NeatSnapshot(
            avg14dSteps = 0, avg30dSteps = 0, baselineStepsPerDay = 0,
            sampleDays14 = 0, sampleDays30 = 0,
            significantStepsDrop = false, belowBaseline = false
        )
    }

    val hasEnoughData: Boolean get() = sampleDays14 >= 5 && sampleDays30 >= 14
}

@Singleton
class NeatAnalyzer @Inject constructor() {

    /**
     * @param recent30dSteps lista kroków per dzień z ostatnich 30 dni
     * @param baselineStepsPerDay wartość z UserDietProfile.avgStepsPerDay
     */
    fun analyze(recent30dSteps: List<Int>, baselineStepsPerDay: Int): NeatSnapshot {
        if (recent30dSteps.isEmpty()) return NeatSnapshot.EMPTY.copy(baselineStepsPerDay = baselineStepsPerDay)

        // Sortujemy przez wywołującego — tu zakładamy że recent30dSteps to ostatnie 30 dni
        val last14 = recent30dSteps.take(14)
        val avg14 = if (last14.isNotEmpty()) last14.average().toInt() else 0
        val avg30 = recent30dSteps.average().toInt()

        val significantDrop = recent30dSteps.size >= 14 &&
            avg30 > 0 && avg14 <= avg30 * 0.7

        val belowBaseline = baselineStepsPerDay > 0 &&
            recent30dSteps.size >= 14 && avg14 <= baselineStepsPerDay * 0.7

        return NeatSnapshot(
            avg14dSteps = avg14,
            avg30dSteps = avg30,
            baselineStepsPerDay = baselineStepsPerDay,
            sampleDays14 = last14.size,
            sampleDays30 = recent30dSteps.size,
            significantStepsDrop = significantDrop,
            belowBaseline = belowBaseline
        )
    }
}
