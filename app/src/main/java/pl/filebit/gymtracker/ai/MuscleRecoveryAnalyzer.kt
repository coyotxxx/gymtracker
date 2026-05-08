package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Recovery per partia mięśniowa (inspirowane Fitbod).
 *
 * **Filozofia:** każda partia regeneruje się w innym tempie. Duże mięśnie (nogi,
 * plecy) potrzebują 72h, mniejsze (biceps, łydki) 48h. Każdy ostatni trening
 * zostawia "fatigue", który spada wykładniczo w czasie.
 *
 * **Algorytm:**
 * - Recovery(t) = 100 × (1 - fatigue(t))
 * - fatigue(t) = ostatni_set_volume / max_recoverable × exp(-t/halfLife)
 * - halfLife per muscle group (czas po którym fatigue spada o połowę)
 *
 * **Użycie:**
 * - Klatka 90% → trenuj
 * - Klatka 30% → daj jej dzień
 * - Średnia <60% przez wszystkie partie → globalny rest day
 *
 * **AI:**
 * - Generując plan na dziś, AI patrzy które partie są fresh i proponuje je
 * - Gdy user pyta "co trenować dziś?", AI sugeruje na bazie tych liczb
 */
data class MuscleRecoveryStatus(
    val muscle: MuscleGroup,
    val recoveryPct: Int,                   // 0-100
    val hoursSinceLastTrained: Double?,     // null = nigdy
    val lastTrainedSets: Int,               // ile setów ostatnio
    val recommendation: TrainingRecommendation
) {
    val polishName: String get() = when (muscle) {
        MuscleGroup.CHEST -> "Klatka"
        MuscleGroup.BACK -> "Plecy"
        MuscleGroup.SHOULDERS -> "Bark"
        MuscleGroup.BICEPS -> "Biceps"
        MuscleGroup.TRICEPS -> "Triceps"
        MuscleGroup.QUADS -> "Czworogłowy"
        MuscleGroup.HAMSTRINGS -> "Dwugłowy uda"
        MuscleGroup.GLUTES -> "Pośladki"
        MuscleGroup.CALVES -> "Łydki"
        MuscleGroup.CORE -> "Brzuch"
        else -> muscle.name
    }
}

enum class TrainingRecommendation {
    TRAIN_HEAVY,    // 80%+ recovery — trenuj ciężko
    TRAIN_LIGHT,    // 60-80% — trenuj ale nie maxuj
    REST,           // 40-60% — daj jeszcze dzień
    AVOID           // <40% — nie ruszaj, pełny rest
}

data class MuscleRecoveryReport(
    val statuses: List<MuscleRecoveryStatus>,
    val avgRecoveryPct: Int,
    val freshGroups: List<MuscleGroup>,         // recovery >=80%
    val tiredGroups: List<MuscleGroup>          // recovery <60%
) {
    /** Globalna sugestia: co trenować dziś. */
    val todayFocus: String
        get() {
            if (freshGroups.isEmpty() && avgRecoveryPct < 50) {
                return "Wszystkie partie zmęczone — dziś rest lub bardzo lekkie cardio Z1."
            }
            val freshNames = freshGroups.take(3).joinToString(", ") { polishGroupName(it) }
            return when {
                freshGroups.isNotEmpty() -> "Najbardziej wypoczęte dziś: $freshNames"
                else -> "Wszystkie partie częściowo zmęczone — wybierz najświeższą."
            }
        }

    private fun polishGroupName(m: MuscleGroup): String = when (m) {
        MuscleGroup.CHEST -> "klatka"
        MuscleGroup.BACK -> "plecy"
        MuscleGroup.SHOULDERS -> "bark"
        MuscleGroup.BICEPS -> "biceps"
        MuscleGroup.TRICEPS -> "triceps"
        MuscleGroup.QUADS -> "nogi (kwad.)"
        MuscleGroup.HAMSTRINGS -> "uda (tylne)"
        MuscleGroup.GLUTES -> "pośladki"
        MuscleGroup.CALVES -> "łydki"
        MuscleGroup.CORE -> "brzuch"
        else -> m.name.lowercase()
    }
}

@Singleton
class MuscleRecoveryAnalyzer @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {
    /** Half-life regeneracji per partia (godziny). Po t=halfLife fatigue spada o 50%. */
    private val halfLifeHours: Map<MuscleGroup, Double> = mapOf(
        // Duże partie — dłużej regenerują
        MuscleGroup.QUADS to 36.0,
        MuscleGroup.HAMSTRINGS to 36.0,
        MuscleGroup.GLUTES to 30.0,
        MuscleGroup.BACK to 30.0,
        MuscleGroup.CHEST to 28.0,
        // Średnie
        MuscleGroup.SHOULDERS to 24.0,
        // Małe — szybciej
        MuscleGroup.BICEPS to 20.0,
        MuscleGroup.TRICEPS to 20.0,
        MuscleGroup.CALVES to 18.0,
        MuscleGroup.CORE to 16.0
    )

