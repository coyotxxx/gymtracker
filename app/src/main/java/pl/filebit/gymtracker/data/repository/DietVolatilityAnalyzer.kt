package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.AdherenceLogDao
import pl.filebit.gymtracker.data.entity.AdherenceLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Wykrywa **mocne wahania kcal** w ostatnich 7 dniach diety.
 *
 * Cykl 'cheat day + kompensacja' (np. 5730 kcal → 491 kcal w ciągu 5 dni) jest
 * typowy dla popsutej diety i prowadzi do:
 *  - utraty masy mięśniowej (drastyczne niedojadanie po cheat),
 *  - problemów hormonalnych (leptyna, kortyzol),
 *  - frustracji i porzucenia planu.
 *
 * Detekcja jest **deterministyczna**: kryterium binarne (jest cheat ORAZ underfeeding
 * w jednym oknie 7 dni), bez progowych % stddev. Jasna logika, prosty komunikat.
 *
 * Wynik: pokazywany jako karta na DietScreen (alert info dla usera).
 */
@Singleton
class DietVolatilityAnalyzer @Inject constructor(
    private val adherenceDao: AdherenceLogDao
) {
    /**
     * Analizuje ostatnie 7 dni adherence. Zwraca raport tylko gdy faktycznie
     * są wahania (cheat ORAZ underfeeding). Null = brak wahań / za mało danych.
     */
    suspend fun analyze(): DietVolatilityReport? {
        val logs = adherenceDao.getRecent(7).filter {
            it.actualKcal > 0 && it.targetKcal > 0
        }
        // Wymagamy ≥4 dni żeby ufać wzorcowi (1-3 dni to za mało statystyki)
        if (logs.size < 4) return null

        val cheatDays = logs.filter { it.actualKcal > it.targetKcal * 1.5 }
        val underDays = logs.filter { it.actualKcal < it.targetKcal * 0.6 }
        // Wahania = jednoczesna obecność CHEAT i UNDER w 7 dni
        if (cheatDays.isEmpty() || underDays.isEmpty()) return null

        val worstCheat = cheatDays.maxBy { it.actualKcal.toDouble() / it.targetKcal }
        val worstUnder = underDays.minBy { it.actualKcal.toDouble() / it.targetKcal }

        val avgKcal = logs.map { it.actualKcal }.average()
        val stdDev = logs.map { (it.actualKcal - avgKcal) * (it.actualKcal - avgKcal) }.average()
            .let { sqrt(it) }
        val coefficientOfVariationPct = (stdDev / avgKcal * 100).toInt()

        return DietVolatilityReport(
            daysAnalyzed = logs.size,
            cheatDate = worstCheat.dateMs,
            cheatActualKcal = worstCheat.actualKcal,
            cheatTargetKcal = worstCheat.targetKcal,
            cheatPctOfTarget = (worstCheat.actualKcal * 100 / worstCheat.targetKcal),
            underDate = worstUnder.dateMs,
            underActualKcal = worstUnder.actualKcal,
            underTargetKcal = worstUnder.targetKcal,
            underPctOfTarget = (worstUnder.actualKcal * 100 / worstUnder.targetKcal),
            avgKcal = avgKcal.toInt(),
            coefficientOfVariationPct = coefficientOfVariationPct
        )
    }
}

data class DietVolatilityReport(
    val daysAnalyzed: Int,
    val cheatDate: Long,
    val cheatActualKcal: Int,
    val cheatTargetKcal: Int,
    val cheatPctOfTarget: Int,
    val underDate: Long,
    val underActualKcal: Int,
    val underTargetKcal: Int,
    val underPctOfTarget: Int,
    val avgKcal: Int,
    /** % zmienności (stddev / avg × 100). >30 = bardzo wysokie wahania. */
    val coefficientOfVariationPct: Int
) {
    /** Konkretny komunikat dla usera. */
    fun toUserMessage(): String {
        val fmt = SimpleDateFormat("d MMM", Locale("pl", "PL"))
        return "Wahania kcal w ostatnich $daysAnalyzed dniach:\n" +
            "• Najwięcej ${fmt.format(java.util.Date(cheatDate))}: $cheatActualKcal kcal ($cheatPctOfTarget% celu)\n" +
            "• Najmniej ${fmt.format(java.util.Date(underDate))}: $underActualKcal kcal ($underPctOfTarget% celu)\n\n" +
            "Cykl cheat → niedojadanie prowadzi do utraty mięśni i problemów hormonalnych. " +
            "Lepiej trzymać 90-110% celu codziennie niż 'idealne' i 'zły' dzień. " +
            "Spróbuj wyrównać kcal na 14 dni — będziesz miał lepsze samopoczucie i progres."
    }
}
