package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.WorkoutSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Status progresji per ćwiczenie. */
enum class ProgressionStatus {
    PROGRESSING,        // e1RM rośnie >2% w ostatnich 4 tygodniach
    STAGNATING,         // wahanie ±2% przez 4+ tygodnie
    REGRESSING,         // e1RM spada >2%
    INSUFFICIENT_DATA   // <3 wykonania w 6 tygodniach
}

data class ExerciseTrend(
    val exerciseId: Long,
    val exerciseName: String,
    val status: ProgressionStatus,
    val sessionsAnalyzed: Int,
    val firstE1rmKg: Double,
    val latestE1rmKg: Double,
    val percentChange: Double  // (latest - first) / first × 100
) {
    val statusEmoji: String get() = when (status) {
        ProgressionStatus.PROGRESSING -> "📈 Postęp"
        ProgressionStatus.STAGNATING -> "➖ Stagnacja"
        ProgressionStatus.REGRESSING -> "📉 Regres"
        ProgressionStatus.INSUFFICIENT_DATA -> "❓ Za mało danych"
    }
}

data class PlanStagnationReport(
    val trends: List<ExerciseTrend>,
    val totalAnalyzed: Int,
    val stagnatingCount: Int,
    val regressingCount: Int,
    val deloadRecommended: Boolean,
    val deloadReason: String
) {
    val stagnatingPct: Double
        get() = if (totalAnalyzed == 0) 0.0 else (stagnatingCount + regressingCount).toDouble() / totalAnalyzed * 100
}

/**
 * Deterministyczna analiza progresji ćwiczeń.
 *
 * **Zasada:** algorytm liczy zawsze tak samo. AI dostaje gotowy werdykt
 * "Wyciskanie sztangi: STAGNATING (waga 80kg od 4 tygodni)" i tylko
 * sugeruje co z tym zrobić — np. deload, zmiana repów, technika.
 *
 * Próg deloadu: gdy ≥30% przeanalizowanych ćwiczeń stagnuje lub regresuje,
 * **i** mamy ≥3 ćwiczenia z wystarczającymi danymi.
 */
@Singleton
class StagnationAnalyzer @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val planExerciseDao: PlanExerciseDao,
    private val exerciseDao: ExerciseDao
) {
    companion object {
        /** Okno analizy — bierzemy ostatnie 6 tygodni. */
        const val ANALYSIS_WINDOW_DAYS = 42L
        /** Próg "tej samej wagi" — ±2% e1RM. */
        const val STAGNATION_THRESHOLD_PCT = 2.0
        /** Min sesji do analizy (mniej = INSUFFICIENT_DATA). */
        const val MIN_SESSIONS = 3
        /** Próg deloadu — % stagnujących/regresujących z analizowanych. */
        const val DELOAD_THRESHOLD_PCT = 30.0
    }

    /** Analiza pojedynczego ćwiczenia. Pure logic dla testowania. */
    fun analyzeExerciseSets(
        exerciseId: Long,
        exerciseName: String,
        sets: List<WorkoutSet>,
        workoutStartByIdMs: Map<Long, Long>,
        nowMs: Long = System.currentTimeMillis()
    ): ExerciseTrend {
        val cutoff = nowMs - ANALYSIS_WINDOW_DAYS * 24 * 3600 * 1000
        // Top 1 set per workout (max e1RM)
        val sessions = sets
            .filter { it.isCompleted && it.setType != SetType.WARMUP && it.weightKg > 0 }
            .filter { (workoutStartByIdMs[it.workoutId] ?: 0L) >= cutoff }
            .groupBy { it.workoutId }
            .map { (wid, list) ->
                val time = workoutStartByIdMs[wid] ?: 0L
                val maxE1rm = list.maxOf { epley1RM(it.weightKg, it.reps) }
                time to maxE1rm
            }
            .sortedBy { it.first }   // od najstarszych do najnowszych

        if (sessions.size < MIN_SESSIONS) {
            return ExerciseTrend(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                status = ProgressionStatus.INSUFFICIENT_DATA,
                sessionsAnalyzed = sessions.size,
                firstE1rmKg = 0.0,
                latestE1rmKg = 0.0,
                percentChange = 0.0
            )
        }

        // Trend: średnia 1. tercji vs średnia 3. tercji (odporne na pojedynczy spike/dziurę).
        val third = sessions.size / 3
        val firstThird = if (third > 0) sessions.take(third) else listOf(sessions.first())
        val lastThird = if (third > 0) sessions.takeLast(third) else listOf(sessions.last())
        val firstAvg = firstThird.map { it.second }.average()
        val latestAvg = lastThird.map { it.second }.average()
        val pctChange = if (firstAvg > 0) (latestAvg - firstAvg) / firstAvg * 100 else 0.0

        val status = when {
            pctChange > STAGNATION_THRESHOLD_PCT -> ProgressionStatus.PROGRESSING
            pctChange < -STAGNATION_THRESHOLD_PCT -> ProgressionStatus.REGRESSING
            else -> ProgressionStatus.STAGNATING
        }

        return ExerciseTrend(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            status = status,
            sessionsAnalyzed = sessions.size,
            firstE1rmKg = firstAvg,
            latestE1rmKg = latestAvg,
            percentChange = pctChange
        )
    }

    /**
     * Analiza całego planu — bierze unikalne ćwiczenia z planu, analizuje historię
     * każdego, zwraca raport z werdyktem czy deload zalecany.
     */
    suspend fun analyzePlan(planId: Long): PlanStagnationReport {
        val planExes = planExerciseDao.getForPlan(planId)
        val uniqueExerciseIds = planExes.map { it.exerciseId }.distinct()
        return analyzeExercises(uniqueExerciseIds)
    }

    /** Wariant bez planu — wszystkie ćwiczenia z historii (np. dla globalnego raportu). */
    suspend fun analyzeAllRecent(maxExercises: Int = 20): PlanStagnationReport {
        val now = System.currentTimeMillis()
        val cutoff = now - ANALYSIS_WINDOW_DAYS * 24 * 3600 * 1000
        // Wszystkie ćwiczenia z setami w oknie
        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }
        if (finishedWorkouts.isEmpty()) {
            return PlanStagnationReport(emptyList(), 0, 0, 0, false, "Brak ostatnich treningów do analizy.")
        }
        val workoutIds = finishedWorkouts.map { it.id }.toSet()
        val exerciseIds = mutableSetOf<Long>()
        for (wid in workoutIds) {
            setDao.getForWorkout(wid).forEach { exerciseIds.add(it.exerciseId) }
            if (exerciseIds.size >= maxExercises) break
        }
        return analyzeExercises(exerciseIds.toList().take(maxExercises))
    }

    private suspend fun analyzeExercises(exerciseIds: List<Long>): PlanStagnationReport {
        val finished = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val workoutStartById = finished.associate { it.id to it.startedAt }

        val trends = exerciseIds.mapNotNull { exId ->
            val ex = exerciseDao.getById(exId) ?: return@mapNotNull null
            val sets = setDao.getAllForExercise(exId)
            analyzeExerciseSets(exId, ex.name, sets, workoutStartById)
        }

        val analyzed = trends.filter { it.status != ProgressionStatus.INSUFFICIENT_DATA }
        val stagnating = analyzed.count { it.status == ProgressionStatus.STAGNATING }
        val regressing = analyzed.count { it.status == ProgressionStatus.REGRESSING }
        val problemPct = if (analyzed.isEmpty()) 0.0 else (stagnating + regressing).toDouble() / analyzed.size * 100

        val deload = analyzed.size >= MIN_SESSIONS && problemPct >= DELOAD_THRESHOLD_PCT
        val reason = when {
            analyzed.isEmpty() -> "Za mało danych aby ocenić."
            deload -> "${stagnating + regressing} z ${analyzed.size} ćwiczeń (${problemPct.toInt()}%) stagnuje/regresuje. Próg ≥${DELOAD_THRESHOLD_PCT.toInt()}% — czas na tydzień deloadu (-30% volume, -10% obciążenie)."
            else -> "Większość ćwiczeń progresuje (${analyzed.size - stagnating - regressing}/${analyzed.size}). Trzymaj ten plan."
        }

        return PlanStagnationReport(
            trends = trends.sortedBy { it.status.ordinal },  // PROGRESSING najpierw → najmniej krzykliwe na górze
            totalAnalyzed = analyzed.size,
            stagnatingCount = stagnating,
            regressingCount = regressing,
            deloadRecommended = deload,
            deloadReason = reason
        )
    }

    private fun epley1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0) return 0.0
        return weightKg * (1.0 + reps / 30.0)
    }
}

