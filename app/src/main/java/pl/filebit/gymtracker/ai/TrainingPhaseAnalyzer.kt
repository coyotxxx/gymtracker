package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faza cyklu treningowego (mezo-cykl).
 *
 * Klasyczna periodyzacja liniowa: akumulacja (build) → intensyfikacja (peak) → deload (rest).
 * Każda faza ma inne priorytety dla volume i intensywności.
 */
enum class TrainingPhase {
    /** Tygodnie 1-3 cyklu. Wyższe volume (więcej setów), umiarkowana intensywność (RPE 7-8). */
    ACCUMULATION,
    /** Tygodnie 4-6. Niższe volume, wyższa intensywność (RPE 8-9), heavy compound. */
    INTENSIFICATION,
    /** Tydzień 7 (lub po stagnacji). -30% volume, -10% obciążenie, RPE ≤7. */
    DELOAD,
    /** Algorytm zaleca deload TERAZ (stagnacja ≥30% lub >6 tyg bez deloadu). */
    NEEDS_DELOAD,
    /** <2 tygodnie historii — za mało żeby ocenić cykl. */
    NO_DATA
}

data class TrainingPhaseStatus(
    val phase: TrainingPhase,
    val weeksSinceLastDeload: Int,
    val recommendation: String
) {
    val polishLabel: String get() = when (phase) {
        TrainingPhase.ACCUMULATION -> "Akumulacja (tydzień $weeksSinceLastDeload/3)"
        TrainingPhase.INTENSIFICATION -> "Intensyfikacja (tydzień ${weeksSinceLastDeload - 3}/3)"
        // v1.24.50: PL "Tydzień lżejszy" zamiast "Deload" (Glossary zachowuje EN)
        TrainingPhase.DELOAD -> "Tydzień lżejszy"
        TrainingPhase.NEEDS_DELOAD -> "Czas na lżejszy tydzień"
        TrainingPhase.NO_DATA -> "Za mało danych"
    }
}

/**
 * Wykrywa fazę cyklu treningowego z historii i raportu stagnacji.
 *
 * **Heurystyka:**
 * 1. <2 tygodnie treningów → NO_DATA
 * 2. Najnowszy tydzień miał volume <60% mediany ostatnich 4 tyg → ten tydzień to DELOAD
 *    (resetujemy licznik fazy)
 * 3. Stagnacja zalecona przez StagnationAnalyzer → NEEDS_DELOAD
 * 4. >6 tyg bez deloadu → NEEDS_DELOAD
 * 5. Domyślnie: tygodnie 1-3 = ACCUMULATION, 4-6 = INTENSIFICATION
 *
 * **Filozofia:** algorytm = księgowy (twardo identyfikuje fazę), AI = trener
 * (tłumaczy DLACZEGO ta faza, dostosowuje konkretne ćwiczenia).
 */