    /** Maksymalna liczba "męczących" setów w jednej sesji (powyżej tego nasycenie). */
    private val maxFatigueSets = 12

    private val tracked = halfLifeHours.keys.toList()

    suspend fun analyze(): MuscleRecoveryReport {
        val now = System.currentTimeMillis()
        val msPerHour = 3600 * 1000.0

        // Wszystkie ukończone treningi z ostatnich 7 dni (więcej już nie wpływa)
        val cutoff = now - 7L * 24 * 3600 * 1000
        val recent = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }
            .sortedByDescending { it.startedAt }

        // Mapa: ćwiczenie -> partia
        val exMap = mutableMapOf<Long, MuscleGroup>()

        val statuses = tracked.map { muscle ->
            // Znajdź najnowszą sesję, w której trenowano tę partię
            var lastSessionMs: Long? = null
            var lastSets = 0

            outer@ for (w in recent) {
                val sets = setDao.getForWorkout(w.id)
                    .filter { it.isCompleted && it.setType != SetType.WARMUP }
                if (sets.isEmpty()) continue

                // Liczymy sety dla tej partii
                var setsForMuscle = 0
                for (s in sets) {
                    val mg = exMap.getOrPut(s.exerciseId) {
                        exerciseDao.getById(s.exerciseId)?.primaryMuscle ?: MuscleGroup.OTHER
                    }
                    if (mg == muscle) setsForMuscle++
                }
                if (setsForMuscle > 0) {
                    lastSessionMs = w.startedAt
                    lastSets = setsForMuscle
                    break@outer
                }
            }

            if (lastSessionMs == null) {
                // Nigdy nie trenowane (w 7d) — recovery 100%
                MuscleRecoveryStatus(
                    muscle = muscle,
                    recoveryPct = 100,
                    hoursSinceLastTrained = null,
                    lastTrainedSets = 0,
                    recommendation = TrainingRecommendation.TRAIN_HEAVY
                )
            } else {
                val hoursSince = (now - lastSessionMs) / msPerHour
                val halfLife = halfLifeHours[muscle] ?: 24.0
                // Inicjalna fatigue = liczba setów / max_sets (saturated 0..1)
                val initialFatigue = min(1.0, lastSets.toDouble() / maxFatigueSets)
                // Decay wykładniczy: fatigue(t) = initial × exp(-t × ln2 / halfLife)
                val decayRate = 0.693 / halfLife
                val currentFatigue = initialFatigue * exp(-hoursSince * decayRate)
                val recovery = ((1 - currentFatigue) * 100).toInt().coerceIn(0, 100)

                val rec = when {
                    recovery >= 80 -> TrainingRecommendation.TRAIN_HEAVY
                    recovery >= 60 -> TrainingRecommendation.TRAIN_LIGHT
                    recovery >= 40 -> TrainingRecommendation.REST
                    else -> TrainingRecommendation.AVOID
                }

                MuscleRecoveryStatus(
                    muscle = muscle,
                    recoveryPct = recovery,
                    hoursSinceLastTrained = hoursSince,
                    lastTrainedSets = lastSets,
                    recommendation = rec
                )
            }
        }

        val avgRec = statuses.map { it.recoveryPct }.average().toInt()
        val fresh = statuses.filter { it.recoveryPct >= 80 }.map { it.muscle }
        val tired = statuses.filter { it.recoveryPct < 60 }.map { it.muscle }

        return MuscleRecoveryReport(
            statuses = statuses.sortedByDescending { it.recoveryPct },
            avgRecoveryPct = avgRec,
            freshGroups = fresh,
            tiredGroups = tired
        )
    }

    /** v1.11.46 — fast variant z pre-fetched StatsSnapshot. */
    fun analyzeWithSnapshot(snapshot: pl.filebit.gymtracker.data.repository.StatsSnapshot): MuscleRecoveryReport =
        computeMuscleRecoveryFromSnapshot(snapshot, System.currentTimeMillis(), tracked, halfLifeHours, maxFatigueSets.toDouble())
}

