package pl.filebit.gymtracker.ai

import kotlinx.coroutines.flow.first
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Generator planu treningowego.
 *
 * Filozofia:
 *  - Bierze ULUBIONE ćwiczenia (Exercise.isFavorite=true) jako preferowane
 *  - Filtruje przez DOSTĘPNY SPRZĘT (UserProfile.availableEquipmentCsv)
 *  - Układa split N-dniowy (2-6 dni) z pokryciem partii mięśniowych
 *  - Każde ćwiczenie ma 3-4 serie z reps/RPE według celu (HYPERTROPHY/STRENGTH)
 *  - Zapisuje TrainingPlan + PlanExercise + PlanExerciseSet do bazy
 *
 * AI tu nie generuje JSON — to silnik regułowy. Deterministyczny.
 * (Jeśli kiedyś dodamy "AI variation" przez Claude/OpenAI — można użyć
 * tego samego patternu co w DietAiService.)
 */
@Singleton
class WorkoutPlanAiService @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
    private val planRepo: PlanRepository
) {

    data class GeneratedPlanResult(
        val planId: Long,
        val planName: String,
        val daysCount: Int,
        val exercisesPerDay: Map<Int, List<String>>,  // dayOfWeek (1-7) → lista nazw
        val warnings: List<String>
    )

    /**
     * Generuje plan treningowy.
     *
     * @param daysPerWeek liczba dni treningowych w tygodniu (2-6)
     * @param favoritesOnly true = tylko z ulubionych, false = pełna baza
     * @param planName nazwa planu (default: auto)
     */
    suspend fun generate(
        daysPerWeek: Int = 4,
        favoritesOnly: Boolean = true,
        planName: String? = null
    ): Result<GeneratedPlanResult> = runCatching {
        val profile = profileRepo.get()
        val warnings = mutableListOf<String>()

        // 1. Pobierz dostępne ćwiczenia
        val all = exerciseRepo.observeAll().first()
        val available = filterByEquipment(all, profile.availableEquipmentCsv)
        val pool = if (favoritesOnly) {
            val favs = available.filter { it.isFavorite }
            if (favs.size < 12) {
                warnings += "Mało ulubionych (${favs.size}) — uzupełniam z pełnej puli."
                (favs + available.filter { !it.isFavorite }).distinctBy { it.id }
            } else favs
        } else available

        if (pool.size < 12) {
            return@runCatching GeneratedPlanResult(
                planId = -1L,
                planName = "—",
                daysCount = 0,
                exercisesPerDay = emptyMap(),
                warnings = listOf("Za mało ćwiczeń w bazie (mniej niż 12) — nie mogę wygenerować planu.")
            )
        }

        // 2. Wybierz split per liczba dni
        val split = splitFor(daysPerWeek, profile.goal)

        // 3. Plan sets per cel treningowy
        val (setsPerEx, repsPerSet, restSec) = setsConfigFor(profile.goal)

        // 4. Stwórz Plan w bazie
        val finalName = planName ?: "Plan ${daysPerWeek}-dniowy (auto)"
        val plan = TrainingPlan(
            name = finalName,
            daysOfWeek = (1..daysPerWeek).toList(),
            notes = "Wygenerowany automatycznie. Cel: ${goalLabel(profile.goal)}. " +
                "${if (favoritesOnly) "Z ulubionych ćwiczeń" else "Z pełnej bazy"}.",
            createdByAi = true
        )
        val planId = planRepo.upsertPlan(plan)

        // 5. Per dzień przypisz ćwiczenia
        val exercisesByDay = mutableMapOf<Int, List<String>>()
        var orderIdx = 0
        split.forEachIndexed { dayIdx, dayMuscles ->
            val dayOfWeek = dayIdx + 1   // ISO: 1=PN..7=ND
            val perDay = pickExercisesForDay(pool, dayMuscles, exercisesPerDayCount = 5)
            val names = mutableListOf<String>()

            perDay.forEach { ex ->
                val pe = PlanExercise(
                    planId = planId,
                    exerciseId = ex.id,
                    dayOfWeek = dayOfWeek,
                    orderIndex = orderIdx++
                )
                val peId = planRepo.upsertPlanExercise(pe)

                // Stwórz serie
                repeat(setsPerEx) { i ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = i + 1,
                            reps = repsPerSet,
                            weightKg = null,                // user wpisze przy treningu
                            restSeconds = restSec,
                            setType = SetType.NORMAL
                        )
                    )
                }
                names += ex.name
            }
            exercisesByDay[dayOfWeek] = names
        }

        GeneratedPlanResult(
            planId = planId,
            planName = finalName,
            daysCount = split.size,
            exercisesPerDay = exercisesByDay,
            warnings = warnings
        )
    }

    /** Filtruj po dostępnym sprzęcie. Pusty CSV = wszystkie. */
    private fun filterByEquipment(all: List<Exercise>, equipmentCsv: String): List<Exercise> {
        if (equipmentCsv.isBlank()) return all
        val allowed = equipmentCsv.split(",").mapNotNull {
            runCatching { Equipment.valueOf(it.trim()) }.getOrNull()
        }.toSet()
        if (allowed.isEmpty()) return all
        return all.filter { it.equipment in allowed }
    }

    /**
     * Split treningowy zależny od liczby dni i celu.
     * 2-day: full body A/B
     * 3-day: PUSH / PULL / LEGS (klasyk)
     * 4-day: UPPER / LOWER / UPPER / LOWER
     * 5-day: PUSH / PULL / LEGS / UPPER / LOWER
     * 6-day: PPL × 2 (advanced)
     */
    private fun splitFor(days: Int, goal: TrainingGoal): List<Set<MuscleGroup>> {
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

    /** Sety/reps/odpoczynek zależnie od celu. */
    private fun setsConfigFor(goal: TrainingGoal): Triple<Int, Int, Int> = when (goal) {
        TrainingGoal.STRENGTH -> Triple(4, 5, 180)            // 4×5, 3 min
        TrainingGoal.HYPERTROPHY -> Triple(4, 10, 90)         // 4×10, 90s
        TrainingGoal.MIX -> Triple(4, 8, 120)                 // 4×8, 2 min
        TrainingGoal.GENERAL_FITNESS -> Triple(3, 12, 60)     // 3×12, 60s
        TrainingGoal.CARDIO_LIFTING -> Triple(3, 15, 45)      // 3×15, 45s (krążeniowe)
    }

    /**
     * Wybiera ćwiczenia na dany dzień. Strategia:
     * 1. Z każdej partii mięśniowej dnia weź 1-2 ćwiczenia
     * 2. Preferuj ulubione (już są w pool jeśli favoritesOnly=true)
     * 3. Mieszaj typy (compound vs isolation jeśli możliwe)
     */
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

        // Pierwsza runda — 1 ćwiczenie z każdej partii
        for (muscle in targetMuscles) {
            val candidates = byMuscle[muscle].orEmpty().filter { it.id !in takenIds }
            if (candidates.isNotEmpty()) {
                picked += candidates.first()
                takenIds += candidates.first().id
                if (picked.size >= exercisesPerDayCount) break
            }
        }

        // Druga runda — uzupełnij do exercisesPerDayCount
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

    private fun goalLabel(goal: TrainingGoal): String = when (goal) {
        TrainingGoal.STRENGTH -> "siła (4×5)"
        TrainingGoal.HYPERTROPHY -> "hipertrofia (4×10)"
        TrainingGoal.MIX -> "siła+masa (4×8)"
        TrainingGoal.GENERAL_FITNESS -> "ogólna sprawność (3×12)"
        TrainingGoal.CARDIO_LIFTING -> "cardio+siłka (3×15)"
    }
}
