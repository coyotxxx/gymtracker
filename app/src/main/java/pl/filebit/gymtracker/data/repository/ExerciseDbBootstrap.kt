package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.MuscleGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * v1.25.0 — Bootstrap ExerciseDB (1500 ćwiczeń, MIT, GIFy z CDN Cloudflare).
 *
 * Przy starcie aplikacji wczytuje `assets/exercisedb_v1.json` i robi
 * fuzzy match z istniejącymi `Exercise` w bazie (nazwy polskie ↔ angielskie):
 *
 *  1. Każde ćwiczenie z DB ma `externalId` (np. "trmte8s"), `gifUrl`, `instructions` (list EN),
 *     `targetMuscles`, `bodyParts`, `equipments`, `secondaryMuscles`.
 *  2. Fuzzy match po nazwie (Levenshtein normalized) + filter po MuscleGroup hint
 *     (mapping z `bodyParts`/`targetMuscles` na enum MuscleGroup).
 *  3. Przy score >= 0.5 → auto-match → ExerciseDao.applyExerciseDbMatch.
 *  4. NIE nadpisujemy fields usera (name, primaryMuscle, equipment, isFavorite, isAvoided, notes).
 *  5. Idempotentne: jeśli ćwiczenie ma `externalId != null` → pomijamy.
 *
 * Po fuzzy match user widzi w detail ekranie:
 *   - GIF animacja (Coil 3, cache)
 *   - Mięśnie + sprzęt z DB (więcej szczegółów niż enum)
 *   - Technika krok po kroku (PL z AI on-demand, EN fallback)
 */
