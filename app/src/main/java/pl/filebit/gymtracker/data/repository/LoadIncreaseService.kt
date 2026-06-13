package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.DiagnosticCategory
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
    private val deloadPrefs: DeloadPreferences,
    // v2.25.0: nullable-default — Hilt wstrzykuje realny logger, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    /**
     * Zastosuj zwiększenie obciążenia: wagi × factor (1.05 = +5%).
     * Zapisuje snapshot oryginalnych wag żeby restore() mogło je przywrócić.
     *
     * Zwraca `null` jeśli aktywny jest deload (konflikt — najpierw zakończ deload).
     */
    suspend fun apply(planId: Long, factor: Double = 1.05): ApplyResult? {
        // Konflikt: nie aplikuj increase gdy aktywny deload
        if (deloadPrefs.activeDeload() != null) {
            diag?.warn(DiagnosticCategory.DETECTOR, "LoadIncreaseService", "increase_blocked_deload",
                "Nie zwiększono obciążenia — aktywny deload (konflikt)", dataJson = """{"planId":$planId}""")
            return null
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
                // v1.20.3: zaokrąglenie do 2.5kg (standardowe talerze) zamiast 80×1.05=84.0
                val newWeight = roundToPlateStep(original * factor)
                planRepo.updatePlanSet(set.copy(weightKg = newWeight))
                updatedCount++
            }
        }

        prefs.setActiveIncrease(
            ActiveLoadIncreaseState(
                startedAtMs = System.currentTimeMillis(),
                planId = planId,
                planName = plan.name,
                factor = factor,
                originalWeights = originalWeights
            )
        )
        diag?.info(DiagnosticCategory.DETECTOR, "LoadIncreaseService", "increase_applied",
            "Zwiększono obciążenie (factor $factor) w '${plan.name}' — $updatedCount serii",
            dataJson = """{"planId":$planId,"factor":$factor,"updatedSets":$updatedCount}""", success = true)
        return ApplyResult(updatedCount, factor, plan.name)
    }

    /**
     * Przywróć oryginalne wagi z snapshot (NIE przez podzielenie, żeby nie zepsuć
     * gdy user manualnie zmienił wagę po apply).
     */
    suspend fun restore(): RestoreResult {
        val state = prefs.activeIncrease() ?: return RestoreResult(0, "")
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
        prefs.clearActiveIncrease()
        diag?.info(DiagnosticCategory.DETECTOR, "LoadIncreaseService", "increase_restored",
            "Przywrócono wagi po zwiększeniu obciążenia '${state.planName}' — $restoredCount serii",
            dataJson = """{"planId":${state.planId},"restoredSets":$restoredCount}""", success = true)
        return RestoreResult(restoredCount, state.planName)
    }

    fun dismiss() {
        prefs.setDismissedNow()
        diag?.info(DiagnosticCategory.USER_ACTION, "LoadIncreaseService", "increase_dismissed",
            "User zamknął sugestię zwiększenia obciążenia")
    }

    fun cancelWithoutRestore() {
        prefs.clearActiveIncrease()
        diag?.info(DiagnosticCategory.DETECTOR, "LoadIncreaseService", "increase_cancelled",
            "Anulowano zwiększenie obciążenia bez przywracania wag")
    }

    fun isActive(): Boolean = prefs.activeIncrease() != null

    /**
     * v1.20.3 — zaokrąglenie wagi do najbliższych 2.5kg (standardowe talerze siłowni).
     * Dla <2.5kg (lekkie hantle) — krok 0.5kg.
     */
    private fun roundToPlateStep(kg: Double): Double {
        if (kg < 2.5) return (kotlin.math.round(kg * 2) / 2)
        return kotlin.math.round(kg / 2.5) * 2.5
    }

    fun dismissedRecently(graceDays: Long = 7): Boolean {
        val dismissedAt = prefs.dismissedAtMs()
        if (dismissedAt <= 0) return false
        val now = System.currentTimeMillis()
        return now - dismissedAt < graceDays * 24 * 3600 * 1000
    }

    data class ApplyResult(val updatedSets: Int, val factor: Double, val planName: String)
    data class RestoreResult(val restoredSets: Int, val planName: String)
}
