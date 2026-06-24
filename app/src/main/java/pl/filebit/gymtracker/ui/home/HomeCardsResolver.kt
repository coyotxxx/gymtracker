package pl.filebit.gymtracker.ui.home

import pl.filebit.gymtracker.ai.DataMaturity
import pl.filebit.gymtracker.ai.LoadZone
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.data.repository.DismissedCardsPrefs

/**
 * v1.27 — FAZA 1 — HomeCardsResolver.
 *
 * Pure function: `HomeUiState` → lista kart które user WIDZI na ekranie Home
 * + lista kart świadomie UKRYTYCH z powodem.
 *
 * Cel: wyciągnąć rozsianą po `HomeScreen.kt` logikę "co pokazać / co ukryć /
 * w jakiej kolejności" do jednego testowalnego miejsca. Dzięki temu test
 * pokazuje dokładnie co zobaczy użytkownik w danym stanie, a reguły
 * kompozycji kart (np. "ukryj fazę cyklu gdy aktywny deload") są jawne
 * i sprawdzalne — koniec z bugami typu duplikat DELOAD+FAZA.
 *
 * Odwzorowuje reguły z HomeScreen.kt (stan na v1.26.7). Gdy HomeScreen
 * zmieni reguły — ten resolver i jego testy też muszą się zmienić;
 * docelowo (FAZA 3) HomeScreen renderuje wynik resolvera.
 */
data class HomeCard(
    val key: String,
    val title: String
)

data class HiddenCard(
    val key: String,
    val reason: String
)

data class HomeCardsResult(
    val visible: List<HomeCard>,
    val hidden: List<HiddenCard>
)

object HomeCardsResolver {

    fun resolve(state: HomeUiState): HomeCardsResult {
        val visible = mutableListOf<HomeCard>()
        val hidden = mutableListOf<HiddenCard>()

        // 1. AI Trener proponuje — pending decision (najwyższy priorytet)
        if (state.pendingDecisions.isNotEmpty()) {
            visible += HomeCard("AI_PROPOSAL", "AI TRENER PROPONUJE")
        }

        // 2. Cel wagi osiągnięty
        if (state.goalAchievement != null) {
            visible += HomeCard("GOAL_ACHIEVED", "CEL OSIĄGNIĘTY")
        }

        // 3. Karta deload — 4 warianty (DeloadService.cardState)
        when (val d = state.deloadCard) {
            is DeloadCardState.Suggestion ->
                visible += HomeCard("DELOAD_SUGGESTION", "DELOAD ZALECANY")
            is DeloadCardState.Active ->
                visible += HomeCard(
                    "DELOAD_ACTIVE",
                    if (d.isFinished) "DELOAD ZAKOŃCZONY — WRÓĆ DO WAG" else "DELOAD TRWA"
                )
            is DeloadCardState.ReturnAfterBreak ->
                visible += HomeCard("RETURN_AFTER_BREAK", "POWRÓT PO PRZERWIE")
            is DeloadCardState.ActiveInjury ->
                visible += HomeCard("ACTIVE_INJURY", "WYKRYTO BÓL")
            is DeloadCardState.MissedWorkout ->
                visible += HomeCard("MISSED_WORKOUT", "OPUSZCZONY TRENING")
            DeloadCardState.None -> {}
        }

        // 4. Hero card — zawsze dokładnie jedna z 4
        visible += HomeCard(
            "HERO",
            when {
                state.activeWorkout != null -> "Trening w toku"
                state.todaysPlan != null -> "Dziś trening z planu"
                state.nextPlannedDay != null -> "Dziś dzień wolny"
                else -> "Brak planu na dziś"
            }
        )

        // 5. Gotowość do treningu (Training Readiness)
        state.trainingReadiness?.let { r ->
            val dismissed = DismissedCardsPrefs.CardKeys.READINESS in state.dismissedCards
            when {
                r.maturity == DataMaturity.LEARNING ->
                    hidden += HiddenCard("TRAINING_READINESS", "za mało danych (maturity=LEARNING)")
                // v2.76.0: brak świeżej aktywności treningowej → werdykt z wartości domyślnych, chowamy.
                !r.hasRecentTraining ->
                    hidden += HiddenCard("TRAINING_READINESS", "brak treningu w 14 dni — gotowość nieoznaczona")
                dismissed ->
                    hidden += HiddenCard("TRAINING_READINESS", "user zamknął kartę")
                else ->
                    visible += HomeCard("TRAINING_READINESS", "GOTOWOŚĆ DO TRENINGU")
            }
        }

        // 6. Faza cyklu treningowego
        state.trainingPhase?.let { p ->
            val dismissed = DismissedCardsPrefs.CardKeys.PHASE in state.dismissedCards
            // v1.24.40: ukryj fazę NEEDS_DELOAD gdy osobna karta DELOAD ZALECANY
            // już to mówi — duplikat z 2 systemów (DeloadService + TrainingPhaseAnalyzer)
            val duplicatesDeload = p.phase == TrainingPhase.NEEDS_DELOAD &&
                state.deloadCard is DeloadCardState.Suggestion
            // v1.26.9 (symulacja realnego użytkowania): po przerwie treningowej
            // tonaż jest niski, więc TrainingPhaseAnalyzer (liczy fazę z tonażu)
            // klasyfikuje to jako DELOAD. To myli — user nie jest w deloadzie,
            // wrócił z przerwy. Karta "POWRÓT PO PRZERWIE" jest właściwym
            // komunikatem; faza ją tylko zaciemnia.
            val misleadingAfterBreak =
                state.deloadCard is DeloadCardState.ReturnAfterBreak
            when {
                p.phase == TrainingPhase.NO_DATA ->
                    hidden += HiddenCard("TRAINING_PHASE", "faza NO_DATA (za mało historii)")
                dismissed ->
                    hidden += HiddenCard("TRAINING_PHASE", "user zamknął kartę")
                duplicatesDeload ->
                    hidden += HiddenCard(
                        "TRAINING_PHASE",
                        "duplikat z karty DELOAD ZALECANY (reguła v1.24.40)"
                    )
                misleadingAfterBreak ->
                    hidden += HiddenCard(
                        "TRAINING_PHASE",
                        "powrót po przerwie — niski tonaż naturalnie wygląda jak " +
                            "deload, faza wprowadzałaby w błąd"
                    )
                else ->
                    visible += HomeCard("TRAINING_PHASE", "FAZA CYKLU")
            }
        }

        // 7. Regeneracja — v2.44.0: scalona w Kartę Coacha (NotificationCenter →
        // CoachOrchestrator). Nie jest już osobną kartą Home, więc resolver jej nie modeluje.

        // 8. Obciążenie treningowe (ACWR)
        state.trainingLoad?.let { l ->
            val dismissed = DismissedCardsPrefs.CardKeys.LOAD in state.dismissedCards
            when {
                l.zone == LoadZone.INSUFFICIENT ->
                    hidden += HiddenCard("TRAINING_LOAD", "za mało danych (zone=INSUFFICIENT)")
                dismissed ->
                    hidden += HiddenCard("TRAINING_LOAD", "user zamknął kartę")
                else ->
                    visible += HomeCard("TRAINING_LOAD", "OBCIĄŻENIE TRENINGOWE (ACWR)")
            }
        }

        return HomeCardsResult(visible, hidden)
    }
}
