package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.strength.StrengthLevel
import pl.filebit.gymtracker.data.strength.StrengthStandard
import pl.filebit.gymtracker.data.strength.StrengthStandards
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class StrengthEvaluation(
    val standard: StrengthStandard,
    val exercise: Exercise?,                  // może być null jeśli brak ćwiczenia w bazie
    val current1RMKg: Double,                 // 0.0 jeśli brak wpisów
    val bodyweightKg: Double,
    val ratio: Double,                        // current1RM / bodyweight
    val level: StrengthLevel,
    val nextLevelKg: Double?,                 // ile kg do następnego progu (null = ELITE)
    val hasData: Boolean,                     // czy są jakiekolwiek sety + bodyweight
    val gender: pl.filebit.gymtracker.data.entity.Gender,
    val eliteRatio: Double
)

@Singleton
class StrengthRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val setDao: WorkoutSetDao,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository,
    private val bodyRepo: BodyRepository
) {

    /**
     * Oblicza poziom siły dla wszystkich 6 wbudowanych standardów.
     * Bodyweight = profile.bodyweightKg ?? ostatni BodyMeasurement.weightKg.
     */
    suspend fun evaluateAll(): List<StrengthEvaluation> {
        val profile = profileRepo.get()
        val bw = profile.bodyweightKg
            ?: bodyRepo.getLatest()?.weightKg
            ?: 0.0
        val allExercises = exerciseDao.getAll()

        return StrengthStandards.all().map { std ->
            val ex = allExercises.firstOrNull { it.slug == std.exerciseSlug }
            val best1RM = ex?.let { exercise ->
                val sets = setDao.getAllForExercise(exercise.id)
                    .filter { it.isCompleted && it.setType != SetType.WARMUP }
                sets.maxOfOrNull { statsRepo.epley1RM(it.weightKg, it.reps) } ?: 0.0
            } ?: 0.0

            // Pull-up: ratio = (BW + dodany ciężar) / BW; ale w naszym modelu weightKg
            // przy podciąganiu może oznaczać dodany ciężar lub całość. Dla MVP zakładamy
            // że user wpisuje całkowity opór (BW + obciążenie). Jeśli weightKg ~ 0 → BW.
            val effective1RM = when (std.exerciseSlug) {
                StrengthStandards.SLUG_PULL_UP -> if (best1RM <= 0.0) bw else best1RM
                else -> best1RM
            }
            val hasData = bw > 0.0 && effective1RM > 0.0
            val ratio = if (bw > 0.0) effective1RM / bw else 0.0
            val level = std.classify(ratio, profile.gender)
            val ratios = std.ratiosFor(profile.gender)
            val nextRatio = when (level) {
                StrengthLevel.BELOW_BEGINNER -> ratios[0]
                StrengthLevel.BEGINNER -> ratios[1]
                StrengthLevel.NOVICE -> ratios[2]
                StrengthLevel.INTERMEDIATE -> ratios[3]
                StrengthLevel.ADVANCED -> ratios[4]
                StrengthLevel.ELITE -> null
            }
            val nextLevelKg = nextRatio?.let { (it * bw - effective1RM).coerceAtLeast(0.0) }
                ?.let { (it * 10).roundToInt() / 10.0 }

            StrengthEvaluation(
                standard = std,
                exercise = ex,
                current1RMKg = (effective1RM * 10).roundToInt() / 10.0,
                bodyweightKg = bw,
                ratio = ratio,
                level = level,
                nextLevelKg = nextLevelKg,
                hasData = hasData,
                gender = profile.gender,
                eliteRatio = ratios.last()
            )
        }
    }
}
