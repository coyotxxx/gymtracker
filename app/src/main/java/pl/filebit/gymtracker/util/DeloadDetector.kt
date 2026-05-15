package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.WeightGoalType

/**
 * Detekcja potrzeby deloadu (lżejszego tygodnia regeneracyjnego).
 *
 * Reguły bazują na literaturze treningowej (RP / Israetel / MASS):
 *  - 4-6 tygodni intensywnego treningu = naturalny cykl, na końcu deload
 *  - Średnie RPE rosnące z tygodnia na tydzień = oznaka kumulującego zmęczenia
 *  - Stagnacja na wagach = sygnał że organizm potrzebuje regeneracji
 *
 * v1.24.41 goal-aware: gdy user jest na CUT, wysokie RPE jest naturalne
 * (deficyt kaloryczny = niższa wydolność). Klasyczny deload (-10% wagi) nie
 * rozwiąże problemu — zmęczenie wynika z energetyki, nie objętości. Zamiast
 * tego algorytm sugeruje refeed/diet break: 1-2 dni na maintenance kcal,
 * bez zmiany wag treningowych.
 *
 * Pure function — nie zależy od DB. Wrapper w DeloadService dostarcza dane.
 *
 * @param avgRpe14d średnie RPE ze wszystkich roboczych setów w ostatnich 14 dniach
 * @param sessionsLast14d liczba ukończonych treningów w ostatnich 14 dniach
 *   (gdy <3, RPE-based reguły nie odpalają — za mało próbek aby ufać średniej)
 * @param sessionsLast35d liczba ukończonych treningów w ostatnich 35 dniach
 * @param stagnationCount liczba ćwiczeń ze stagnacją 3+ treningów
 * @param userWeightGoal cel wagowy z UserProfile — dla CUT przełącza reguły
 *   HIGH/MED na sugestię refeedu zamiast redukcji obciążenia.
 * @return rekomendacja deloadu (poziom + uzasadnienie) lub null gdy nie trzeba
 */