/** Pure function — testowalne bez DAO. Logika identyczna z MuscleRecoveryAnalyzer.analyze(). */
fun computeMuscleRecoveryFromSnapshot(
    snapshot: pl.filebit.gymtracker.data.repository.StatsSnapshot,
    now: Long,
    tracked: List<MuscleGroup>,
    halfLifeHours: Map<MuscleGroup, Double>,
    maxFatigueSets: Double
): MuscleRecoveryReport {
    val msPerHour = 3600 * 1000.0
    val cutoff = now - 7L * 24 * 3600 * 1000

    val recent = snapshot.finishedWorkouts
        .filter { it.startedAt >= cutoff }
        .sortedByDescending { it.startedAt }

    val statuses = tracked.map { muscle ->
        var lastSessionMs: Long? = null
        var lastSets = 0

        outer@ for (w in recent) {
            val sets = snapshot.completedSetsFor(w.id)
            if (sets.isEmpty()) continue

            var setsForMuscle = 0
            for (s in sets) {
                val mg = snapshot.exercisesById[s.exerciseId]?.primaryMuscle ?: MuscleGroup.OTHER
                if (mg == muscle) setsForMuscle++
            }
            if (setsForMuscle > 0) {
                lastSessionMs = w.startedAt
                lastSets = setsForMuscle
                break@outer
            }
        }

        if (lastSessionMs == null) {
            MuscleRecoveryStatus(
                muscle = muscle,
                recoveryPct = 100,
                hoursSinceLastTrained = null,
                lastTrainedSets = 0,
                recommendation = TrainingRecommendation.TRAIN_HEAVY
            )
        } else {
            val hoursSince = (now - lastSessionMs) / msPerHour
            val halfLife = halfLifeHours[muscle] ?: 24.0
            val initialFatigue = kotlin.math.min(1.0, lastSets.toDouble() / maxFatigueSets)
            val decayRate = 0.693 / halfLife
            val currentFatigue = initialFatigue * kotlin.math.exp(-hoursSince * decayRate)
            val recovery = ((1 - currentFatigue) * 100).toInt().coerceIn(0, 100)
            val rec = when {
                recovery >= 80 -> TrainingRecommendation.TRAIN_HEAVY
                recovery >= 60 -> TrainingRecommendation.TRAIN_LIGHT
                recovery >= 40 -> TrainingRecommendation.REST
                else -> TrainingRecommendation.AVOID
            }
            MuscleRecoveryStatus(
                muscle = muscle,
                recoveryPct = recovery,
                hoursSinceLastTrained = hoursSince,
                lastTrainedSets = lastSets,
                recommendation = rec
            )
        }
    }

    val avgRec = statuses.map { it.recoveryPct }.average().toInt()
    val fresh = statuses.filter { it.recoveryPct >= 80 }.map { it.muscle }
    val tired = statuses.filter { it.recoveryPct < 60 }.map { it.muscle }

    return MuscleRecoveryReport(
        statuses = statuses.sortedByDescending { it.recoveryPct },
        avgRecoveryPct = avgRec,
        freshGroups = fresh,
        tiredGroups = tired
    )
}

object MuscleRecoveryPromptHelper {
    fun toPromptSection(report: MuscleRecoveryReport): String = buildString {
        append("\n=== RECOVERY PER PARTIA MIĘŚNIOWA (deterministyczne, decay model) ===\n")
        append("Średnia regeneracja: ${report.avgRecoveryPct}%\n")
        append("Wypoczęte (≥80%): ${if (report.freshGroups.isEmpty()) "—" else report.freshGroups.joinToString(", ") { it.name }}\n")
        append("Zmęczone (<60%): ${if (report.tiredGroups.isEmpty()) "—" else report.tiredGroups.joinToString(", ") { it.name }}\n")
        append("\n## Status per partia:\n")
        report.statuses.forEach { s ->
            val emoji = when (s.recommendation) {
                TrainingRecommendation.TRAIN_HEAVY -> "✅"
                TrainingRecommendation.TRAIN_LIGHT -> "⚠️"
                TrainingRecommendation.REST -> "🔶"
                TrainingRecommendation.AVOID -> "❌"
            }
            val timeInfo = s.hoursSinceLastTrained?.let { " (${it.toInt()}h temu, ${s.lastTrainedSets} setów)" } ?: " (nie trenowane 7d)"
            append("- $emoji ${s.polishName}: ${s.recoveryPct}%$timeInfo\n")
        }
        append("\n**Twoja rola:** gdy generujesz plan na dziś LUB sugerujesz co trenować — ")
        append("preferuj partie z recovery ≥80% (TRAIN_HEAVY). Partie <60% pomijaj lub trenuj bardzo lekko (TRAIN_LIGHT/REST). ")
        append("Partie <40% (AVOID) wykluczaj całkowicie — pełna regeneracja konieczna.\n")
    }
}
