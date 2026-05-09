package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * v1.11.59 — Pure functions wykrywające eventy z danych treningowych.
 *
 * Testowalne bez DI — biorą prymitywy / data klasy, zwracają TrainingEvent.
 * Service `EventDetectorService` wywołuje te funkcje przy odpowiednich hooks
 * (finishWorkout, plan change, nightly worker).
 */

/**
 * Liczy estymowany 1RM przez Epleya: weight × (1 + reps/30).
 * Standardowa formuła używana w aplikacji i analyzerach.
 */
internal fun e1rm(weightKg: Double, reps: Int): Double =
    if (reps <= 0) 0.0 else weightKg * (1.0 + reps / 30.0)

/**
 * Detekcja PR-ów z konkretnego workoutu.
 *
 * Dla każdego ćwiczenia w workoutcie sprawdza czy najlepszy set (po e1RM) bije
 * historyczne maksimum dla tego ćwiczenia. Zwraca eventy PR_SET tylko dla
 * faktycznie nowych rekordów.
 *
 * @param workout zakończony workout do oceny
 * @param workoutSets sety z tego workoutu (ukończone, NORMAL/AMRAP/DROP, bez WARMUP)
 * @param historicalSetsByExerciseId WSZYSTKIE historyczne sety user'a (z innych workoutów)
 *   pogrupowane po exerciseId. Tylko ukończone, bez WARMUP.
 * @param exerciseNamesById mapa exerciseId → nazwa (dla payloadu eventu)
 */
fun detectPrsFromWorkout(
    workout: Workout,
    workoutSets: List<WorkoutSet>,
    historicalSetsByExerciseId: Map<Long, List<WorkoutSet>>,
    exerciseNamesById: Map<Long, String>
): List<TrainingEvent> {
    val results = mutableListOf<TrainingEvent>()

    // Pogrupuj sety z tego workoutu po exerciseId
    val byExercise = workoutSets
        .filter { it.isCompleted && it.setType != SetType.WARMUP && it.weightKg > 0.0 && it.reps > 0 }
        .groupBy { it.exerciseId }

    for ((exerciseId, sets) in byExercise) {
        // Najlepszy set z tego workoutu (po e1RM)
        val bestSet = sets.maxByOrNull { e1rm(it.weightKg, it.reps) } ?: continue
        val newE1rm = e1rm(bestSet.weightKg, bestSet.reps)

        // Historyczny max e1RM dla tego ćwiczenia (sety SPRZED tego workoutu)
        val historical = historicalSetsByExerciseId[exerciseId].orEmpty()
            .filter { it.isCompleted && it.setType != SetType.WARMUP && it.weightKg > 0.0 && it.reps > 0 }
        val historicalMaxE1rm = historical.maxOfOrNull { e1rm(it.weightKg, it.reps) } ?: 0.0

        // PR tylko gdy faktycznie wyższy (próg 0.1 kg żeby uniknąć floating-point noise)
        if (newE1rm > historicalMaxE1rm + 0.1) {
            results.add(
                TrainingEvent(
                    date = workout.finishedAt ?: workout.startedAt,
                    type = TrainingEventType.PR_SET,
                    workoutId = workout.id,
                    exerciseId = exerciseId,
                    exerciseName = exerciseNamesById[exerciseId],
                    weightKg = bestSet.weightKg,
                    reps = bestSet.reps,
                    e1rmKg = newE1rm,
                    notes = "Nowy PR: ${bestSet.weightKg} kg × ${bestSet.reps} (e1RM ${"%.1f".format(newE1rm)})"
                )
            )
        }
    }
    return results
}

/**
 * Detekcja kontuzji z workoutu — gdy painArea jest wypełnione.
 * Zwraca pojedynczy event INJURY (lub pustą listę).
 */
fun detectInjuryFromWorkout(workout: Workout): List<TrainingEvent> {
    if (workout.painArea.isNullOrBlank()) return emptyList()
    return listOf(
        TrainingEvent(
            date = workout.finishedAt ?: workout.startedAt,
            type = TrainingEventType.INJURY,
            workoutId = workout.id,
            area = workout.painArea,
            notes = workout.painNotes ?: "",
        )
    )
}

/**
 * Detekcja gap_resumed — gdy nowy workout następuje po przerwie >14 dni
 * od poprzedniego ZAKOŃCZONEGO workoutu.
 *
 * @param newWorkout aktualny workout
 * @param previousFinishedWorkouts wszystkie zakończone workouts SPRZED tego (posortowane chronologicznie)
 */
fun detectGapResumed(
    newWorkout: Workout,
    previousFinishedWorkouts: List<Workout>
): List<TrainingEvent> {
    val lastBefore = previousFinishedWorkouts
        .filter { it.finishedAt != null && (it.finishedAt) < newWorkout.startedAt }
        .maxByOrNull { it.finishedAt!! }
        ?: return emptyList()

    val gapDays = (newWorkout.startedAt - lastBefore.finishedAt!!) / (1000L * 60 * 60 * 24)
    if (gapDays < 14) return emptyList()

    return listOf(
        TrainingEvent(
            date = newWorkout.startedAt,
            type = TrainingEventType.GAP_RESUMED,
            workoutId = newWorkout.id,
            weeksContext = (gapDays / 7).toInt(),
            notes = "Powrót po przerwie ${gapDays} dni"
        )
    )
}

/**
 * Detekcja deloadu — gdy volume tego tygodnia spadł ≥40% vs ostatnie 4 tyg
 * Z parametrami zgodnymi z TrainingPhaseAnalyzer (mediana 4 tyg, próg 60%).
 *
 * @param weeklyVolumes lista objętości tygodniowej, indeks 0 = najdawniej, indeks last = bieżący tydzień
 * @param currentWeekStartMs początek bieżącego tygodnia (epoch ms) — używane jako date eventu
 */
fun detectDeloadFromWeeklyVolumes(
    weeklyVolumes: List<Double>,
    currentWeekStartMs: Long
): List<TrainingEvent> {
    if (weeklyVolumes.size < 5) return emptyList()  // potrzebujemy bieżący + 4 poprzednie

    val current = weeklyVolumes.last()
    val prior4 = weeklyVolumes.dropLast(1).takeLast(4).filter { it > 0.0 }
    if (prior4.size < 3) return emptyList()  // za mało danych

    val median = prior4.sorted().let { sorted ->
        if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
    if (median <= 0.0) return emptyList()

    val ratio = current / median
    if (ratio >= 0.60) return emptyList()  // nie deload (granica zgodna z TrainingPhaseAnalyzer)

    return listOf(
        TrainingEvent(
            date = currentWeekStartMs,
            type = TrainingEventType.DELOAD_DETECTED,
            weeksContext = prior4.size,
            notes = "Volume spadł do ${"%.0f".format(ratio * 100)}% mediany ostatnich ${prior4.size} tyg"
        )
    )
}
