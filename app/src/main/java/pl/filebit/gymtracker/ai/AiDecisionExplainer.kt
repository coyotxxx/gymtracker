package pl.filebit.gymtracker.ai

import android.util.Log
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.util.AdjustmentDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rozszerza wyjaśnienia z silnika regułowego (CalorieAdjustmentEngine) na
 * bardziej "ludzkie", motywujące, w 1. osobie wyjaśnienie dla usera.
 *
 * AI nie zmienia decyzji silnika — tylko ubiera w słowa.
 *
 * Wynik np. dla decyzji 'cut_stagnation_high_adherence':
 * - silnik: 'Średnia waga z 14 dni nie spada (78.4 kg). Zgodność: 92%, treningi: 4/4. Obniżam o 150 kcal.'
 * - AI: 'Hej Maciej, świetna robota — przez ostatnie 2 tygodnie trzymałeś dietę
 *        i zrobiłeś każdy trening. Niestety waga stoi na 78.4 kg, więc czas na
 *        małą korektę. Obniżam Twój dzienny cel o 150 kcal — to ~1 łyżka ryżu mniej.
 *        Zobaczymy efekt za tydzień.'
 *
 * Failure-safe: gdy AI niedostępne, używamy explanation z silnika.
 */
@Singleton
class AiDecisionExplainer @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val masterContextBuilder: MasterAiContextBuilder
) {

    suspend fun rewriteForUser(
        decision: AdjustmentDecision,
        profile: UserProfile
    ): String? {
        val cfg = prefs.load()
        if (!cfg.isConnected) return null  // brak klucza = nie generuj

        val displayName = profile.displayName.ifBlank { "Cześć" }

        // Master context — krótka sekcja z trendem + recovery żeby AI tłumaczył
        // decyzję w pełnym kontekście (np. "tracisz wagę, sen 8h, super!")
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()

        val prompt = buildString {
            append("Jesteś personalnym dietetykiem-trenerem. Przerób TECHNICZNE wyjaśnienie ")
            append("decyzji silnika regułowego na CIEPŁY, KRÓTKI komunikat dla osoby '${displayName}' po polsku.\n\n")
            append("ZASADY:\n")
            append("- Max 3 zdania, max 280 znaków\n")
            append("- Nie zmieniaj LICZB ani DECYZJI z silnika — tylko ubierz w słowa\n")
            append("- Jeśli jest motywujący kontekst (zgodność wysoka/treningi wykonane), pochwal\n")
            append("- Jeśli korekta jest minus kcal — podkreśl że to drobna zmiana, nie panika\n")
            append("- Bez markdown, bez list, bez 'Świetnie!'\n")
            append("- Mów 'Ty' nie 'Pan/Pani'\n\n")

            if (masterCtx != null) {
                append("KONTEKST OGÓLNY (referencja):\n")
                append(MasterAiContextPromptHelper.toWeightTrendSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append("\n")
            }

            append("DECYZJA SILNIKA:\n")
            append("Action: ${decision.action.name}\n")
            append("Delta kcal: ${decision.kcalDeltaProposed}\n")
            append("Nowy cel: ${decision.newKcal} kcal\n")
            append("Reason code: ${decision.reason}\n")
            append("Wyjaśnienie techniczne: ${decision.explanation}\n")
            if (decision.warnings.isNotEmpty()) {
                append("Ostrzeżenia: ${decision.warnings.joinToString("; ")}\n")
            }
        }

        val result = client.chat(cfg, listOf(AiMessage(AiRole.USER, prompt)), source = "DecisionExplainer")
        return result.fold(
            onSuccess = { raw -> raw.trim().lines().joinToString(" ").take(400) },
            onFailure = {
                Log.e("AiDecisionExplainer", "rewrite failed", it)
                null
            }
        )
    }
}
