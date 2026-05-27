package pl.filebit.gymtracker.data.repository

import android.content.Context
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.ExerciseCategory
import pl.filebit.gymtracker.data.entity.Force
import pl.filebit.gymtracker.data.entity.KineticChain
import pl.filebit.gymtracker.data.entity.Laterality
import pl.filebit.gymtracker.data.entity.Level
import pl.filebit.gymtracker.data.entity.Mechanic
import pl.filebit.gymtracker.data.entity.MetricType
import pl.filebit.gymtracker.data.entity.MovementPattern
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.Plane
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0.0 — bootstrap canonical exercise-db.
 *
 * Ładuje 1317 ćwiczeń z `assets/exercises_canonical/{slug}.json` do tabeli `exercises`.
 * GIF-y serwowane z Cloudflare R2: `https://pub-08c99eae34c0472292614316cf7ea4e9.r2.dev/gifs/{slug}.gif`.
 *
 * **Idempotentny:** każdy slug jest INSERT-em lub UPDATE-em po `slug` (UNIQUE). Pozwala
 * to dodać nowe ćwiczenia w przyszłości — wystarczy wrzucić nowy `{slug}.json` do
 * assets + GIF do R2, następny start aplikacji wykryje brakujący slug i zaimportuje.
 *
 * **Reimport historii (po Migration 65→66):**
 * Migration 65→66 zarchiwizowała workout_sets/plan_exercises/goals/training_events do
 * `_backup_*` tabel z polem `_legacy_exercise_name`. Po wypełnieniu exercises canonical
 * bootstrap fuzzy-matchuje nazwy z _backup_* na nowe ćwiczenia i przepisuje historię
 * na nowe ID. Te bez matchu trafiają jako Exercise(isCustom=true).
 */