fun detectDeloadNeed(
    avgRpe14d: Double?,
    sessionsLast14d: Int,
    sessionsLast35d: Int,
    stagnationCount: Int = 0,
    userWeightGoal: WeightGoalType? = null
): DeloadRecommendation? {
    // Minimum próbek aby ufać średniej RPE: 3 sesje w ciągu 14 dni
    val hasReliableRpe = sessionsLast14d >= 3 && avgRpe14d != null
    val isCut = userWeightGoal == WeightGoalType.CUT

    // Reguła 1: HIGH — ciężkie 14 dni + dużo sesji w cyklu
    if (hasReliableRpe && avgRpe14d!! >= 8.5 && sessionsLast35d >= 15) {
        return if (isCut) {
            DeloadRecommendation(
                severity = DeloadSeverity.HIGH,
                reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} " +
                    "przy $sessionsLast35d sesjach. Jesteś na redukcji — wysokie RPE jest naturalne " +
                    "(deficyt = mniej energii). Zamiast obniżać wagi, daj sobie 1-2 dni refeedu " +
                    "(kcal na maintenance, więcej węgli). Trening zostaje bez zmian.",
                recommendsDietBreak = true
            )
        } else {
            DeloadRecommendation(
                severity = DeloadSeverity.HIGH,
                reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} " +
                    "przy $sessionsLast35d sesjach w 35 dni. Mocno zakumulowane zmęczenie — czas na deload."
            )
        }
    }
    // Reguła 2: MED — wysokie RPE niezależnie od liczby sesji (ale wymagaj N=3)
    if (hasReliableRpe && avgRpe14d!! >= 9.0) {
        return if (isCut) {
            DeloadRecommendation(
                severity = DeloadSeverity.MED,
                reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} (bardzo wysokie). " +
                    "Na redukcji to często sygnał wyczerpania glikogenu, nie przetrenowania. " +
                    "Zaplanuj refeed 1-2 dni na maintenance kcal — siła wróci bez zmiany wag.",
                recommendsDietBreak = true
            )
        } else {
            DeloadRecommendation(
                severity = DeloadSeverity.MED,
                reason = "Średnie RPE z ostatnich 14 dni: ${"%.1f".format(avgRpe14d)} (bardzo wysokie). " +
                    "Zalecany lżejszy tydzień (waga −10%, reps tak samo)."
            )
        }
    }
    // Reguła 3: MED — wiele stagnacji jednocześnie.
    // Guard sessionsLast35d >= 9 (B1): stagnacja → deload TYLKO gdy user ma
    // realną historię (≈3 tyg regularnego treningu). Świeży user z 3 treningami
    // ma 3 ćwiczenia "stagnujące" (3× ta sama waga to normalne wdrażanie, nie
    // utknięcie po progresji) — bez guarda dostawał fałszywy "DELOAD ZALECANY".
    if (stagnationCount >= 3 && sessionsLast35d >= 9) {
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
    val reason: String,
    /**
     * v1.24.41: dla usera na CUT klasyczny deload (-10% wagi) nie pomoże —
     * zmęczenie pochodzi z deficytu kalorycznego. Flaga sygnalizuje UI że
     * zamiast "Zastosuj redukcję wag" pokazać "Zaplanuj refeed".
     */
    val recommendsDietBreak: Boolean = false
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
 * v1.24.42: dodatkowo wspiera LONG_BREAK gdy ostatni regularny okres treningu
 * jest WCZEŚNIEJ niż 35 dni (np. user trenował 6× w okresie 36-70 dni temu,
 * potem przestał). Bez `sessionsLast70d` algorytm gubił takie przypadki —
 * widział tylko `sessions35d == sessions14d == 0` i nie miał "powrotu".
 *
 * @param sessionsLast14d liczba treningów w ostatnich 14 dniach
 * @param sessionsLast35d liczba treningów w ostatnich 35 dniach (włącznie z 14d)
 * @param daysSinceLastWorkout ile dni temu był ostatni workout
 * @param sessionsLast70d liczba treningów w ostatnich 70 dniach. Gdy `null`
 *   lub 0, algorytm zachowuje się jak przed v1.24.42 (tylko 35-dniowe okno).
 * @return rekomendacja powrotu lub null gdy nie wykryto przerwy
 */
fun detectReturnAfterBreak(
    sessionsLast14d: Int,
    sessionsLast35d: Int,
    daysSinceLastWorkout: Int?,
    sessionsLast70d: Int? = null
): ReturnAfterBreakRecommendation? {
    // Wymagamy historii regularnego treningu (przynajmniej 6 sesji w 35 dni przed przerwą).
    // v1.24.42: dla LONG_BREAK (przerwa >14 dni) akceptujemy też regularny trening
    // w okresie 36-70 dni temu (sessionsLast70d >= 6) — bez tego user który zerwał
    // 30 dni temu i ma <6 sesji w 35d nie dostawał karty POWRÓT PO PRZERWIE.
    val sessionsBeforeRecent = sessionsLast35d - sessionsLast14d
    val hasRegularHistory35d = sessionsBeforeRecent >= 6
    val hasRegularHistory70d = (sessionsLast70d ?: 0) >= 6
    if (!hasRegularHistory35d && !hasRegularHistory70d) return null

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

/**
 * Snapshot tygodnia treningowego do auto-detekcji deload week.
 *
 * @param weekStartMs początek tygodnia (timestamp)
 * @param avgRpe średnie RPE z roboczych setów (null gdy brak setów lub brak RPE)
 * @param sessionCount liczba treningów w tygodniu
 */
data class WeekRpeSnapshot(
    val weekStartMs: Long,
    val avgRpe: Double?,
    val sessionCount: Int
)

/**
 * Wykryty tydzień-deload (auto).
 *
 * @param weekStartMs początek tygodnia
 * @param avgRpe średnie RPE tygodnia
 * @param confidence 0.0-1.0 — pewność detekcji bazująca na różnicy RPE
 *   z sąsiednimi tygodniami (większa różnica = większa pewność)
 */
data class DetectedDeloadWeek(
    val weekStartMs: Long,
    val avgRpe: Double,
    val confidence: Double
)

/**
 * Auto-detekcja tygodni deload z historii treningu (wave loading, RPE patterns).
 *
 * Filozofia: zaawansowani trenujący robią wave loading (intensify → intensify → deload)
 * ale niekoniecznie wrzucają DELOAD_DETECTED event. Bez auto-detekcji
 * MesocycleBackfillService nie wykrywał tych tygodni i tworzył jeden długi
 * cykl akumulacji.
 *
 * Algorytm:
 *  - Tygodnie ze średnim RPE >= 2 punkty niższym od ŚREDNIEJ z sąsiadów (N-1, N+1)
 *    = prawdopodobny deload (wave loading: 8 → 8.5 → 6 → 8 → 8.5 → 6...)
 *  - Wymagamy że tydzień ma min 1 sesję (inaczej nie wiemy)
 *  - Confidence = clamp((differenceFromNeighbors - 2.0) / 2.0, 0.0, 1.0)
 *    (różnica 2.0 = confidence 0.0, różnica 4.0 = confidence 1.0)
 *
 * Pure function — testowalna bez DB.
 *
 * @param weeks lista tygodni posortowana po `weekStartMs` rosnąco
 * @return wykryte tygodnie-deload (może być pusta lista)
 */
fun detectDeloadWeeks(weeks: List<WeekRpeSnapshot>): List<DetectedDeloadWeek> {
    if (weeks.size < 3) return emptyList()  // potrzebujemy N-1, N, N+1
    val result = mutableListOf<DetectedDeloadWeek>()
    for (i in 1 until weeks.size - 1) {
        val prev = weeks[i - 1]
        val curr = weeks[i]
        val next = weeks[i + 1]
        if (curr.sessionCount == 0) continue
        if (curr.avgRpe == null || prev.avgRpe == null || next.avgRpe == null) continue
        val neighborAvg = (prev.avgRpe + next.avgRpe) / 2.0
        val diff = neighborAvg - curr.avgRpe
        if (diff >= 2.0) {
            val confidence = ((diff - 2.0) / 2.0).coerceIn(0.0, 1.0)
            result += DetectedDeloadWeek(
                weekStartMs = curr.weekStartMs,
                avgRpe = curr.avgRpe,
                confidence = confidence
            )
        }
    }
    return result
}

