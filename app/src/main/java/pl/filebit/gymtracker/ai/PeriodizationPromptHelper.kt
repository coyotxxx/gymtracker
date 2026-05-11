package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.repository.TransitionProposal

/**
 * Helper budujący sekcję promptu z fazą periodyzacji (v1.15.0).
 *
 * Wzorzec z `TrainingPhasePromptHelper` — top-level object żeby uniknąć inject DI.
 *
 * Dołączany do promptu w:
 *  - `ProactiveAiCheckWorker` gdy pyta AI o decyzję periodyzacyjną
 *  - `AiTrainerScreen` gdy user pyta o cykl
 *  - `WeeklyReportService` gdy generuje raport (kontekst dla rekomendacji)
 *
 * Filozofia: algorytm = księgowy, AI = trener. Prompt dostarcza AI **fakty**
 * (faza, tydzień, daty, sygnały) — AI **decyduje**.
 */
object PeriodizationPromptHelper {

    /**
     * Buduje sekcję promptu opisującą bieżący stan periodyzacji + propozycję algorytmu.
     *
     * @param meso aktywny mesocykl (null jeśli nie ma — wtedy zwraca pustą sekcję)
     * @param algorithmProposal propozycja `PeriodizationOrchestrator.recommendNextPhase` (null
     *        jeśli orchestrator nie wykrył TransitionDue)
     * @param stagnatingExercisesCount opcjonalny kontekst (z StagnationAnalyzer)
     * @param nowMs aktualny czas (parametryzowane dla testów)
     */
    fun toPromptSection(
        meso: TrainingMesocycle?,
        algorithmProposal: TransitionProposal? = null,
        stagnatingExercisesCount: Int = 0,
        totalAnalyzedExercises: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): String = buildString {
        if (meso == null && algorithmProposal == null) return@buildString

        append("\n=== PERIODYZACJA TRENINGOWA (v1.15.0) ===\n")

        meso?.let { m ->
            val daysElapsed = m.daysSinceStart(nowMs)
            val daysRemaining = m.daysRemaining(nowMs)
            val totalDays = m.totalDaysPlanned
            append("**Aktywny mesocykl:**\n")
            append("- Faza: ${phaseLabelPl(m.phase)} (tydzień ${m.weekInPhase}/${m.phaseLengthWeeks})\n")
            append("- Trwa: dzień $daysElapsed z $totalDays (zostało $daysRemaining)\n")
            append("- Start: ${formatDate(m.startDateMs)}, planowany koniec: ${formatDate(m.plannedEndDateMs)}\n")
            append("- Parametry: volume ${m.volumeProgression}x, intensity ${m.intensityProgression}x, target RPE ${m.targetRpe}\n")
            if (m.notes.isNotBlank()) append("- Notatki: ${m.notes}\n")
            append("\n")
        }

        if (stagnatingExercisesCount > 0 && totalAnalyzedExercises > 0) {
            val pct = (stagnatingExercisesCount.toDouble() / totalAnalyzedExercises * 100).toInt()
            append("**Sygnał stagnacji:** $stagnatingExercisesCount/$totalAnalyzedExercises ćwiczeń stagnuje ($pct%).\n")
            if (pct >= 30) {
                append("Próg 30% przekroczony — algorytm sugeruje deload nawet jeśli plannedEnd nie minął.\n")
            }
            append("\n")
        }

        algorithmProposal?.let { p ->
            append("**Algorithm proposal (PeriodizationOrchestrator.recommendNextPhase):**\n")
            append("- Przejście: ${phaseLabelPl(p.fromPhase)} → ${phaseLabelPl(p.recommendedNext)}\n")
            append("- Planowany start: ${formatDate(p.plannedStartDate)}\n")
            append("- Planowana długość: ${p.plannedDurationWeeks} tygodni\n")
            append("- Confidence: ${(p.confidence * 100).toInt()}%\n")
            append("- Uzasadnienie algorytmu: ${p.reasoning}\n")
            append("\n")
            append("**Twoja rola (AI):**\n")
            append("Oceń propozycję algorytmu w szerszym kontekście: sen/HRV, adherence, RPE trend, ")
            append("samopoczucie z ostatnich treningów, kalendarz wydarzeń (PR podejścia, zawody). ")
            append("Możesz:\n")
            append("- **Zaakceptować** propozycję as-is (high confidence — algorithm + Twoje dane się zgadzają)\n")
            append("- **Przesunąć datę startu** o kilka dni (np. \"deload od środy zamiast pn bo PR podejście w sobotę\")\n")
            append("- **Zmodyfikować parametry** (volume_reduction inny niż default)\n")
            append("- **Odrzucić** z konkretnym uzasadnieniem (np. \"jeszcze 1 tydzień akumulacji — RPE wciąż 7, masz zapas\")\n")
            append("\n")
            append("Użyj toola `propose_periodization_action` z konkretnymi parametrami. ")
            append("Twoja decyzja zostanie pokazana userowi z buttonami Zastosuj/Zmień/Wyjaśnij — ")
            append("user MUSI explicit zaakceptować, więc bądź konkretny i uzasadniaj wybory.\n")
        }
    }

    /** Polskie etykiety dla MesocyclePhase. */
    fun phaseLabelPl(phase: MesocyclePhase): String = when (phase) {
        MesocyclePhase.ACCUMULATION -> "Akumulacja"
        MesocyclePhase.INTENSIFICATION -> "Intensyfikacja"
        MesocyclePhase.DELOAD -> "Deload"
        MesocyclePhase.PEAKING -> "Peaking"
        MesocyclePhase.RECOVERY -> "Recovery"
    }

    private fun formatDate(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d.%02d.%d".format(
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.YEAR)
        )
    }
}