@Singleton
class TrainingPhaseAnalyzer @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao
) {
    companion object {
        const val WEEKS_PER_CYCLE = 6  // 3 akumulacji + 3 intensyfikacji
        const val MAX_WEEKS_WITHOUT_DELOAD = 6
        const val DELOAD_WEEK_VOLUME_THRESHOLD_PCT = 60.0  // <60% mediany = deload
        const val ANALYSIS_WINDOW_WEEKS = 8L
    }

    suspend fun analyze(stagnationReport: PlanStagnationReport? = null): TrainingPhaseStatus {
        val now = System.currentTimeMillis()
        val cutoff = now - ANALYSIS_WINDOW_WEEKS * 7 * 24 * 3600 * 1000

        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }
            .sortedBy { it.startedAt }

        if (finished.size < 4) {
            return TrainingPhaseStatus(
                phase = TrainingPhase.NO_DATA,
                weeksSinceLastDeload = 0,
                recommendation = "Trenuj jeszcze 1-2 tygodnie żeby algorytm mógł wykryć fazę cyklu."
            )
        }

        // Volume per tydzień (suma reps × weight wszystkich seriek z tygodnia)
        val msPerWeek = 7L * 24 * 3600 * 1000
        val volumeByWeek = mutableMapOf<Int, Double>()
        for (w in finished) {
            val weekIdx = ((now - w.startedAt) / msPerWeek).toInt()
            val workVolume = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .sumOf { it.reps * it.weightKg }
            volumeByWeek[weekIdx] = (volumeByWeek[weekIdx] ?: 0.0) + workVolume
        }

        // Sortowane od najnowszych (weekIdx=0) do najstarszych
        val weeklyVolumes = volumeByWeek.entries.sortedBy { it.key }
        if (weeklyVolumes.size < 2) {
            return TrainingPhaseStatus(TrainingPhase.NO_DATA, 0, "Za mało tygodni z treningami.")
        }

        val median = run {
            val sorted = weeklyVolumes.map { it.value }.sorted()
            if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
        val deloadThreshold = median * DELOAD_WEEK_VOLUME_THRESHOLD_PCT / 100

        // Znajdź ostatni deload week (idąc od najnowszych — weekIdx 0,1,2...)
        val lastDeloadWeekIdx = weeklyVolumes
            .firstOrNull { it.value < deloadThreshold && it.value > 0 }
            ?.key
        val weeksSinceDeload = lastDeloadWeekIdx ?: weeklyVolumes.last().key + 1
        // weekIdx 0 = bieżący tydzień; cykl liczymy od 1
        val cycleWeek = weeksSinceDeload + 1

        // 1. Stagnacja → NEEDS_DELOAD
        if (stagnationReport != null && stagnationReport.deloadRecommended) {
            return TrainingPhaseStatus(
                phase = TrainingPhase.NEEDS_DELOAD,
                weeksSinceLastDeload = cycleWeek,
                recommendation = "Algorytm wykrył stagnację (${stagnationReport.stagnatingCount + stagnationReport.regressingCount}/${stagnationReport.totalAnalyzed} ćwiczeń). " +
                    "Następny tydzień zaplanuj jako deload: -30% volume, -10% obciążenie, RPE ≤7. " +
                    "Potem wracasz na akumulację (cykl 1)."
            )
        }

        // 2. Wykryto deload w bieżącym tygodniu
        if (lastDeloadWeekIdx != null && lastDeloadWeekIdx <= 1) {
            return TrainingPhaseStatus(
                phase = TrainingPhase.DELOAD,
                weeksSinceLastDeload = 0,
                recommendation = "Bieżący tydzień to deload — niskie volume jest celowe. Po nim akumulacja (3 tygodnie podwyższonego volume)."
            )
        }

        // 3. >6 tygodni bez deloadu → NEEDS_DELOAD
        if (cycleWeek > MAX_WEEKS_WITHOUT_DELOAD) {
            return TrainingPhaseStatus(
                phase = TrainingPhase.NEEDS_DELOAD,
                weeksSinceLastDeload = cycleWeek,
                recommendation = "$cycleWeek tygodni bez deloadu — czas na tydzień lekki. " +
                    "Bez deloadu CNS się akumuluje, intensywność spada."
            )
        }

        // 4. Domyślnie — akumulacja vs intensyfikacja
        return when {
            cycleWeek <= 3 -> TrainingPhaseStatus(
                phase = TrainingPhase.ACCUMULATION,
                weeksSinceLastDeload = cycleWeek,
                recommendation = "Faza akumulacji ($cycleWeek/3): wyższe volume, RPE 7-8. Build phase. " +
                    "Skup się na ilości pracy — więcej setów, więcej powtórzeń."
            )
            cycleWeek <= 6 -> TrainingPhaseStatus(
                phase = TrainingPhase.INTENSIFICATION,
                weeksSinceLastDeload = cycleWeek,
                recommendation = "Faza intensyfikacji (${cycleWeek - 3}/3): niższe volume, RPE 8-9. " +
                    "Cięższe ciężary, krótsze serie, dłuższe odpoczynki."
            )
            else -> TrainingPhaseStatus(
                phase = TrainingPhase.NEEDS_DELOAD,
                weeksSinceLastDeload = cycleWeek,
                recommendation = "Czas na deload."
            )
        }
    }

    /** v1.11.46 — fast variant z pre-fetched StatsSnapshot. */
    fun analyzeWithSnapshot(
        snapshot: pl.filebit.gymtracker.data.repository.StatsSnapshot,
        stagnationReport: PlanStagnationReport? = null
    ): TrainingPhaseStatus = computeTrainingPhaseFromSnapshot(
        snapshot, stagnationReport, System.currentTimeMillis()
    )
}