/** Helper budujący sekcję promptu z raportem stagnacji. */
object StagnationPromptHelper {
    fun toPromptSection(report: PlanStagnationReport): String = buildString {
        if (report.totalAnalyzed == 0) {
            append("\n=== PROGRESJA ===\n")
            append("Brak wystarczających danych do analizy progresji (potrzeba 3+ sesji per ćwiczenie z ostatnich 6 tyg.).\n")
            return@buildString
        }
        append("\n=== PROGRESJA (deterministyczne — NIE LICZ SAMODZIELNIE) ===\n")
        append("Przeanalizowane ćwiczenia: ${report.totalAnalyzed}\n")
        append("Stagnacja: ${report.stagnatingCount}, Regres: ${report.regressingCount}, ")
        append("Postęp: ${report.totalAnalyzed - report.stagnatingCount - report.regressingCount}\n")

        if (report.deloadRecommended) {
            append("\n⚠️ **DELOAD ZALECANY**: ${report.deloadReason}\n")
            append("Twoja rola: jeśli generujesz/poprawiasz plan — zaproponuj **tydzień deloadu** ")
            append("(redukcja volume o 30%, obciążenia o 10%, RPE ≤7). Wytłumacz po polsku dlaczego ")
            append("(z konkretnymi nazwami stagnujących ćwiczeń) i co user zyska (ergogenia, regeneracja CNS).\n")
        } else {
            append("\n✅ ${report.deloadReason}\n")
            append("Twoja rola: NIE proponuj deloadu — większość ćwiczeń progresuje. ")
            append("Gdy generujesz plan, kontynuuj progresję liniową.\n")
        }

        // Lista konkretnych problemów
        val problems = report.trends.filter {
            it.status == ProgressionStatus.STAGNATING || it.status == ProgressionStatus.REGRESSING
        }
        if (problems.isNotEmpty()) {
            append("\n## Konkretne ćwiczenia z problemem:\n")
            problems.take(10).forEach { t ->
                val pct = "%.1f".format(t.percentChange)
                append("- ${t.exerciseName}: ${t.statusEmoji} (${t.sessionsAnalyzed} sesji, e1RM ${pct}% w 6 tyg.)\n")
            }
        }
    }
}
