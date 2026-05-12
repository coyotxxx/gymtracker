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
 * @param sessionsLast14d liczba ukończonych treningów w ostatnich 14 dniach
 *   (gdy <3, RPE-based reguły nie odpalają — za mało próbek aby ufać średniej)
 * @param sessionsLast35d liczba ukończonych treningów w ostatnich 35 dniach
 * @param stagnationCount liczba ćwiczeń ze stagnacją 3+ treningów
 * @return rekomendacja deloadu (poziom + uzasadnienie) lub null gdy nie trzeba
 */
fun detectDeloadNeed(
    avgRpe14d: Double?,
    sessionsLast14d: Int,
    sessionsLast35d: Int,
    stagnationCount: Int = 0
): DeloadRecommendation? {
    // Minimum próbek aby ufać średniej RPE: 3 sesje w ciągu 14 dni
    val hasReliableRpe = sessionsLast14d >= 3 && avgRpe14d != null

    // Reguła 1: HIGH — ciężkie 14 dni + dużo sesji w cyklu
    if (hasReliableRpe && avgRpe14d!! >= 8.5 && sessionsLast35d >= 15) {
        return DeloadRecommendation(
            severity = DeloadSeverity.HIGH,
            reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} " +
                "przy $sessionsLast35d sesjach w 35 dni. Mocno zakumulowane zmęczenie — czas na deload."
        )
    }
    // Reguła 2: MED — wysokie RPE niezależnie od liczby sesji (ale wymagaj N=3)
    if (hasReliableRpe && avgRpe14d!! >= 9.0) {
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
    if (hasReliableRpe && sessionsLast35d >= 18 && avgRpe14d!! >= 7.5) {
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

/**
 * Detekcja powrotu po przerwie treningowej.
 *
 * Wykrywa sytuację: user trenował regularnie, potem przerwa ≥7 dni, teraz wraca.
 * To NIE jest deload — to potrzeba ostrożnego restartu (organism out of training shape,
 * fascia/CNS odzwyczajone od bodźca, ryzyko kontuzji przy próbie "kontynuować od ostatniej sesji").
 *
 * Logika ma PRIORYTET wyższy niż deload — pojedynczy workout z RPE 10
 * po przerwie 14 dni to nie "przetrenowanie", tylko szok powrotu.
 *
 * @param sessionsLast14d liczba treningów w ostatnich 14 dniach
 * @param sessionsLast35d liczba treningów w ostatnich 35 dniach (włącznie z 14d)
 * @param daysSinceLastWorkout ile dni temu był ostatni workout
 * @return rekomendacja powrotu lub null gdy nie wykryto przerwy
 */
fun detectReturnAfterBreak(
    sessionsLast14d: Int,
    sessionsLast35d: Int,
    daysSinceLastWorkout: Int?
): ReturnAfterBreakRecommendation? {
    // Wymagamy historii regularnego treningu (przynajmniej 6 sesji w 35 dni przed przerwą)
    val sessionsBeforeRecent = sessionsLast35d - sessionsLast14d
    if (sessionsBeforeRecent < 6) return null  // user nie był regularny, nie ma "powrotu"

    // Wymagamy przerwy: aktywnie <2 sesje w 14 dni
    if (sessionsLast14d > 2) return null

    // Wymagamy że user właśnie wrócił (1 trening w ostatnich 7 dniach)
    // lub że ma >= 7 dni przerwy ale jeszcze nie trenował
    val daysSince = daysSinceLastWorkout ?: return null

    return when {
        // Aktualna przerwa >14 dni i NIE było żadnego treningu w 14d
        daysSince >= 14 && sessionsLast14d == 0 -> ReturnAfterBreakRecommendation(
            breakDays = daysSince,
            severity = ReturnSeverity.LONG_BREAK,
            reason = "Wykryto przerwę $daysSince dni od ostatniego treningu. " +
                "Po dłuższej przerwie zacznij od 75-80% poprzednich wag i stopniowo wracaj " +
                "(1-2 tygodnie). Twoje ciało potrzebuje czasu na readaptację."
        )
        // Wróciłeś świeżo: 1 trening w 14d, przerwa była 7-14 dni
        sessionsLast14d in 1..2 && daysSince <= 7 -> ReturnAfterBreakRecommendation(
            breakDays = (14 - sessionsLast14d).coerceAtLeast(7),  // estymata przerwy przed powrotem
            severity = ReturnSeverity.SHORT_BREAK,
            reason = "Wróciłeś po przerwie. Po >7 dniach bez treningu obniż wagi o ~15% " +
                "na 1-2 tygodnie — to NIE deload (przetrenowanie), tylko ostrożny restart. " +
                "Wysokie RPE po powrocie jest normalne, nie oznacza że jesteś słaby."
        )
        else -> null
    }
}

data class ReturnAfterBreakRecommendation(
    val breakDays: Int,
    val severity: ReturnSeverity,
    val reason: String
)

enum class ReturnSeverity {
    /** 7-13 dni przerwy — krótki restart (~-15% na 1 tydz). */
    SHORT_BREAK,
    /** ≥14 dni przerwy — długi restart (~-20-25% na 2 tyg). */
    LONG_BREAK
}

/**
 * Detekcja aktywnej kontuzji.
 *
 * Wykrywa: user notował painArea w ostatnich 14 dniach (1+ workout z bólem).
 * Priorytet WYŻSZY niż deload — ból to większy sygnał niż wysokie RPE
 * (high RPE z bólem to nie przetrenowanie, tylko kompensacja kontuzji).
 *
 * @param workoutsLast14d lista workoutów z 14d (z painArea / wellbeingRating)
 * @return rekomendacja kontuzji lub null
 */
data class WorkoutPainSnapshot(
    val daysAgo: Int,
    val painArea: String?,
    val wellbeingRating: Int?
)

fun detectActiveInjury(workoutsLast14d: List<WorkoutPainSnapshot>): ActiveInjuryRecommendation? {
    val withPain = workoutsLast14d.filter { !it.painArea.isNullOrBlank() }
    if (withPain.isEmpty()) return null

    // Grupuj po partii — pokaż partię z największą liczbą notowań
    val byArea = withPain.groupBy { it.painArea!! }
    val (area, occurrences) = byArea.maxBy { it.value.size }
    val daysSinceLast = occurrences.minOf { it.daysAgo }
    val lowestWellbeing = occurrences.mapNotNull { it.wellbeingRating }.minOrNull()

    val severity = when {
        occurrences.size >= 2 || (lowestWellbeing != null && lowestWellbeing <= 2) -> InjurySeverity.PERSISTENT
        else -> InjurySeverity.FLAG
    }

    val reason = when (severity) {
        InjurySeverity.PERSISTENT ->
            "Notowałeś ból w partii ${area.toDisplay()} w ${occurrences.size} ostatnich treningach " +
                "(najświeższy: $daysSinceLast dni temu" +
                (lowestWellbeing?.let { ", wellbeing $it/5" } ?: "") + "). " +
                "Zanim pójdziesz dalej — odpuść ćwiczenia angażujące tę partię na 5-7 dni i rozważ konsultację z fizjoterapeutą. " +
                "To NIE jest przetrenowanie — to sygnał kontuzji."
        InjurySeverity.FLAG ->
            "Notowałeś ból w partii ${area.toDisplay()} ($daysSinceLast dni temu). " +
                "Obserwuj — jeśli ból wraca w kolejnym treningu, zmniejsz obciążenie tej partii o 30-50% " +
                "lub zastąp ćwiczenia alternatywami bez bólu."
    }

    return ActiveInjuryRecommendation(
        painArea = area,
        occurrences = occurrences.size,
        daysSinceLast = daysSinceLast,
        severity = severity,
        reason = reason
    )
}

private fun String.toDisplay(): String = when (this.uppercase()) {
    "CHEST" -> "klatka"
    "BACK" -> "plecy"
    "LEGS" -> "nogi"
    "SHOULDERS" -> "barki"
    "ARMS" -> "ręce"
    "CORE" -> "core"
    "GLUTES" -> "pośladki"
    else -> this.lowercase()
}

data class ActiveInjuryRecommendation(
    val painArea: String,
    val occurrences: Int,
    val daysSinceLast: Int,
    val severity: InjurySeverity,
    val reason: String
)

enum class InjurySeverity {
    /** 1 notowanie + wellbeing OK — obserwuj. */
    FLAG,
    /** ≥2 notowania lub wellbeing ≤2 — odpuść / fizjoterapeuta. */
    PERSISTENT
}
