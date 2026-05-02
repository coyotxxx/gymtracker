package pl.filebit.gymtracker.util

/**
 * Detekcja potrzeby deloadu (lżejszego tygodnia regeneracyjnego).
 *
 * Reguły bazują na literaturze treningowej (RP / Israetel / MASS):
 *  - 4-6 tygodni intensywnego treningu = naturalny cykl, na końcu deload
 *  - Średnie RPE rosnące z tygodnia na tydzień = oznaka kumulującego zmęczenia
 *  - Stagnacja na wagach = sygnał że organizm potrzebuje regeneracji
 *
 * Pure function — nie zależy od DB. Wrapper w DeloadService dostarcza dane.
 *
 * @param avgRpe14d średnie RPE ze wszystkich roboczych setów w ostatnich 14 dniach
 * @param sessionsLast35d liczba ukończonych treningów w ostatnich 35 dniach
 * @param stagnationCount liczba ćwiczeń ze stagnacją 3+ treningów
 * @return rekomendacja deloadu (poziom + uzasadnienie) lub null gdy nie trzeba
 */
fun detectDeloadNeed(
    avgRpe14d: Double?,
    sessionsLast35d: Int,
    stagnationCount: Int = 0
): DeloadRecommendation? {
    // Reguła 1: HIGH — ciężkie 14 dni + dużo sesji w cyklu
    if (avgRpe14d != null && avgRpe14d >= 8.5 && sessionsLast35d >= 15) {
        return DeloadRecommendation(
            severity = DeloadSeverity.HIGH,
            reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} " +
                "przy $sessionsLast35d sesjach w 35 dni. Mocno zakumulowane zmęczenie — czas na deload."
        )
    }
    // Reguła 2: MED — wysokie RPE niezależnie od liczby sesji
    if (avgRpe14d != null && avgRpe14d >= 9.0) {
        return DeloadRecommendation(
            severity = DeloadSeverity.MED,
            reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} (bardzo wysokie). " +
                "Zalecany lżejszy tydzień (waga −10%, reps tak samo)."
        )
    }
    // Reguła 3: MED — wiele stagnacji jednocześnie
    if (stagnationCount >= 3) {
        return DeloadRecommendation(
            severity = DeloadSeverity.MED,
            reason = "Stagnacja na $stagnationCount ćwiczeniach jednocześnie. " +
                "Zamiast forsować — odpuść tydzień (waga −10%) i wróć z nowymi siłami."
        )
    }
    // Reguła 4: LOW — długi cykl bez przerwy
    if (sessionsLast35d >= 18 && avgRpe14d != null && avgRpe14d >= 7.5) {
        return DeloadRecommendation(
            severity = DeloadSeverity.LOW,
            reason = "$sessionsLast35d sesji w 35 dniach z RPE ${"%.1f".format(avgRpe14d)}. " +
                "Cykl 5-6 tygodni dobiega końca — rozważ deload."
        )
    }
    return null
}

data class DeloadRecommendation(
    val severity: DeloadSeverity,
    val reason: String
)

enum class DeloadSeverity { LOW, MED, HIGH }
