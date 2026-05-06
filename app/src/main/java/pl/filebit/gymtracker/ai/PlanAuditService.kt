package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audyt planu treningowego — AI sprawdza balans (push/pull, częstotliwość per
 * partia, volume tygodniowo, brakujące partie, dysbalans antagonistów) i
 * sugeruje konkretne korekty.
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
    /**
     * Po audycie — wywołuje AI ponownie z prośbą o WYGENEROWANIE poprawionej
     * wersji planu. AI dostaje stary plan, wcześniejszą analizę i listę ćwiczeń
     * z biblioteki — zwraca JSON nowego planu (parsowany przez AiPlanApplier).
     *
     * @param userMessage opcjonalne doprecyzowanie ("nie chcę dipów", "dodaj dzień nóg")
     */
    suspend fun improvePlan(
        planId: Long,
        auditMarkdown: String,
        userMessage: String? = null
    ): Result<AiPlanProposal> {
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

        // === MASTER CONTEXT — pełen obraz dla audytu ===
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()

        val prompt = buildString {
            append("Jesteś trenerem personalnym. Otrzymałeś plan treningowy oraz audyt z poprzedniej tury. ")
            append("Wygeneruj POPRAWIONĄ wersję planu uwzględniając wszystkie problemy z audytu. ")
            append("Trzymaj styl/cel użytkownika. Używaj WYŁĄCZNIE ćwiczeń z biblioteki poniżej (cytuj nazwy 1:1).\n\n")

            if (masterCtx != null) {
                append("# KONTEKST OGÓLNY (do tła decyzji)\n")
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append("\n")
            }
            if (recentFeedback.isNotBlank()) {
                append("# OSTATNIE FEEDBACK Z TRENINGÓW\n")
                append(recentFeedback)
                append("Jeśli widać ból w danej partii — zaproponuj LŻEJSZĄ alternatywę dla ćwiczeń ją obciążających. ")
                append("Jeśli wellbeing 1-2 przez >2 sesje — rozważ deload (-10% volume).\n\n")
            }

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
                    val sets = planSetDao.getForPlanExercise(pe.id)
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

            append("# AUDYT (poprzednia analiza AI)\n")
            append(auditMarkdown.take(2000))
            append("\n\n")

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

    /**
     * Krótkie podsumowanie ostatnich 5 treningów: ich data + wellbeing + painArea
     * (jeśli zgłoszono). Pusty string jeśli brak feedbacku — wtedy AI nie dostaje
     * tej sekcji w prompt.
     */
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

    suspend fun audit(planId: Long): Result<String> {
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

        // Cache exercise per id
        val exMap = exercises.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }

        // Per dzień tygodnia
        val byDay = exercises.groupBy { it.dayOfWeek }.toSortedMap()
        val profile = profileRepo.get()

        val recentFeedback = collectRecentFeedback()
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()
        val prompt = buildString {
            append("Jesteś trenerem personalnym. Przeanalizuj plan treningowy użytkownika ")
            append("i wskaż mocne strony oraz problemy. Bądź konkretny — cytuj nazwy ćwiczeń ")
            append("i liczby. Bazuj na zasadach: balans push/pull, antagonista wzgl. agonisty, ")
            append("volume 10-20 setów/partia/tydzień (hipertrofia), nie więcej niż 6 ćwiczeń/dzień.\n\n")

            if (masterCtx != null) {
                append("# KONTEKST OGÓLNY (do tła audytu)\n")
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toAdherenceSection(masterCtx))
                append(MasterAiContextPromptHelper.toRecoverySection(masterCtx))
                append(MasterAiContextPromptHelper.toPRsSection(masterCtx))
                append("\n")
            }
            if (recentFeedback.isNotBlank()) {
                append("# OSTATNIE FEEDBACK Z TRENINGÓW (wellbeing 1-5 + ból)\n")
                append(recentFeedback)
                append("Jeśli widać ból lub niski wellbeing — UWZGLĘDNIJ to w ocenie planu ")
                append("(czy plan nie nadmiernie obciąża bolącej partii, czy nie wymaga deloadu).\n\n")
            }

            append("# PLAN: ${plan.name}\n")
            append("- Cel użytkownika: ${profile.goal.name}\n")
            append("- Częstotliwość: ${plan.daysOfWeek.size} dni / tydzień\n")
            if (plan.notes.isNotBlank()) append("- Notatki: ${plan.notes}\n")
            append("\n")

            byDay.forEach { (day, dayExes) ->
                val dayName = when (day) {
                    1 -> "Poniedziałek"
                    2 -> "Wtorek"
                    3 -> "Środa"
                    4 -> "Czwartek"
                    5 -> "Piątek"
                    6 -> "Sobota"
                    7 -> "Niedziela"
                    else -> "Dzień $day"
                }
                append("## $dayName (${dayExes.size} ćwiczeń)\n")
                dayExes.sortedBy { it.orderIndex }.forEach { pe ->
                    val ex = exMap[pe.exerciseId]
                    val sets = planSetDao.getForPlanExercise(pe.id)
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

            append("# OCZEKIWANY FORMAT ODPOWIEDZI (Markdown, max 500 słów)\n\n")
            append("## Mocne strony\n(2-3 punkty z konkretami)\n\n")
            append("## Problemy do poprawy\n(jeśli są — luki, dysbalans push/pull, brak partii, za dużo ćwiczeń jednego dnia)\n\n")
            append("## Konkretne sugestie\n")
            append("(dla każdego problemu — co dodać/wymienić/usunąć, najlepiej z nazwą ćwiczenia)\n\n")
            append("## Volume per partia (oszacowanie tygodniowe)\n")
            append("Klatka: X setów (10-20 OK / ZA MAŁO / ZA DUŻO)\n")
            append("Plecy: ...\n(itd dla głównych partii)\n\n")
            append("Bądź pomocny, nie laudator. Cytuj liczby.")
        }

        return client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt)),
            source = "PlanAudit.audit"
        ).map { it.trim() }
    }
}
