package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.BodyMeasurement
import kotlin.math.abs
import kotlin.math.ceil

private const val DAY_MS = 86_400_000L

data class TrendInfo(
    val first: Double,
    val last: Double,
    val delta: Double,             // last - first
    val deltaPerWeek: Double,      // delta normalizowane na tydzień
    val daysSpan: Int              // liczba dni między pierwszym a ostatnim pomiarem
)

/**
 * Filtruje pomiary do podanego zakresu dni (null = wszystkie).
 * Zwraca rosnąco po dacie (najstarszy pierwszy) — wygodne do wykresu.
 */
fun filterMeasurementsByRange(
    all: List<BodyMeasurement>,
    rangeDays: Int?,
    now: Long = System.currentTimeMillis()
): List<BodyMeasurement> {
    val sorted = all.sortedBy { it.date }
    if (rangeDays == null) return sorted
    val cutoff = now - rangeDays.toLong() * DAY_MS
    return sorted.filter { it.date >= cutoff }
}

/**
 * Oblicza trend dla wybranej metryki w podanym zakresie dni.
 * @param extractor wyciąga wartość z BodyMeasurement (np. { it.weightKg })
 * @return TrendInfo lub null jeśli mniej niż 2 punkty z wartością
 */
fun computeTrend(
    measurements: List<BodyMeasurement>,
    extractor: (BodyMeasurement) -> Double?,
    rangeDays: Int = 30,
    now: Long = System.currentTimeMillis()
): TrendInfo? {
    val inRange = filterMeasurementsByRange(measurements, rangeDays, now)
    val withValue = inRange.mapNotNull { m -> extractor(m)?.let { m to it } }
    if (withValue.size < 2) return null

    val (firstM, firstV) = withValue.first()
    val (lastM, lastV) = withValue.last()
    val daysSpan = ((lastM.date - firstM.date) / DAY_MS).toInt().coerceAtLeast(1)
    val delta = lastV - firstV
    val deltaPerWeek = delta / (daysSpan / 7.0).coerceAtLeast(0.001)

    return TrendInfo(
        first = firstV,
        last = lastV,
        delta = delta,
        deltaPerWeek = deltaPerWeek,
        daysSpan = daysSpan
    )
}

/**
 * Postęp do celu (0..100%). Bierze pod uwagę kierunek (CUT/BULK).
 * @param startWeight startowa waga (na początku celu)
 * @param current aktualna waga
 * @param target waga docelowa
 *
 * Przykład CUT (start=82, target=75, current=78): postęp = (82-78)/(82-75) = 57%
 * Przykład BULK (start=70, target=80, current=74): postęp = (74-70)/(80-70) = 40%
 */
fun progressToGoal(startWeight: Double, current: Double, target: Double): Double {
    val total = target - startWeight
    if (abs(total) < 0.01) return 100.0  // start == target
    val done = current - startWeight
    val pct = (done / total) * 100
    return pct.coerceIn(0.0, 100.0)
}

/**
 * Szacuje liczbę tygodni potrzebną do osiągnięcia celu na podstawie aktualnego trendu.
 * @return liczba tygodni (zaokrąglona w górę) lub null jeśli trend nie idzie w kierunku celu
 */
fun estimatedWeeksToGoal(
    currentWeight: Double,
    targetWeight: Double,
    deltaPerWeek: Double
): Int? {
    val remainingDelta = targetWeight - currentWeight
    if (abs(remainingDelta) < 0.1) return 0  // już w celu

    // Sprawdź czy trend idzie w dobrym kierunku
    if (remainingDelta > 0 && deltaPerWeek <= 0) return null  // chcesz przybrać, ale chudniesz
    if (remainingDelta < 0 && deltaPerWeek >= 0) return null  // chcesz schudnąć, ale przybierasz

    val weeks = abs(remainingDelta / deltaPerWeek)
    if (weeks > 520) return null  // > 10 lat = nieosiągalne praktycznie
    return ceil(weeks).toInt()
}

/**
 * Zaokrągla wagę do najbliższego punktu wyświetlania na osi Y wykresu.
 * Wybiera step: 1, 2, 5 kg w zależności od range.
 */
fun chartYStep(minValue: Double, maxValue: Double): Double {
    val range = maxValue - minValue
    return when {
        range <= 4 -> 1.0
        range <= 10 -> 2.0
        range <= 25 -> 5.0
        else -> 10.0
    }
}
