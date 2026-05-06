package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generator planu treningowego — DWUTOROWO:
 *
 * 1. Z LLM (Claude/OpenAI) — gdy user ma skonfigurowany klucz AI
 *    → Pełen prompt z profilem, sprzętem, ulubionymi, kontuzjami, celem,
 *      doświadczeniem, fazą diety. AI generuje JSON z planem dni × ćwiczenia × serie.
 *
 * 2. Fallback regułowy — gdy klucz AI nieskonfigurowany (offline, free user)
 *    → Deterministyczny algorytm: split per liczba dni, sets/reps per cel,
 *      losowe wybieranie ulubionych. Działa bez Internetu.
 *
 * Aplikacja musi działać OBYDWIE drogi (założenie zachowane od startu).
 */
@Singleton
class WorkoutPlanAiService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val planRepo: PlanRepository,
    private val contextBuilder: MasterAiContextBuilder
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class GeneratedPlanResult(
        val planId: Long,
        val planName: String,
        val daysCount: Int,
        val exercisesPerDay: Map<Int, List<String>>,
        val warnings: List<String>,
        /** True = użyto LLM, False = silnik regułowy (fallback). */
        val usedAi: Boolean
    )

    /**
     * Główne wejście. Decyduje czy AI czy fallback.
     */
    suspend fun generate(
        daysPerWeek: Int = 4,
        favoritesOnly: Boolean = true,
        planName: String? = null
    ): Result<GeneratedPlanResult> = runCatching {
        val cfg = prefs.load()
        val all = exerciseRepo.observeAll().first()
        val profile = profileRepo.get()
        val available = filterByEquipment(all, profile.availableEquipmentCsv)
        val pool = if (favoritesOnly) {
            val favs = available.filter { it.isFavorite }
            if (favs.size < 12) {
                (favs + available.filter { !it.isFavorite }).distinctBy { it.id }
            } else favs
        } else available

        Log.d("WorkoutPlanAi", "generate(): pool=${pool.size} favs=${pool.count { it.isFavorite }} cfg.isConnected=${cfg.isConnected} provider=${cfg.provider}")

        if (pool.size < 12) {
            return@runCatching GeneratedPlanResult(
                planId = -1L,
                planName = "—",
                daysCount = 0,
                exercisesPerDay = emptyMap(),
                warnings = listOf("Za mało ćwiczeń w bazie (${pool.size}/12) — nie mogę wygenerować planu."),
                usedAi = false
            )
        }

        if (cfg.isConnected) {
            // Spróbuj AI; jeśli się nie uda → fallback z KONKRETNYM błędem
            val aiResult = runCatching { generateWithAi(daysPerWeek, favoritesOnly, planName, cfg, profile, pool) }
            aiResult.getOrNull()?.let { return@runCatching it }
            val errMsg = aiResult.exceptionOrNull()?.message ?: "nieznany błąd AI"
            Log.e("WorkoutPlanAi", "AI generation failed: $errMsg", aiResult.exceptionOrNull())
            // Fallback z DOKŁADNYM komunikatem błędu (nie ukrywamy)
            val fallback = generateRuleBased(daysPerWeek, favoritesOnly, planName, profile, pool, aiFailed = true)
            fallback.copy(warnings = listOf("⚠ AI nie zadziałało: $errMsg") + fallback.warnings)
        } else {
            // Klucz nie skonfigurowany — fallback od razu z konkretnym powodem
            val fallback = generateRuleBased(daysPerWeek, favoritesOnly, planName, profile, pool, aiFailed = false)
            fallback.copy(warnings = listOf("ℹ️ Brak klucza AI — wpisz go w Profil → Ustawienia AI żeby otrzymać lepszy plan z LLM") + fallback.warnings)
        }
    }

    // =====================================================================
    // === AI PATH (z LLM)
    // =====================================================================

    @Serializable
    private data class AiPlanResponse(
        val days: List<AiDay>
    )

    @Serializable
    private data class AiDay(
        val dayOfWeek: Int,
        val muscleGroups: List<String> = emptyList(),
        val exercises: List<AiExercise>
    )

    @Serializable
    private data class AiExercise(
        val name: String,
        val movementType: String = "ACCESSORY",      // COMPOUND / ISOLATION / ACCESSORY
        val sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        val restSeconds: Int,
        val notes: String = ""
    )

    private suspend fun generateWithAi(
        daysPerWeek: Int,
        favoritesOnly: Boolean,
        planName: String?,
        cfg: AiConfig,
        profile: UserProfile,
        pool: List<Exercise>
    ): GeneratedPlanResult {
        val warnings = mutableListOf<String>()

        // === MASTER CONTEXT — ten sam co używa DietAi ===
        val masterCtx = runCatching { contextBuilder.build() }.getOrNull()

        // === POOL DOSTĘPNYCH ĆWICZEŃ ===
        val poolListing = pool.joinToString("\n") { ex ->
            val fav = if (ex.isFavorite) " ⭐" else ""
            "- ${ex.name}$fav (partia: ${ex.primaryMuscle.name}, sprzęt: ${ex.equipment.name})"
        }

        val (defaultSets, defaultReps, defaultRest) = setsConfigFor(profile.goal)

        val prompt = buildString {
            // === KRYTYCZNE — TYLKO JSON ===
            append("⚠ TYLKO JSON ⚠\n")
            append("ODPOWIEDŹ MUSI ZACZYNAĆ SIĘ ZNAKIEM `{` I KOŃCZYĆ `}`\n")
            append("ZERO TEKSTU PRZED JSON. ZERO MARKDOWN. ZERO ``` FENCES. ZERO KOMENTARZY.\n")
            append("Jeśli zaczniesz od '#' lub 'Oto plan' lub czegokolwiek innego niż '{' — system Cię odrzuci.\n\n")

            append("Jesteś profesjonalnym trenerem siłowym (jak Israetel/Helms/Schoenfeld). ")
            append("Twoja wiedza: split treningowy, SFR (stimulus-fatigue ratio), progresja, ")
            append("compound vs isolation, ratio objętości per partia. Wygeneruj plan.\n\n")

            // === MASTER CONTEXT (jeden obraz dla wszystkich AI) ===
            if (masterCtx != null) {
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append("- Liczba dni TEGO planu: $daysPerWeek\n")
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append("\n→ Plan ma uwzględniać:\n")
                append("  • Trend wagi (jeśli redukcja → mniej objętości)\n")
                append("  • Adherence (jeśli niska → uprość plan, mniej ćwiczeń)\n")
                append("  • Recovery (jeśli słaby sen/wysoki stres → DELOAD)\n")
                append("  • PRy (rekomendacja wag jako % maksów — np. 70% PR jako start)\n")
            } else {
                // Fallback gdy MasterContext się nie zbudował
                append("=== PROFIL ===\n")
                append("- Płeć: ${if (profile.gender.name == "MALE") "M" else "K"}\n")
                profile.bodyweightKg?.let { append("- Waga: $it kg\n") }
                append("- Cel: ${profile.goal.name}\n")
                append("- Dni: $daysPerWeek\n")
                if (profile.injuriesNotes.isNotBlank()) {
                    append("- ⚠ KONTUZJE: ${profile.injuriesNotes}\n")
                }
            }

            // ZASADY METODOLOGICZNE
            append("\n=== ZASADY DOBREGO PLANU ===\n")
            append("1. **SFR (Stimulus-Fatigue Ratio):** compound (wielostawowe) NA POCZĄTKU dnia (świeże), izolacje na końcu.\n")
            append("2. **Liczba ćwiczeń/dzień zależnie od czasu sesji:**\n")
            append("   - 30 min → 3 ćwiczenia\n")
            append("   - 45 min → 4 ćwiczenia\n")
            append("   - 60 min → 5-6 ćwiczeń\n")
            append("   - 90 min → 7-8 ćwiczeń\n")
            append("3. **Sets × reps zależnie od typu ruchu:**\n")
            append("   - COMPOUND (przysiad/martwy/wycisk/wiosłowanie/podciąganie):\n")
            append("     siła → 4-5 serii × 3-6 reps, 2-3 min rest\n")
            append("     hipertrofia → 4 serie × 6-10 reps, 90-120s rest\n")
            append("   - ISOLATION (uginanie ramion, wznosy bokiem, izolacje na nogi):\n")
            append("     hipertrofia → 3-4 serie × 10-15 reps, 60-90s rest\n")
            append("4. **Modulacja przez cel wagowy:**\n")
            append("   - CUT (redukcja): -1 ćwiczenie/dzień, więcej cardio (ochrona mięśni przy deficycie)\n")
            append("   - BULK (masa): +1 ćwiczenie/dzień, dłuższy odpoczynek (lepsza regeneracja)\n")
            append("   - MAINTAIN: standard\n")
            append("5. **Modulacja przez doświadczenie:**\n")
            append("   - BEGINNER: TYLKO compound, 3 dni full-body, prosta progresja, 2-3 ćwiczenia/dzień\n")
            append("   - INTERMEDIATE: split UPPER/LOWER lub PUSH/PULL/LEGS\n")
            append("   - ADVANCED: mocny split 5-6 dni, supersets, dropset\n")
            append("6. **Pokrycie partii w cyklu tygodnia:**\n")
            append("   - Każda duża partia (klatka/plecy/nogi) trafiona MIN 2× w tygodniu\n")
            append("   - Małe (biceps/triceps/barki) — mogą być z większymi (PUSH/PULL)\n")
            append("7. **Kolejność:** najpierw najtrudniejsze (compound nogi przed izolacjami nóg).\n")
            append("8. **Liczba serii efektywnych/tygodniowo na partię:**\n")
            append("   - klatka/plecy/nogi: 10-20 serii\n")
            append("   - barki/biceps/triceps: 6-12 serii\n")

            // SPLIT SUGESTIE
            append("\n=== SUGEROWANE SPLITY ===\n")
            append("$daysPerWeek dni / tydz:\n")
            when (daysPerWeek) {
                2 -> append("  - Pn: full body A | Czw: full body B\n")
                3 -> append("  - Pn: PUSH | Śr: PULL | Pt: LEGS  LUB  Pn/Śr/Pt: full body x3\n")
                4 -> append("  - Pn: UPPER | Wt: LOWER | Czw: UPPER | Pt: LOWER\n")
                5 -> append("  - Pn: PUSH | Wt: PULL | Śr: LEGS | Pt: UPPER | Sob: LOWER\n")
                6 -> append("  - Pn-Sb: Push/Pull/Legs × 2 (advanced)\n")
            }

            // POOL ĆWICZEŃ
            append("\n=== DOSTĘPNE ĆWICZENIA (TYLKO Z TEJ LISTY) ===\n")
            append("⭐ = ulubione użytkownika (PREFERUJ jak najczęściej)\n")
            append("Format: nazwa (partia, sprzęt)\n\n")
            append(poolListing)

            // OUTPUT
            append("\n\n=== OUTPUT (JSON, BEZ MARKDOWN) ===\n")
            append("Zwróć JSON z $daysPerWeek dniami. Nazwy DOKŁADNIE z listy. ")
            append("Format:\n")
            append("""
            {
              "days": [
                {
                  "dayOfWeek": 1,
                  "muscleGroups": ["CHEST","SHOULDERS","TRICEPS"],
                  "exercises": [
                    {
                      "name": "Wyciskanie sztangi leżąc",
                      "movementType": "COMPOUND",
                      "sets": 4,
                      "repsMin": 6,
                      "repsMax": 10,
                      "restSeconds": 120,
                      "notes": "Główne — pierwsze, świeże siły"
                    },
                    {
                      "name": "Wznosy bokiem (lateral raise)",
                      "movementType": "ISOLATION",
                      "sets": 3,
                      "repsMin": 12,
                      "repsMax": 15,
                      "restSeconds": 60,
                      "notes": ""
                    }
                  ]
                }
              ]
            }
            """.trimIndent())
            append("\n\nWAŻNE:\n")
            append("- Liczba dni = $daysPerWeek (dokładnie)\n")
            append("- dayOfWeek 1=PN, 2=WT, 3=ŚR, 4=CZW, 5=PT, 6=SB, 7=ND\n")
            append("- nazwy ćwiczeń DOKŁADNIE z listy DOSTĘPNE ĆWICZENIA (literówka = błąd)\n")
            append("- max ${if (profile.sessionMinutes <= 30) 3 else if (profile.sessionMinutes <= 45) 4 else if (profile.sessionMinutes <= 60) 6 else 8} ćwiczeń per dzień\n")
            append("- compound NA POCZĄTKU, izolacje NA KOŃCU\n\n")

            // OSTATNIE PRZYPOMNIENIE — najczęstszy błąd Claude
            append("=== KRYTYCZNE ===\n")
            append("Pierwszy znak Twojej odpowiedzi MUSI być '{'.\n")
            append("Ostatni znak MUSI być '}'.\n")
            append("Nie pisz 'Oto plan'. Nie pisz '# Plan'. Nie pisz nic poza JSON.\n")
            append("Jeśli złamiesz tę zasadę, system odrzuci odpowiedź i będę musiał Cię prosić o korektę.")
        }

        Log.d("WorkoutPlanAi", "Prompt length: ${prompt.length} chars, pool=${pool.size} ćwiczeń, days=$daysPerWeek, exp=${profile.experience}, goal=${profile.goal}")

        // === DWIE PRÓBY: pierwsza, potem retry z korektą ===
        var parsed: AiPlanResponse? = null
        var lastError: String? = null
        var currentMessages = listOf(AiMessage(AiRole.USER, prompt))

        for (attempt in 1..2) {
            val response = client.chat(cfg, currentMessages, source = "WorkoutPlanAi").fold(
                onSuccess = { it },
                onFailure = { throw IllegalStateException("Błąd komunikacji z AI: ${it.message}") }
            )
            Log.d("WorkoutPlanAi", "Attempt $attempt — response (first 500): ${response.take(500)}")

            val cleaned = stripJsonFences(response)
            try {
                parsed = json.decodeFromString<AiPlanResponse>(cleaned)
                break  // sukces
            } catch (e: Exception) {
                lastError = e.message?.take(200) ?: "parse error"
                Log.w("WorkoutPlanAi", "Attempt $attempt parse failed: $lastError")
                if (attempt < 2) {
                    // Retry z explicit korektą
                    currentMessages = listOf(
                        AiMessage(AiRole.USER, prompt),
                        AiMessage(AiRole.ASSISTANT, response),
                        AiMessage(AiRole.USER,
                            "Poprzednia odpowiedź NIE była poprawnym JSON. Błąd: $lastError\n\n" +
                            "ODPOWIEDŹ MUSI ZACZYNAĆ SIĘ OD '{' I KOŃCZYĆ NA '}'.\n" +
                            "ZERO tekstu, ZERO markdown, ZERO komentarzy. Tylko czysty JSON.\n" +
                            "Spróbuj ponownie — wygeneruj TEN SAM plan ale w poprawnym formacie."
                        )
                    )
                }
            }
        }

        if (parsed == null) {
            throw IllegalStateException("Po 2 próbach AI nadal nie zwróciło poprawnego JSON: $lastError")
        }

        if (parsed.days.isEmpty()) {
            throw IllegalStateException("AI zwróciło 0 dni treningowych w JSON")
        }

        // === WALIDACJA ===
        val byNameLower = pool.associateBy { it.name.lowercase() }
        val finalName = planName ?: "Plan ${daysPerWeek}-dniowy AI (${experienceLabel(profile.experience.name)})"
        val tplan = TrainingPlan(
            name = finalName,
            daysOfWeek = (1..daysPerWeek).toList(),
            notes = "Wygenerowany przez AI. Cel: ${goalLabelShort(profile.goal)}. " +
                "${if (favoritesOnly) "Z ulubionych" else "Z bazy"}. ${if (profile.weightGoalType != WeightGoalType.NONE) "Faza: ${weightGoalLabel(profile.weightGoalType)}." else ""}",
            createdByAi = true
        )
        val planId = planRepo.upsertPlan(tplan)

        var orderIdx = 0
        val exercisesByDay = mutableMapOf<Int, List<String>>()
        var totalAdded = 0
        var skipped = 0

        for (day in parsed.days.take(daysPerWeek)) {
            val names = mutableListOf<String>()
            for (aiEx in day.exercises) {
                val match = byNameLower[aiEx.name.trim().lowercase()]
                    ?: byNameLower.entries.firstOrNull { (k, _) -> k.contains(aiEx.name.lowercase()) || aiEx.name.lowercase().contains(k) }?.value
                if (match == null) {
                    skipped++
                    warnings += "Pominięto '${aiEx.name}' — brak w bazie"
                    continue
                }
                val pe = PlanExercise(
                    planId = planId,
                    exerciseId = match.id,
                    dayOfWeek = day.dayOfWeek.coerceIn(1, 7),
                    orderIndex = orderIdx++
                )
                val peId = planRepo.upsertPlanExercise(pe)
                val avgReps = ((aiEx.repsMin + aiEx.repsMax) / 2).coerceIn(1, 30)
                repeat(aiEx.sets.coerceIn(1, 8)) { i ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = i + 1,
                            reps = avgReps,
                            weightKg = null,
                            restSeconds = aiEx.restSeconds.coerceIn(20, 600),
                            setType = SetType.NORMAL
                        )
                    )
                }
                names += match.name
                totalAdded++
            }
            exercisesByDay[day.dayOfWeek] = names
        }

        Log.d("WorkoutPlanAi", "AI parsed: ${parsed.days.size} dni, $totalAdded ćwiczeń dodanych, $skipped pominiętych")

        if (totalAdded < 3) {
            // AI zwróciło prawie nic użytecznego — fallback
            planRepo.deletePlanById(planId)
            throw IllegalStateException(
                "AI zwróciło tylko $totalAdded użytecznych ćwiczeń ($skipped pominiętych — brak nazw w bazie)"
            )
        }
        if (skipped > totalAdded) {
            warnings += "⚠ AI pominęło więcej ćwiczeń ($skipped) niż dodało ($totalAdded) — odpowiedź była niedopasowana do bazy"
        }

        return GeneratedPlanResult(
            planId = planId,
            planName = finalName,
            daysCount = exercisesByDay.size,
            exercisesPerDay = exercisesByDay,
            warnings = warnings,
            usedAi = true
        )
    }

    // =====================================================================
    // === FALLBACK PATH (silnik regułowy bez AI)
    // =====================================================================

    private suspend fun generateRuleBased(
        daysPerWeek: Int,
        favoritesOnly: Boolean,
        planName: String?,
        profile: UserProfile,
        pool: List<Exercise>,
        aiFailed: Boolean
    ): GeneratedPlanResult {
        val warnings = mutableListOf<String>()
        if (aiFailed) warnings += "AI niedostępne — użyto trybu regułowego (offline)"

        val split = splitFor(daysPerWeek)
        val (setsPerEx, repsPerSet, restSec) = setsConfigFor(profile.goal)

        // Liczba ćwiczeń/dzień zależna od czasu sesji
        val exPerDay = when {
            profile.sessionMinutes <= 30 -> 3
            profile.sessionMinutes <= 45 -> 4
            profile.sessionMinutes <= 60 -> 5
            profile.sessionMinutes <= 75 -> 6
            else -> 7
        }
        // Modulacja przez cel wagowy
        val adjExPerDay = when (profile.weightGoalType) {
            WeightGoalType.CUT -> (exPerDay - 1).coerceAtLeast(3)
            WeightGoalType.BULK -> exPerDay + 1
            else -> exPerDay
        }

        val finalName = planName ?: "Plan ${daysPerWeek}-dniowy (auto)"
        val tplan = TrainingPlan(
            name = finalName,
            daysOfWeek = (1..daysPerWeek).toList(),
            notes = "Tryb offline. Cel: ${goalLabelShort(profile.goal)}. ${if (favoritesOnly) "Z ulubionych" else "Z bazy"}.",
            createdByAi = false
        )
        val planId = planRepo.upsertPlan(tplan)

        var orderIdx = 0
        val exercisesByDay = mutableMapOf<Int, List<String>>()
        split.forEachIndexed { dayIdx, dayMuscles ->
            val dayOfWeek = dayIdx + 1
            val perDay = pickExercisesForDay(pool, dayMuscles, adjExPerDay)
            val names = mutableListOf<String>()
            perDay.forEach { ex ->
                val pe = PlanExercise(
                    planId = planId,
                    exerciseId = ex.id,
                    dayOfWeek = dayOfWeek,
                    orderIndex = orderIdx++
                )
                val peId = planRepo.upsertPlanExercise(pe)
                repeat(setsPerEx) { i ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = i + 1,
                            reps = repsPerSet,
                            weightKg = null,
                            restSeconds = restSec,
                            setType = SetType.NORMAL
                        )
                    )
                }
                names += ex.name
            }
            exercisesByDay[dayOfWeek] = names
        }

        return GeneratedPlanResult(
            planId = planId,
            planName = finalName,
            daysCount = split.size,
            exercisesPerDay = exercisesByDay,
            warnings = warnings,
            usedAi = false
        )
    }

    // =====================================================================
    // === HELPERS (wspólne dla obu ścieżek)
    // =====================================================================

    private fun filterByEquipment(all: List<Exercise>, equipmentCsv: String): List<Exercise> {
        if (equipmentCsv.isBlank()) return all
        val allowed = equipmentCsv.split(",").mapNotNull {
            runCatching { Equipment.valueOf(it.trim()) }.getOrNull()
        }.toSet()
        if (allowed.isEmpty()) return all
        return all.filter { it.equipment in allowed }
    }

    private fun splitFor(days: Int): List<Set<MuscleGroup>> {
        val push = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
        val pull = setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
        val legs = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val core = setOf(MuscleGroup.CORE)
        val upper = push + pull + core
        val lower = legs + core
        val full = push + pull + legs + core

        return when (days.coerceIn(2, 6)) {
            2 -> listOf(full, full)
            3 -> listOf(push + core, pull + core, legs + core)
            4 -> listOf(upper, lower, upper, lower)
            5 -> listOf(push + core, pull + core, legs + core, upper, lower)
            6 -> listOf(push, pull, legs, push, pull, legs)
            else -> listOf(full, full, full)
        }
    }

    private fun setsConfigFor(goal: TrainingGoal): Triple<Int, Int, Int> = when (goal) {
        TrainingGoal.STRENGTH -> Triple(4, 5, 180)
        TrainingGoal.HYPERTROPHY -> Triple(4, 10, 90)
        TrainingGoal.MIX -> Triple(4, 8, 120)
        TrainingGoal.GENERAL_FITNESS -> Triple(3, 12, 60)
        TrainingGoal.CARDIO_LIFTING -> Triple(3, 15, 45)
    }

    private fun pickExercisesForDay(
        pool: List<Exercise>,
        targetMuscles: Set<MuscleGroup>,
        exercisesPerDayCount: Int
    ): List<Exercise> {
        val byMuscle = targetMuscles.associateWith { muscle ->
            pool.filter { it.primaryMuscle == muscle }.shuffled()
        }
        val picked = mutableListOf<Exercise>()
        val takenIds = mutableSetOf<Long>()
        for (muscle in targetMuscles) {
            val candidates = byMuscle[muscle].orEmpty().filter { it.id !in takenIds }
            if (candidates.isNotEmpty()) {
                picked += candidates.first()
                takenIds += candidates.first().id
                if (picked.size >= exercisesPerDayCount) break
            }
        }
        for (muscle in targetMuscles) {
            if (picked.size >= exercisesPerDayCount) break
            val candidates = byMuscle[muscle].orEmpty().filter { it.id !in takenIds }
            if (candidates.isNotEmpty()) {
                picked += candidates.first()
                takenIds += candidates.first().id
            }
        }
        return picked
    }

    /**
     * Robust JSON extractor — radzi sobie z 4 wariantami które AI może zwrócić:
     *  1. Czysty JSON: {"days":[...]}
     *  2. JSON w markdown fence: ```json\n{...}\n```
     *  3. Tekst + JSON: "Oto plan: {...}"
     *  4. Markdown + JSON: "# Plan\n\n{...}\n\nUwagi: ..."
     *
     * Strategia: znajdź pierwsze '{' i odpowiadające mu ostatnie '}'.
     * Wyczyść markdown fences jeśli są.
     */
    private fun stripJsonFences(s: String): String {
        var t = s.trim()
        // Usuń markdown fence ```json...```
        if (t.startsWith("```")) {
            t = t.substringAfter("\n").substringBeforeLast("```").trim()
        }
        // Jeśli już zaczyna się od { — OK
        if (t.startsWith("{")) return t

        // AI zwróciło coś przed JSON — znajdź pierwsze '{' i ostatnie '}'
        val firstBrace = t.indexOf('{')
        val lastBrace = t.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return t.substring(firstBrace, lastBrace + 1)
        }
        return t
    }

    private fun goalLabelShort(goal: TrainingGoal): String = when (goal) {
        TrainingGoal.STRENGTH -> "siła"
        TrainingGoal.HYPERTROPHY -> "hipertrofia"
        TrainingGoal.MIX -> "siła+masa"
        TrainingGoal.GENERAL_FITNESS -> "ogólna sprawność"
        TrainingGoal.CARDIO_LIFTING -> "cardio+siłka"
    }

    private fun goalLabelLong(goal: TrainingGoal): String = when (goal) {
        TrainingGoal.STRENGTH -> "SIŁA — niskie reps (3-6), długi rest (3 min), heavy compound"
        TrainingGoal.HYPERTROPHY -> "HIPERTROFIA (masa mięśniowa) — średnie reps (8-12), 90s rest, mix compound+izolacja"
        TrainingGoal.MIX -> "SIŁA + MASA — średnie reps (6-8), 2 min rest"
        TrainingGoal.GENERAL_FITNESS -> "Ogólna sprawność — wyższe reps (10-15), 60s rest, mniej objętości"
        TrainingGoal.CARDIO_LIFTING -> "Cardio + siłka — krążeniowy, 12-15 reps, 45-60s rest, supersets"
    }

    private fun experienceLabel(name: String): String = when (name) {
        "BEGINNER" -> "początkujący"
        "INTERMEDIATE" -> "średnio zaawansowany"
        "ADVANCED" -> "zaawansowany"
        else -> name
    }

    private fun weightGoalLabel(g: WeightGoalType): String = when (g) {
        WeightGoalType.CUT -> "redukcja (CUT)"
        WeightGoalType.BULK -> "masa (BULK)"
        WeightGoalType.MAINTAIN -> "utrzymanie"
        WeightGoalType.NONE -> "bez celu wagowego"
    }
}
