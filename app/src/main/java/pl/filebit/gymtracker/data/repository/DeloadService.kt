package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.util.DeloadRecommendation
import pl.filebit.gymtracker.util.DeloadSeverity
import pl.filebit.gymtracker.util.detectDeloadNeed
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
    private val prefs: DeloadPreferences
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
        val dismissedAt = prefs.dismissedAtMs()
        if (dismissedAt > 0 && now - dismissedAt < DISMISS_GRACE_DAYS * 24 * 3600 * 1000) {
            return DeloadCardState.None
        }
        val rec = checkRecommendation()
        return if (rec != null) DeloadCardState.Suggestion(rec) else DeloadCardState.None
    }

    /** Pure detection — używana też przez legacy code. */
    suspend fun check(): DeloadRecommendation? = checkRecommendation()

    private suspend fun checkRecommendation(): DeloadRecommendation? {
        val now = System.currentTimeMillis()
        val ms14d = 14L * 24 * 60 * 60 * 1000
        val ms35d = 35L * 24 * 60 * 60 * 1000

        val finishedWorkouts = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val sessions35d = finishedWorkouts.count { it.startedAt >= now - ms35d }
        val recent14dWorkouts = finishedWorkouts.filter { it.startedAt >= now - ms14d }
        val rpeValues = recent14dWorkouts.flatMap { w ->
            setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .mapNotNull { it.rpe }
        }
        val avgRpe14d = rpeValues.takeIf { it.isNotEmpty() }?.let { it.average() }
        val lastWorkoutId = finishedWorkouts.maxByOrNull { it.startedAt }?.id
        val stagnationCount = lastWorkoutId?.let { id ->
            statsRepo.detectStagnation(id, threshold = 3).size
        } ?: 0

        return detectDeloadNeed(
            avgRpe14d = avgRpe14d,
            sessionsLast35d = sessions35d,
            stagnationCount = stagnationCount
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

    fun dismiss() {
        prefs.setDismissedNow()
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
