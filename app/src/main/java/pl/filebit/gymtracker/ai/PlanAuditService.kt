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
    private val profileRepo: UserProfileRepository
) {
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

        val prompt = buildString {
            append("Jesteś trenerem personalnym. Przeanalizuj plan treningowy użytkownika ")
            append("i wskaż mocne strony oraz problemy. Bądź konkretny — cytuj nazwy ćwiczeń ")
            append("i liczby. Bazuj na zasadach: balans push/pull, antagonista wzgl. agonisty, ")
            append("volume 10-20 setów/partia/tydzień (hipertrofia), nie więcej niż 6 ćwiczeń/dzień.\n\n")

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
            listOf(AiMessage(AiRole.USER, prompt))
        ).map { it.trim() }
    }
}
