package pl.filebit.gymtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Tygodniowy budżet kcal — alternatywa dla sztywnego dziennego celu.
 *
 * Filozofia: jeden gorszy dzień nie zepsuje tygodnia. System redystrybuuje
 * deficyt/nadwyżkę na pozostałe dni (ALE nigdy poniżej 0.85 × dailyGoal).
 */
data class WeeklyBudgetSummary(
    /** Liczba dni w tygodniu z zalogowanymi posiłkami. */
    val daysLogged: Int,
    /** Cel kcal × 7. */
    val weeklyTargetKcal: Int,
    /** Suma kcal zjedzonych w tygodniu. */
    val weeklyActualKcal: Int,
    /** target - actual. Dodatni = zjedzono mniej (zapas), ujemny = za dużo. */
    val bankBalanceKcal: Int,
    /** Adherence tygodnia w procentach (actual/target × 100). */
    val weeklyAdherencePct: Int,
    /** Liczba dni pozostałych w tygodniu (z dziennym celem do realizacji). */
    val daysRemaining: Int,
    /** Sugerowany cel na pozostałe dni (z bezpiecznym minimum). */
    val suggestedRemainingDailyKcal: Int,
    /** Floor minimalny dla suggestedRemainingDailyKcal (0.85 × dailyGoal). */
    val safetyFloorKcal: Int,
    /** Komunikat dla usera (po ludzku). */
    val message: String
)

@Singleton
class WeeklyBudgetCalculator @Inject constructor() {

    /**
     * @param daysLoggedKcalSum suma kcal zalogowanych w danym tygodniu
     * @param dailyGoalKcal cel dzienny
     * @param daysLogged ile dni w tygodniu user zalogował posiłki
     * @param daysRemainingInWeek ile dni do końca tygodnia (włącznie z dzisiajszym)
     */
    fun compute(
        daysLoggedKcalSum: Int,
        dailyGoalKcal: Int,
        daysLogged: Int,
        daysRemainingInWeek: Int
    ): WeeklyBudgetSummary {
        val weeklyTarget = dailyGoalKcal * 7
        val balance = weeklyTarget - daysLoggedKcalSum
        val adherence = if (weeklyTarget > 0) (daysLoggedKcalSum * 100 / weeklyTarget) else 0
        val safetyFloor = (dailyGoalKcal * 0.85).toInt()

        val suggestedDaily = if (daysRemainingInWeek > 0) {
            val raw = balance / daysRemainingInWeek
            // Nigdy nie zalecaj poniżej safety floor
            max(raw, safetyFloor)
        } else dailyGoalKcal

        val message = when {
            daysLogged < 2 -> "Loguj dalej — za mało dni żeby ocenić tydzień."
            balance < -dailyGoalKcal * 1.5 -> {
                // Bardzo duże przekroczenie (>1.5 dnia) — nie nadrabiamy
                "Zjedzono o ${-balance} kcal więcej niż cel tygodniowy. Spokojnie, nie nadrabiamy ciężko — wracamy do planu jutro. Kontynuuj normalnie."
            }
            balance < 0 -> {
                "Tydzień ${-balance} kcal nad celem. Pozostałe dni: ${suggestedDaily} kcal/dzień (bezpieczny floor: $safetyFloor)."
            }
            balance > dailyGoalKcal -> {
                "Tydzień ${balance} kcal pod celem. Możesz zjeść więcej w pozostałe dni: do ${suggestedDaily} kcal/dzień."
            }
            else -> {
                "Tydzień idzie zgodnie z planem (adherence ${adherence}%)."
            }
        }

        return WeeklyBudgetSummary(
            daysLogged = daysLogged,
            weeklyTargetKcal = weeklyTarget,
            weeklyActualKcal = daysLoggedKcalSum,
            bankBalanceKcal = balance,
            weeklyAdherencePct = adherence,
            daysRemaining = daysRemainingInWeek,
            suggestedRemainingDailyKcal = suggestedDaily,
            safetyFloorKcal = safetyFloor,
            message = message
        )
    }
}
