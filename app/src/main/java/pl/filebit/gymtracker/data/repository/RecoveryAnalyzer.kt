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
    val highDifficulty: Boolean,
    // === Metryki z zegarka (v1.7.3) ===
    val avgRestingHr: Double? = null,
    val avgSpO2: Double? = null,
    val avgHrv: Double? = null,
    val avgVo2max: Double? = null,
    /** True gdy ostatnie 3d tętno spoczynkowe ≥+10bpm vs baseline 7d → przemęczenie/stres. */
    val elevatedHeartRate: Boolean = false,
    /** True gdy HRV spadek ≥15% vs baseline 7d → CNS przeładowane. */
    val lowHrv: Boolean = false,
    /** True gdy SpO2 <94% — niedobór tlenu, problem oddechowy lub kiepski sen. */
    val lowSpO2: Boolean = false,
    /** True gdy VO2Max rośnie o >0.5 ml/kg/min/miesiąc — kondycja się poprawia. */
    val improvingFitness: Boolean = false
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

        // === Metryki z zegarka (v1.7.3) ===
        val sortedByDate = logs7d.sortedByDescending { it.dateMs }
        val hrAll = sortedByDate.mapNotNull { it.restingHeartRateBpm?.toDouble() }
        val spO2All = sortedByDate.mapNotNull { it.spO2Pct?.toDouble() }
        val hrvAll = sortedByDate.mapNotNull { it.hrvMs }
        val vo2All = sortedByDate.mapNotNull { it.vo2max }

        val avgHr = hrAll.takeIf { it.isNotEmpty() }?.average()
        val avgSpO2 = spO2All.takeIf { it.isNotEmpty() }?.average()
        val avgHrv = hrvAll.takeIf { it.isNotEmpty() }?.average()
        val avgVo2 = vo2All.takeIf { it.isNotEmpty() }?.average()

        // Trend tętna: ostatnie 3d vs cała 7d
        val recentHr = sortedByDate.take(3).mapNotNull { it.restingHeartRateBpm?.toDouble() }
        val baselineHr = sortedByDate.drop(3).mapNotNull { it.restingHeartRateBpm?.toDouble() }
        val elevatedHr = if (recentHr.isNotEmpty() && baselineHr.isNotEmpty())
            recentHr.average() - baselineHr.average() >= 10.0 else false

        // Trend HRV: ostatnie 3d vs cała 7d (>15% spadek = problem)
        val recentHrv = sortedByDate.take(3).mapNotNull { it.hrvMs }
        val baselineHrv = sortedByDate.drop(3).mapNotNull { it.hrvMs }
        val lowHrv = if (recentHrv.isNotEmpty() && baselineHrv.isNotEmpty()) {
            val recent = recentHrv.average()
            val baseline = baselineHrv.average()
            baseline > 0 && (baseline - recent) / baseline > 0.15
        } else false

        // SpO2 niskie — średnia <94% (lub <93% jeśli chcemy ostrzejszy próg)
        val lowSpO2 = avgSpO2 != null && avgSpO2 < 94.0

        // Improving fitness — VO2Max rośnie w czasie (trend dodatni)
        val improvingFitness = if (vo2All.size >= 3) {
            val first = vo2All.takeLast(vo2All.size / 2).average()
            val last = vo2All.take(vo2All.size / 2).average()
            last - first > 0.5
        } else false

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
            highDifficulty = avgDifficulty != null && avgDifficulty >= 4.0,
            avgRestingHr = avgHr,
            avgSpO2 = avgSpO2,
            avgHrv = avgHrv,
            avgVo2max = avgVo2,
            elevatedHeartRate = elevatedHr,
            lowHrv = lowHrv,
            lowSpO2 = lowSpO2,
            improvingFitness = improvingFitness
        )
    }
}
