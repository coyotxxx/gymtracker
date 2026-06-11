package pl.filebit.gymtracker.ai

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.AiWeeklyReport
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pojedyncza zalecona akcja po analizie tygodnia. AI generuje listę 3-7
 * konkretnych akcji do zaznaczenia przez użytkownika.
 */
data class ReportAction(
    val id: Int,
    val label: String,
    val severity: ReportActionSeverity = ReportActionSeverity.NORMAL
)

enum class ReportActionSeverity { NORMAL, IMPORTANT, WARNING }

/**
 * Generuje pełny raport tygodniowy treningu — analizę per partia mięśniowa,
 * trendy RPE, stagnacje, konkretne rekomendacje na następny tydzień.
 *
 * BYOK — wymagana konfiguracja AI (OpenAI lub Anthropic).
 *
 * To jest "drugie zdanie" trenera AI — uzupełnienie deterministycznego algorytmu
 * z util/Autoregulation.kt o kontekst tygodniowy + nieliniową analizę.
 */
@Singleton
class WeeklyReportService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val reportDao: AiWeeklyReportDao,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val planRepo: PlanRepository,
    private val planExerciseDao: PlanExerciseDao,
    private val planSetDao: PlanExerciseSetDao,
    private val applier: AiPlanApplier,
    private val masterContextBuilder: MasterAiContextBuilder
) {
    private val jsonCfg = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Wyciąga listę akcji z raportu — szuka bloku ```json ... ``` zawierającego
     * tablicę obiektów `[{"id": 1, "label": "...", "severity": "..."}]`.
     */
    fun parseActions(reportContent: String): List<ReportAction> {
        val fenced = Regex(
            "```json\\s*(\\[[\\s\\S]+?\\])\\s*```",
            RegexOption.MULTILINE
        ).find(reportContent)?.groupValues?.get(1)
            ?: extractFirstJsonArray(reportContent)
            ?: return emptyList()

        return runCatching {
            val arr = jsonCfg.parseToJsonElement(fenced).jsonArray
            arr.mapIndexed { idx, el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: (idx + 1)
                val label = obj["label"]?.jsonPrimitive?.content ?: return@mapIndexed null
                val sev = obj["severity"]?.jsonPrimitive?.content?.uppercase()
                val severity = when (sev) {
                    "IMPORTANT" -> ReportActionSeverity.IMPORTANT
                    "WARNING" -> ReportActionSeverity.WARNING
                    else -> ReportActionSeverity.NORMAL
                }
                ReportAction(id, label, severity)
            }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    private fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Po raporcie — wywołuje AI z wybranymi akcjami i istniejącym planem,
     * dostaje JSON poprawionej wersji planu (parsowany przez AiPlanApplier).
     */
    suspend fun improvePlanFromReport(
        planId: Long,
        reportContent: String,
        selectedActions: List<ReportAction>,
        userMessage: String? = null
    ): Result<AiPlanProposal> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane"))
        }
        if (selectedActions.isEmpty() && userMessage.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Zaznacz przynajmniej jedną akcję lub doprecyzuj prośbą"))
        }
        val plan = planRepo.getPlan(planId)
            ?: return Result.failure(NoSuchElementException("Plan nie istnieje"))
        val exercises = planExerciseDao.getForPlan(planId)
        val exMap = exercises.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }
        val library = exerciseDao.getAll()

        val byDay = exercises.groupBy { it.dayOfWeek }.toSortedMap()

        val prompt = buildString {
            append("Jesteś trenerem personalnym. Po analizie tygodnia użytkownik wybrał ")
            append("konkretne akcje do zastosowania w planie. Wygeneruj poprawioną wersję planu ")
            append("uwzględniając WSZYSTKIE wybrane akcje. Używaj WYŁĄCZNIE ćwiczeń z biblioteki (nazwy 1:1).\n\n")

            append("# OBECNY PLAN: ${plan.name}\n")
            byDay.forEach { (day, dayExes) ->
                val dayName = dayName(day)
                append("## $dayName\n")
                dayExes.sortedBy { it.orderIndex }.forEach { pe ->
                    val ex = exMap[pe.exerciseId]
                    val sets = planSetDao.getForPlanExercise(pe.id)
                    val repsRange = if (sets.isNotEmpty()) {
                        val unique = sets.map { it.reps }.distinct().sorted()
                        if (unique.size == 1) "${unique[0]} powt." else "${unique.first()}-${unique.last()} powt."
                    } else "?"
                    append("- ${ex?.name ?: "(?)"}: ${sets.size} setów, $repsRange\n")
                }
            }
            append("\n")

            append("# RAPORT TYGODNIA (kontekst)\n")
            append(reportContent.take(2500))
            append("\n\n")

            append("# WYBRANE AKCJE DO ZASTOSOWANIA\n")
            selectedActions.forEachIndexed { i, a -> append("${i + 1}. ${a.label}\n") }
            append("\n")

            if (!userMessage.isNullOrBlank()) {
                append("# DODATKOWA INSTRUKCJA UŻYTKOWNIKA\n")
                append(userMessage.take(500))
                append("\n\n")
            }

            append("# BIBLIOTEKA ĆWICZEŃ (cytuj nazwy 1:1, ${library.size} pozycji)\n")
            library.take(220).forEach { ex ->
                append("- ${ex.name} (${ex.primaryMuscle.name})\n")
            }
            append("\n")

            append("# WYMAGANY FORMAT ODPOWIEDZI\n")
            append("Odpowiedz blokiem ```json zawierającym TYLKO obiekt:\n")
            append("```json\n")
            append("{\n")
            append("  \"name\": \"<nazwa planu>\",\n")
            append("  \"description\": \"<krótki opis 1-2 zdania uzasadniający zmiany>\",\n")
            append("  \"daysOfWeek\": [1,3,5],\n")
            append("  \"days\": [\n")
            append("    {\"dayOfWeek\": 1, \"exercises\": [\n")
            append("      {\"exerciseName\": \"<nazwa 1:1>\", \"sets\": [{\"reps\": 8, \"restSec\": 90}], \"supersetGroup\": null}\n")
            append("    ]}\n")
            append("  ]\n")
            append("}\n```\n")
            append("Bez komentarzy poza blokiem JSON.")
        }

        val response = client.chat(cfg, listOf(AiMessage(AiRole.USER, prompt)), source = "WeeklyReport.improvePlan")
            .getOrElse { return Result.failure(it) }
        val proposal = applier.extractProposal(response)
            ?: return Result.failure(IllegalStateException("AI nie zwrócił poprawnego JSON. Spróbuj ponownie."))
        return Result.success(proposal)
    }

    private fun dayName(day: Int) = when (day) {
        1 -> "Poniedziałek"; 2 -> "Wtorek"; 3 -> "Środa"; 4 -> "Czwartek"
        5 -> "Piątek"; 6 -> "Sobota"; 7 -> "Niedziela"
        else -> "Dzień $day"
    }
    /** Raport bieżącego tygodnia (przycisk ręczny — bez zmian zachowania). */
    suspend fun generate(): Result<String> = generateForWeek(currentWeekStartMillis())

    /** Poniedziałek 00:00 bieżącego tygodnia (lokalny czas). */
    fun currentWeekStartMillis(): Long {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val daysFromMonday = (today.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber)
        val mondayDate = today.minus(daysFromMonday, DateTimeUnit.DAY)
        return mondayDate.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /** Poniedziałek 00:00 ostatniego ZAKOŃCZONEGO tygodnia (bieżący − 7 dni). */
    fun lastCompletedWeekStartMillis(): Long =
        currentWeekStartMillis() - 7.days.inWholeMilliseconds

    /**
     * Generuje raport dla tygodnia rozpoczynającego się w [weekStartMillis] (poniedziałek 00:00).
     * Worker auto podaje ostatni zakończony tydzień; przycisk ręczny — bieżący.
     */
    suspend fun generateForWeek(weekStartMillis: Long): Result<String> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane — wpisz klucz API w Profilu"))
        }

        val tz = TimeZone.currentSystemDefault()
        val mondayDate = kotlinx.datetime.Instant.fromEpochMilliseconds(weekStartMillis)
            .toLocalDateTime(tz).date
        val weekEndMillis = weekStartMillis + 7.days.inWholeMilliseconds

        val workouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt in weekStartMillis until weekEndMillis }
        if (workouts.isEmpty()) {
            return Result.failure(IllegalStateException("Brak ukończonych treningów w tym tygodniu"))
        }

        // Per workout: zaliczone sety (bez warmupów)
        val allSets = workouts.flatMap { w ->
            setDao.getForWorkout(w.id).filter { it.isCompleted && it.setType != SetType.WARMUP }
        }
        if (allSets.isEmpty()) {
            return Result.failure(IllegalStateException("Brak zaliczonych serii w tym tygodniu"))
        }

        // Cache exercise per id
        val exMap = allSets.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }

        val totalVolume = allSets.sumOf { it.reps * it.weightKg }
        val totalDurationMin = workouts.sumOf { it.durationMillis } / 60_000

        // Per partia mięśniowa
        val byMuscle = allSets.groupBy { exMap[it.exerciseId]?.primaryMuscle }
            .mapNotNull { (muscle, sets) ->
                muscle ?: return@mapNotNull null
                val volume = sets.sumOf { it.reps * it.weightKg }
                val sessions = sets.map { it.workoutId }.distinct().size
                Triple(muscle, sessions, sets.size to volume)
            }.sortedByDescending { it.third.first }

        // Per ćwiczenie — z trendem RPE
        val byExercise = allSets.groupBy { it.exerciseId }
            .mapNotNull { (exId, sets) ->
                val ex = exMap[exId] ?: return@mapNotNull null
                val sortedByDate = sets.sortedBy { it.createdAt }
                val rpeValues = sortedByDate.mapNotNull { it.rpe }
                val rpeTrend = when {
                    rpeValues.size < 2 -> if (rpeValues.size == 1) "RPE ${rpeValues[0]}" else "brak RPE"
                    else -> "RPE ${rpeValues.first()}→${rpeValues.last()}"
                }
                val topSet = sets.maxByOrNull { it.weightKg * it.reps }!!
                val sessions = sets.map { it.workoutId }.distinct().size
                AiExerciseSummary(
                    name = ex.name,
                    muscle = ex.primaryMuscle.name,
                    sessions = sessions,
                    setCount = sets.size,
                    topSet = "${topSet.reps}×${formatKg(topSet.weightKg)}kg",
                    rpeTrend = rpeTrend,
                    movementPattern = ex.movementPattern?.name
                )
            }

        // Stagnacje — z ostatniego treningu tygodnia
        val lastWorkout = workouts.maxByOrNull { it.startedAt }
        val stagnations = lastWorkout?.let {
            statsRepo.detectStagnation(it.id, threshold = 3)
        }.orEmpty()

        // Cel treningowy
        val profile = profileRepo.get()
        val goal = profile.goal.name

        // === MASTER CONTEXT — pełen obraz usera ===
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()

        // Build prompt
        val withFeedback = workouts.filter { it.wellbeingRating != null || it.painArea != null }
        val prompt = buildString {
            append("Jesteś trenerem personalnym. Przeanalizuj poniższy tydzień treningowy ")
            append("użytkownika i daj rekomendacje na następny tydzień. Bądź konkretny — ")
            append("cytuj liczby z danych. Bazuj na zasadach RP, MASS i Israetela. Polski język.\n\n")

            // Pełen kontekst z całej aplikacji (recovery, sen, NEAT, adherence diety,
            // faza, PRy) — pozwala AI łączyć kropki: trening + dieta + regeneracja
            if (masterCtx != null) {
                append("# KONTEKST OGÓLNY (do tła analizy tygodnia)\n")
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toWeightTrendSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append("\n")
            }
            if (withFeedback.isNotEmpty()) {
                append("# FEEDBACK Z TRENINGÓW (samopoczucie 1-5 + ból)\n")
                val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                withFeedback.forEach { w ->
                    append("- ${df.format(java.util.Date(w.startedAt))}: ")
                    w.wellbeingRating?.let { append("wellbeing $it/5") }
                    w.painArea?.let {
                        if (w.wellbeingRating != null) append(", ")
                        append("ból: $it")
                        w.painNotes?.takeIf { n -> n.isNotBlank() }?.let { n -> append(" ($n)") }
                    }
                    append("\n")
                }
                append("UWZGLĘDNIJ to we wnioskach — niski wellbeing lub powtarzający się ból ")
                append("to silny sygnał na dostosowanie planu/deloadu.\n\n")
            }

            append("# DANE TYGODNIA: $mondayDate – ${mondayDate.plus(6, DateTimeUnit.DAY)}\n")
            append("- Sesji: ${workouts.size}, łącznie ${totalDurationMin} min\n")
            append("- Łączna objętość: ${formatKg(totalVolume)} kg (${allSets.size} setów roboczych)\n")
            append("- Cel treningowy: $goal\n\n")

            append("## Per partia mięśniowa\n")
            byMuscle.forEach { (muscle, sessions, setsAndVol) ->
                val (setsCount, vol) = setsAndVol
                append("- ${muscle.name}: $sessions sesji, $setsCount setów, ${formatKg(vol)} kg objętości\n")
            }

            append("\n## Per ćwiczenie (top set + trend RPE)\n")
            byExercise.sortedByDescending { it.sessions }.forEach { e ->
                append("- ${e.name} (${e.muscle}): ${e.sessions} sesji, ${e.setCount} setów, ")
                append("top ${e.topSet}, ${e.rpeTrend}\n")
            }

            // v2.5.0: balans wzorców ruchowych (push/pull/hinge/squat...) z canonical.
            // Pozwala AI wykryć dysbalans niewidoczny w podziale per partia
            // (np. dużo push poziomego, mało pull pionowego → ryzyko barków).
            val byPattern = byExercise
                .filter { it.movementPattern != null }
                .groupBy { it.movementPattern!! }
                .mapValues { (_, list) -> list.sumOf { it.setCount } }
                .toList().sortedByDescending { it.second }
            if (byPattern.isNotEmpty()) {
                append("\n## Balans wzorców ruchowych (serie/wzorzec)\n")
                byPattern.forEach { (pattern, setCount) ->
                    append("- $pattern: $setCount serii\n")
                }
                append("→ Sprawdź balans: push vs pull (antagoniści), poziom vs pion, ")
                append("kolana (squat) vs biodra (hinge). Wskaż dysproporcje.\n")
            }

            if (stagnations.isNotEmpty()) {
                append("\n## STAGNACJE wykryte\n")
                stagnations.forEach { s ->
                    append("- ${s.exerciseName}: ${s.workoutsAtSameWeight} treningów z rzędu na ${formatKg(s.stuckAtKg)} kg\n")
                }
            }

            append("\n# OCZEKIWANY FORMAT ODPOWIEDZI (Markdown)\n\n")
            append("## Mocne strony\n(2-3 punkty oparte na liczbach)\n\n")
            append("## Wnioski per partia mięśniowa\n")
            append("**Klatka:** rekomendacja na następny tydzień (konkretna waga / reps / liczba sesji)\n")
            append("**Plecy:** ...\n(itd. dla każdej partii którą trenowano)\n\n")
            append("## Konkretne kroki na następny tydzień\n")
            append("1. (akcja z liczbami)\n2. ...\n\n")
            append("## Ostrzeżenia\n")
            append("(jeśli są — np. volume za niski/wysoki, brak partii, stagnacje wymagają deloadu)\n\n")

            append("## AKCJE DO ZASTOSOWANIA (JSON)\n")
            append("Na końcu raportu dodaj blok ```json z listą 3-7 konkretnych akcji do zaznaczenia ")
            append("przez użytkownika. Każda akcja ma być zwięzła (max 100 znaków), JEDNOZNACZNA i ")
            append("wykonalna w planie treningowym (zwiększ/zmniejsz X, dodaj/usuń ćwiczenie, zmień zakres reps, ")
            append("zaproponuj deload). Severity: 'NORMAL' (zalecenie), 'IMPORTANT' (priorytetowe), 'WARNING' (pilne).\n")
            append("```json\n")
            append("[\n")
            append("  {\"id\": 1, \"label\": \"Zwiększ objętość pleców z 10 do 14 setów/tydz\", \"severity\": \"IMPORTANT\"},\n")
            append("  {\"id\": 2, \"label\": \"Wymień martwy ciąg klasyczny na rumuński (deload stagnacji)\", \"severity\": \"WARNING\"}\n")
            append("]\n")
            append("```\n\n")
            append("---\n\n")
            append("Reguły volume na tydzień (wg literatury): 10–20 setów / partia / tydzień (hipertrofia), ")
            append("8–14 (siła). Jeśli stagnacja 3+ treningów → sugeruj −10% deload na ten tydzień ")
            append("LUB wymianę wariantu ćwiczenia. Maks 600 słów. Zachowaj balans motywacji i ")
            append("konkretu — bądź pomocny, nie laudator.")
        }

        val result = client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "WeeklyReport.generate"
        )
        // Po sukcesie — zapisz do bazy żeby user miał historię
        return result.onSuccess { content ->
            reportDao.insert(
                AiWeeklyReport(
                    weekStartMillis = weekStartMillis,
                    weekEndMillis = weekEndMillis,
                    generatedAtMillis = System.currentTimeMillis(),
                    content = content,
                    aiProvider = cfg.provider.name,
                    aiModel = cfg.model
                )
            )
        }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) kg.toInt().toString()
        else String.format("%.1f", kg)
    }
}

private data class AiExerciseSummary(
    val name: String,
    val muscle: String,
    val sessions: Int,
    val setCount: Int,
    val topSet: String,
    val rpeTrend: String,
    val movementPattern: String? = null  // v2.5.0: do analizy balansu wzorców
)
