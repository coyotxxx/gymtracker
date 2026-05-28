package pl.filebit.gymtracker.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.repository.NextSetSuggestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wynik "drugiej opinii" AI. Jeśli AI sugeruje konkretne zmiany (waga/reps),
 * wyciągamy je z tekstu i pozwalamy userowi zastosować jednym tapem.
 */
data class RpeOpinion(
    val text: String,
    val parsedKg: Double? = null,
    val parsedReps: Int? = null
) {
    val hasActionableSuggestion: Boolean
        get() = parsedKg != null && parsedReps != null
}

/**
 * "Drugie zdanie" trenera AI — kwestionuje algorytmiczną sugestię na podstawie
 * pełnej historii ostatnich sesji ćwiczenia. Zwraca krótką opinię (max ~100 słów).
 *
 * Algorytm w util/Autoregulation.kt patrzy tylko na poprzednią sesję.
 * AI widzi 4-6 ostatnich sesji + trend RPE + objętość → może wykryć:
 *   - narastające zmęczenie ("RPE rośnie z każdym treningiem — utrzymaj wagę")
 *   - stagnację ukrytą ("trzy sesje na tej samej wadze, czas na deload")
 *   - pochopną sugestię ("algorytm chce +2.5kg ale ostatni RPE 9.5 — to ryzyko")
 *
 * BYOK — wymaga klucza API.
 */
