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
    private val profileRepo: UserProfileRepository
) {
    suspend fun generate(): Result<String> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("AI nie skonfigurowane — wpisz klucz API w Profilu"))
        }

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        // Poniedziałek tego tygodnia
        val daysFromMonday = (today.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber)
        val mondayDate = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val weekStartMillis = mondayDate.atStartOfDayIn(tz).toEpochMilliseconds()
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
                    rpeTrend = rpeTrend
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

        // Build prompt
        val prompt = buildString {
            append("Jesteś trenerem personalnym. Przeanalizuj poniższy tydzień treningowy ")
            append("użytkownika i daj rekomendacje na następny tydzień. Bądź konkretny — ")
            append("cytuj liczby z danych. Bazuj na zasadach RP, MASS i Israetela. Polski język.\n\n")

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
            append("---\n\n")
            append("Reguły volume na tydzień (wg literatury): 10–20 setów / partia / tydzień (hipertrofia), ")
            append("8–14 (siła). Jeśli stagnacja 3+ treningów → sugeruj −10% deload na ten tydzień ")
            append("LUB wymianę wariantu ćwiczenia. Maks 600 słów. Zachowaj balans motywacji i ")
            append("konkretu — bądź pomocny, nie laudator.")
        }

        val result = client.chat(
            cfg,
            listOf(AiMessage(AiRole.USER, prompt))
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
    val rpeTrend: String
)