/** Pure function — testowalne bez DAO. Logika identyczna z TrainingPhaseAnalyzer.analyze(). */
fun computeTrainingPhaseFromSnapshot(
    snapshot: pl.filebit.gymtracker.data.repository.StatsSnapshot,
    stagnationReport: PlanStagnationReport? = null,
    now: Long
): TrainingPhaseStatus {
    val cutoff = now - TrainingPhaseAnalyzer.ANALYSIS_WINDOW_WEEKS * 7 * 24 * 3600 * 1000

    val finished = snapshot.finishedWorkouts
        .filter { it.startedAt >= cutoff }
        .sortedBy { it.startedAt }

    if (finished.size < 4) {
        return TrainingPhaseStatus(
            phase = TrainingPhase.NO_DATA,
            weeksSinceLastDeload = 0,
            recommendation = "Trenuj jeszcze 1-2 tygodnie żeby algorytm mógł wykryć fazę cyklu."
        )
    }

    val msPerWeek = 7L * 24 * 3600 * 1000
    val volumeByWeek = mutableMapOf<Int, Double>()
    for (w in finished) {
        val weekIdx = ((now - w.startedAt) / msPerWeek).toInt()
        val workVolume = snapshot.completedSetsFor(w.id).sumOf { it.reps * it.weightKg }
        volumeByWeek[weekIdx] = (volumeByWeek[weekIdx] ?: 0.0) + workVolume
    }

    val weeklyVolumes = volumeByWeek.entries.sortedBy { it.key }
    if (weeklyVolumes.size < 2) {
        return TrainingPhaseStatus(TrainingPhase.NO_DATA, 0, "Za mało tygodni z treningami.")
    }

    val median = run {
        val sorted = weeklyVolumes.map { it.value }.sorted()
        if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
    val deloadThreshold = median * TrainingPhaseAnalyzer.DELOAD_WEEK_VOLUME_THRESHOLD_PCT / 100

    val lastDeloadWeekIdx = weeklyVolumes
        .firstOrNull { it.value < deloadThreshold && it.value > 0 }
        ?.key
    val weeksSinceDeload = lastDeloadWeekIdx ?: weeklyVolumes.last().key + 1
    val cycleWeek = weeksSinceDeload + 1

    if (stagnationReport != null && stagnationReport.deloadRecommended) {
        return TrainingPhaseStatus(
            phase = TrainingPhase.NEEDS_DELOAD,
            weeksSinceLastDeload = cycleWeek,
            recommendation = "Algorytm wykrył stagnację (${stagnationReport.stagnatingCount + stagnationReport.regressingCount}/${stagnationReport.totalAnalyzed} ćwiczeń). " +
                "Następny tydzień zaplanuj jako deload: -30% volume, -10% obciążenie, RPE ≤7. " +
                "Potem wracasz na akumulację (cykl 1)."
        )
    }

    if (lastDeloadWeekIdx != null && lastDeloadWeekIdx <= 1) {
        return TrainingPhaseStatus(
            phase = TrainingPhase.DELOAD,
            weeksSinceLastDeload = 0,
            recommendation = "Bieżący tydzień to deload — niskie volume jest celowe. Po nim akumulacja (3 tygodnie podwyższonego volume)."
        )
    }

    if (cycleWeek > TrainingPhaseAnalyzer.MAX_WEEKS_WITHOUT_DELOAD) {
        return TrainingPhaseStatus(
            phase = TrainingPhase.NEEDS_DELOAD,
            weeksSinceLastDeload = cycleWeek,
            recommendation = "$cycleWeek tygodni bez deloadu — czas na tydzień lekki. " +
                "Bez deloadu CNS się akumuluje, intensywność spada."
        )
    }

    return when {
        cycleWeek <= 3 -> TrainingPhaseStatus(
            phase = TrainingPhase.ACCUMULATION,
            weeksSinceLastDeload = cycleWeek,
            recommendation = "Faza akumulacji ($cycleWeek/3): wyższe volume, RPE 7-8. Build phase. " +
                "Skup się na ilości pracy — więcej setów, więcej powtórzeń."
        )
        cycleWeek <= 6 -> TrainingPhaseStatus(
            phase = TrainingPhase.INTENSIFICATION,
            weeksSinceLastDeload = cycleWeek,
            recommendation = "Faza intensyfikacji (${cycleWeek - 3}/3): niższe volume, RPE 8-9. " +
                "Cięższe ciężary, krótsze serie, dłuższe odpoczynki."
        )
        else -> TrainingPhaseStatus(
            phase = TrainingPhase.NEEDS_DELOAD,
            weeksSinceLastDeload = cycleWeek,
            recommendation = "Czas na deload."
        )
    }
}

/** Helper budujący sekcję promptu z fazą cyklu. */
object TrainingPhasePromptHelper {
    fun toPromptSection(status: TrainingPhaseStatus): String = buildString {
        if (status.phase == TrainingPhase.NO_DATA) return@buildString
        append("\n=== FAZA CYKLU TRENINGOWEGO (deterministyczne) ===\n")
        append("Aktualna faza: **${status.polishLabel}**\n")
        append("${status.recommendation}\n")
        when (status.phase) {
            TrainingPhase.ACCUMULATION -> {
                append("Twoja rola: gdy generujesz/poprawiasz plan, dobierz volume **wyższe** ")
                append("(top zakres normy z PlanAuditEngine), reps 8-12, RPE 7-8, ")
                append("pauzy 90-120s. Skup się na wzroście tonażu tygodniowego.\n")
            }
            TrainingPhase.INTENSIFICATION -> {
                append("Twoja rola: zmniejsz volume o ~20%, zwiększ obciążenia. Reps 5-8, RPE 8-9, ")
                append("pauzy 180-240s na compoundach. Mniej setów, ale cięższe.\n")
            }
            TrainingPhase.NEEDS_DELOAD, TrainingPhase.DELOAD -> {
                append("Twoja rola: jeśli generujesz plan na **bieżący tydzień** — wygeneruj ")
                append("**tydzień deloadu**: redukcja volume o 30%, obciążenie -10% PR, RPE ≤7, ")
                append("krótsze sesje. Cel = regeneracja CNS, nie progres. ")
                append("Po nim wracaj do akumulacji.\n")
            }
            TrainingPhase.NO_DATA -> {}
        }
    }
}
