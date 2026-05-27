package pl.filebit.gymtracker.data.seed

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MetricType
import pl.filebit.gymtracker.data.entity.MuscleGroup

@Serializable
private data class SeedExercise(
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val description: String = ""
)

class ExerciseSeeder(
    private val context: Context,
    private val dao: ExerciseDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * v2.0.0 — stary seed `exercises.json` (193 ćwiczenia) jest zastąpiony przez
     * CanonicalExerciseBootstrap (1317 canonical). Ta metoda została odchudzona
     * do trzech zadań maintenance:
     *  1. `markMacjiejFavorites` — oznacza ~75 PL nazw jako ulubione (idempotent)
     *  2. `fixCardioMetricType` — naprawa metricType cardio (legacy data fix)
     *  3. `fixCardioPlanSetDurations` — naprawa reps → durationSec w planach cardio
     *
     * Stary loader `exercises.json` + `applySearchAliases` z `exercise_aliases.json`
     * są wyłączone — canonical bootstrap dostarcza nazwy PL/EN i aliases bezpośrednio
     * z `assets/exercises_canonical/{slug}.json`.
     */
    suspend fun seedIfEmpty() {
        android.util.Log.i("ExerciseSeeder", "seedIfEmpty start, exercise count=${runCatching { dao.count() }.getOrDefault(-1)}")
        markMacjiejFavorites()
        runCatching { dao.fixCardioMetricType() }
        runCatching { dao.fixCardioPlanSetDurations() }
        val favCount = runCatching {
            // policz po wszystkim
            dao.getFavorites().size
        }.getOrDefault(-1)
        android.util.Log.i("ExerciseSeeder", "seedIfEmpty done, favorites=$favCount")
    }

    /**
     * v2.0.0 — lista 55 canonical slugów odpowiadających ćwiczeniom z xlsx Macieja
     * (3.5 roku planów). UPDATE isFavorite=1 dla każdego slugu który istnieje w bazie.
     * Deterministyczny (slug-based zamiast LIKE po PL nazwie) i idempotentny.
     */
    private suspend fun markMacjiejFavorites() {
        android.util.Log.i("ExerciseSeeder", "markMacjiejFavorites start")
        val canonicalSlugs = listOf(
            // Big lifts
            "barbell-back-squat",
            "barbell-bench-press",
            "barbell-deadlift",
            "barbell-bent-over-row",
            "pull-up",
            "chin-up",
            "weighted-pull-up",
            "wide-grip-pull-up",
            "barbell-overhead-press",
            "barbell-incline-bench-press",
            // Klatka pomocnicze
            "dumbbell-bench-press",
            "dumbbell-incline-bench-press",
            "triceps-dip",
            "weighted-triceps-dip-on-high-parallel-bars",
            "push-up",
            "diamond-push-up",
            "exercise-ball-pike-push-up",
            "bench-dip-knees-bent",
            "incline-push-up",
            "decline-push-up",
            "clap-push-up",
            "dumbbell-fly",
            "dumbbell-incline-fly",
            "barbell-pullover",
            // Plecy / barki pomocnicze
            "dumbbell-bent-over-row",
            "barbell-shrug",
            "dumbbell-shrug",
            "barbell-seated-overhead-press",
            "barbell-seated-behind-head-military-press",
            "dumbbell-lateral-raise",
            "barbell-front-raise",
            "band-standing-rear-delt-row",
            "barbell-upright-row",
            // Biceps / Triceps
            "barbell-curl",
            "dumbbell-biceps-curl",
            "dumbbell-cross-body-hammer-curl",
            "ez-barbell-curl",
            "barbell-lying-triceps-extension-skull-crusher",
            "dumbbell-standing-triceps-extension",
            // Nogi
            "barbell-lunge",
            "walking-lunge",
            "barbell-good-morning",
            "barbell-glute-bridge",
            "standing-calves",
            // Core
            "crunch-floor",
            "side-bridge-v-2",
            "hanging-leg-raise",
            "hanging-pike",
            "russian-twist",
            "dead-bug",
            // Cardio
            "jump-rope",
            "burpee",
            "walking-on-incline-treadmill",
            "stationary-bike-run-v-3",
            "walk-elliptical-cross-trainer"
        )

        var matched = 0
        var failed = 0
        canonicalSlugs.forEach { slug ->
            val r = runCatching { dao.markFavoriteBySlug(slug) }
            if (r.isSuccess) {
                if ((r.getOrNull() ?: 0) > 0) matched++
            } else {
                failed++
                android.util.Log.w("ExerciseSeeder", "markFavoriteBySlug($slug) failed: ${r.exceptionOrNull()?.message}")
            }
        }
        android.util.Log.i("ExerciseSeeder", "markMacjiejFavorites: matched=$matched/${canonicalSlugs.size}, failed=$failed")
    }

    /**
     * Heurystyka: rozpoznaje kardio (CARDIO) i izometryczne po nazwie.
     */
    private fun inferMetricType(name: String, muscle: String): MetricType {
        val lowercase = name.lowercase()
        if (muscle == "CARDIO" || lowercase.contains("bieżni") ||
            lowercase.contains("bieg") || lowercase.contains("rower") ||
            lowercase.contains("orbitrek") || lowercase.contains("eliptyczn") ||
            lowercase.contains("wioślar") || lowercase.contains("skakank") ||
            lowercase.contains("spinning")
        ) {
            return MetricType.DISTANCE_DURATION
        }
        if (lowercase.contains("plank") || lowercase.contains("deska") ||
            lowercase.contains("hold") || lowercase.contains("statyczn") ||
            lowercase.contains("zwis")
        ) {
            return MetricType.DURATION
        }
        if (lowercase.startsWith("pompki") || lowercase.contains("brzuszki") ||
            lowercase.contains("przysiad bw")
        ) {
            return MetricType.REPS_ONLY
        }
        return MetricType.WEIGHT_REPS
    }
}
