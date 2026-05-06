package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audyt planu treningowego — HYBRYDA algorytm + AI.
 *
 * - **Algorytm (Kotlin/PlanAuditEngine)** liczy sety per partia, push/pull,
 *   ćwiczenia/dzień. Twarde reguły bazujące na konsensusie naukowym
 *   (Schoenfeld, Helms, RP). Zawsze zwraca te same liczby — koniec halucynacji.
 * - **AI (LLM)** dostaje gotowy raport algorytmu i tylko PROPONUJE konkretne
 *   zmiany. Nie liczy, nie ocenia. Tłumaczy DLACZEGO i podaje nazwy ćwiczeń.
 *
 * Pętla audyt→popraw ograniczona do **2 iteracji** — żeby AI nie oscylował
 * w nieskończoność ("klatka mała / klatka duża / klatka mała").
 *
 * BYOK — wymaga klucza API.
 */
@Singleton
class PlanAuditService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val planDao: TrainingPlanDao,
    private val planExerciseDao: PlanExerciseDao,
    private val planSetDao: PlanExerciseSetDao,
    private val exerciseDao: ExerciseDao,
    private val profileRepo: UserProfileRepository,
    private val applier: AiPlanApplier,
    private val workoutDao: pl.filebit.gymtracker.data.db.dao.WorkoutDao,
    private val masterContextBuilder: MasterAiContextBuilder
) {
    companion object {
        /** Max ile razy ten sam plan może być poprawiany cyklem audyt→popraw. */
        const val MAX_IMPROVE_ITERATIONS = 2
    }

    /**
     * Po audycie — wywołuje AI ponownie z prośbą o WYGENEROWANIE poprawionej
     * wersji planu.
     *
     * @param iterationCount który to z kolei cykl popraw (1 = pierwsza poprawa).
     *                        Po przekroczeniu MAX_IMPROVE_ITERATIONS zwraca błąd
     *                        — gate przeciw nieskończonej pętli.
     * @param userMessage opcjonalne doprecyzowanie ("nie chcę dipów", "dodaj dzień nóg")
     */
    suspend fun improvePlan(
        planId: Long,
        auditMarkdown: String,
        userMessage: String? = null,
        iterationCount: Int = 1
    ): Result<AiPlanProposal> {
        if (iterationCount > MAX_IMPROVE_ITERATIONS) {
            return Result.failure(IllegalStateException(
                "Plan był poprawiany $MAX_IMPROVE_ITERATIONS× — to wystarczy. " +
                "Zaakceptuj aktualną wersję, edytuj ręcznie lub wygeneruj cały plan od zera. " +
                "Powtarzanie audytu prowadzi do oscylacji (raz 'za mało', raz 'za dużo')."
            ))
        }

        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane — wpisz klucz API w Profilu"))
        }
        val plan = planDao.getById(planId)
            ?: return Result.failure(NoSuchElementException("Plan nie istnieje"))

        val exercises = planExerciseDao.getForPlan(planId)
        val exMap = exercises.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }
        val library = exerciseDao.getAll()
        val profile = profileRepo.get()

        val byDay = exercises.groupBy { it.dayOfWeek }.toSortedMap()
        val recentFeedback = collectRecentFeedback()

        // === DETERMINISTYCZNY RAPORT — nie pozwalamy AI liczyć samodzielnie ===
        val setsByPe = exercises.associate { it.id to planSetDao.getForPlanExercise(it.id) }
        val report = PlanAuditEngine.audit(exercises, setsByPe, exMap, profile.goal)

        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()

        val prompt = buildString {
            append("Jesteś trenerem personalnym. Otrzymałeś plan treningowy oraz GOTOWY raport ")
            append("z deterministycznego algorytmu audytu (liczby SĄ POPRAWNE — nie sprawdzaj ich). ")
            append("Wygeneruj POPRAWIONĄ wersję planu adresując TYLKO problemy ze WERDYKTU ALGORYTMU. ")
            append("Używaj WYŁĄCZNIE ćwiczeń z biblioteki poniżej (cytuj nazwy 1:1).\n\n")

            append("⚠️ ITERACJA $iterationCount z max $MAX_IMPROVE_ITERATIONS — ")
            if (iterationCount == MAX_IMPROVE_ITERATIONS) {
                append("**TO OSTATNIA POPRAWA**. Zrób ją perfekcyjnie. Po niej plan jest finalny.\n\n")
            } else {
                append("zachowaj umiar — drobne poprawki, nie przebudowuj planu od zera.\n\n")
            }

            if (masterCtx != null) {
                append("# KONTEKST OGÓLNY (do tła decyzji)\n")
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append(MasterAiContextPromptHelper.toExercisePreferencesSection(masterCtx))
                append("\n")
            }
            if (recentFeedback.isNotBlank()) {
                append("# OSTATNIE FEEDBACK Z TRENINGÓW\n")
                append(recentFeedback)
                append("Jeśli widać ból w danej partii — zaproponuj LŻEJSZĄ alternatywę. ")
                append("Jeśli wellbeing 1-2 przez >2 sesje — rozważ deload (-10% volume).\n\n")
            }

            // === RAPORT Z ALGORYTMU — gotowe liczby ===
            append(PlanAuditEngine.toPromptSection(report))

            append("# OBECNY PLAN: ${plan.name}\n")
            append("- Cel użytkownika: ${profile.goal.name}\n")
            append("- Częstotliwość: ${plan.daysOfWeek.size} dni / tydzień\n")
            if (plan.notes.isNotBlank()) append("- Notatki: ${plan.notes}\n")
            append("\n")
            byDay.forEach { (day, dayExes) ->
                val dayName = dayName(day)
                append("## $dayName\n")
                dayExes.sortedBy { it.orderIndex }.forEach { pe ->
                    val ex = exMap[pe.exerciseId]
                    val sets = setsByPe[pe.id] ?: emptyList()
                    val repsRange = if (sets.isNotEmpty()) {
                        val unique = sets.map { it.reps }.distinct().sorted()
                        if (unique.size == 1) "${unique[0]} powt." else "${unique.first()}-${unique.last()} powt."
                    } else "?"
                    append("- ${ex?.name ?: "(?)"}: ${sets.size} setów, $repsRange")
                    if (pe.supersetGroup != null) append(" [superseria ${pe.supersetGroup}]")
                    append("\n")
                }
                append("\n")
            }

            append("# POPRZEDNI AUDYT (jakościowy — kontekst, nie literalna instrukcja)\n")
            append(auditMarkdown.take(1500))
            append("\n\nUWAGA: jeśli poprzedni audyt wskazywał problem KTÓREGO ALGORYTM NIE POTWIERDZA ")
            append("(patrz WERDYKT ALGORYTMU wyżej), zignoruj go — algorytm jest źródłem prawdy.\n\n")

            if (!userMessage.isNullOrBlank()) {
                append("# DODATKOWE WYMAGANIE UŻYTKOWNIKA\n")
                append(userMessage.take(500))
                append("\n\n")
            }

            append("# BIBLIOTEKA ĆWICZEŃ DOSTĘPNA (cytuj nazwy 1:1, ${library.size} pozycji)\n")
            library.take(220).forEach { ex ->
                append("- ${ex.name} (${ex.primaryMuscle.name})\n")
            }
            append("\n")

            append("# WYMAGANY FORMAT ODPOWIEDZI\n")
            append("Odpowiedz blokiem ```json zawierającym TYLKO obiekt o strukturze:\n")
            append("```json\n")
            append("{\n")
            append("  \"name\": \"<nazwa planu>\",\n")
            append("  \"description\": \"<krótki opis 1-2 zdania uzasadniający zmiany>\",\n")
            append("  \"daysOfWeek\": [1,3,5],\n")
            append("  \"days\": [\n")
            append("    {\"dayOfWeek\": 1, \"exercises\": [\n")
            append("      {\"exerciseName\": \"<nazwa 1:1 z biblioteki>\", \"sets\": [{\"reps\": 8, \"restSec\": 90}, ...], \"supersetGroup\": null},\n")
            append("      ...\n")
            append("    ]},\n")
            append("    ...\n")
            append("  ]\n")
            append("}\n")
            append("```\n")
            append("Zachowaj logikę progresji: 4-12 powt. dla hipertrofii, 3-6 dla siły. ")
            append("RestSec: 60-90s izolacje, 120-180s compound. ")
            append("supersetGroup tej samej litery dla ćwiczeń w supersersji (A,B,...) lub null. ")
            append("Nie dodawaj komentarzy poza blokiem JSON.")
        }

        val response = client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "PlanAudit.improve"
        ).getOrElse { return Result.failure(it) }

        val proposal = applier.extractProposal(response)
            ?: return Result.failure(IllegalStateException(
                "AI nie zwrócił poprawnego JSON. Spróbuj ponownie lub zmień model."
            ))
        return Result.success(proposal)
    }

    private fun dayName(day: Int) = when (day) {
        1 -> "Poniedziałek"; 2 -> "Wtorek"; 3 -> "Środa"; 4 -> "Czwartek"
        5 -> "Piątek"; 6 -> "Sobota"; 7 -> "Niedziela"
        else -> "Dzień $day"
    }

    private suspend fun collectRecentFeedback(): String {
        val recent = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedByDescending { it.startedAt }
            .take(5)
        val withFeedback = recent.filter {
            it.wellbeingRating != null || it.painArea != null
        }
        if (withFeedback.isEmpty()) return ""
        val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return buildString {
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
        }
    }

    /**
     * Audyt planu. **Hybrid: algorytm liczy + ocenia, AI tylko sugeruje zmiany.**
     *
     * Gdy algorytm uzna plan za dobry (`isPlanGood = true`) — AI dostaje
     * instrukcję żeby tylko potwierdzić "plan OK" zamiast wymyślać problemy.
     *
     * Zwraca pair: (markdown audytu od AI, raport algorytmu).
     * VM/UI mogą sprawdzić `report.isPlanGood` żeby wiedzieć czy w ogóle
     * pokazywać przycisk "Popraw plan".
     */
    suspend fun auditWithReport(planId: Long): Result<Pair<String, AuditReport>> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane — wpisz klucz API w Profilu"))
        }

        val plan = planDao.getById(planId)
            ?: return Result.failure(NoSuchElementException("Plan nie istnieje"))

        val exercises = planExerciseDao.getForPlan(planId)
        if (exercises.isEmpty()) {
            return Result.failure(IllegalStateException("Plan nie ma żadnych ćwiczeń — dodaj choć jedno"))
        }

        val exMap = exercises.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }
        val byDay = exercises.groupBy { it.dayOfWeek }.toSortedMap()
        val profile = profileRepo.get()

        // === DETERMINISTYCZNY RAPORT — wstrzykujemy w prompt ===
        val setsByPe = exercises.associate { it.id to planSetDao.getForPlanExercise(it.id) }
        val report = PlanAuditEngine.audit(exercises, setsByPe, exMap, profile.goal)

        val recentFeedback = collectRecentFeedback()
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()
        val prompt = buildString {
            append("Jesteś trenerem personalnym. Otrzymałeś plan oraz GOTOWY RAPORT z deterministycznego ")
            append("algorytmu audytu. Liczby SĄ POPRAWNE — nie weryfikuj, nie przeliczaj. ")
            append("Twoje zadanie: skomentować raport po polsku i (jeśli są problemy) zaproponować KONKRETNE zmiany.\n\n")

            if (masterCtx != null) {
                append("# KONTEKST OGÓLNY\n")
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append(MasterAiContextPromptHelper.toExercisePreferencesSection(masterCtx))
                append("\n")
            }
            if (recentFeedback.isNotBlank()) {
                append("# OSTATNIE FEEDBACK Z TRENINGÓW\n")
                append(recentFeedback)
                append("Jeśli widać ból — uwzględnij w komentarzu, nawet jeśli volume w normie.\n\n")
            }

            // === RAPORT ALGORYTMU — gotowe liczby + werdykt ===
            append(PlanAuditEngine.toPromptSection(report))

            append("# PLAN: ${plan.name}\n")
            append("- Cel użytkownika: ${profile.goal.name}\n")
            append("- Częstotliwość: ${plan.daysOfWeek.size} dni / tydzień\n")
            if (plan.notes.isNotBlank()) append("- Notatki: ${plan.notes}\n")
            append("\n")

            byDay.forEach { (day, dayExes) ->
                val dayName = dayName(day)
                append("## $dayName (${dayExes.size} ćwiczeń)\n")
                dayExes.sortedBy { it.orderIndex }.forEach { pe ->
                    val ex = exMap[pe.exerciseId]
                    val sets = setsByPe[pe.id] ?: emptyList()
                    val repsRange = if (sets.isNotEmpty()) {
                        val unique = sets.map { it.reps }.distinct().sorted()
                        if (unique.size == 1) "${unique[0]} powt." else "${unique.first()}-${unique.last()} powt."
                    } else "?"
                    val muscleStr = ex?.primaryMuscle?.name ?: "?"
                    append("- ${ex?.name ?: "(?)"} ($muscleStr): ${sets.size} setów, $repsRange")
                    if (pe.supersetGroup != null) append(" [superseria ${pe.supersetGroup}]")
                    append("\n")
                }
                append("\n")
            }

            append("# OCZEKIWANY FORMAT ODPOWIEDZI (Markdown, max 400 słów)\n\n")
            if (report.isPlanGood) {
                append("## Plan jest dobry ✅\n")
                append("(2-3 zdania potwierdzające że plan jest OK, ze wskazaniem 1-2 mocnych stron z konkretnymi liczbami z raportu)\n\n")
                append("## Drobne sugestie (opcjonalnie, max 1)\n")
                append("(JEDNA opcjonalna sugestia stylu — np. zmiana kolejności ćwiczeń. NIE wymyślaj problemów których algorytm nie zgłosił.)\n\n")
                append("**KRYTYCZNE: NIE PISZ że jakaś partia ma 'za mało' lub 'za dużo' setów. Algorytm sprawdził — wszystko jest w normie. **\n")
            } else {
                append("## Mocne strony\n(1-2 punkty)\n\n")
                append("## Konkretne sugestie zmian\n")
                append("(dla KAŻDEGO problemu z WERDYKTU ALGORYTMU napisz: jakie ćwiczenie dodać/wymienić/usunąć i ile setów)\n\n")
                append("**KRYTYCZNE: nie wymyślaj problemów których algorytm nie zgłosił. Trzymaj się raportu wyżej. ")
                append("Jeśli partia ma ✅ OK — NIE komentuj jej.**\n")
            }

            append("\nBądź pomocny, nie laudator. Cytuj liczby z RAPORTU ALGORYTMU.")
        }

        return client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "PlanAudit.audit"
        ).map { it.trim() to report }
    }

    /** Backward-compatible: stara sygnatura tylko ze stringiem. */
    suspend fun audit(planId: Long): Result<String> =
        auditWithReport(planId).map { it.first }
}
