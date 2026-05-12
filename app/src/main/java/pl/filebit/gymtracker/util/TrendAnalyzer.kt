package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.BodyMeasurement
import kotlin.math.abs

enum class TrendDirection {
    INSUFFICIENT_DATA,
    FALLING,        // waga maleje (np. CUT idzie dobrze)
    FLAT,           // ±0.1 kg/tydz — stagnacja
    RISING          // waga rośnie (BULK / niezamierzony przyrost)
}

data class WeightTrend(
    val sampleCount: Int,
    val avg7Days: Double?,
    val avg14Days: Double?,
    val avg28Days: Double?,
    /** Tempo zmiany w kg/tydz (slope dopasowany liniowo). */
    val slopeKgPerWeek: Double?,
    val direction: TrendDirection,
    val isStagnationLikely: Boolean,    // 14d slope w przedziale [-0.05, +0.05]
    val isFastLoss: Boolean,            // <-1.5 kg/tydz
    val isFastGain: Boolean,            // >+0.5 kg/tydz
    /** v1.24.20: wczesny sygnał plateau — 7 dni bez wyraźnej zmiany (|slope|<0.05).
     *  Wcześniejszy niż isStagnationLikely (który wymaga 14 dni).
     *  Wymaga ≥3 wpisów wagi w ostatnich 7 dniach. */
    val isEarlyPlateau: Boolean = false,
    /** Ile dni waga "stoi" (od ostatniego wpisu z różnicą ≥0.2 kg vs aktualna). */
    val daysWithoutProgress: Int = 0
) {
    val hasEnoughData: Boolean get() = direction != TrendDirection.INSUFFICIENT_DATA

    companion object {
        val NO_DATA = WeightTrend(
            sampleCount = 0, avg7Days = null, avg14Days = null, avg28Days = null,
            slopeKgPerWeek = null, direction = TrendDirection.INSUFFICIENT_DATA,
            isStagnationLikely = false, isFastLoss = false, isFastGain = false,
            isEarlyPlateau = false, daysWithoutProgress = 0
        )
    }
}

/**
 * Analizuje pomiary wagi w czasie. Pure function — testowalne, deterministyczne.
 *
 * Bierze wszystkie BodyMeasurement z weightKg != null, posortowane po dacie ASC.
 * Liczy:
 * - średnia 7/14/28 dni (filtr po dacie)
 * - slope: (avg ostatnich 7 - avg poprzednich 7) × 1 (kg/tydz)
 * - direction: na bazie slope w 14 dni
 * - isStagnation: avg 14d slope ~ 0
 * - isFastLoss/Gain: extreme tempo
 */
object TrendAnalyzer {

    fun analyze(measurements: List<BodyMeasurement>, nowMs: Long = System.currentTimeMillis()): WeightTrend {
        val withWeight = measurements
            .filter { it.weightKg != null && it.weightKg > 0 }
            .sortedBy { it.date }
        if (withWeight.size < 3) return WeightTrend.NO_DATA

        val ms7d = 7L * 24 * 3600 * 1000
        val ms14d = 14L * 24 * 3600 * 1000
        val ms28d = 28L * 24 * 3600 * 1000

        fun avgWindow(windowMs: Long): Double? {
            val window = withWeight.filter { it.date >= nowMs - windowMs }
                .mapNotNull { it.weightKg }
            return window.takeIf { it.isNotEmpty() }?.average()
        }

        val avg7 = avgWindow(ms7d)
        val avg14 = avgWindow(ms14d)
        val avg28 = avgWindow(ms28d)

        // Slope: porównaj ostatnie 7 dni vs poprzednie 7 dni z 14
        val recentWeek = withWeight.filter { it.date >= nowMs - ms7d }
            .mapNotNull { it.weightKg }
        val previousWeek = withWeight.filter { it.date in (nowMs - ms14d)..(nowMs - ms7d) }
            .mapNotNull { it.weightKg }

        val slope = if (recentWeek.isNotEmpty() && previousWeek.isNotEmpty()) {
            recentWeek.average() - previousWeek.average()  // kg/tydz
        } else null

        val direction = when {
            slope == null -> TrendDirection.INSUFFICIENT_DATA
            slope < -0.1 -> TrendDirection.FALLING
            slope > 0.1 -> TrendDirection.RISING
            else -> TrendDirection.FLAT
        }

        val isStagnation = slope != null && abs(slope) < 0.1
        val isFastLoss = slope != null && slope < -1.5
        val isFastGain = slope != null && slope > 0.5

        // v1.24.20: Wczesny plateau — 7 dni bez wyraźnej zmiany.
        // Wymaga ≥3 pomiarów w ostatnich 7 dniach (żeby nie reagować na 1 wpis).
        // Próg: |slope_7d| < 0.05 kg/tydz (czyli waga praktycznie stoi).
        val recent7 = recentWeek
        val isEarlyPlateau = recent7.size >= 3 && run {
            val maxW = recent7.max()
            val minW = recent7.min()
            // różnica max-min < 0.5 kg = waga stoi
            (maxW - minW) < 0.5
        }

        // daysWithoutProgress: liczba dni od ostatniej zmiany ≥0.2 kg
        val lastChange = withWeight.findLast { w ->
            val latest = withWeight.last().weightKg!!
            kotlin.math.abs((w.weightKg ?: 0.0) - latest) >= 0.2
        }
        val daysWithoutProgress = if (lastChange != null) {
            val daysDiff = ((withWeight.last().date - lastChange.date) / (24L * 3600 * 1000)).toInt()
            daysDiff.coerceAtLeast(0)
        } else 0

        return WeightTrend(
            sampleCount = withWeight.size,
            avg7Days = avg7,
            avg14Days = avg14,
            avg28Days = avg28,
            slopeKgPerWeek = slope,
            direction = direction,
            isStagnationLikely = isStagnation,
            isFastLoss = isFastLoss,
            isFastGain = isFastGain,
            isEarlyPlateau = isEarlyPlateau,
            daysWithoutProgress = daysWithoutProgress
        )
    }
}
