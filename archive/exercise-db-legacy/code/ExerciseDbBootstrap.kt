package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MetricType
import pl.filebit.gymtracker.data.entity.MuscleGroup
import javax.inject.Inject
import javax.inject.Singleton

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
     * v1.25.1: pre-translated PL nazwy + instrukcje (skrypt offline /tmp/translate_exercises.py).
     * Format: { "trmte8s": { "namePl": "...", "instructionsPl": ["...", "..."] } }
     */
    @Serializable
    data class ExerciseDbPlEntry(
        val namePl: String,
        val instructionsPl: List<String> = emptyList()
    )

    /**
     * Główne entry point. Idempotentne — można wywoływać przy każdym starcie.
     *
     * Etap A: fuzzy match istniejących PL ćwiczeń z ExerciseDB (zostaje user.name).
     * Etap B: import wszystkich pozostałych entries z DB jako nowe Exercise (name=EN).
     *   Po imporcie user ma ~1500 ćwiczeń (istniejące + nowe z ExerciseDB).
     *
     * @return Pair(matched, imported) — liczba zmatchowanych + nowo zaimportowanych
     */
    suspend fun bootstrap(): Pair<Int, Int> {
        val entries = loadEntries() ?: return 0 to 0
        if (entries.isEmpty()) return 0 to 0
        // v1.25.1: pre-translated PL data — name + instructions po polsku
        val plMap = loadPlEntries()

        // Etap A: fuzzy match istniejących PL ćwiczeń bez externalId
        val unmatched = dao.getAll().filter { it.externalId == null }
        var matchedCount = 0
        val usedExternalIds = mutableSetOf<String>()
        // Dodaj externalIds z już zmatchowanych (ze starszych bootstrap)
        dao.getAll().mapNotNull { it.externalId }.forEach { usedExternalIds.add(it) }

        for (exercise in unmatched) {
            val candidate = findBestMatch(exercise.name, exercise.primaryMuscle, entries)
            if (candidate != null && candidate.exerciseId !in usedExternalIds) {
                // v1.25.1: zachowaj user.name (PL już), ale dodaj instructionsPl od razu z cache
                val plData = plMap[candidate.exerciseId]
                dao.applyExerciseDbMatch(
                    id = exercise.id,
                    externalId = candidate.exerciseId,
                    gifUrl = candidate.gifUrl,
                    instructionsEnJson = serializeInstructions(candidate.instructions),
                    targetMusclesCsv = candidate.targetMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    secondaryMusclesCsv = candidate.secondaryMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    equipmentDbCsv = candidate.equipments.joinToString(",").takeIf { it.isNotBlank() },
                    bodyPartCsv = candidate.bodyParts.joinToString(",").takeIf { it.isNotBlank() }
                )
                // Polskie instrukcje od razu z pre-translated JSON
                plData?.instructionsPl?.takeIf { it.isNotEmpty() }?.let { steps ->
                    dao.updateInstructionsPl(exercise.id, serializeInstructions(steps) ?: return@let)
                }
                usedExternalIds.add(candidate.exerciseId)
                matchedCount++
            }
        }

        // Etap B: import niezmatchowanych entries jako nowe Exercise
        // v1.25.1: TYLKO z GIF + użyj pre-translated PL nazwy/instrukcji
        val toImport = entries.filter {
            it.exerciseId !in usedExternalIds && !it.gifUrl.isNullOrBlank()
        }
        var importedCount = 0
        val newExercises = toImport.map { entry ->
            val plData = plMap[entry.exerciseId]
            val name = plData?.namePl?.takeIf { it.isNotBlank() }
                ?: entry.name.replaceFirstChar { c -> c.uppercase() }
            val instructionsPlJson = plData?.instructionsPl
                ?.takeIf { it.isNotEmpty() }
                ?.let { serializeInstructions(it) }
            Exercise(
                name = name,
                primaryMuscle = inferMuscleGroup(entry),
                equipment = inferEquipment(entry),
                isCustom = false,
                metricType = MetricType.WEIGHT_REPS,
                description = "",
                isFavorite = false,
                isAvoided = false,
                externalId = entry.exerciseId,
                gifUrl = entry.gifUrl,
                instructionsEnJson = serializeInstructions(entry.instructions),
                instructionsPlJson = instructionsPlJson,
                targetMusclesCsv = entry.targetMuscles.joinToString(",").takeIf { it.isNotBlank() },
                secondaryMusclesCsv = entry.secondaryMuscles.joinToString(",").takeIf { it.isNotBlank() },
                equipmentDbCsv = entry.equipments.joinToString(",").takeIf { it.isNotBlank() },
                bodyPartCsv = entry.bodyParts.joinToString(",").takeIf { it.isNotBlank() }
            )
        }
        if (newExercises.isNotEmpty()) {
            val ids = dao.insertAll(newExercises)
            importedCount = ids.count { it > 0 }
        }

        // v1.25.5: Backfill polskich instrukcji dla istniejących ćwiczeń ze starszych
        // bootstrap'ów (z czasów przed cache PL), które już mają externalId ale nie
        // dostały instructionsPlJson — wcześniej Etap A pomijał je przez filter
        // externalId == null.
        var backfilledPl = 0
        for (ex in dao.getAll()) {
            if (ex.externalId == null) continue
            if (!ex.instructionsPlJson.isNullOrBlank()) continue
            val plData = plMap[ex.externalId] ?: continue
            val steps = plData.instructionsPl.takeIf { it.isNotEmpty() } ?: continue
            val json = serializeInstructions(steps) ?: continue
            dao.updateInstructionsPl(ex.id, json)
            backfilledPl++
        }

        // v1.25.6 + v1.26.7: Re-match po katalogu. Dwa źródła mapowań PL→exerciseId:
        //  1. CANONICAL_PL_MATCH — ~65 wpisów ręcznie zweryfikowanych
        //  2. exercisedb_v1_seed_match.json — 193 domyślne ćwiczenia (seed aplikacji),
        //     zmapowane przez AI + walidator słów kluczowych. Dzięki temu KAŻDE
        //     seed ćwiczenie dostaje GIF + instrukcje (wcześniej fuzzy match
        //     gubił ~120 z nich → user widział "Bieżnia", "Plank", "Hip thrust"
        //     bez GIFu w trakcie treningu).
        //
        // CANONICAL ma priorytet (ręcznie zweryfikowane). Etap działa też dla
        // ćwiczeń BEZ externalId (seed bez GIFu) — przypisuje brakujące dane.
        val entriesById = entries.associateBy { it.exerciseId }
        val seedMatch = loadSeedMatch()
        var rematchedCount = 0
        for (ex in dao.getAll()) {
            val key = normalize(ex.name)
            val canonicalId = CANONICAL_PL_MATCH[key] ?: seedMatch[key] ?: continue
            if (canonicalId == ex.externalId) continue  // już dobrze
            val newEntry = entriesById[canonicalId] ?: continue
            val plData = plMap[canonicalId]
            dao.forceReplaceExerciseDbMatch(
                id = ex.id,
                externalId = newEntry.exerciseId,
                gifUrl = newEntry.gifUrl,
                instructionsEnJson = serializeInstructions(newEntry.instructions),
                instructionsPlJson = plData?.instructionsPl
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { serializeInstructions(it) },
                targetMusclesCsv = newEntry.targetMuscles.joinToString(",").takeIf { it.isNotBlank() },
                secondaryMusclesCsv = newEntry.secondaryMuscles.joinToString(",").takeIf { it.isNotBlank() },
                equipmentDbCsv = newEntry.equipments.joinToString(",").takeIf { it.isNotBlank() },
                bodyPartCsv = newEntry.bodyParts.joinToString(",").takeIf { it.isNotBlank() }
            )
            rematchedCount++
        }

        // v1.25.7 — Etap F: alias remap. Po pre-dedup w assets niektóre stare
        // externalIds nie istnieją już w bazie (zostały zlane w grupę z kanonicznym
        // ID). aliasMap mówi old_id → canonical_id; jeśli user ma w bazie stary
        // externalId, podmieniamy go na canonical + przepisujemy wszystkie pola
        // z nowego entry.
        val aliasMap = loadAliases()
        if (aliasMap.isNotEmpty()) {
            for (ex in dao.getAll()) {
                val oldId = ex.externalId ?: continue
                val canonicalId = aliasMap[oldId] ?: continue
                val newEntry = entriesById[canonicalId] ?: continue
                val plData = plMap[canonicalId]
                dao.forceReplaceExerciseDbMatch(
                    id = ex.id,
                    externalId = newEntry.exerciseId,
                    gifUrl = newEntry.gifUrl,
                    instructionsEnJson = serializeInstructions(newEntry.instructions),
                    instructionsPlJson = plData?.instructionsPl
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { serializeInstructions(it) },
                    targetMusclesCsv = newEntry.targetMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    secondaryMusclesCsv = newEntry.secondaryMuscles.joinToString(",").takeIf { it.isNotBlank() },
                    equipmentDbCsv = newEntry.equipments.joinToString(",").takeIf { it.isNotBlank() },
                    bodyPartCsv = newEntry.bodyParts.joinToString(",").takeIf { it.isNotBlank() }
                )
            }
        }

        // v1.25.7 — Etap G: runtime deduplikacja w bazie aplikacji. Po wszystkich
        // poprzednich etapach mogą zostać duplikaty (różne ćwiczenia z tą samą
        // znormalizowaną nazwą + tym samym primaryMuscle):
        //  • user-defined "Pompki" + zaimportowane "pompki"
        //  • dwa importy z różnych v. bootstrap'u
        //  • re-match wprowadził kolizję z istniejącym ćwiczeniem
        //
        // Algorytm:
        //  1. Grupuj Exercise po (normalize(name), primaryMuscle)
        //  2. Dla grup >1: wybierz KANONICZNE wg priorytetu
        //  3. Re-link FK referencji (workout_sets, plan_exercises, goals, training_events)
        //  4. Usuń duplikaty Exercise
        //
        // Priorytet wyboru kanonicznego (najwyższy → najniższy):
        //  a. ma externalId i jest w CANONICAL_PL_MATCH (kanoniczna nazwa)
        //  b. ma externalId (z ExerciseDB — GIF + instrukcje)
        //  c. isFavorite (user oznaczył)
        //  d. ma więcej WorkoutSet history (był używany)
        //  e. najmniejsze id (najstarszy wpis)
        val allExercises = dao.getAll()
        val groupedByNorm = allExercises.groupBy {
            normalize(it.name) to it.primaryMuscle
        }
        var dedupedCount = 0
        for ((_, group) in groupedByNorm) {
            if (group.size <= 1) continue
            // Wybór kanonicznego
            val canonical = group.maxWithOrNull(Comparator { a, b ->
                // wyższy priorytet = wygrywa
                fun priority(e: Exercise): Int {
                    var p = 0
                    if (e.externalId != null && CANONICAL_PL_MATCH.containsValue(e.externalId)) p += 1000
                    if (e.externalId != null) p += 100
                    if (e.isFavorite) p += 10
                    // mniejsze id = starszy = +1 (lekka preferencja)
                    return p
                }
                val pa = priority(a); val pb = priority(b)
                if (pa != pb) pa.compareTo(pb)
                else b.id.compareTo(a.id)  // przy remisie: mniejsze id wygrywa (-> b<a -> b.compareTo(a) odwrotne)
            }) ?: continue

            for (dup in group) {
                if (dup.id == canonical.id) continue
                // Re-link wszystkich FK
                dao.relinkWorkoutSets(dup.id, canonical.id)
                dao.relinkPlanExercises(dup.id, canonical.id)
                dao.relinkGoals(dup.id, canonical.id)
                dao.relinkTrainingEvents(dup.id, canonical.id)
                // Zachowaj isFavorite/isAvoided na kanonicznym jeśli był w duplikacie
                if (dup.isFavorite && !canonical.isFavorite) {
                    dao.setFavorite(canonical.id, true)
                }
                dao.deleteById(dup.id)
                dedupedCount++
            }
        }

        // v1.25.9 — Etap H: cleanup ćwiczeń z martwym gifUrl. 171 wpisów w
        // ExerciseDB ma URL z HTTP 404 — większość (168) ma zamiennik w
        // alias_map, ale 3 nie mają sensownego ekwiwalentu. Dla tych odłączamy
        // pola ExerciseDB (zachowując user data: name, primaryMuscle, eq, fav,
        // history) — bez tego user widziałby pustą ramkę "ANIMACJA WYKONANIA".
        val deadIds = loadDeadIds().toSet()
        if (deadIds.isNotEmpty()) {
            for (ex in dao.getAll()) {
                val extId = ex.externalId ?: continue
                if (extId in deadIds) {
                    dao.clearExerciseDbFields(ex.id)
                }
            }
        }

        return matchedCount to importedCount
    }

    /** v1.25.9: lista exerciseId z martwym gifUrl (HTTP 404). */
    private fun loadDeadIds(): List<String> = runCatching {
        context.assets.open("exercisedb_v1_dead.json").use { stream ->
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(stream.bufferedReader().readText())
                as kotlinx.serialization.json.JsonArray
            root.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        }
    }.getOrDefault(emptyList())

    /** v1.25.7: alias map — exerciseId które zostały zlane do kanonicznych w pre-dedup. */
    private fun loadAliases(): Map<String, String> = runCatching {
        context.assets.open("exercisedb_v1_aliases.json").use { stream ->
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(stream.bufferedReader().readText())
                as kotlinx.serialization.json.JsonObject
            root.mapValues { (_, v) -> (v as kotlinx.serialization.json.JsonPrimitive).content }
        }
    }.getOrDefault(emptyMap())

    /**
     * v1.26.7: seed match — 193 domyślne ćwiczenia aplikacji (assets/exercises.json)
     * zmapowane na exerciseId z ExerciseDB. Klucz = znormalizowana nazwa PL.
     * Wygenerowane offline (AI mapping GPT-4o + walidator słów kluczowych).
     */
    private fun loadSeedMatch(): Map<String, String> = runCatching {
        context.assets.open("exercisedb_v1_seed_match.json").use { stream ->
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(stream.bufferedReader().readText())
                as kotlinx.serialization.json.JsonObject
            root.mapValues { (_, v) -> (v as kotlinx.serialization.json.JsonPrimitive).content }
        }
    }.getOrDefault(emptyMap())

    private fun loadPlEntries(): Map<String, ExerciseDbPlEntry> = runCatching {
        context.assets.open("exercisedb_v1_pl.json").use { stream ->
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(stream.bufferedReader().readText())
                as kotlinx.serialization.json.JsonObject
            root.mapValues { (_, value) ->
                json.decodeFromJsonElement(ExerciseDbPlEntry.serializer(), value)
            }
        }
    }.getOrDefault(emptyMap())

    private fun serializeInstructions(instructions: List<String>): String? {
        if (instructions.isEmpty()) return null
        return kotlinx.serialization.json.buildJsonArray {
            instructions.forEach {
                add(kotlinx.serialization.json.JsonPrimitive(it))
            }
        }.toString()
    }

    /**
     * Mapowanie ExerciseDB bodyParts/targetMuscles → MuscleGroup enum.
     * Pierwsze pasujące keyword wygrywa. Default: OTHER.
     */
    private fun inferMuscleGroup(entry: ExerciseDbEntry): MuscleGroup {
        val all = (entry.bodyParts + entry.targetMuscles).map { it.lowercase() }
        return when {
            all.any { it.contains("chest") || it.contains("pec") } -> MuscleGroup.CHEST
            all.any { it.contains("back") || it.contains("lat") || it.contains("trap") || it.contains("rhomboid") } -> MuscleGroup.BACK
            all.any { it.contains("shoulder") || it.contains("delt") } -> MuscleGroup.SHOULDERS
            all.any { it.contains("biceps") || it.contains("brachi") } -> MuscleGroup.BICEPS
            all.any { it.contains("triceps") } -> MuscleGroup.TRICEPS
            all.any { it.contains("quad") } -> MuscleGroup.QUADS
            all.any { it.contains("hamstring") } -> MuscleGroup.HAMSTRINGS
            all.any { it.contains("glute") } -> MuscleGroup.GLUTES
            all.any { it.contains("calf") || it.contains("calves") } -> MuscleGroup.CALVES
            all.any { it.contains("abs") || it.contains("abdom") || it.contains("core") || it.contains("oblique") || it.contains("waist") } -> MuscleGroup.CORE
            all.any { it.contains("cardio") } -> MuscleGroup.CARDIO
            // Upper arm bez biceps/triceps → BICEPS jako default
            all.any { it.contains("upper arm") } -> MuscleGroup.BICEPS
            // Lower legs bez calf → CALVES default
            all.any { it.contains("lower leg") } -> MuscleGroup.CALVES
            // Upper legs bez quad/hamstring/glute → QUADS default
            all.any { it.contains("upper leg") || it.contains("thigh") } -> MuscleGroup.QUADS
            // Neck → BACK (trapezius zwykle)
            all.any { it.contains("neck") } -> MuscleGroup.BACK
            // Forearms → BICEPS (najbliższe)
            all.any { it.contains("forearm") } -> MuscleGroup.BICEPS
            else -> MuscleGroup.OTHER
        }
    }

    /**
     * Mapowanie ExerciseDB equipments → Equipment enum.
     */
    private fun inferEquipment(entry: ExerciseDbEntry): Equipment {
        val all = entry.equipments.map { it.lowercase() }
        return when {
            all.any { it.contains("barbell") || it.contains("ez barbell") || it.contains("trap bar") } -> Equipment.BARBELL
            all.any { it.contains("dumbbell") } -> Equipment.DUMBBELLS
            all.any { it.contains("cable") || it.contains("rope") } -> Equipment.CABLE
            all.any { it.contains("machine") || it.contains("smith") || it.contains("leverage") || it.contains("sled") || it.contains("hammer") } -> Equipment.MACHINE
            all.any { it.contains("body weight") || it.contains("bodyweight") || it.isBlank() } -> Equipment.BODYWEIGHT
            else -> Equipment.OTHER
        }
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
        // v1.25.6 — krok 0: DIRECT match z kanonicznego katalogu
        val normalized = normalize(plName)
        val canonical = CANONICAL_PL_MATCH[normalized]
        if (canonical != null) {
            val entry = entries.firstOrNull { it.exerciseId == canonical }
            if (entry != null) return entry
        }

        val plTokens = normalized.split(" ").filter { it.isNotBlank() }.toSet()
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
        private const val MATCH_THRESHOLD = 0.55  // v1.25.6: zaostrzony próg (było 0.4)

        /**
         * v1.25.6: kanoniczny katalog mapowań PL→exerciseId. Klucze to znormalizowane
         * polskie nazwy (lowercase, ASCII, bez znaków specjalnych). DIRECT match nie
         * używa fuzzy — gwarantuje poprawne dopasowanie dla popularnych ćwiczeń.
         *
         * Tu trafiają nazwy które fuzzy myli (np. "Pompki na poręczach (dipy)" →
         * push-up zamiast chest dip). Listę poszerzaj gdy widzisz błędny match.
         */
        private val CANONICAL_PL_MATCH = mapOf(
            // Pompki / dipy
            "pompki" to "I4hDWkc",                          // push-up
            "pompki na kolanach" to "ZOuKWir",              // kneeling push-up
            "pompki na poreczach" to "9WTm7dq",             // chest dip
            "pompki na poreczach (dipy)" to "9WTm7dq",      // chest dip
            "pompki na poreczach dipy" to "9WTm7dq",
            "dipy" to "9WTm7dq",                            // chest dip
            "dipy na poreczach" to "9WTm7dq",
            // Wyciskania klatka
            "wyciskanie sztangi" to "EIeI8Vf",              // barbell bench press
            "wyciskanie sztangi na lawce poziomej" to "EIeI8Vf",
            "wyciskanie sztangi na lawce skosnej" to "3TZduzM",  // incline
            "wyciskanie hantli" to "SpYC0Kp",               // dumbbell bench press
            "wyciskanie hantli na lawce poziomej" to "SpYC0Kp",
            "wyciskanie hantli na lawce skosnej" to "ns0SIbU",
            "wyciskanie hantli siedzac" to "znQUdHY",       // seated shoulder press
            "wyciskanie hantli nad glowe siedzac" to "znQUdHY",
            "wyciskanie nad glowe" to "A6wtbuL",            // dumbbell standing overhead press
            // Suwnica / leg press / przysiad
            "suwnica" to "10Z2DXU",                         // sled 45° leg press
            "suwnica (leg press)" to "10Z2DXU",
            "suwnica leg press" to "10Z2DXU",
            "leg press" to "10Z2DXU",
            "przysiad" to "qXTaZnJ",                        // barbell full squat
            "przysiad ze sztanga" to "qXTaZnJ",
            "przysiady" to "qXTaZnJ",
            "przysiady ze sztanga" to "qXTaZnJ",
            // Martwy ciąg
            "martwy ciag" to "ila4NZS",                     // barbell deadlift
            "martwy ciag ze sztanga" to "ila4NZS",
            "martwy ciag na prostych nogach" to "wQ2c4XD", // romanian deadlift
            "rumunski martwy ciag" to "wQ2c4XD",
            // Podciąganie
            "podciaganie" to "lBDjFxJ",                     // pull-up
            "podciaganie nachwytem" to "lBDjFxJ",
            "podciaganie podchwytem" to "T2mxWqc",          // chin-up
            "podciaganie na drazku" to "lBDjFxJ",
            // Biceps
            "uginanie ramion" to "25GPyDY",                 // barbell curl
            "uginanie ramion ze sztanga" to "25GPyDY",
            "uginanie ramion z hantlami" to "NbVPDMW",      // dumbbell biceps curl
            "uginania na biceps" to "25GPyDY",
            "biceps curl" to "NbVPDMW",
            // Triceps
            "prostowanie ramion" to "3ZflifB",              // cable pushdown
            "prostowanie ramion na wyciagu" to "3ZflifB",
            "scinanie wyciagu" to "3ZflifB",
            "wyciskanie francuskie" to "iZop9xO",           // barbell lying triceps extension
            "francuskie ze sztanga" to "iZop9xO",
            // Plecy
            "wioslowanie" to "eZyBC3j",                     // barbell bent over row
            "wioslowanie sztanga" to "eZyBC3j",
            "wioslowanie ze sztanga" to "eZyBC3j",
            "wioslowanie hantla" to "BJ0Hz5L",              // dumbbell bent over row
            "wioslowanie hantlem w opadzie" to "BJ0Hz5L",
            // Klatka — rozpiętki
            "rozpietki" to "yz9nUhF",                       // dumbbell fly
            "rozpietki hantlami" to "yz9nUhF",
            "rozpietki w opadzie" to "8DiFDVA",             // dumbbell rear fly
            // Barki
            "wznosy bokiem" to "DsgkuIt",                   // dumbbell lateral raise
            "wzosy bokiem" to "DsgkuIt",                    // typo z xlsx
            "wznosy bokiem z hantlami" to "DsgkuIt",
            "wznosy bokiem ze sztangielkami" to "DsgkuIt",
            "wznosy w przod" to "3eGE2JC",                  // dumbbell front raise
            "wznosy w przod ze sztangielkami" to "3eGE2JC",
            // Nogi
            "wykroki" to "RRWFUcw",                         // dumbbell lunge
            "wykroki z hantlami" to "RRWFUcw",
            "wykroki ze sztangielkami" to "RRWFUcw",
            "uginanie nog" to "17lJ1kr",                    // lever lying leg curl
            "uginanie nog lezac" to "17lJ1kr",
            "prostowanie nog" to "my33uHU",                 // lever leg extension
            "prostowanie nog siedzac" to "my33uHU",
            "wspiecia" to "8ozhUIZ",                        // barbell standing calf raise
            "wspiecia na palce" to "8ozhUIZ",
            "good morning" to "XlZ4lAC"                     // barbell good morning
        )

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
            // v1.25.6: dipy + poręcze (wcześniej brak — dlatego push-up wygrywało)
            "dip" to setOf("dip", "dips"),
            "dipy" to setOf("dip", "dips"),
            "porecz" to setOf("parallel", "bars"),
            "poreczach" to setOf("parallel", "bars"),
            "poreczy" to setOf("parallel", "bars"),
            "francuskie" to setOf("triceps", "extension", "lying"),
            "scinanie" to setOf("pushdown"),
            "suwnica" to setOf("leg", "press", "sled"),
            "wspiecia" to setOf("calf", "raise"),
            "rumunski" to setOf("romanian"),
            "biceps" to setOf("biceps", "curl"),
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
