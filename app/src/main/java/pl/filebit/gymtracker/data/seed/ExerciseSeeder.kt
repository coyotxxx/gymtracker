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

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val seedList = json.decodeFromString<List<SeedExercise>>(raw)
        val entities = seedList.map { s ->
            Exercise(
                name = s.name,
                primaryMuscle = runCatching { MuscleGroup.valueOf(s.primaryMuscle) }
                    .getOrDefault(MuscleGroup.OTHER),
                equipment = runCatching { Equipment.valueOf(s.equipment) }
                    .getOrDefault(Equipment.OTHER),
                isCustom = false,
                metricType = inferMetricType(s.name, s.primaryMuscle),
                description = s.description
            )
        }
        dao.insertAll(entities)
    }

    /**
     * Heurystyka: rozpoznaje kardio (CARDIO) i izometryczne po nazwie.
     */
    private fun inferMetricType(name: String, muscle: String): MetricType {
        val lowercase = name.lowercase()
        // Cardio: bieżnia/biega/rower/orbitrek/maszyna eliptyczna/wioślarz/skakanka
        if (muscle == "CARDIO" || lowercase.contains("bieżnia") ||
            lowercase.contains("biega") || lowercase.contains("rower") ||
            lowercase.contains("orbitrek") || lowercase.contains("eliptyczn") ||
            lowercase.contains("wioślar") || lowercase.contains("skakank") ||
            lowercase.contains("spinning")
        ) {
            return MetricType.DISTANCE_DURATION
        }
        // Izometryczne: plank, deska, hold, statyczne
        if (lowercase.contains("plank") || lowercase.contains("deska") ||
            lowercase.contains("hold") || lowercase.contains("statyczn") ||
            lowercase.contains("zwis")
        ) {
            return MetricType.DURATION
        }
        // Pompki bodyweight bez obciążenia
        if (lowercase.startsWith("pompki") || lowercase.contains("brzuszki") ||
            lowercase.contains("przysiad bw")
        ) {
            return MetricType.REPS_ONLY
        }
        return MetricType.WEIGHT_REPS
    }
}