@Singleton
class ExerciseDbBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExerciseDao
) {

    @Serializable
    data class ExerciseDbEntry(
        val exerciseId: String,
        val name: String,
        val gifUrl: String? = null,
        val targetMuscles: List<String> = emptyList(),
        val bodyParts: List<String> = emptyList(),
        val equipments: List<String> = emptyList(),
        val secondaryMuscles: List<String> = emptyList(),
        val instructions: List<String> = emptyList()
    )

    /**
     * Główne entry point. Idempotentne — można wywoływać przy każdym starcie.
     * @return liczba ćwiczeń zmatchowanych w tym wywołaniu (0 jeśli już zrobione lub brak match'ów)
     */
    suspend fun bootstrap(): Int {
        // Idempotencja: jeśli już mamy ≥50% pokrycia z externalId, nie powtarzaj
        val total = dao.count()
        if (total == 0) return 0
        val matched = dao.countWithExternalId()
        if (matched > total / 2) return 0  // już zbootstrapowane

        val entries = loadEntries() ?: return 0
        val existingExercises = dao.getAll().filter { it.externalId == null }
        if (existingExercises.isEmpty()) return 0

        var matchedCount = 0
        for (exercise in existingExercises) {
            val candidate = findBestMatch(exercise.name, exercise.primaryMuscle, entries)
            if (candidate != null) {
                dao.applyExerciseDbMatch(
                    id = exercise.id,
                    externalId = candidate.exerciseId,
                    gifUrl = candidate.gifUrl,
                    instructionsEnJson = kotlinx.serialization.json.buildJsonArray {
                        candidate.instructions.forEach {
                            add(kotlinx.serialization.json.JsonPrimitive(it))
                        }
                    }.toString(),
                    targetMusclesCsv = candidate.targetMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    secondaryMusclesCsv = candidate.secondaryMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    equipmentDbCsv = candidate.equipments.joinToString(",").takeIf { it.isNotBlank() },
                    bodyPartCsv = candidate.bodyParts.joinToString(",").takeIf { it.isNotBlank() }
                )
                matchedCount++
            }
        }
        return matchedCount
    }

    private fun loadEntries(): List<ExerciseDbEntry>? = runCatching {
        context.assets.open("exercisedb_v1.json").use { stream ->
            Json { ignoreUnknownKeys = true }.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ExerciseDbEntry.serializer()),
                stream.bufferedReader().readText()
            )
        }
    }.getOrNull()

    /**
     * Fuzzy match: normalize names (lowercase, removed diacritics, split words),
     * compute Jaccard similarity na tokenach, plus MuscleGroup hint jako filter.
     * Próg 0.5 dla auto-match — wystarczająco wysoki by uniknąć false positives,
     * niski enough żeby polskie "Wyciskanie sztangi na ławce poziomej" zmatchowało
     * z angielskim "barbell bench press" (po normalizacji "wyciskanie sztanga
     * lawce poziomej" vs "barbell bench press" — bez wspólnych słów, ale...).
     *
     * Dlatego trzymamy mapping polskich keywords → english keywords (sprzęt, pozycja).
     */
    private fun findBestMatch(
        plName: String,
        plMuscle: MuscleGroup,
        entries: List<ExerciseDbEntry>
    ): ExerciseDbEntry? {
        val plTokens = normalize(plName).split(" ").filter { it.isNotBlank() }.toSet()
        val plKeywords = plToEnKeywords(plTokens)

        // Filter by MuscleGroup hint — bodyParts/targetMuscles → MuscleGroup mapping
        val candidates = entries.filter { entry ->
            muscleMatches(plMuscle, entry)
        }

        var best: ExerciseDbEntry? = null
        var bestScore = 0.0
        for (entry in candidates) {
            val enTokens = normalize(entry.name).split(" ").filter { it.isNotBlank() }.toSet()
            val intersection = enTokens.intersect(plKeywords).size
            val union = enTokens.union(plKeywords).size
            val score = if (union == 0) 0.0 else intersection.toDouble() / union
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        return if (bestScore >= MATCH_THRESHOLD) best else null
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace("ą", "a").replace("ć", "c").replace("ę", "e")
        .replace("ł", "l").replace("ń", "n").replace("ó", "o")
        .replace("ś", "s").replace("ź", "z").replace("ż", "z")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Mapping polskich keywords ćwiczeń na angielskie żeby fuzzy match miał szansę.
     * Lista nie jest kompletna — to "wystarczająco dobre" dla popularnych ćwiczeń.
     */
    private fun plToEnKeywords(plTokens: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        for (token in plTokens) {
            val mapped = PL_EN_MAP[token]
            if (mapped != null) result.addAll(mapped) else result.add(token)
        }
        return result
    }

    private fun muscleMatches(plMuscle: MuscleGroup, entry: ExerciseDbEntry): Boolean {
        val allMuscles = (entry.targetMuscles + entry.bodyParts + entry.secondaryMuscles)
            .map { it.lowercase() }
        return MUSCLE_GROUP_TO_DB[plMuscle]?.any { hint -> allMuscles.any { it.contains(hint) } } ?: true
    }

    companion object {
        private const val MATCH_THRESHOLD = 0.4  // Jaccard >= 40% → match

        // Mapping polskich słów keyword'owych na angielskie (popularne ćwiczenia)
        private val PL_EN_MAP = mapOf(
            // Sprzęt
            "sztanga" to setOf("barbell"),
            "sztangą" to setOf("barbell"),
            "sztangi" to setOf("barbell"),
            "hantle" to setOf("dumbbell"),
            "hantli" to setOf("dumbbell"),
            "hantlami" to setOf("dumbbell"),
            "maszyna" to setOf("machine"),
            "maszynie" to setOf("machine"),
            "wyciag" to setOf("cable"),
            "wyciagu" to setOf("cable"),
            "wyciagi" to setOf("cable"),
            "drazek" to setOf("pull", "bar"),
            "drazku" to setOf("pull", "bar"),
            "gryf" to setOf("barbell", "bar"),
            "podloga" to setOf("floor"),
            "podlodze" to setOf("floor"),
            "lawce" to setOf("bench"),
            "lawka" to setOf("bench"),
            // Pozycje
            "poziomej" to setOf("flat"),
            "skosnej" to setOf("incline"),
            "ujemnej" to setOf("decline"),
            "stojac" to setOf("standing"),
            "siedzac" to setOf("seated"),
            "lezac" to setOf("lying"),
            "leza" to setOf("lying"),
            // Ruchy / czasowniki
            "wyciskanie" to setOf("press", "bench"),
            "przysiad" to setOf("squat"),
            "przysiady" to setOf("squat"),
            "martwy" to setOf("deadlift"),
            "ciag" to setOf("deadlift", "row", "pull"),
            "ciagu" to setOf("row", "pull"),
            "ciagi" to setOf("row"),
            "podciaganie" to setOf("pull", "up"),
            "uginanie" to setOf("curl"),
            "uginania" to setOf("curl"),
            "prostowanie" to setOf("extension"),
            "rozpietki" to setOf("fly"),
            "wykroki" to setOf("lunge"),
            "wykrok" to setOf("lunge"),
            "wspiecia" to setOf("calf", "raise"),
            "wzdluz" to setOf("kickback"),
            "francuskie" to setOf("triceps", "extension"),
            "pompka" to setOf("pushup", "push", "up"),
            "pompki" to setOf("pushup", "push", "up"),
            "deska" to setOf("plank"),
            "brzuszki" to setOf("crunch", "situp"),
            // Partie ciała
            "klatki" to setOf("chest"),
            "klatka" to setOf("chest"),
            "piersiowej" to setOf("chest"),
            "plecow" to setOf("back"),
            "plecy" to setOf("back"),
            "ramiona" to setOf("shoulders", "shoulder"),
            "barkow" to setOf("shoulders", "shoulder"),
            "barki" to setOf("shoulders"),
            "biceps" to setOf("biceps", "bicep"),
            "bicepsa" to setOf("biceps", "bicep"),
            "tricepsa" to setOf("triceps", "tricep"),
            "triceps" to setOf("triceps", "tricep"),
            "nogi" to setOf("legs", "leg"),
            "noga" to setOf("leg"),
            "udo" to setOf("thigh", "quad"),
            "uda" to setOf("thigh", "quad"),
            "lydki" to setOf("calf", "calves"),
            "lydka" to setOf("calf"),
            "posladki" to setOf("glutes", "glute"),
            "posladkow" to setOf("glutes"),
            "brzuch" to setOf("abs", "abdominal", "core"),
            "brzucha" to setOf("abs"),
            "przedramion" to setOf("forearm"),
            // Często ignorowane słowa funkcyjne
            "na" to emptySet(),
            "w" to emptySet(),
            "z" to emptySet(),
            "i" to emptySet(),
            "do" to emptySet(),
            "od" to emptySet(),
            "po" to emptySet()
        )

        // MuscleGroup enum → keywords z ExerciseDB.bodyParts/targetMuscles
        private val MUSCLE_GROUP_TO_DB = mapOf(
            MuscleGroup.CHEST to setOf("chest", "pectoral", "pec"),
            MuscleGroup.BACK to setOf("back", "lat", "trap", "rhomboid", "neck"),
            MuscleGroup.SHOULDERS to setOf("shoulder", "delt", "rotator"),
            MuscleGroup.BICEPS to setOf("biceps", "brachi"),
            MuscleGroup.TRICEPS to setOf("triceps", "upper arm"),
            MuscleGroup.QUADS to setOf("quad", "thigh", "upper leg"),
            MuscleGroup.HAMSTRINGS to setOf("hamstring", "thigh", "upper leg"),
            MuscleGroup.GLUTES to setOf("glute", "hip"),
            MuscleGroup.CALVES to setOf("calf", "calves", "lower leg"),
            MuscleGroup.CORE to setOf("abs", "abdominal", "core", "oblique", "waist"),
            MuscleGroup.CARDIO to setOf("cardio")
        )
    }
}
