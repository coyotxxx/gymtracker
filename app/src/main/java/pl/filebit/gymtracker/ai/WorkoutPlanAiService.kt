package pl.filebit.gymtracker.ai

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MetricType
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
import pl.filebit.gymtracker.util.cardioDistanceM
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
    private val contextBuilder: MasterAiContextBuilder,
    // v2.6.0: atomowy zapis planu (withTransaction) — plan+ćwiczenia+sety razem,
    // żeby UI nie widziało przejściowego "0 ćwiczeń / bez harmonogramu".
    private val db: pl.filebit.gymtracker.data.db.AppDatabase
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
        planName: String? = null,
        userNotes: String? = null
    ): Result<GeneratedPlanResult> = runCatching {
        val cfg = prefs.load()
        val all = exerciseRepo.observeAll().first()
        val profile = profileRepo.get()
        val available = filterByEquipment(all, profile.equipmentCategoriesCsv)
        val rawPool = if (favoritesOnly) {
            val favs = available.filter { it.isFavorite }
            if (favs.size < 12) {
                (favs + available.filter { !it.isFavorite }).distinctBy { it.id }
            } else favs
        } else {
            // v1.26.3: smart filter — zamiast całej bazy (~1200) AI dostaje
            // ograniczoną pulę: preferowane partie więcej, reszta mniej.
            smartFilterPool(available, profile.preferredMuscleGroupsCsv)
        }

        // v2.5.0: filtr bezpieczeństwa — usuń z puli ćwiczenia z przeciwwskazaniami
        // (contraindicationsJson) pasującymi do schorzeń usera (medicalConditions).
        // AI nie zaproponuje ćwiczenia którego user nie powinien robić. Tylko severity
        // "avoid" usuwamy z puli; modify/caution zostają (AI dostanie je w bibliotece).
        val conditions = profile.medicalConditions
            .split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val pool = if (conditions.isEmpty()) rawPool else rawPool.filter { ex ->
            val contra = ex.contraindicationsJson?.lowercase() ?: return@filter true
            val matched = conditions.any { contra.contains(it) }
            // wyklucz tylko gdy match + severity avoid
            !(matched && contra.contains("\"severity\":\"avoid\""))
        }
        val excludedForSafety = rawPool.size - pool.size

        Log.d("WorkoutPlanAi", "generate(): pool=${pool.size} (excludedForSafety=$excludedForSafety) favs=${pool.count { it.isFavorite }} cfg.isConnected=${cfg.isConnected} provider=${cfg.provider}")

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
            val aiResult = runCatching { generateWithAi(daysPerWeek, favoritesOnly, planName, cfg, profile, pool, userNotes) }
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
        val sets: Int = 3,
        val repsMin: Int = 8,
        val repsMax: Int = 12,
        val restSeconds: Int = 90,
        val durationMin: Int? = null,                // cardio/izometria: czas serii w minutach
        val speedKmh: Double? = null,                // cardio DISTANCE_DURATION: prędkość km/h
        val notes: String = ""
    )

    private suspend fun generateWithAi(
        daysPerWeek: Int,
        favoritesOnly: Boolean,
        planName: String?,
        cfg: AiConfig,
        profile: UserProfile,
        pool: List<Exercise>,
        userNotes: String? = null
    ): GeneratedPlanResult {
        val warnings = mutableListOf<String>()

        // === MASTER CONTEXT — ten sam co używa DietAi ===
        val masterCtx = runCatching { contextBuilder.build() }.getOrNull()

        // === POOL DOSTĘPNYCH ĆWICZEŃ ===
        // v1.29.9: separator " | " zamiast nawiasu — wiele nazw ćwiczeń samo
        // zawiera (...), przez co AI wklejało dopisek partia/sprzęt do nazwy.
        // v2.5.0: wzbogacona pula — dodatkowo wzorzec ruchowy + poziom + trudność z canonical.
        // Pozwala AI balansować push/pull/hinge i dobierać trudność do poziomu usera
        // bez wołania tools (efektywniej tokenowo — plan w jednym calle).
        val poolListing = pool.joinToString("\n") { ex ->
            val fav = if (ex.isFavorite) " ⭐" else ""
            val metricTag = when (ex.metricType) {
                MetricType.DISTANCE_DURATION -> " | ⏱CARDIO(czas+prędkość)"
                MetricType.DURATION -> " | ⏱CZAS(izometria)"
                else -> ""
            }
            val mp = ex.movementPattern?.let { " | ${it.name}" } ?: ""
            val lvl = ex.levelMin?.let { lv ->
                val diff = ex.difficulty1To10?.let { "$it/10" } ?: ""
                " | ${lv.name}${if (diff.isNotEmpty()) " $diff" else ""}"
            } ?: ""
            "- ${ex.name}$fav | ${ex.primaryMuscle.name} | ${ex.equipment.name}$mp$lvl$metricTag"
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
                append(MasterAiContextPromptHelper.toReadinessAndMuscleSection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append(MasterAiContextPromptHelper.toExercisePreferencesSection(masterCtx))
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

            // v1.26.3: OBSZAR ZAINTERESOWANIA — preferowane partie z UserProfile.
            // AI ma priorytetyzować objętość na tych grupach, ale plan dalej całościowy.
            val preferredMuscles = profile.preferredMuscleGroupsCsv
                .split(",")
                .mapNotNull { runCatching { MuscleGroup.valueOf(it.trim()) }.getOrNull() }
            if (preferredMuscles.isNotEmpty()) {
                append("\n=== OBSZAR ZAINTERESOWANIA (preferencje usera) ===\n")
                append("User chce się skupić na: ")
                append(preferredMuscles.joinToString(", ") { it.displayName() })
                append("\n→ Priorytetyzuj objętość (więcej serii/ćwiczeń) na tych partiach, ")
                append("ale plan MUSI dalej pokrywać całe ciało (split bez luk).\n")
            }

            // v1.26.0: UWAGI UŻYTKOWNIKA — wolny tekst od usera (cardio, czas, kontuzje).
            // v1.29.9: traktowane jako WYMÓG nadrzędny. Wcześniej były „sugestią" obok
            // sztywnych reguł (5-6 ćwiczeń/dzień, split) — gdy user pisał „tylko bieżnia",
            // AI i tak dorzucało ćwiczenia, żeby spełnić strukturę.
            if (!userNotes.isNullOrBlank()) {
                append("\n=== ⚠ UWAGI UŻYTKOWNIKA — WYMÓG (nie sugestia) ===\n")
                append("User świadomie to napisał. Zastosuj BEZWZGLĘDNIE — ma ")
                append("PIERWSZEŃSTWO przed regułami struktury powyżej (liczba ćwiczeń ")
                append("na dzień, split, pokrycie partii). Gdy user chce wyłącznie ")
                append("jeden rodzaj treningu (np. samą bieżnię, samo cardio) — ")
                append("generuj WYŁĄCZNIE to, choćby wyszło 1 ćwiczenie dziennie. ")
                append("NIE dokładaj nic spoza tego, o co user prosi.\n")
                append(userNotes.trim().take(800))
                append("\n")
            }

            // POOL ĆWICZEŃ
            append("\n=== DOSTĘPNE ĆWICZENIA (TYLKO Z TEJ LISTY) ===\n")
            append("⭐ = ulubione użytkownika (PREFERUJ jak najczęściej)\n")
            append("Format: nazwa | partia | sprzęt. W JSON jako `name` wpisz TYLKO ")
            append("część PRZED pierwszym `|` (bez ⭐, bez partii, bez sprzętu).\n")
            append("⏱CARDIO(czas+prędkość) — bieżnia/rower/orbitrek: w JSON podaj ")
            append("`durationMin` (minuty) i `speedKmh` (km/h) ZAMIAST repsMin/repsMax. ")
            append("⏱CZAS(izometria) — np. plank: podaj tylko `durationMin`. ")
            append("Pozostałe ćwiczenia — repsMin/repsMax jak zwykle.\n\n")
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
                      "name": "Wyciskanie sztangi na ławce poziomej",
                      "movementType": "COMPOUND",
                      "sets": 4,
                      "repsMin": 6,
                      "repsMax": 10,
                      "restSeconds": 120,
                      "notes": "Główne — pierwsze, świeże siły"
                    },
                    {
                      "name": "Wznosy bokiem z hantlami",
                      "movementType": "ISOLATION",
                      "sets": 3,
                      "repsMin": 12,
                      "repsMax": 15,
                      "restSeconds": 60,
                      "notes": ""
                    },
                    {
                      "name": "Chodzenie na bieżni z nachyleniem",
                      "movementType": "ACCESSORY",
                      "sets": 1,
                      "durationMin": 25,
                      "speedKmh": 9,
                      "restSeconds": 60,
                      "notes": "Cardio — 25 min, tempo 9 km/h"
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
        var orderIdx = 0
        val exercisesByDay = mutableMapOf<Int, List<String>>()
        var totalAdded = 0
        var skipped = 0

        // v2.6.0: cały zapis (plan + ćwiczenia + sety) w JEDNEJ transakcji.
        // Bez tego upsertPlan commitował od razu → lista planów (obserwuje
        // training_plans) pokazywała plan z 0 ćwiczeń; plan_exercises dodawane
        // później NIE triggerowały re-emisji → "0 ćwiczeń / bez harmonogramu" aż
        // do odświeżenia. Teraz Room emituje raz, po commicie kompletnego planu.
        val planId = db.withTransaction {
        val pid = planRepo.upsertPlan(tplan)

        for (day in parsed.days.take(daysPerWeek)) {
            val names = mutableListOf<String>()
            for (aiEx in day.exercises) {
                // v1.29.9: defensywnie — gdy AI wklei dopisek formatu do nazwy
                // ("Mountain climbers | CARDIO | ..." lub "... (partia: ...)").
                val cleanName = aiEx.name
                    .substringBefore(" |")
                    .substringBefore("(partia:")
                    .replace("⭐", "")
                    .trim()
                val match = byNameLower[cleanName.lowercase()]
                    ?: byNameLower.entries.firstOrNull { (k, _) -> k.contains(cleanName.lowercase()) || cleanName.lowercase().contains(k) }?.value
                    // v2.5.1: fallback do PEŁNEJ bazy. AI może użyć ulubionego ćwiczenia
                    // które prompt pokazuje w sekcji ULUBIONE, ale filterByEquipment
                    // wyrzucił je z `pool` (np. cardio bieżnia gdy user nie ma "cardio"
                    // w equipmentCategoriesCsv). Bez tego AI dawał poprawny plan,
                    // a parser pomijał wszystko → fallback offline. Szukamy po nazwie
                    // (Exercise.name = namePl) i po slug.
                    ?: exerciseRepo.findByName(cleanName)
                    ?: exerciseRepo.findBySlug(cleanName.lowercase().replace(" ", "-"))
                if (match == null) {
                    skipped++
                    warnings += "Pominięto '${aiEx.name}' — brak w bazie"
                    continue
                }
                val pe = PlanExercise(
                    planId = pid,
                    exerciseId = match.id,
                    dayOfWeek = day.dayOfWeek.coerceIn(1, 7),
                    orderIndex = orderIdx++
                )
                val peId = planRepo.upsertPlanExercise(pe)
                val avgReps = ((aiEx.repsMin + aiEx.repsMax) / 2).coerceIn(1, 30)
                val isCardio = match.metricType == MetricType.DISTANCE_DURATION ||
                    match.metricType == MetricType.DURATION
                // Cardio: czas (durationMin) + prędkość (speedKmh dla DISTANCE_DURATION).
                // Gdy AI nie poda czasu — fallback na avgReps potraktowane jako minuty.
                val durSec = if (isCardio) {
                    ((aiEx.durationMin?.takeIf { it > 0 } ?: avgReps).coerceIn(1, 180)) * 60
                } else null
                val distM = if (match.metricType == MetricType.DISTANCE_DURATION) {
                    cardioDistanceM(aiEx.speedKmh, durSec)
                } else null
                repeat(aiEx.sets.coerceIn(1, 8)) { i ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = i + 1,
                            reps = if (isCardio) 0 else avgReps,
                            weightKg = null,
                            restSeconds = aiEx.restSeconds.coerceIn(20, 600),
                            durationSec = durSec,
                            distanceM = distM,
                            setType = SetType.NORMAL
                        )
                    )
                }
                names += match.name
                totalAdded++
            }
            exercisesByDay[day.dayOfWeek] = names
        }
        pid  // wartość zwracana z transakcji = ID utworzonego planu
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

    /**
     * v1.26.5: filtr po praktycznych kategoriach sprzętu (EquipmentCategory).
     * Filtruje po surowym `Exercise.equipmentDbCsv` (28 typów z ExerciseDB)
     * mapowanym przez EquipmentCategory.dbEquipments. Ćwiczenia user-defined
     * bez `equipmentDbCsv` przepuszczamy (user sam je dodał — nie blokujemy).
     */
    private fun filterByEquipment(all: List<Exercise>, categoriesCsv: String): List<Exercise> {
        if (categoriesCsv.isBlank()) return all
        val categories = pl.filebit.gymtracker.data.entity.EquipmentCategory.parse(categoriesCsv)
        if (categories.isEmpty()) return all
        val allowedDbEq = pl.filebit.gymtracker.data.entity.EquipmentCategory
            .dbEquipmentsFor(categories)
        return all.filter { ex ->
            val dbEq = ex.equipmentDbCsv
            if (dbEq.isNullOrBlank()) {
                true  // user-defined bez ExerciseDB equipment — nie blokuj
            } else {
                dbEq.split(",").any { it.trim().lowercase() in allowedDbEq }
            }
        }
    }

    /**
     * v1.26.3: smart filter puli dla AI. Cała baza (~1200 ćwiczeń) w prompcie to
     * ~30k tokenów. Redukcja BEZ utraty pokrycia splitu:
     *  - grupuj po primaryMuscle
     *  - preferowane partie (UserProfile.preferredMuscleGroupsCsv): limit 60/grupa
     *  - pozostałe partie: limit 20/grupa (wystarczy do uzupełnienia splitu)
     *  - w każdej grupie: favorites first, potem alfabetycznie
     *  - jeśli preferredMuscleGroupsCsv puste → wszystkie grupy po 40
     *
     * Efekt: ~1200 → ~350-550 ćwiczeń, plan dalej całościowy, preferowane
     * partie mają bogatszy wybór dla AI.
     */
    private fun smartFilterPool(available: List<Exercise>, preferredCsv: String): List<Exercise> {
        val preferred = preferredCsv.split(",").mapNotNull {
            runCatching { MuscleGroup.valueOf(it.trim()) }.getOrNull()
        }.toSet()
        val limitPreferred = 60
        val limitOther = if (preferred.isEmpty()) 40 else 20

        return available.groupBy { it.primaryMuscle }.flatMap { (muscle, list) ->
            val limit = if (preferred.isEmpty() || muscle in preferred) {
                if (preferred.isEmpty()) limitOther else limitPreferred
            } else limitOther
            list.sortedWith(
                compareByDescending<Exercise> { it.isFavorite }
                    .thenBy { it.name.lowercase() }
            ).take(limit)
        }
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
