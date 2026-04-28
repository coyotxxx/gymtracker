package pl.filebit.gymtracker.data.seed

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup

@Serializable
private data class SeedExercise(
    val name: String,
    val primaryMuscle: String,
    val equipment: String
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
                isCustom = false
            )
        }
        dao.insertAll(entities)
    }
}
