package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.health.HealthConnectManager
import javax.inject.Inject
import javax.inject.Singleton

/** Wskaźnik regeneracji bazujący na śnie i HRV z Health Connect. */
enum class RecoveryStatus {
    EXCELLENT,    // sen ≥7h + HRV w normie/wysoki
    GOOD,         // sen 6-7h LUB HRV stabilne
    MODERATE,     // sen 5-6h LUB HRV niskie
    POOR,         // sen <5h LUB HRV znacząco niskie
    NO_DATA       // brak permission lub brak danych
}

data class HealthInsight(
    val recoveryStatus: RecoveryStatus,
    val avgSleepHours7d: Double?,           // null = brak permission
    val lastNightSleepHours: Double?,
    val avgHrvMs7d: Double?,                // null = brak permission
    val recommendation: String,
    val workoutAdjustment: WorkoutAdjustment
)

/** Sugestia dla AI/algorytmu jak dostosować dzisiejszy trening. */
enum class WorkoutAdjustment {
    AS_PLANNED,       // pełen plan, RPE jak zaplanowane
    LIGHT_VOLUME,     // -10-15% volume, RPE -1
    DELOAD_TODAY,     // -30% volume, RPE ≤7
    REST_RECOMMENDED  // odpuść trening dzisiaj, regeneracja > postęp
}

/**
 * Analizuje regenerację z Health Connect i zwraca wskazówki dla AI/algorytmu.
 *
 * **Filozofia:** algorytm = księgowy (twardo czyta sen i HRV, decyduje), AI = trener
 * (tłumaczy DLACZEGO lżejszy trening i dostosowuje konkretne ćwiczenia).
 *
 * **Heurystyki** (klasyka treningu sportowego):
 * - sen <5h ≥2 nocy z 3 → POOR → REST_RECOMMENDED lub DELOAD_TODAY
 * - sen 5-6h średnio → MODERATE → LIGHT_VOLUME
 * - HRV spadek >15% vs baseline 7d → POOR (CNS przeładowane)
 * - sen ≥7h + HRV stable → EXCELLENT → AS_PLANNED
 */
@Singleton
class HealthInsightAnalyzer @Inject constructor(
    private val hcManager: HealthConnectManager
) {
    suspend fun analyze(): HealthInsight {
        val sleepHours = runCatching { hcManager.readSleepHoursForLastNights(7) }.getOrNull()
        val hrvList = runCatching { hcManager.readHrvForLastDays(7) }.getOrNull()

        val sleepValid = sleepHours?.filter { it > 0 }
        val avgSleep = sleepValid?.takeIf { it.isNotEmpty() }?.average()
        val lastNight = sleepHours?.firstOrNull()?.takeIf { it > 0 }

        val hrvValid = hrvList?.filter { it > 0 }
        val avgHrv = hrvValid?.takeIf { it.isNotEmpty() }?.average()

        // Brak danych w ogóle → NO_DATA, AI nie dostaje tej sekcji
        if (avgSleep == null && avgHrv == null) {
            return HealthInsight(
                recoveryStatus = RecoveryStatus.NO_DATA,
                avgSleepHours7d = null,
                lastNightSleepHours = null,
                avgHrvMs7d = null,
                recommendation = "Brak danych z Health Connect (brak uprawnień lub urządzenie nie raportuje snu/HRV).",
                workoutAdjustment = WorkoutAdjustment.AS_PLANNED
            )
        }

        // Detekcja "ostatnie 3 noce źle"
        val poorSleepStreak = sleepValid?.take(3)?.count { it < 5.0 } ?: 0
        // HRV trend: ostatnie 3 dni vs średnia 7d
        val recentHrvAvg = hrvValid?.take(3)?.takeIf { it.isNotEmpty() }?.average()
        val hrvDropPct = if (avgHrv != null && recentHrvAvg != null && avgHrv > 0)
            (avgHrv - recentHrvAvg) / avgHrv * 100 else 0.0

        // Decyzja w priorytecie: najgorszy sygnał wygrywa.
        val (status, adjustment, msg) = when {
            poorSleepStreak >= 2 -> Triple(
                RecoveryStatus.POOR,
                WorkoutAdjustment.REST_RECOMMENDED,
                "Sen <5h przez ≥2 noce z ostatnich 3 — regeneracja krytycznie niska. Rozważ dzień rest. " +
                    "Trening na deficycie snu = wyższe ryzyko kontuzji, niższy progres."
            )
            hrvDropPct > 15 -> Triple(
                RecoveryStatus.POOR,
                WorkoutAdjustment.DELOAD_TODAY,
                "HRV spadło o ${"%.0f".format(hrvDropPct)}% vs 7-dniowa baseline. CNS przeładowane. " +
                    "Dzisiejszy trening: -30% volume, RPE ≤7."
            )
            avgSleep != null && avgSleep < 6.0 -> Triple(
                RecoveryStatus.MODERATE,
                WorkoutAdjustment.LIGHT_VOLUME,
                "Średnia snu 7d: ${"%.1f".format(avgSleep)}h (norma 7-9h). Zmniejsz volume o 10-15%, RPE -1."
            )
            avgSleep != null && avgSleep >= 7.0 -> Triple(
                RecoveryStatus.EXCELLENT,
                WorkoutAdjustment.AS_PLANNED,
                "Sen ${"%.1f".format(avgSleep)}h (7d), regeneracja optymalna. Trenuj zgodnie z planem."
            )
            else -> Triple(
                RecoveryStatus.GOOD,
                WorkoutAdjustment.AS_PLANNED,
                "Regeneracja OK. Trenuj zgodnie z planem."
            )
        }

        return HealthInsight(
            recoveryStatus = status,
            avgSleepHours7d = avgSleep,
            lastNightSleepHours = lastNight,
            avgHrvMs7d = avgHrv,
            recommendation = msg,
            workoutAdjustment = adjustment
        )
    }
}

