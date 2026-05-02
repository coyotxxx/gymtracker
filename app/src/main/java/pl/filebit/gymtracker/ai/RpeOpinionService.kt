package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.repository.NextSetSuggestion
import javax.inject.Inject
import javax.inject.Singleton

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
     */
    suspend fun ask(
        exerciseId: Long,
        suggestion: NextSetSuggestion,
        excludeWorkoutId: Long?
    ): Result<String> {
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

            append("# ĆWICZENIE: ${exercise.name} (${exercise.primaryMuscle.name})\n\n")

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

            append("\n# OCZEKIWANY FORMAT ODPOWIEDZI\n")
            append("Krótka opinia (max 80 słów, polski język). Zacznij od jednego z:\n")
            append("- '✓ Zgadzam się' (jeśli sugestia ma sens)\n")
            append("- '⚠ Sugeruję inaczej: <waga>kg × <reps>' (jeśli zmieniłbyś)\n")
            append("Potem 1-2 zdania uzasadnienia bazującego na konkretnych liczbach z historii. ")
            append("Bez markdownu, bez list, czysty tekst.")
        }

        return client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt))
        ).map { raw -> raw.trim() }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) kg.toInt().toString()
        else String.format("%.1f", kg)
    }
}
