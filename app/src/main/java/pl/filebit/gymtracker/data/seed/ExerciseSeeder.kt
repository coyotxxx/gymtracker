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
     * Idempotentny seeder:
     *  - pusta baza → ładuje wszystko
     *  - baza z ćwiczeniami → dodaje TYLKO brakujące (po nazwie, case-insensitive)
     *
     * Plus: oznacza listę kanonicznych ćwiczeń jako ulubione (z xlsx Macieja —
     * ćwiczenia używane regularnie przez 3.5 roku planu treningowego).
     */
    suspend fun seedIfEmpty() {
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val seedList = json.decodeFromString<List<SeedExercise>>(raw)
        val seedEntities = seedList.map { s ->
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

        if (dao.count() == 0) {
            dao.insertAll(seedEntities)
        } else {
            // Dodaj tylko brakujące — po nazwie (case-insensitive)
            val existingNames = dao.allNamesLower().toHashSet()
            val newOnly = seedEntities.filter { it.name.lowercase() !in existingNames }
            if (newOnly.isNotEmpty()) dao.insertAll(newOnly)
        }

        // Oznacz kanoniczne ulubione (z xlsx Macieja) — idempotentne, można uruchamiać wielokrotnie
        markMacjiejFavorites()

        // v1.29.10: napraw cardio, które przez starszą ścieżkę ma metricType WEIGHT_REPS
        runCatching { dao.fixCardioMetricType() }
    }

    /**
     * Lista nazw ćwiczeń z xlsx Macieja (3.5 roku planów). UPDATE setting isFavorite=1
     * dla każdej nazwy która istnieje w bazie. Idempotentne.
     */
    private suspend fun markMacjiejFavorites() {
        val canonical = listOf(
            // Big lifts
            "Przysiad ze sztangą (back squat)",
            "Wyciskanie sztangi leżąc",
            "Martwy ciąg klasyczny",
            "Wiosłowanie sztangą",
            "Podciąganie nachwytem",
            "Podciąganie podchwytem (chin-up)",
            "Podciąganie z obciążeniem",
            "Podciąganie szerokim chwytem",
            "Wyciskanie żołnierskie (OHP)",
            "Wyciskanie sztangi - skos dodatni",

            // Klatka pomocnicze
            "Wyciskanie sztangielek leżąc",
            "Wyciskanie sztangielek - skos dodatni",
            "Pompki na poręczach (dipy)",
            "Dipy z obciążeniem",
            "Pompki",
            "Pompki diamentowe",
            "Pompki na barki (pike push-up)",
            "Pompki w podporze tyłem (bench dips)",
            "Pompki incline (dłonie na podwyższeniu)",
            "Pompki decline (stopy na podwyższeniu)",
            "Pompki z odrywaniem dłoni",
            "Rozpiętki sztangielkami",
            "Rozpiętki sztangielkami skos dodatni",
            "Pull-over sztangielką",

            // Plecy / barki pomocnicze
            "Wiosłowanie sztangielką (jednorącz)",
            "Szrugsy ze sztangą",
            "Szrugsy ze sztangielkami",
            "Wyciskanie sztangielek nad głowę",
            "Wyciskanie zza karku",
            "Wznosy bokiem (lateral raise)",
            "Wznosy przodem (front raise)",
            "Odwrotne rozpiętki (rear delt fly)",
            "Podciąganie sztangi pod brodę (upright row)",

            // Biceps / Triceps
            "Uginanie ramion ze sztangą",
            "Uginanie sztangielek (na biceps)",
            "Uginanie młotkowe",
            "Uginanie ramion z gryfem łamanym (EZ curl)",
            "Wyciskanie francuskie ze sztangą",
            "Skull crusher EZ (francuskie wyciskanie EZ)",
            "Francuskie wyciskanie hantlami",
            "Prostowanie ramion zza głowy sztangielką",

            // Nogi
            "Bułgarski przysiad",
            "Wykrok ze sztangielkami",
            "Wykrok kroczący (walking lunge)",
            "Good morning sztangą",
            "Hip thrust (wypchnięcie biodrami)",
            "Wspięcia na palce stojąc (calf raise)",
            "Wspięcia na palce jednonóż",
            "Wstawanie z krzesła jednonóż",

            // Core
            "Brzuszki",
            "Plank (deska)",
            "Side plank (deska boczna)",
            "Plank z przyciąganiem kolan do klatki",
            "Wznosy nóg w zwisie",
            "Toes to bar (T2B, palce do drążka)",
            "Spięcia na wyciągu (cable crunch)",
            "Russian twist (skręty rosyjskie)",
            "Martwy robak (dead bug)",
            "Świeca gimnastyczna",
            "Syzyfki (kopnięcia w bok)",

            // Cardio
            "Bieżnia (bieg)",
            "Bieżnia interwały (HIIT)",
            "Rower stacjonarny",
            "Skakanka",
            "Burpees (przysiad-pompka-skok)"
        )

        canonical.forEach { name ->
            runCatching { dao.markFavoriteByName(name) }
        }
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
