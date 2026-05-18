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

        val exById = sets.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }

        val volumeKg = sets.sumOf { it.reps * it.weightKg }
        val totalSets = sets.size
        val durationMin = workout.durationMillis / 60_000
        val cardioDistanceM = sets.sumOf { it.distanceM ?: 0.0 }

        // Klasyfikacja treningu — AI musi rozróżniać siłowy od cardio.
        val cardioExCount = exById.values.count {
            it?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
                it?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
        }
        val workoutTypeLabel = when {
            exById.isEmpty() -> "TRENING"
            cardioExCount == exById.size -> "CARDIO (czas, dystans, prędkość — to NIE są powtórzenia ani ciężar)"
            cardioExCount == 0 -> "SIŁOWY (ciężar × powtórzenia)"
            else -> "MIESZANY (część siłowa + część cardio)"
        }

        // Krótka tabela: ćwiczenie -> najlepszy zestaw. Cardio: czas + prędkość;
        // siłowe: max kg × reps.
        val perExercise = sets.groupBy { it.exerciseId }
            .map { (exId, exSets) ->
                val ex = exById[exId]
                val name = ex?.name ?: "?"
                val top = when (ex?.metricType) {
                    pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION -> {
                        val best = exSets.maxByOrNull { it.distanceM ?: 0.0 } ?: exSets.first()
                        listOfNotNull(
                            best.durationSec?.takeIf { it > 0 }?.let { "${it / 60} min" },
                            pl.filebit.gymtracker.util.cardioSpeedKmh(best.durationSec, best.distanceM)
                                ?.let { "${pl.filebit.gymtracker.util.formatCardioNumber(it)} km/h" }
                        ).joinToString(", ").ifBlank { "cardio" }
                    }
                    pl.filebit.gymtracker.data.entity.MetricType.DURATION -> {
                        val best = exSets.maxByOrNull { it.durationSec ?: 0 } ?: exSets.first()
                        best.durationSec?.takeIf { it > 0 }?.let { "${it / 60} min" } ?: "izometria"
                    }
                    else -> {
                        val best = exSets.maxByOrNull { it.weightKg * it.reps }!!
                        "${best.reps}×${formatKg(best.weightKg)}kg"
                    }
                }
                Triple(name, exSets.size, top)
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
            append("⚠ TYP TRENINGU: $workoutTypeLabel\n")
            append("Przy cardio NIGDY nie pisz o 'powtórzeniach' ani 'ciężarze' — opisuj czas, ")
            append("dystans i prędkość. Przy siłowym mów o ciężarze i powtórzeniach.\n\n")
            append("Dane treningu:\n")
            append("- Czas: ${durationMin} min\n")
            if (volumeKg > 0) {
                append("- Łączna objętość: ${formatKg(volumeKg)} kg (${totalSets} serii roboczych)\n")
                if (prevVolume != null && prevVolume > 0) {
                    val diff = volumeKg - prevVolume
                    val pctText = " (${if (diff >= 0) "+" else ""}${(diff * 100 / prevVolume).toInt()}%)"
                    append("- Poprzedni trening: ${formatKg(prevVolume)} kg → różnica ${if (diff >= 0) "+" else ""}${formatKg(diff)} kg$pctText\n")
                }
            } else {
                append("- Serie robocze: ${totalSets}\n")
            }
            if (cardioDistanceM > 0) {
                append("- Cardio łącznie: ${pl.filebit.gymtracker.util.formatCardioNumber(cardioDistanceM / 1000.0)} km\n")
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
