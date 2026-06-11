package pl.filebit.gymtracker

import android.content.Intent
import android.os.Bundle
import pl.filebit.gymtracker.service.DietAutoAdjustmentWorker
import pl.filebit.gymtracker.service.ProactiveAiCheckWorker
import pl.filebit.gymtracker.service.WeeklyReportWorker

/**
 * v1.14.1 — luka K4 z audytu (2026-05-10): MainActivity ignorowała intent.extras
 * z push notifikacji. ProactiveAiCheckWorker i DietAutoAdjustmentWorker ustawiały
 * extras (EXTRA_OPEN_AI_TRAINER, EXTRA_QUICK_ACTION, EXTRA_OPEN_DIET) ale były
 * niewykorzystywane → wszystkie push'e otwierały Home zamiast docelowego ekranu.
 *
 * Pure function — testowalne bez Android/Activity (wzorzec z `reconstructMesocycles`).
 *
 * Mapowanie:
 *  - EXTRA_OPEN_AI_TRAINER=true + EXTRA_QUICK_ACTION=X → "ai/trainer/0?auto=X"
 *  - EXTRA_OPEN_DIET=true → "diet"
 *  - EXTRA_OPEN_WEEKLY_REPORT=true → "ai/weekly-report" (v2.13.0)
 *  - null lub nieznane → null (pozostaje na Home — default)
 */
object IntentNavigationParser {

    /**
     * Pure function — wyciąga ścieżkę nawigacyjną z prostych typów (testowalne bez Android).
     *
     * @param openAiTrainer flaga z EXTRA_OPEN_AI_TRAINER (ProactiveAiCheckWorker)
     * @param quickAction wartość EXTRA_QUICK_ACTION (np. "DELOAD", "PAIN_RECOVERY", "TODAY")
     * @param openDiet flaga z EXTRA_OPEN_DIET (DietAutoAdjustmentWorker)
     * @return route do nawigacji jeśli rozpoznano, null jeśli brak deep linka
     */
    fun parseInitialNavigation(
        openAiTrainer: Boolean,
        quickAction: String?,
        openDiet: Boolean,
        openWeeklyReport: Boolean = false
    ): String? {
        // ProactiveAiCheckWorker — otworzyć AI Trener z auto-action
        if (openAiTrainer) {
            val action = quickAction.orEmpty()
            // Route: ai/trainer/{conversationId}?auto={auto}
            // conversationId=0 = nowa konwersacja
            return if (action.isNotBlank()) "ai/trainer/0?auto=$action" else "ai/trainer/0"
        }

        // DietAutoAdjustmentWorker — otworzyć Dietę
        if (openDiet) {
            // Route: diet (samo otworzenie. Pokazanie konkretnego adjustment z ID
            // wymaga rozszerzenia route'a — TODO v1.15.0+).
            return "diet"
        }

        // WeeklyReportWorker — otworzyć ekran raportu tygodniowego (Screen.AiWeeklyReport)
        if (openWeeklyReport) {
            return "ai/weekly-report"
        }

        return null
    }

    /** Wrapper Bundle — wyciąga extras i deleguje do pure function. */
    fun parseInitialNavigation(extras: Bundle?): String? {
        if (extras == null) return null
        return parseInitialNavigation(
            openAiTrainer = extras.getBoolean(ProactiveAiCheckWorker.EXTRA_OPEN_AI_TRAINER, false),
            quickAction = extras.getString(ProactiveAiCheckWorker.EXTRA_QUICK_ACTION),
            openDiet = extras.getBoolean(DietAutoAdjustmentWorker.EXTRA_OPEN_DIET, false),
            openWeeklyReport = extras.getBoolean(WeeklyReportWorker.EXTRA_OPEN_WEEKLY_REPORT, false)
        )
    }

    /** Wrapper Intent — wyciąga extras i deleguje do pure function. */
    fun parseInitialNavigation(intent: Intent?): String? =
        parseInitialNavigation(intent?.extras)
}
