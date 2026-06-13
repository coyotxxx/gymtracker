package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.util.ActiveInjuryRecommendation
import pl.filebit.gymtracker.util.DeloadRecommendation
import pl.filebit.gymtracker.util.DeloadSeverity
import pl.filebit.gymtracker.util.MissedWorkoutRecommendation
import pl.filebit.gymtracker.util.ReturnAfterBreakRecommendation
import pl.filebit.gymtracker.util.WorkoutPainSnapshot
import pl.filebit.gymtracker.util.detectActiveInjury
import pl.filebit.gymtracker.util.detectDeloadNeed
import pl.filebit.gymtracker.util.detectMissedWorkouts
import pl.filebit.gymtracker.util.detectReturnAfterBreak
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val DISMISS_GRACE_DAYS = 7L
private const val DELOAD_LENGTH_DAYS = 7L

/** Stan kafla na Home — łączy detektor + storage. */
sealed class DeloadCardState {
    /** Aktywny deload — pokazujemy info "trwa X dni z 7" lub "skończył się — wrócić?". */
    data class Active(
        val state: ActiveDeloadState,
        val daysElapsed: Int,
        val daysRemaining: Int,
        val isFinished: Boolean
    ) : DeloadCardState()

    /** Sugestia deload — pokazujemy alert z buttonami Zastosuj/Wyjaśnij/Anuluj. */
    data class Suggestion(val recommendation: DeloadRecommendation) : DeloadCardState()

    /**
     * Wykryto powrót po przerwie — pokazujemy alert "POWRÓT PO PRZERWIE" zamiast deloadu.
     * Inna ikonografia i komunikat: NIE przetrenowanie, tylko ostrożny restart.
     */
    data class ReturnAfterBreak(val recommendation: ReturnAfterBreakRecommendation) : DeloadCardState()

    /**
     * Wykryto aktywną kontuzję (painArea w ostatnich workoutach).
     * Priorytet wyższy niż deload — ból to inny sygnał niż przetrenowanie.
     */
    data class ActiveInjury(val recommendation: ActiveInjuryRecommendation) : DeloadCardState()

    /**
     * v2.12.0: opuszczony(e) zaplanowany(e) trening(i) w ostatnim tygodniu.
     * Priorytet niższy niż ReturnAfterBreak (długa przerwa ma własny komunikat),
     * wyższy niż Suggestion (nie sugerujemy deloadu komuś kto i tak nie trenuje).
     */
    data class MissedWorkout(val recommendation: MissedWorkoutRecommendation) : DeloadCardState()

    /** Brak alertu — kafel ukryty. */
    object None : DeloadCardState()
}

/**
 * Wrapper pełen-cykl deloadu: detekcja → zastosuj → przypomnij wrócić → restore.
 * Pamięta o cyklach jak personalny trener (filozofia projektu).
 */