@Singleton
class CanonicalExerciseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val exerciseDao: ExerciseDao
) {

    companion object {
        private const val ASSETS_DIR = "exercises_canonical"
        private const val INDEX_FILE = "_index.json"
        const val R2_BASE_URL = "https://pub-08c99eae34c0472292614316cf7ea4e9.r2.dev/gifs"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun bootstrap() {
        val indexEntries = loadIndex() ?: return
        var inserted = 0
        var updated = 0
        var failed = 0

        for (entry in indexEntries) {
            try {
                val canonical = loadCanonical(entry.slug) ?: continue
                val existing = exerciseDao.findBySlug(entry.slug)
                if (existing == null) {
                    exerciseDao.insertAll(listOf(canonical.toEntity(slug = entry.slug)))
                    inserted++
                } else {
                    // Zachowaj user fields (isFavorite, isAvoided, notes, isCustom, metricType) z existing
                    exerciseDao.updateBySlug(
                        canonical.toEntityForUpdate(existing.id, slug = entry.slug)
                            .copy(
                                isFavorite = existing.isFavorite,
                                isAvoided = existing.isAvoided,
                                notes = existing.notes,
                                isCustom = existing.isCustom,
                                metricType = existing.metricType
                            )
                    )
                    updated++
                }
            } catch (t: Throwable) {
                failed++
                android.util.Log.w("CanonicalBootstrap", "Failed slug=${entry.slug}: ${t.message}")
            }
        }

        android.util.Log.i(
            "CanonicalBootstrap",
            "Done: $inserted inserted, $updated updated, $failed failed (total ${indexEntries.size})"
        )

        reimportHistoryIfNeeded()
    }

    // ---------- LOADERS ----------

    private fun loadIndex(): List<IndexEntry>? {
        return try {
            val text = context.assets.open("$ASSETS_DIR/$INDEX_FILE").bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text).jsonObject
            root["exercises"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val slug = o["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                IndexEntry(
                    slug = slug,
                    namePl = o["namePl"]?.jsonPrimitive?.contentOrNull ?: slug,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: slug,
                    category = o["category"]?.jsonPrimitive?.contentOrNull ?: "strength"
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("CanonicalBootstrap", "Failed to load index: ${t.message}", t)
            null
        }
    }

    private fun loadCanonical(slug: String): CanonicalExercise? {
        return try {
            val text = context.assets.open("$ASSETS_DIR/$slug.json").bufferedReader().use { it.readText() }
            parseCanonical(text)
        } catch (t: Throwable) {
            android.util.Log.w("CanonicalBootstrap", "Skipping malformed JSON: $slug — ${t.message}")
            null
        }
    }

    private fun parseCanonical(text: String): CanonicalExercise {
        val root = json.parseToJsonElement(text).jsonObject
        val classification = root["classification"]?.jsonObject
        val muscles = root["muscles"]?.jsonObject
        val muscleIntensity = root["muscle_intensity"]?.jsonObject
        val equipmentObj = root["equipment"]?.jsonObject
        val level = root["level"]?.jsonObject
        val coaching = root["coaching"]?.jsonObject
        val media = root["media"]?.jsonObject

        return CanonicalExercise(
            id = root["id"]?.jsonPrimitive?.contentOrNull,
            name = root["name"]?.jsonPrimitive?.contentOrNull ?: "",
            namePl = root["namePl"]?.jsonPrimitive?.contentOrNull ?: "",
            description = root["description"]?.jsonPrimitive?.contentOrNull ?: "",
            descriptionPl = root["descriptionPl"]?.jsonPrimitive?.contentOrNull ?: "",
            aliases = root["aliases"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            aliasesPl = root["aliasesPl"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            instructions = root["instructions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            instructionsPl = root["instructionsPl"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            movementPattern = classification?.get("movement_pattern")?.jsonPrimitive?.contentOrNull,
            force = classification?.get("force")?.jsonPrimitive?.contentOrNull,
            mechanic = classification?.get("mechanic")?.jsonPrimitive?.contentOrNull,
            laterality = classification?.get("laterality")?.jsonPrimitive?.contentOrNull,
            plane = classification?.get("plane")?.jsonPrimitive?.contentOrNull,
            kineticChain = classification?.get("kinetic_chain")?.jsonPrimitive?.contentOrNull,
            musclesPrimary = muscles?.get("primary")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            musclesSecondary = muscles?.get("secondary")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            musclesStabilizers = muscles?.get("stabilizers")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            muscleIntensity = muscleIntensity?.entries?.associate {
                it.key to (it.value.jsonPrimitive.contentOrNull ?: "")
            } ?: emptyMap(),
            equipmentRequired = equipmentObj?.get("required")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            equipmentOptional = equipmentObj?.get("optional")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            category = root["category"]?.jsonPrimitive?.contentOrNull,
            levelMin = level?.get("minimum")?.jsonPrimitive?.contentOrNull,
            difficulty = level?.get("difficulty_1_10")?.jsonPrimitive?.intOrNull,
            prerequisites = level?.get("prerequisites")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            progressionTo = level?.get("progression_to")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            alternatives = level?.get("alternatives")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            coachingCues = serializeCoaching(coaching),
            commonFaults = coaching?.get("common_faults")?.jsonArray?.toString() ?: "[]",
            contraindications = root["contraindications"]?.jsonArray?.toString(),
            tags = root["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            mediaPrimary = media?.get("primary")?.jsonPrimitive?.contentOrNull,
            framesDir = media?.get("frames_extracted")?.jsonArray?.firstOrNull()
                ?.jsonPrimitive?.contentOrNull?.substringBeforeLast("/")
        )
    }

    private fun serializeCoaching(coaching: JsonObject?): String {
        if (coaching == null) return "{}"
        return buildString {
            append("{")
            val keys = listOf(
                "setup_cues", "setup_cues_pl", "execution_cues", "execution_cues_pl",
                "breathing", "breathingPl"
            )
            val parts = keys.mapNotNull { k ->
                coaching[k]?.let { v -> "\"$k\":${v}" }
            }
            append(parts.joinToString(","))
            append("}")
        }
    }

    // ---------- MAPPING TO ENTITY ----------

    private fun CanonicalExercise.toEntity(slug: String): Exercise {
        val primaryMuscleEnum = mapMuscleGroup(musclesPrimary.firstOrNull())
        val equipmentEnum = mapEquipment(equipmentRequired.firstOrNull())
        val categoryEnum = ExerciseCategory.fromString(category)
        val metricTypeInferred = inferMetricType(categoryEnum, equipmentEnum, slug)

        return Exercise(
            id = 0L,
            name = namePl.ifBlank { name },
            primaryMuscle = primaryMuscleEnum,
            equipment = equipmentEnum,
            isCustom = false,
            notes = "",
            metricType = metricTypeInferred,
            description = descriptionPl.ifBlank { description },
            isFavorite = false,
            isAvoided = false,
            externalId = slug,
            gifUrl = "$R2_BASE_URL/$slug.gif",
            instructionsEnJson = jsonArrayString(instructions),
            instructionsPlJson = jsonArrayString(instructionsPl),
            targetMusclesCsv = musclesPrimary.joinToString(",").ifBlank { null },
            secondaryMusclesCsv = musclesSecondary.joinToString(",").ifBlank { null },
            equipmentDbCsv = (equipmentRequired + equipmentOptional).joinToString(",").ifBlank { null },
            bodyPartCsv = primaryMuscleEnum.name.lowercase(),
            searchAliases = buildSearchAliases(this),
            slug = slug,
            namePl = namePl,
            descriptionPl = descriptionPl,
            aliasesEnJson = jsonArrayString(aliases),
            aliasesPlJson = jsonArrayString(aliasesPl),
            framesDirUrl = framesDir?.let { "$R2_BASE_URL/$it" },
            category = categoryEnum,
            movementPattern = MovementPattern.fromString(movementPattern),
            mechanic = Mechanic.fromString(mechanic),
            force = Force.fromString(force),
            kineticChain = KineticChain.fromString(kineticChain),
            plane = Plane.fromString(plane),
            laterality = Laterality.fromString(laterality),
            levelMin = Level.fromString(levelMin),
            difficulty1To10 = difficulty,
            muscleIntensityJson = serializeMap(muscleIntensity),
            coachingCuesJson = coachingCues,
            commonFaultsJson = commonFaults,
            contraindicationsJson = contraindications,
            prerequisitesJson = jsonArrayString(prerequisites),
            progressionToJson = jsonArrayString(progressionTo),
            alternativesJson = jsonArrayString(alternatives),
            tagsJson = jsonArrayString(tags),
            equipmentOptionalCsv = equipmentOptional.joinToString(",").ifBlank { null },
            searchIndex = buildSearchIndex(this)
        )
    }

    /** UPDATE — zachowuje user-fields (isFavorite, isAvoided, notes) z existing. */
    private fun CanonicalExercise.toEntityForUpdate(existingId: Long, slug: String): Exercise {
        val baseline = toEntity(slug)
        return baseline.copy(id = existingId)
    }

    // ---------- ENUM MAPPERS ----------

    private fun mapMuscleGroup(name: String?): MuscleGroup {
        if (name.isNullOrBlank()) return MuscleGroup.OTHER
        val lower = name.lowercase()
        return when {
            lower.contains("pectoralis") || lower == "chest" -> MuscleGroup.CHEST
            lower.contains("latissimus") || lower.contains("trapezius") || lower.contains("rhomboid") ||
                lower.contains("erector_spinae") || lower.contains("teres") || lower.contains("infraspinatus") ||
                lower == "back" -> MuscleGroup.BACK
            lower.contains("deltoid") || lower == "shoulders" -> MuscleGroup.SHOULDERS
            lower.contains("biceps_brachii") || lower.contains("brachialis") || lower.contains("brachioradialis") ||
                lower == "biceps" -> MuscleGroup.BICEPS
            lower.contains("triceps") -> MuscleGroup.TRICEPS
            lower.contains("quadriceps") || lower == "quads" -> MuscleGroup.QUADS
            lower.contains("hamstrings") || lower.contains("biceps_femoris") || lower.contains("semitendinosus") ||
                lower.contains("semimembranosus") -> MuscleGroup.HAMSTRINGS
            lower.contains("gluteus") || lower == "glutes" -> MuscleGroup.GLUTES
            lower.contains("gastrocnemius") || lower.contains("soleus") || lower == "calves" -> MuscleGroup.CALVES
            lower.contains("rectus_abdominis") || lower.contains("obliques") || lower.contains("transverse_abdominis") ||
                lower == "core" || lower == "abs" -> MuscleGroup.CORE
            lower.contains("cardiovascular") || lower == "cardio" || lower == "heart" -> MuscleGroup.CARDIO
            else -> MuscleGroup.OTHER
        }
    }

    private fun mapEquipment(name: String?): Equipment {
        if (name.isNullOrBlank()) return Equipment.BODYWEIGHT
        val lower = name.lowercase()
        return when {
            lower == "barbell" || lower.contains("ez_barbell") || lower.contains("ez-barbell") -> Equipment.BARBELL
            lower == "dumbbell" || lower == "dumbbells" -> Equipment.DUMBBELLS
            lower == "cable" || lower.contains("cable") -> Equipment.CABLE
            lower == "machine" || lower.contains("lever") || lower.contains("smith") -> Equipment.MACHINE
            lower == "bodyweight" || lower == "body_weight" -> Equipment.BODYWEIGHT
            else -> Equipment.OTHER
        }
    }

    private fun inferMetricType(category: ExerciseCategory, equipment: Equipment, slug: String): MetricType {
        if (category == ExerciseCategory.CARDIO) return MetricType.DISTANCE_DURATION
        val lower = slug.lowercase()
        if (lower.contains("plank") || lower.contains("hold") || lower.contains("l-sit") ||
            lower.contains("isometric") || lower.contains("dead-hang") || lower.contains("wall-sit")) {
            return MetricType.DURATION
        }
        if (equipment == Equipment.BODYWEIGHT) {
            return MetricType.REPS_ONLY
        }
        return MetricType.WEIGHT_REPS
    }

    // ---------- HELPERS ----------

    private fun jsonArrayString(items: List<String>): String? {
        if (items.isEmpty()) return null
        return items.joinToString(",", prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
    }

    private fun serializeMap(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "\"${k}\":\"${v}\""
        }
    }

    private fun buildSearchAliases(c: CanonicalExercise): String {
        // v2.3.0: searchAliases zostaje dla backward compat (UI moze go pokazac),
        // ale faktyczny silnik wyszukiwania uzywa pola searchIndex (zob. buildSearchIndex).
        val parts = (listOf(c.name) + c.aliases + listOf(c.namePl) + c.aliasesPl)
            .filter { it.isNotBlank() }
            .distinct()
        return parts.joinToString(", ")
    }

    /**
     * v2.3.0 — kompleksowy pre-computowany index do wyszukiwania.
     *
     * Zawiera (połączone spacjami, lowercase):
     *  1. Cale frazy: name (EN), namePl (PL), wszystkie aliases EN+PL
     *  2. ASCII-fold wersje PL (bez diakrytykow + bez ł) - "podciaganie" matchuje "podciąganie"
     *  3. Pojedyncze tokeny (kazde slowo z fraz) - "sztanga" matchuje "Przysiad ze sztangą"
     *  4. Synonimy potoczne dla niektórych grup mięśni i sprzętu
     *
     * Format: " token1 token2 fraza1 fraza2 fold1 fold2 " (space-padded żeby LIKE '% q%' znajdował początek)
     */
    private fun buildSearchIndex(c: CanonicalExercise): String {
        val combiningMarks = Regex("[\\u0300-\\u036f]+")
        fun asciiFold(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace("ł", "l").replace("Ł", "L")
            .lowercase()

        // 1. Wszystkie cale frazy (PL+EN+aliases)
        val phrases = (listOf(c.name, c.namePl) + c.aliases + c.aliasesPl)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toMutableSet()

        // 2. ASCII-fold dla wszystkich (bez polskich znakow)
        val folded = phrases.map { asciiFold(it) }.toSet()
        phrases.addAll(folded)

        // 3. Tokeny: kazde slowo osobno (min 2 znaki)
        val tokens = mutableSetOf<String>()
        phrases.forEach { phrase ->
            phrase.split(Regex("[\\s\\-\\(\\)/,]+"))
                .filter { it.length >= 2 }
                .forEach { tokens.add(it) }
        }

        // 4. Synonimy potoczne (Polish gym slang)
        addColloquialSynonyms(c, tokens)

        // Połącz: tokeny + frazy. Padding spacjami żeby LIKE '% q%' znajdował początek tokenu.
        val all = (phrases + tokens).distinct().sorted()
        return " " + all.joinToString(" ") + " "
    }

    private fun addColloquialSynonyms(c: CanonicalExercise, tokens: MutableSet<String>) {
        // Mapowanie potocznych slangów polskich na canonical mięśnie
        val musclePl = c.musclesPrimary.joinToString(" ").lowercase()
        if ("pectoralis" in musclePl || c.musclesPrimary.contains("pectoralis_major")) {
            tokens.addAll(listOf("klata", "klatka", "klate"))
        }
        if ("biceps_brachii" in musclePl) {
            tokens.addAll(listOf("bicek", "biceps", "bicepsa"))
        }
        if ("triceps" in musclePl) {
            tokens.addAll(listOf("tricek", "tris"))
        }
        if ("deltoid" in musclePl) {
            tokens.addAll(listOf("bark", "barki", "delty"))
        }
        if ("latissimus" in musclePl || "trapezius" in musclePl) {
            tokens.addAll(listOf("plecy", "lata", "lats"))
        }
        if ("quadriceps" in musclePl) {
            tokens.addAll(listOf("uda", "czworogłowe", "czworoglowe", "quady"))
        }
        if ("hamstrings" in musclePl || "biceps_femoris" in musclePl) {
            tokens.addAll(listOf("dwuglowe", "dwugłowe", "ham", "hamy"))
        }
        if ("gluteus" in musclePl) {
            tokens.addAll(listOf("posladki", "pośladki", "pośladek", "tylek"))
        }
        if ("gastrocnemius" in musclePl || "soleus" in musclePl) {
            tokens.addAll(listOf("lydki", "łydki", "calf"))
        }
        if ("rectus_abdominis" in musclePl || "obliques" in musclePl) {
            tokens.addAll(listOf("brzuch", "abs", "core", "kaloryfer"))
        }

        // Equipment slang
        c.equipmentRequired.forEach { eq ->
            when (eq.lowercase()) {
                "barbell" -> tokens.addAll(listOf("sztanga", "gryf"))
                "dumbbell" -> tokens.addAll(listOf("hantle", "hantel", "sztangielki"))
                "cable" -> tokens.addAll(listOf("wyciag", "wyciąg", "linka"))
                "machine" -> tokens.addAll(listOf("maszyna"))
                "bodyweight" -> tokens.addAll(listOf("bw", "ciezar ciala", "ciężar ciała"))
                "kettlebell" -> tokens.addAll(listOf("kettel", "odważnik"))
            }
        }
    }

    // ---------- HISTORY REIMPORT (po Migration 65→66) ----------

    private suspend fun reimportHistoryIfNeeded() {
        if (!hasBackupTable("_backup_workout_sets")) {
            android.util.Log.d("CanonicalBootstrap", "No _backup_* tables — fresh install or already reimported")
            return
        }

        // Buduj cache slug→id po nazwie/aliasach
        val all = exerciseDao.getAll()
        val byNamePl = all.filter { !it.namePl.isNullOrBlank() }.associate { it.namePl!!.lowercase().trim() to it.id }
        val byName = all.associate { it.name.lowercase().trim() to it.id }
        val byAlias = mutableMapOf<String, Long>()
        for (ex in all) {
            ex.searchAliases?.split(",")?.forEach { alias ->
                val a = alias.trim().lowercase()
                if (a.isNotBlank()) byAlias.putIfAbsent(a, ex.id)
            }
        }

        fun findIdByLegacyName(legacy: String?): Long? {
            if (legacy.isNullOrBlank()) return null
            val key = legacy.lowercase().trim()
            return byNamePl[key] ?: byName[key] ?: byAlias[key]
        }

        // Re-import workout_sets
        val customByName = mutableMapOf<String, Long>()
        suspend fun resolveExerciseId(legacy: String?): Long? {
            if (legacy.isNullOrBlank()) return null
            findIdByLegacyName(legacy)?.let { return it }
            customByName[legacy.lowercase().trim()]?.let { return it }
            // utwórz custom
            val custom = Exercise(
                name = legacy,
                primaryMuscle = MuscleGroup.OTHER,
                equipment = Equipment.OTHER,
                isCustom = true,
                slug = "custom-" + legacy.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-'),
                gifUrl = null
            )
            val ids = exerciseDao.insertAll(listOf(custom))
            val newId = ids.firstOrNull() ?: return null
            customByName[legacy.lowercase().trim()] = newId
            return newId
        }

        var setsRestored = 0
        var setsLostNoExercise = 0
        val workoutSetCols = listOf(
            "id", "workoutId", "exerciseId", "setIndex", "reps", "weight", "rpe",
            "restSeconds", "tempo", "isWarmup", "isPr", "notes", "completed",
            "createdAt", "metricType", "durationSec", "distanceM", "speedKmh"
        )
        val backupSets = readBackup("_backup_workout_sets")
        for (row in backupSets) {
            val legacy = row["_legacy_exercise_name"] as? String
            val newId = resolveExerciseId(legacy)
            if (newId == null) {
                setsLostNoExercise++
                continue
            }
            row["exerciseId"] = newId
            insertRow("workout_sets", workoutSetCols, row)
            setsRestored++
        }

        var peRestored = 0
        val planExCols = listOf(
            "id", "planId", "exerciseId", "orderIndex", "sets", "repsTarget",
            "weightTarget", "restSeconds", "notes", "tempo", "rpeTarget",
            "metricType", "durationSecTarget", "distanceMTarget", "speedKmhTarget"
        )
        val backupPe = readBackup("_backup_plan_exercises")
        for (row in backupPe) {
            val legacy = row["_legacy_exercise_name"] as? String
            val newId = resolveExerciseId(legacy)
            if (newId == null) continue
            row["exerciseId"] = newId
            insertRow("plan_exercises", planExCols, row)
            peRestored++
        }

        // Goals i training_events mają exerciseId nullable bez FK constraint — UPDATE wystarczy
        val goalsBackup = readBackup("_backup_goals")
        var goalsUpdated = 0
        for (row in goalsBackup) {
            val id = (row["id"] as? Number)?.toLong() ?: continue
            val legacy = row["_legacy_exercise_name"] as? String
            val newId = findIdByLegacyName(legacy)
            if (newId != null) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE goals SET exerciseId = ? WHERE id = ?",
                    arrayOf<Any>(newId, id)
                )
                goalsUpdated++
            }
        }

        val eventsBackup = readBackup("_backup_training_events")
        var eventsUpdated = 0
        for (row in eventsBackup) {
            val id = (row["id"] as? Number)?.toLong() ?: continue
            val legacy = row["_legacy_exercise_name"] as? String
            val newId = findIdByLegacyName(legacy)
            if (newId != null) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE training_events SET exerciseId = ? WHERE id = ?",
                    arrayOf<Any>(newId, id)
                )
                eventsUpdated++
            }
        }

        android.util.Log.i(
            "CanonicalBootstrap",
            "History reimport: sets=$setsRestored (lost=$setsLostNoExercise), pe=$peRestored, " +
                "goals=$goalsUpdated, events=$eventsUpdated, customs=${customByName.size}"
        )

        // Sprzątanie backup tabel
        db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS _backup_workout_sets")
        db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS _backup_plan_exercises")
        db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS _backup_goals")
        db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS _backup_training_events")
    }

    private fun hasBackupTable(name: String): Boolean {
        return try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$name'"
            )
            cursor.use { it.moveToFirst() }
        } catch (t: Throwable) {
            false
        }
    }

    private fun readBackup(table: String): List<MutableMap<String, Any?>> {
        val result = mutableListOf<MutableMap<String, Any?>>()
        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM $table")
        cursor.use { c ->
            val cols = c.columnNames
            while (c.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (i in cols.indices) {
                    row[cols[i]] = when {
                        c.isNull(i) -> null
                        else -> when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            android.database.Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                            else -> null
                        }
                    }
                }
                result.add(row)
            }
        }
        return result
    }

    private fun insertRow(table: String, columns: List<String>, row: Map<String, Any?>) {
        val pickedCols = columns.filter { row.containsKey(it) }
        val placeholders = pickedCols.joinToString(",") { "?" }
        val args = pickedCols.map { row[it] }.toTypedArray()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO $table (${pickedCols.joinToString(",")}) VALUES ($placeholders)",
            args
        )
    }

    // ---------- DATA CLASSES ----------

    private data class IndexEntry(val slug: String, val namePl: String, val name: String, val category: String)

    private data class CanonicalExercise(
        val id: String?,
        val name: String,
        val namePl: String,
        val description: String,
        val descriptionPl: String,
        val aliases: List<String>,
        val aliasesPl: List<String>,
        val instructions: List<String>,
        val instructionsPl: List<String>,
        val movementPattern: String?,
        val force: String?,
        val mechanic: String?,
        val laterality: String?,
        val plane: String?,
        val kineticChain: String?,
        val musclesPrimary: List<String>,
        val musclesSecondary: List<String>,
        val musclesStabilizers: List<String>,
        val muscleIntensity: Map<String, String>,
        val equipmentRequired: List<String>,
        val equipmentOptional: List<String>,
        val category: String?,
        val levelMin: String?,
        val difficulty: Int?,
        val prerequisites: List<String>,
        val progressionTo: List<String>,
        val alternatives: List<String>,
        val coachingCues: String,
        val commonFaults: String,
        val contraindications: String?,
        val tags: List<String>,
        val mediaPrimary: String?,
        val framesDir: String?
    )
}