object HealthInsightPromptHelper {
    fun toPromptSection(insight: HealthInsight): String = buildString {
        if (insight.recoveryStatus == RecoveryStatus.NO_DATA) return@buildString
        append("\n=== REGENERACJA (Health Connect — sen + HRV, deterministyczne) ===\n")
        insight.lastNightSleepHours?.let {
            append("Ostatnia noc: ${"%.1f".format(it)}h\n")
        }
        insight.avgSleepHours7d?.let {
            append("Sen średnio (7d): ${"%.1f".format(it)}h\n")
        }
        insight.avgHrvMs7d?.let {
            append("HRV średnio (7d): ${"%.1f".format(it)} ms\n")
        }
        val statusLabel = when (insight.recoveryStatus) {
            RecoveryStatus.EXCELLENT -> "✅ Doskonała"
            RecoveryStatus.GOOD -> "✅ Dobra"
            RecoveryStatus.MODERATE -> "⚠️ Umiarkowana"
            RecoveryStatus.POOR -> "❌ Słaba"
            RecoveryStatus.NO_DATA -> "❓ Brak danych"
        }
        append("Status regeneracji: $statusLabel\n")
        append("${insight.recommendation}\n")

        when (insight.workoutAdjustment) {
            WorkoutAdjustment.AS_PLANNED -> {
                append("Twoja rola: trzymaj plan jak jest. Bez zmian.\n")
            }
            WorkoutAdjustment.LIGHT_VOLUME -> {
                append("Twoja rola: gdy generujesz/poprawiasz plan — zmniejsz volume o ~10-15%, RPE -1, ")
                append("krócej rest. Pomóż usere przejść przez gorszy okres bez utraty progresu.\n")
            }
            WorkoutAdjustment.DELOAD_TODAY -> {
                append("Twoja rola: NA DZIŚ proponuj DELOAD — -30% volume, -10% obciążenie, RPE ≤7. ")
                append("Wyjaśnij że to nie regres, tylko rozumna regeneracja CNS.\n")
            }
            WorkoutAdjustment.REST_RECOMMENDED -> {
                append("Twoja rola: SUGERUJ DZIEŃ REST. Trening na deficycie snu szkodzi. ")
                append("Jeśli user MUSI trenować — minimalne LIGHT_VOLUME, tylko cardio Z1 lub stretching.\n")
            }
        }
    }
}
