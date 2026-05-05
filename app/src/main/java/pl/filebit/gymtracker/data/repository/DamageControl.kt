package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Wynik DamageControl po nieplanowanym jedzeniu.
 *
 * Filozofia: NIE karzemy usera, NIE każemy głodzić — proporcjonalnie redukujemy
 * pozostałe sloty, ALE nigdy poniżej safety floor (0.85 × dailyGoal).
 */
data class DamageControlResult(
    val unplannedKcal: Int,
    val originalDailyGoal: Int,
    val alreadyConsumedKcal: Int,
    val remainingSlots: Int,
    val newKcalPerRemainingSlot: Int,
    val safetyFloorKcal: Int,
    val isExtremeOvereating: Boolean,
    val message: String
)

@Singleton
class DamageControl @Inject constructor() {

    /**
     * @param unplannedKcal kcal z nieplanowanego posiłku (np. kebab 700)
     * @param dailyGoalKcal cel dzienny
     * @param alreadyConsumedKcalIncludingUnplanned suma kcal zjedzonych dziś z nieplanowanym
     * @param remainingSlots ile slotów zostało dziś do zjedzenia
     * @return rekomendacja
     */
    fun recommend(
        unplannedKcal: Int,
        dailyGoalKcal: Int,
        alreadyConsumedKcalIncludingUnplanned: Int,
        remainingSlots: Int
    ): DamageControlResult {
        val safetyFloor = (dailyGoalKcal * 0.85).toInt()
        val safetyFloorPerSlot = if (remainingSlots > 0) safetyFloor / remainingSlots else 0
        val budgetLeft = dailyGoalKcal - alreadyConsumedKcalIncludingUnplanned
        val isExtreme = budgetLeft < -dailyGoalKcal / 2  // przekroczenie >50% celu

        val newPerSlot = if (remainingSlots > 0) {
            val raw = budgetLeft / remainingSlots
            // Floor: pozostałe sloty NIE niżej niż safety
            max(raw, safetyFloorPerSlot)
        } else 0

        val msg = when {
            remainingSlots == 0 && budgetLeft < -300 -> {
                "Zjadłeś ${alreadyConsumedKcalIncludingUnplanned} kcal vs cel ${dailyGoalKcal}. " +
                    "Przekroczenie ${-budgetLeft} kcal. Spokojnie — jeden dzień nie zepsuje tygodnia. Jutro wracamy do planu."
            }
            isExtreme -> {
                "Zjadłeś +${-budgetLeft} kcal nad cel. NIE głoduj — to się odbije podjadaniem. " +
                    "Spokojnie. Jutro wracamy do bazy. Pozostałe sloty: ${newPerSlot} kcal każdy (safety floor)."
            }
            budgetLeft > 0 -> {
                "Po nieplanowanym posiłku zostało ${budgetLeft} kcal na ${remainingSlots} slot${if (remainingSlots > 1) "ów" else ""}. " +
                    "Sugerowane: ${newPerSlot} kcal/slot. Lekkie posiłki wysokobiałkowe."
            }
            else -> {
                "Limit dnia osiągnięty. Pozostałe sloty: ${newPerSlot} kcal każdy (safety minimum). " +
                    "Wybierz lekkie, wysokobiałkowe (twaróg, kurczak, warzywa)."
            }
        }

        return DamageControlResult(
            unplannedKcal = unplannedKcal,
            originalDailyGoal = dailyGoalKcal,
            alreadyConsumedKcal = alreadyConsumedKcalIncludingUnplanned,
            remainingSlots = remainingSlots,
            newKcalPerRemainingSlot = newPerSlot,
            safetyFloorKcal = safetyFloor,
            isExtremeOvereating = isExtreme,
            message = msg
        )
    }
}
