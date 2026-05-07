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
    val recommendation: String,
    val maturity: DataMaturity
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
    suspend fun analyze(): TrainingReadiness {
        val recovery = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        val load = runCatching { trainingLoadAnalyzer.analyze() }.getOrNull()
        val muscle = runCatching { muscleRecoveryAnalyzer.analyze() }.getOrNull()

        val recoveryScore = recovery?.score ?: 75   // neutral default
        val muscleAvg = muscle?.avgRecoveryPct ?: 80

        // LoadFactor: ACWR optimum 0.8-1.3 → 100; im dalej tym mniej
        val loadFactor = if (load != null && load.isReliable) {
            when {
                load.acwr in 0.8..1.3 -> 100
                load.acwr in 0.7..1.5 -> 80
                load.acwr in 0.5..1.7 -> 60
                else -> 40
            }
        } else 80   // brak danych = neutral

        val score = (recoveryScore * 0.5 + loadFactor * 0.3 + muscleAvg * 0.2).toInt().coerceIn(0, 100)

        val zone = when {
            score >= 90 -> ReadinessZone.PEAK
            score >= 70 -> ReadinessZone.GOOD
            score >= 50 -> ReadinessZone.MODERATE
            else -> ReadinessZone.REST
        }

        // Inteligentna rekomendacja — bazuje na tym co najbardziej obniża score
        val rec = buildRecommendation(zone, recoveryScore, loadFactor, muscleAvg, muscle)

        return TrainingReadiness(
            score = score,
            zone = zone,
            recoveryComponent = recoveryScore,
            loadComponent = loadFactor,
            muscleComponent = muscleAvg,
            recommendation = rec,
            maturity = recovery?.maturity ?: DataMaturity.LEARNING
        )
    }

    private fun buildRecommendation(
        zone: ReadinessZone,
        recovery: Int,
        load: Int,
        muscle: Int,
        muscleReport: MuscleRecoveryReport?
    ): String = when (zone) {
        ReadinessZone.PEAK -> {
            val freshList = muscleReport?.freshGroups?.take(3)
                ?.joinToString(", ") { it.name.lowercase() } ?: ""
            "Jesteś gotowy na ciężki trening. ${if (freshList.isNotEmpty()) "Najświeższe partie: $freshList." else ""}"
        }
        ReadinessZone.GOOD -> {
            "Dobra forma — trenuj zgodnie z planem. Wszystkie systemy w normie."
        }
        ReadinessZone.MODERATE -> {
            // Znajdź najsłabszy komponent
            val weakest = listOf(
                "regeneracja (sen/HRV)" to recovery,
                "obciążenie treningowe (ACWR)" to load,
                "regeneracja mięśni" to muscle
            ).minByOrNull { it.second }
            "Umiarkowana gotowość. Najbardziej obniża: ${weakest?.first ?: "?"} (${weakest?.second ?: 0}/100). " +
                "Trenuj łagodnie, RPE 7, krótsze sesje."
        }
        ReadinessZone.REST -> {
            "Niska gotowość — wszystkie systemy sygnalizują zmęczenie. Dziś rest lub bardzo lekkie cardio Z1. " +
                "Po regeneracji wracaj na pełen plan."
        }
    }
}

object TrainingReadinessPromptHelper {
    fun toPromptSection(readiness: TrainingReadiness): String = buildString {
        if (readiness.maturity == DataMaturity.LEARNING) return@buildString
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
        append("**Rekomendacja:** ${readiness.recommendation}\n")
        append("**Twoja rola:** gdy proponujesz trening na DZIŚ — bazuj na Score i konkretnych komponentach. ")
        append("Score <50 = sugestia rest. 50-70 = lżejszy plan. 70-90 = standard. 90+ = ciężki.\n")
    }
}