@Singleton
class RpeOpinionService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {
    /**
     * @param exerciseId ID ćwiczenia
     * @param suggestion sugestia z algorytmu (computeProgression)
     * @param excludeWorkoutId aktualny trening (żeby nie mieszał się w "historii")
     * @param followUpMessage opcjonalna doprecyzowana wiadomość usera
     *   (np. "źle dziś śpię" / "boli mnie bark" / "chcę iść ciężej")
     */
    suspend fun ask(
        exerciseId: Long,
        suggestion: NextSetSuggestion,
        excludeWorkoutId: Long?,
        followUpMessage: String? = null
    ): Result<RpeOpinion> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane — wpisz klucz API w Profilu"))
        }

        val exercise = exerciseDao.getById(exerciseId)
            ?: return Result.failure(NoSuchElementException("Brak ćwiczenia"))

        // Ostatnie 6 sesji tego ćwiczenia
        val allSets = setDao.getAllForExercise(exerciseId)
            .filter { excludeWorkoutId == null || it.workoutId != excludeWorkoutId }
            .filter { it.isCompleted && it.setType != SetType.WARMUP }

        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .associateBy { it.id }

        val sessions = allSets.groupBy { it.workoutId }
            .filterKeys { it in finishedWorkouts }
            .toList()
            .sortedByDescending { (wid, _) -> finishedWorkouts[wid]!!.startedAt }
            .take(6)

        if (sessions.isEmpty()) {
            return Result.failure(IllegalStateException("Brak historii dla tego ćwiczenia"))
        }

        // Build prompt
        val prompt = buildString {
            append("Jesteś trenerem personalnym. Algorytm sugeruje wagę i powtórzenia ")
            append("na następną serię. Twoja rola — zweryfikować czy sugestia ma sens ")
            append("biorąc pod uwagę pełniejszy kontekst (ostatnie sesje, trend RPE).\n\n")

            append("# ĆWICZENIE: ${exercise.name} (${exercise.primaryMuscle.name})\n")
            // v2.5.0: wskazówki techniczne + częste błędy z canonical — pomagają AI
            // ocenić czy bezpiecznie iść ciężej (np. błąd techniczny przy zmęczeniu).
            canonicalTechniqueHint(exercise)?.let { append(it) }
            append("\n")

            append("# SUGESTIA ALGORYTMU\n")
            append("- Waga: ${formatKg(suggestion.suggestedWeightKg)} kg\n")
            append("- Powtórzenia: ${suggestion.suggestedReps}\n")
            append("- Powód: ${suggestion.rationale}\n")
            append("- Bazuje na poprzedniej sesji: ${formatKg(suggestion.previousWeightKg)} kg × ${suggestion.previousReps}\n\n")

            append("# HISTORIA OSTATNICH ${sessions.size} SESJI (od najnowszej)\n")
            sessions.forEachIndexed { idx, (wid, sets) ->
                val w = finishedWorkouts[wid]!!
                val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(w.startedAt))
                val topSet = sets.maxByOrNull { it.weightKg * it.reps }!!
                val rpeList = sets.mapNotNull { it.rpe }
                val rpeStr = when {
                    rpeList.isEmpty() -> "brak RPE"
                    rpeList.size == 1 -> "RPE ${rpeList[0]}"
                    else -> "RPE: ${rpeList.joinToString(", ")}"
                }
                append("${idx + 1}. $date — ${sets.size} setów, top ${topSet.reps}×${formatKg(topSet.weightKg)}kg, $rpeStr\n")
            }

            if (!followUpMessage.isNullOrBlank()) {
                append("\n# DODATKOWA WIADOMOŚĆ UŻYTKOWNIKA\n")
                append(followUpMessage.take(300))
                append("\nUwzględnij ją w ocenie i ewentualnej kontr-sugestii.\n")
            }

            append("\n# OCZEKIWANY FORMAT ODPOWIEDZI\n")
            append("Krótka opinia (max 80 słów, polski język). Zacznij od jednego z:\n")
            append("- '✓ Zgadzam się' (jeśli sugestia ma sens)\n")
            append("- '⚠ Sugeruję inaczej: <waga>kg × <reps>' (jeśli zmieniłbyś — ZAWSZE w tym formacie z liczbami)\n")
            append("Potem 1-2 zdania uzasadnienia bazującego na konkretnych liczbach z historii. ")
            append("Bez markdownu, bez list, czysty tekst.")
        }

        return client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "RpeOpinion"
        ).map { raw ->
            val text = raw.trim()
            val (kg, reps) = RpeOpinionService.parseSuggestion(text)
            RpeOpinion(text = text, parsedKg = kg, parsedReps = reps)
        }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) kg.toInt().toString()
        else String.format("%.1f", kg)
    }

    companion object {
        private val SUGGESTION_REGEX = Regex(
            """([0-9]+(?:[.,][0-9]+)?)\s*kg\s*[x×]\s*([0-9]+)""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Wyciąga `<waga>kg × <reps>` z tekstu odpowiedzi AI. Akceptuje warianty
         * '4 kg × 8', '4kg x 8', '4.5kg×8', '82,5 kg × 5'.
         */
        fun parseSuggestion(text: String): Pair<Double?, Int?> {
            val match = SUGGESTION_REGEX.find(text) ?: return null to null
            val kg = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            val reps = match.groupValues[2].toIntOrNull()
            return kg to reps
        }
    }
}

/**
 * v2.5.0 — wyciąga zwięzłą wskazówkę techniczną z canonical (execution cues + 1-2
 * częste błędy) do promptu RpeOpinion. Null jeśli ćwiczenie nie ma danych canonical.
 * Krótko — max ~3 cues + 2 faults, żeby nie przeładować tokenów.
 */
private fun canonicalTechniqueHint(exercise: pl.filebit.gymtracker.data.entity.Exercise): String? {
    val sb = StringBuilder()
    runCatching {
        val coaching = exercise.coachingCuesJson
        if (!coaching.isNullOrBlank() && coaching != "{}") {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(coaching).jsonObject
            val cues = (obj["execution_cues_pl"] ?: obj["execution_cues"])
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.take(3) ?: emptyList()
            if (cues.isNotEmpty()) {
                sb.append("- Technika: ").append(cues.joinToString("; ")).append("\n")
            }
        }
    }
    runCatching {
        val faults = exercise.commonFaultsJson
        if (!faults.isNullOrBlank() && faults != "[]") {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(faults).jsonArray
            val items = arr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val f = (o["faultPl"] ?: o["fault"])?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                f
            }.take(2)
            if (items.isNotEmpty()) {
                sb.append("- Częste błędy (uważaj przy zmęczeniu): ").append(items.joinToString("; ")).append("\n")
            }
        }
    }
    return sb.toString().takeIf { it.isNotBlank() }
}