@Singleton
class DeloadService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val statsRepo: StatsRepository,
    private val planRepo: PlanRepository,
    private val prefs: DeloadPreferences,
    private val profileRepo: UserProfileRepository
) {
    /**
     * Aktualny stan kafla na Home — Active > Suggestion > None.
     * Active ma priorytet: jeśli deload aktywny, nie pokazujemy nowej sugestii.
     */
    suspend fun cardState(): DeloadCardState {
        prefs.activeDeload()?.let { state ->
            val now = System.currentTimeMillis()
            val daysElapsed = ((now - state.startedAtMs) / (24L * 3600 * 1000)).toInt()
            val daysRemaining = (DELOAD_LENGTH_DAYS - daysElapsed).coerceAtLeast(0L).toInt()
            return DeloadCardState.Active(
                state = state,
                daysElapsed = daysElapsed,
                daysRemaining = daysRemaining,
                isFinished = daysElapsed >= DELOAD_LENGTH_DAYS
            )
        }
        val now = System.currentTimeMillis()
        val graceMs = DISMISS_GRACE_DAYS * 24 * 3600 * 1000
        // PRIORYTET (od najwyższego):
        // 1. ActiveInjury — ból to inny sygnał niż przetrenowanie/przerwa
        // 2. ReturnAfterBreak — wysokie RPE po przerwie ≠ deload
        // 3. Suggestion (deload klasyczny)
        // Per-type dismiss (v1.24.0): X-owanie jednego alertu nie blokuje innych typów.
        checkActiveInjury()?.let {
            val d = prefs.dismissedAtMs(AlertType.ACTIVE_INJURY)
            if (d == 0L || now - d >= graceMs) return DeloadCardState.ActiveInjury(it)
        }
        checkReturnAfterBreak()?.let {
            val d = prefs.dismissedAtMs(AlertType.RETURN_AFTER_BREAK)
            if (d == 0L || now - d >= graceMs) return DeloadCardState.ReturnAfterBreak(it)
        }
        // v2.12.0: opuszczony zaplanowany trening — między powrotem-po-przerwie a deloadem.
        checkMissedWorkouts()?.let {
            val d = prefs.dismissedAtMs(AlertType.MISSED_WORKOUT)
            if (d == 0L || now - d >= graceMs) return DeloadCardState.MissedWorkout(it)
        }

        val rec = checkRecommendation()
        if (rec != null) {
            val d = prefs.dismissedAtMs(AlertType.DELOAD_SUGGESTION)
            if (d == 0L || now - d >= graceMs) return DeloadCardState.Suggestion(rec)
        }
        return DeloadCardState.None
    }

    /** Pure detection — używana też przez legacy code. */
    suspend fun check(): DeloadRecommendation? = checkRecommendation()

    private suspend fun checkRecommendation(): DeloadRecommendation? {
        val ctx = buildDetectionContext()
        return detectDeloadNeed(
            avgRpe14d = ctx.avgRpe14d,
            sessionsLast14d = ctx.sessions14d,
            sessionsLast35d = ctx.sessions35d,
            stagnationCount = ctx.stagnationCount,
            userWeightGoal = ctx.weightGoalType
        )
    }

    private suspend fun checkReturnAfterBreak(): ReturnAfterBreakRecommendation? {
        val ctx = buildDetectionContext()
        return detectReturnAfterBreak(
            sessionsLast14d = ctx.sessions14d,
            sessionsLast35d = ctx.sessions35d,
            daysSinceLastWorkout = ctx.daysSinceLastWorkout,
            sessionsLast70d = ctx.sessions70d
        )
    }

    private suspend fun checkActiveInjury(): ActiveInjuryRecommendation? {
        val now = System.currentTimeMillis()
        val ms14d = 14L * 24 * 60 * 60 * 1000
        val workouts14d = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= now - ms14d }
            .map {
                WorkoutPainSnapshot(
                    daysAgo = ((now - it.startedAt) / (24L * 3600 * 1000)).toInt(),
                    painArea = it.painArea,
                    wellbeingRating = it.wellbeingRating
                )
            }
        return detectActiveInjury(workouts14d)
    }

    /** Publiczny sygnał dla workera w tle (ProactiveAiCheckWorker). */
    suspend fun missedWorkoutSignal(): MissedWorkoutRecommendation? = checkMissedWorkouts()

    /**
     * v2.12.0: liczy opuszczone zaplanowane treningi w ostatnich 7 PEŁNYCH dniach
     * (bez dziś — dziś jeszcze możesz zatrenować). Źródło planu: AKTYWNY plan
     * (getActivePlan), nie getPlansForDay — inaczej liczylibyśmy dni z nieaktywnych planów.
     */
    private suspend fun checkMissedWorkouts(): MissedWorkoutRecommendation? {
        val activePlan = runCatching { planRepo.getActivePlan() }.getOrNull() ?: return null
        if (activePlan.daysOfWeek.isEmpty()) return null
        // plan bez ćwiczeń nie jest realnym treningiem — nie strasz
        val planExercises = runCatching { planRepo.getPlanExercises(activePlan.id) }.getOrNull().orEmpty()
        if (planExercises.isEmpty()) return null

        val now = System.currentTimeMillis()
        val finished = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val trainedDayStarts = finished.map { dayStartOf(it.startedAt) }.toSet()
        // v2.23.0 (K1 fix): nie licz dni sprzed utworzenia aktywnego planu — świeży plan
        // nie miał szansy być wykonany, inaczej alarmuje "opuściłeś N/N" od razu po utworzeniu.
        val planStart = dayStartOf(activePlan.createdAt)

        var planned = 0
        var missed = 0
        val cal = Calendar.getInstance()
        for (offset in 1..7) {                       // wczoraj..7 dni temu (bez dziś)
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            if (!activePlan.daysOfWeek.contains(isoDayOfWeek(cal))) continue
            if (dayStartOf(cal.timeInMillis) < planStart) continue   // dzień sprzed planu
            planned++
            if (!trainedDayStarts.contains(dayStartOf(cal.timeInMillis))) missed++
        }
        val daysSinceLast = finished.maxByOrNull { it.startedAt }?.let {
            ((now - it.startedAt) / (24L * 3600 * 1000)).toInt()
        }
        return detectMissedWorkouts(planned, missed, daysSinceLast)
    }

    private fun dayStartOf(ms: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun isoDayOfWeek(cal: Calendar): Int {
        val cd = cal.get(Calendar.DAY_OF_WEEK)
        return if (cd == Calendar.SUNDAY) 7 else cd - 1   // ISO: 1=Pn..7=Nd
    }

    private data class DetectionContext(
        val avgRpe14d: Double?,
        val sessions14d: Int,
        val sessions35d: Int,
        val sessions70d: Int,
        val stagnationCount: Int,
        val daysSinceLastWorkout: Int?,
        val weightGoalType: WeightGoalType?
    )

    private suspend fun buildDetectionContext(): DetectionContext {
        val now = System.currentTimeMillis()
        val ms14d = 14L * 24 * 60 * 60 * 1000
        val ms35d = 35L * 24 * 60 * 60 * 1000
        val ms70d = 70L * 24 * 60 * 60 * 1000

        val finishedWorkouts = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val sessions14d = finishedWorkouts.count { it.startedAt >= now - ms14d }
        val sessions35d = finishedWorkouts.count { it.startedAt >= now - ms35d }
        // v1.24.42: 70-dniowe okno wykrywa LONG_BREAK gdy user trenował 36-70 dni temu
        val sessions70d = finishedWorkouts.count { it.startedAt >= now - ms70d }
        val recent14dWorkouts = finishedWorkouts.filter { it.startedAt >= now - ms14d }
        val rpeValues = recent14dWorkouts.flatMap { w ->
            setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .mapNotNull { it.rpe }
        }
        val avgRpe14d = rpeValues.takeIf { it.isNotEmpty() }?.let { it.average() }
        val lastWorkout = finishedWorkouts.maxByOrNull { it.startedAt }
        val daysSinceLastWorkout = lastWorkout?.let {
            ((now - it.startedAt) / (24L * 3600 * 1000)).toInt()
        }
        val stagnationCount = lastWorkout?.id?.let { id ->
            statsRepo.detectStagnation(id, threshold = 3).size
        } ?: 0
        val weightGoalType = runCatching { profileRepo.get().weightGoalType }.getOrNull()

        return DetectionContext(
            avgRpe14d = avgRpe14d,
            sessions14d = sessions14d,
            sessions35d = sessions35d,
            sessions70d = sessions70d,
            stagnationCount = stagnationCount,
            daysSinceLastWorkout = daysSinceLastWorkout,
            weightGoalType = weightGoalType
        )
    }

    /**
     * Zastosuj deload do planu: zmniejsz wszystkie wagi × factor (0.9 lub 0.8),
     * zapisz snapshot oryginalnych wag żeby restore() mogło je przywrócić.
     *
     * v1.20.3 GUARD: jeśli już istnieje aktywny deload → NIE re-aplikuj.
     * Wcześniej powtórne kliknięcie "Zastosuj" mnożyło wagi factor² lub factor³
     * (np. 80kg × 0.81 × 0.81 × 0.80 = 41.99) i nadpisywało originalWeights (utrata snapshot).
     *
     * v1.20.3 ROUNDING: nowa waga zaokrąglana do najbliższych 2.5kg.
     */
    suspend fun apply(planId: Long, severity: DeloadSeverity): ApplyResult {
        // GUARD: nie pozwól na podwójne zastosowanie
        prefs.activeDeload()?.let { existing ->
            val planName = planRepo.getPlan(existing.planId)?.name ?: ""
            return ApplyResult(0, existing.factor, planName, alreadyActive = true)
        }

        val factor = when (severity) {
            DeloadSeverity.HIGH -> 0.80
            DeloadSeverity.MED -> 0.90
            DeloadSeverity.LOW -> 0.90
        }
        val plan = planRepo.getPlan(planId) ?: return ApplyResult(0, factor, "")
        val planExercises = planRepo.getPlanExercises(planId)
        val originalWeights = mutableMapOf<Long, Double>()
        var updatedCount = 0

        for (pe in planExercises) {
            val sets = planRepo.getSetsForPlanExercise(pe.id)
            for (set in sets) {
                val original = set.weightKg ?: continue
                if (original <= 0) continue
                originalWeights[set.id] = original
                // v1.20.3: zaokrąglenie do 2.5kg żeby uniknąć wag typu 41.99 w UI
                val newWeight = roundToPlateStep(original * factor)
                planRepo.updatePlanSet(set.copy(weightKg = newWeight))
                updatedCount++
            }
        }

        prefs.setActiveDeload(
            ActiveDeloadState(
                startedAtMs = System.currentTimeMillis(),
                planId = planId,
                planName = plan.name,
                factor = factor,
                originalWeights = originalWeights
            )
        )
        return ApplyResult(updatedCount, factor, plan.name)
    }

    /**
     * v1.20.3 — zaokrąglenie wagi do najbliższych 2.5kg (standardowe talerze siłowni).
     * 41.99 → 42.5, 26.244 → 27.5, 12.59 → 12.5.
     * Dla wag <2.5kg (np. hantle 1kg) — zaokrąglenie do 0.5kg.
     */
    private fun roundToPlateStep(kg: Double): Double {
        if (kg < 2.5) return (kotlin.math.round(kg * 2) / 2)  // 0.5kg step
        return kotlin.math.round(kg / 2.5) * 2.5
    }

    /**
     * Przywróć oryginalne wagi z snapshot. Wywoływane po zakończeniu cyklu deload (~7 dni).
     */
    suspend fun restore(): RestoreResult {
        val state = prefs.activeDeload() ?: return RestoreResult(0, "")
        val planExercises = planRepo.getPlanExercises(state.planId)
        var restoredCount = 0
        for (pe in planExercises) {
            val sets = planRepo.getSetsForPlanExercise(pe.id)
            for (set in sets) {
                val original = state.originalWeights[set.id] ?: continue
                planRepo.updatePlanSet(set.copy(weightKg = original))
                restoredCount++
            }
        }
        prefs.clearActiveDeload()
        return RestoreResult(restoredCount, state.planName)
    }

    /**
     * v1.20.3 — naprawa wag po cumulative apply bug.
     * Jeśli wykryto że current weights ≠ originalWeights × factor (z tolerancją 0.1kg)
     * → restore z snapshot + jednorazowy re-apply z zaokrągleniem.
     *
     * Zwraca true jeśli naprawiono.
     */
    suspend fun repairWeightsIfCorrupted(): Boolean {
        val state = prefs.activeDeload() ?: return false
        if (state.originalWeights.isEmpty()) return false

        val planExercises = planRepo.getPlanExercises(state.planId)
        var corruptedFound = false
        for (pe in planExercises) {
            val sets = planRepo.getSetsForPlanExercise(pe.id)
            for (set in sets) {
                val original = state.originalWeights[set.id] ?: continue
                val current = set.weightKg ?: continue
                val expected = roundToPlateStep(original * state.factor)
                if (kotlin.math.abs(current - expected) > 0.1) {
                    corruptedFound = true
                    break
                }
            }
            if (corruptedFound) break
        }

        if (!corruptedFound) return false

        // Restore + jednorazowy reapply z zaokrągleniem
        for (pe in planExercises) {
            val sets = planRepo.getSetsForPlanExercise(pe.id)
            for (set in sets) {
                val original = state.originalWeights[set.id] ?: continue
                val newWeight = roundToPlateStep(original * state.factor)
                planRepo.updatePlanSet(set.copy(weightKg = newWeight))
            }
        }
        return true
    }

    /** Legacy dismiss (tylko global flag). Używaj `dismiss(type)` — per-type. */
    fun dismiss() {
        prefs.setDismissedNow()
    }

    /** v1.24.0: per-type dismiss — zamknięcie jednego alertu nie blokuje innych. */
    fun dismiss(type: AlertType) {
        prefs.setDismissedNow(type)
    }

    /** Anuluj aktywny deload bez restore (np. user zmienił plan). */
    fun cancelWithoutRestore() {
        prefs.clearActiveDeload()
    }

    data class ApplyResult(
        val updatedSets: Int,
        val factor: Double,
        val planName: String,
        val alreadyActive: Boolean = false
    )
    data class RestoreResult(val restoredSets: Int, val planName: String)
}
