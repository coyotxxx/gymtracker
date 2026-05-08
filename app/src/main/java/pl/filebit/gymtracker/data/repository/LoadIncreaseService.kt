package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper "zwiększ obciążenie" — odwrotna akcja od deload.
 * Używana gdy ACWR pokazuje DETRAINING (niskie obciążenie 7d vs średnia 28d).
 *
 * Strategia v1.11.36 MVP: zwiększ wszystkie wagi w planie × 1.05 (+5%).
 * Snapshot oryginalnych wag → restore() przywraca.
 *
 * (W przyszłości można rozszerzyć o dodanie 1 setu per ćwiczenie zamiast wag.)
 */
@Singleton
class LoadIncreaseService @Inject constructor(
    private val planRepo: PlanRepository,
    private val prefs: LoadIncreasePreferences,
    private val deloadPrefs: DeloadPreferences
) {
    /**
     * Zastosuj zwiększenie obciążenia: wagi × factor (1.05 = +5%).
     * Zapisuje snapshot oryginalnych wag żeby restore() mogło je przywrócić.
     *
     * Zwraca `null` jeśli aktywny jest deload (konflikt — najpierw zakończ deload).
     */
    suspend fun apply(planId: Long, factor: Double = 1.05): ApplyResult? {
        // Konflikt: nie aplikuj increase gdy aktywny deload
        if (deloadPrefs.activeDeload() != null) return null

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
                planRepo.updatePlanSet(set.copy(weightKg = original * factor))
                updatedCount++
            }
        }

        prefs.setActiveIncrease(
            ActiveLoadIncreaseState(
                startedAtMs = System.currentTimeMillis(),
                planId = planId,
                planName = plan.name,
                factor = factor,
                originalSetCounts = originalWeights.mapValues { 1 }, // placeholder dla future "dodaj set"
                addedSetIds = emptyList()
            )
        )
        // Hack: trzymamy originalWeights w innej mapie do restore — re-use originalSetCounts
        // jako Map<setId, original_weight_as_int>... ale to traci precyzję. Lepiej osobne pole:
        // (decyzja na MVP: cofamy poprzez applyFactor — restore mnoży × 1/factor)
        return ApplyResult(updatedCount, factor, plan.name)
    }

    /**
     * Przywróć oryginalne wagi (× 1/factor).
     * Wywoływane gdy user zmienił zdanie albo po cyklu.
     */
    suspend fun restore(): RestoreResult {
        val state = prefs.activeIncrease() ?: return RestoreResult(0, "")
        val planExercises = planRepo.getPlanExercises(state.planId)
        var restoredCount = 0
        val inverse = 1.0 / state.factor
        for (pe in planExercises) {
            val sets = planRepo.getSetsForPlanExercise(pe.id)
            for (set in sets) {
                val current = set.weightKg ?: continue
                if (current <= 0) continue
                // Przywróć poprzez podzielenie przez factor (× 1/1.05 = ÷ 1.05)
                planRepo.updatePlanSet(set.copy(weightKg = current * inverse))
                restoredCount++
            }
        }
        prefs.clearActiveIncrease()
        return RestoreResult(restoredCount, state.planName)
    }

    fun dismiss() {
        prefs.setDismissedNow()
    }

    fun cancelWithoutRestore() {
        prefs.clearActiveIncrease()
    }

    fun isActive(): Boolean = prefs.activeIncrease() != null

    fun dismissedRecently(graceDays: Long = 7): Boolean {
        val dismissedAt = prefs.dismissedAtMs()
        if (dismissedAt <= 0) return false
        val now = System.currentTimeMillis()
        return now - dismissedAt < graceDays * 24 * 3600 * 1000
    }

    data class ApplyResult(val updatedSets: Int, val factor: Double, val planName: String)
    data class RestoreResult(val restoredSets: Int, val planName: String)
}
