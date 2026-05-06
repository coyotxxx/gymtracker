package pl.filebit.gymtracker.ai

import android.util.Log
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generuje krótkie (2-3 zdania) motywujące podsumowanie treningu po jego zakończeniu.
 * Używa konfigu AI użytkownika (BYOK) — gdy nie skonfigurowane, zwraca null
 * i nic się nie dzieje (cicho pomijamy).
 */
@Singleton
class WorkoutAiSummaryService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {
    suspend fun generate(workoutId: Long): Result<String> {
        val cfg = prefs.load()
        if (!cfg.isConnected) return Result.failure(IllegalStateException("AI nie skonfigurowane"))

        val workout = workoutDao.getById(workoutId)
            ?: return Result.failure(NoSuchElementException("Brak treningu $workoutId"))

        val sets = setDao.getForWorkout(workoutId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (sets.isEmpty()) return Result.failure(IllegalStateException("Brak zaliczonych serii"))

        val exMap = sets.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it)?.name ?: "?" }

        val volumeKg = sets.sumOf { it.reps * it.weightKg }
        val totalSets = sets.size
        val durationMin = workout.durationMillis / 60_000

        // Krótka tabela: ćwiczenie -> najlepszy zestaw (max kg × reps)
        val perExercise = sets.groupBy { it.exerciseId }
            .map { (exId, exSets) ->
                val best = exSets.maxByOrNull { it.weightKg * it.reps }!!
                val name = exMap[exId] ?: "?"
                Triple(name, exSets.size, "${best.reps}×${formatKg(best.weightKg)}kg")
            }

        // Poprzedni trening (do porównania objętości)
        val prevVolume = workoutDao.observeAllOnce()
            .asSequence()
            .filter { it.id != workoutId && it.finishedAt != null && it.startedAt < workout.startedAt }
            .sortedByDescending { it.startedAt }
            .firstOrNull()
            ?.let { prev ->
                setDao.getForWorkout(prev.id)
                    .filter { it.isCompleted && it.setType != SetType.WARMUP }
                    .sumOf { it.reps * it.weightKg }
            }

        val prompt = buildString {
            append("Jesteś trenerem. Napisz KRÓTKIE (max 3 zdania, do 280 znaków) motywujące podsumowanie ")
            append("zakończonego treningu po polsku. Bądź konkretny — cytuj liczby. Wskaż jedną mocną ")
            append("stronę i opcjonalnie jedną krótką sugestię na następny raz. Nie używaj markdown ani list. ")
            append("Nie zaczynaj od 'Świetny trening!' — bądź autentyczny.\n\n")
            append("Dane treningu:\n")
            append("- Czas: ${durationMin} min\n")
            append("- Łączna objętość: ${formatKg(volumeKg)} kg (${totalSets} serii roboczych)\n")
            if (prevVolume != null) {
                val diff = volumeKg - prevVolume
                val pctText = if (prevVolume > 0) " (${if (diff >= 0) "+" else ""}${(diff * 100 / prevVolume).toInt()}%)" else ""
                append("- Poprzedni trening: ${formatKg(prevVolume)} kg → różnica ${if (diff >= 0) "+" else ""}${formatKg(diff)} kg$pctText\n")
            }
            append("- Ćwiczenia (top set):\n")
            perExercise.forEach { (name, count, top) ->
                append("  • $name — $count serii, najlepszy: $top\n")
            }
        }

        val result = client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "WorkoutSummary"
        )
        return result.mapCatching { raw ->
            val cleaned = raw.trim().lines().joinToString(" ").take(400)
            Log.d("WorkoutAiSummary", "generated for workout=$workoutId: $cleaned")
            cleaned
        }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) kg.toInt().toString()
        else String.format("%.1f", kg)
    }
}
