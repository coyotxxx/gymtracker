package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acute:Chronic Workload Ratio (ACWR) — naukowy wskaźnik z Australian Institute of Sport.
 *
 * **Wzór:** ACWR = 7-day load / 28-day average load
 *
 * **Strefy** (Gabbett 2016):
 * - <0.8 — DETRAINING (utrata formy, możesz dodać objętości)
 * - 0.8-1.3 — OPTIMAL (sweet spot, niskie ryzyko kontuzji)
 * - 1.3-1.5 — OVERREACHING (uwaga, możliwe przemęczenie)
 * - >1.5 — RISKY (wysokie ryzyko kontuzji, redukcja konieczna)
 *
 * Używane przez Garmin Forerunner, TrainingPeaks, WHOOP.
 */
data class TrainingLoad(
    val acuteLoad7d: Double,         // suma reps × weight z 7 dni
    val chronicLoad28d: Double,      // średnia z 28 dni
    val acwr: Double,                // ratio
    val zone: LoadZone,
    val daysOfData: Int,             // ile dni z treningiem mamy w 28d window
    val workoutsCount14d: Int,       // ile ukończonych treningów w 14d (ważne dla decyzji o deload)
    val recommendation: String
) {
    val isReliable: Boolean get() = workoutsCount14d >= 6  // <6 = za mało żeby ACWR cokolwiek znaczył
}

enum class LoadZone {
    DETRAINING,   // <0.8
    OPTIMAL,      // 0.8-1.3
    OVERREACHING, // 1.3-1.5
    RISKY,        // >1.5
    INSUFFICIENT  // za mało treningów żeby liczyć
}

@Singleton
class TrainingLoadAnalyzer @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao
) {
    suspend fun analyze(): TrainingLoad {
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 3600 * 1000

        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= now - 28 * msPerDay }

        // Tonaż per dzień
        val volumeByDay = mutableMapOf<Int, Double>()  // klucz = dni-temu (0..27)
        for (w in finished) {
            val daysAgo = ((now - w.startedAt) / msPerDay).toInt().coerceIn(0, 27)
            val v = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .sumOf { it.reps * it.weightKg }
            volumeByDay[daysAgo] = (volumeByDay[daysAgo] ?: 0.0) + v
        }

        val acute7d = (0..6).sumOf { volumeByDay[it] ?: 0.0 }
        val chronic28d = (0..27).sumOf { volumeByDay[it] ?: 0.0 } / 28.0  // średnia dzienna

        val workoutsIn14d = finished.count { it.startedAt >= now - 14 * msPerDay }
        val daysOfData = volumeByDay.keys.size

        // Walidacja — bez wystarczających danych nie liczymy
        if (workoutsIn14d < 6) {
            return TrainingLoad(
                acuteLoad7d = acute7d,
                chronicLoad28d = chronic28d,
                acwr = 0.0,
                zone = LoadZone.INSUFFICIENT,
                daysOfData = daysOfData,
                workoutsCount14d = workoutsIn14d,
                recommendation = if (workoutsIn14d == 0)
                    "Brak treningów w 14 dni. Zacznij regularnie (3-4×/tydz) zanim algorytm zacznie analizować obciążenie."
                else
                    "Trenujesz $workoutsIn14d×/14d — potrzeba ≥6 do oceny ACWR."
            )
        }

        // ACWR — chronic to **dzienna średnia** × 7 (żeby skala była porównywalna do acute)
        val acwr = if (chronic28d > 0) acute7d / (chronic28d * 7) else 0.0

        val zone = when {
            acwr < 0.8 -> LoadZone.DETRAINING
            acwr <= 1.3 -> LoadZone.OPTIMAL
            acwr <= 1.5 -> LoadZone.OVERREACHING
            else -> LoadZone.RISKY
        }
        val rec = when (zone) {
            LoadZone.DETRAINING -> "Obciążenie 7d niższe niż twoja zwykła średnia. Możesz dodać objętości — np. 1 dodatkowy trening lub +10% setów."
            LoadZone.OPTIMAL -> "Sweet spot — ACWR ${"%.2f".format(acwr)}. Niskie ryzyko kontuzji, optymalna progresja. Trzymaj plan."
            LoadZone.OVERREACHING -> "Tonaż 7d podwyższony — ACWR ${"%.2f".format(acwr)}. Uwaga: możliwe przemęczenie. Rozważ lżejszy tydzień."
            LoadZone.RISKY -> "Niebezpieczna strefa — ACWR ${"%.2f".format(acwr)}. Wysokie ryzyko kontuzji. Konieczna redukcja: -20% objętości."
            LoadZone.INSUFFICIENT -> ""  // nie powinniśmy tu dotrzeć
        }

        return TrainingLoad(
            acuteLoad7d = acute7d,
            chronicLoad28d = chronic28d * 7,  // przeliczona na "tygodniowy ekwiwalent" dla wyświetlania
            acwr = acwr,
            zone = zone,
            daysOfData = daysOfData,
            workoutsCount14d = workoutsIn14d,
            recommendation = rec
        )
    }
}
