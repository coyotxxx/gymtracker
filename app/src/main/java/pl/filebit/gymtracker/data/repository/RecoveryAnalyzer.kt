package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.RecoveryLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot regeneracji 7-dniowy dla silnika korekt.
 *
 * Użycie: CalorieAdjustmentEngine sprawdza te flagi PRZED decyzją o cięciu kcal.
 *  - badSleep + highStress → HOLD (nie tnij)
 *  - highHunger → REFEED zamiast DECREASE
 *  - highSoreness + lowEnergy → DELOAD (sygnał z regeneracji)
 *  - highDifficulty → SIMPLIFY_PLAN (zamiast cięcia kcal)
 */
data class RecoverySnapshot(
    val sampleDays: Int,
    val avgSleepHours: Double?,
    val avgSleepQuality: Double?,
    val avgStress: Double?,
    val avgHunger: Double?,
    val avgEnergy: Double?,
    val avgSoreness: Double?,
    val avgDifficulty: Double?,
    /** True gdy avgSleep <6h LUB avgSleepQuality <2.5. */
    val badSleep: Boolean,
    /** True gdy avgStress >=4. */
    val highStress: Boolean,
    /** True gdy avgHunger >=4. */
    val highHunger: Boolean,
    /** True gdy avgEnergy <=2. */
    val lowEnergy: Boolean,
    /** True gdy avgSoreness >=4. */
    val highSoreness: Boolean,
    /** True gdy avgDifficulty >=4. */
    val highDifficulty: Boolean
) {
    companion object {
        val EMPTY = RecoverySnapshot(
            sampleDays = 0,
            avgSleepHours = null, avgSleepQuality = null,
            avgStress = null, avgHunger = null, avgEnergy = null,
            avgSoreness = null, avgDifficulty = null,
            badSleep = false, highStress = false, highHunger = false,
            lowEnergy = false, highSoreness = false, highDifficulty = false
        )
    }

    val hasEnoughData: Boolean get() = sampleDays >= 3
}

/**
 * Analizuje RecoveryLog z ostatnich 7 dni, zwraca sygnały dla silnika korekt.
 */
@Singleton
class RecoveryAnalyzer @Inject constructor() {

    fun analyze(logs7d: List<RecoveryLog>): RecoverySnapshot {
        if (logs7d.isEmpty()) return RecoverySnapshot.EMPTY

        val sleepHours = logs7d.mapNotNull { it.sleepHours }
        val sleepQuality = logs7d.mapNotNull { it.sleepQuality }
        val stress = logs7d.mapNotNull { it.stressLevel }
        val hunger = logs7d.mapNotNull { it.hungerLevel }
        val energy = logs7d.mapNotNull { it.energyLevel }
        val soreness = logs7d.mapNotNull { it.sorenessLevel }
        val difficulty = logs7d.mapNotNull { it.difficultyAdherence }

        val avgSleep = sleepHours.takeIf { it.isNotEmpty() }?.average()
        val avgSleepQ = sleepQuality.takeIf { it.isNotEmpty() }?.average()
        val avgStress = stress.takeIf { it.isNotEmpty() }?.average()
        val avgHunger = hunger.takeIf { it.isNotEmpty() }?.average()
        val avgEnergy = energy.takeIf { it.isNotEmpty() }?.average()
        val avgSoreness = soreness.takeIf { it.isNotEmpty() }?.average()
        val avgDifficulty = difficulty.takeIf { it.isNotEmpty() }?.average()

        return RecoverySnapshot(
            sampleDays = logs7d.size,
            avgSleepHours = avgSleep,
            avgSleepQuality = avgSleepQ,
            avgStress = avgStress,
            avgHunger = avgHunger,
            avgEnergy = avgEnergy,
            avgSoreness = avgSoreness,
            avgDifficulty = avgDifficulty,
            badSleep = (avgSleep != null && avgSleep < 6.0) || (avgSleepQ != null && avgSleepQ < 2.5),
            highStress = avgStress != null && avgStress >= 4.0,
            highHunger = avgHunger != null && avgHunger >= 4.0,
            lowEnergy = avgEnergy != null && avgEnergy <= 2.0,
            highSoreness = avgSoreness != null && avgSoreness >= 4.0,
            highDifficulty = avgDifficulty != null && avgDifficulty >= 4.0
        )
    }
}
