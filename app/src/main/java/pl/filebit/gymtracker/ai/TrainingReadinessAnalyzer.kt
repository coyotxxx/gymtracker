package pl.filebit.gymtracker.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Training Readiness Score — kompozyt 0-100 inspirowany Garmin Forerunner.
 *
 * **Filozofia:** zamiast 3 osobnych metryk (Recovery / Load / Muscle Recovery),
 * jeden score który mówi userowi "ile możesz dziś". Garmin używa tego jako
 * primary metric — 4 osobne pod spodem są opcjonalne dla zaawansowanych.
 *
 * **Wzór:**
 * Readiness = 50% × RecoveryScore + 30% × LoadFactor + 20% × MuscleAvg
 *
 * - RecoveryScore (0-100) — z RecoveryScoreCalculator (sen, HRV, HR, stres)
 * - LoadFactor (0-100) — z TrainingLoadAnalyzer (ACWR penalizing)
 * - MuscleAvg (0-100) — z MuscleRecoveryAnalyzer (średnia per partia)
 *
 * **Strefy:**
 * - 90-100: PEAK — trenuj ciężko, gotowy na PR
 * - 70-90: GOOD — normalne treningi
 * - 50-70: MODERATE — łagodnie, RPE 7
 * - <50: REST — odpuść lub bardzo lekko
 */
data class TrainingReadiness(
    val score: Int,                          // 0-100
    val zone: ReadinessZone,
    val recoveryComponent: Int,              // 0-100 z 50%
    val loadComponent: Int,                  // 0-100 z 30%
    val muscleComponent: Int,                // 0-100 z 20%
    val maturity: DataMaturity,
    /**
     * v2.76.0: czy jest ŚWIEŻA aktywność treningowa (≥1 ukończony trening w 14 dni).
     * Bez niej Tonaż (→80 neutral) i Mięśnie (→100 nic nietrenowane) to wartości
     * DOMYŚLNE, które zawyżają werdykt do PEAK/„gotów na PR" mimo braku bazy treningowej.
     * Gdy false → karta jest chowana (jak przy maturity=LEARNING) i sekcja w prompt AI pomijana.
     */
    val hasRecentTraining: Boolean
)

enum class ReadinessZone {
    PEAK,       // 90-100
    GOOD,       // 70-89
    MODERATE,   // 50-69
    REST        // <50
}

@Singleton
class TrainingReadinessAnalyzer @Inject constructor(
    private val recoveryScoreCalculator: RecoveryScoreCalculator,
    private val trainingLoadAnalyzer: TrainingLoadAnalyzer,
    private val muscleRecoveryAnalyzer: MuscleRecoveryAnalyzer
) {
    suspend fun analyze(currentPhase: TrainingPhase = TrainingPhase.NO_DATA): TrainingReadiness {
        val recovery = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        val load = runCatching { trainingLoadAnalyzer.analyze() }.getOrNull()
        val muscle = runCatching { muscleRecoveryAnalyzer.analyze() }.getOrNull()

        // v2.44.0: brak ŚWIEŻYCH danych regeneracji = UNKNOWN, nie fałszywe „75".
        // Inaczej kompozyt „myśli" że user jest wyregenerowany, choć nic nie wie —
        // i zawyża gotowość. Gdy nieznana → przeważamy load+mięśnie.
        val recoveryKnown = recovery != null && recovery.isFresh &&
            recovery.maturity != DataMaturity.LEARNING
        val recoveryScore = recovery?.score ?: 75   // tylko do wyświetlenia komponentu
        val muscleAvg = muscle?.avgRecoveryPct ?: 80

        // v2.76.0: gotowość ma sens TYLKO przy świeżej aktywności treningowej.
        // Bez treningu w 14 dni load=INSUFFICIENT (loadFactor→80) i muscle=100 to
        // wartości domyślne — score windowany do PEAK/„gotów na PR" mimo zerowej bazy.
        val hasRecentTraining = load != null && load.workoutsCount14d > 0

        // LoadFactor: ACWR optimum 0.8-1.3 → 100; im dalej tym mniej
        // v1.11.68: gdy phase=DELOAD niski ACWR jest CELOWY → loadFactor neutralny (80)
        // żeby Readiness nie penalizował za zaplanowany niski tonaż.
        val loadFactor = if (currentPhase == TrainingPhase.DELOAD) {
            80   // deload jest oczekiwany, nie kara
        } else if (load != null && load.isReliable) {
            when {
                load.acwr in 0.8..1.3 -> 100
                load.acwr in 0.7..1.5 -> 80
                load.acwr in 0.5..1.7 -> 60
                else -> 40
            }
        } else 80   // brak danych = neutral

        val score = if (recoveryKnown) {
            recoveryScore * 0.5 + loadFactor * 0.3 + muscleAvg * 0.2
        } else {
            // Regeneracja nieznana → wagi przeliczone z load(30)+mięśni(20) = 60/40.
            loadFactor * 0.6 + muscleAvg * 0.4
        }.toInt().coerceIn(0, 100)

        val zone = when {
            score >= 90 -> ReadinessZone.PEAK
            score >= 70 -> ReadinessZone.GOOD
            score >= 50 -> ReadinessZone.MODERATE
            else -> ReadinessZone.REST
        }

        // v2.77.0: czujnik NIE pisze już prozy-werdyktu („gotów na PR"/„odpuść dziś").
        // Akcyjny głos to wyłącznie Karta Coacha (CoachOrchestrator). Tu zostają czyste dane.
        return TrainingReadiness(
            score = score,
            zone = zone,
            recoveryComponent = recoveryScore,
            loadComponent = loadFactor,
            muscleComponent = muscleAvg,
            maturity = recovery?.maturity ?: DataMaturity.LEARNING,
            hasRecentTraining = hasRecentTraining
        )
    }
}

object TrainingReadinessPromptHelper {
    fun toPromptSection(readiness: TrainingReadiness): String = buildString {
        if (readiness.maturity == DataMaturity.LEARNING) return@buildString
        // v2.76.0: bez świeżej aktywności treningowej werdykt jest oparty na wartościach
        // domyślnych — nie karmimy mózgu fałszywym „PEAK/gotów na PR" (AI i tak widzi historię treningów).
        if (!readiness.hasRecentTraining) return@buildString
        append("\n=== TRAINING READINESS (kompozyt — gotowość do treningu) ===\n")
        append("Score: ${readiness.score}/100 — ")
        append(when (readiness.zone) {
            ReadinessZone.PEAK -> "PEAK (90+) — gotów na PR\n"
            ReadinessZone.GOOD -> "GOOD (70-89) — normalne treningi\n"
            ReadinessZone.MODERATE -> "MODERATE (50-69) — trenuj łagodniej\n"
            ReadinessZone.REST -> "REST (<50) — odpuść lub bardzo lekko\n"
        })
        append("Składniki:\n")
        append("- Regeneracja (sen/HRV): ${readiness.recoveryComponent}/100 (waga 50%)\n")
        append("- Obciążenie (ACWR): ${readiness.loadComponent}/100 (waga 30%)\n")
        append("- Regeneracja mięśni: ${readiness.muscleComponent}/100 (waga 20%)\n")
        append("**Twoja rola:** gdy proponujesz trening na DZIŚ — bazuj na Score i konkretnych komponentach. ")
        append("Score <50 = sugestia rest. 50-70 = lżejszy plan. 70-90 = standard. 90+ = ciężki.\n")
    }
}
