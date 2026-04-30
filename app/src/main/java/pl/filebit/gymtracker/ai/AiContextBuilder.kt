package pl.filebit.gymtracker.ai

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.ProgressPhotoRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.StrengthRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiContextBuilder @Inject constructor(
    private val profileRepo: UserProfileRepository,
    private val bodyRepo: BodyRepository,
    private val statsRepo: StatsRepository,
    private val strengthRepo: StrengthRepository,
    private val photoRepo: ProgressPhotoRepository,
    private val planDao: TrainingPlanDao,
    private val planExerciseDao: PlanExerciseDao,
    private val planSetDao: PlanExerciseSetDao,
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {

    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    /**
     * Buduje pełny kontekst usera jako JSON. recentWorkoutsLimit kontroluje ile
     * ostatnich treningów dołączyć (default 30).
     */
    suspend fun buildContextJson(recentWorkoutsLimit: Int = 30): String {
        val profile = profileRepo.get()
        val measurements = bodyRepo.getAllAsc().takeLast(15)
        val overview = statsRepo.overview()
        val streak = statsRepo.streakInfo()
        val weekProgress = statsRepo.weekProgress(profile.daysPerWeek)
        val achievements = statsRepo.unlockedAchievements(profile.daysPerWeek)
        val muscle = statsRepo.muscleEngagement(periodDays = 90)
        val strength = strengthRepo.evaluateAll()
        val photos = photoRepo.observeAll().first()

        val plans = planDao.getAll()
        val allExercises = exerciseDao.getAll()
        val recentWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .take(recentWorkoutsLimit)

        val obj = buildJsonObject {
            put("now", dfTime.format(Date()))

            putJsonObject("profile") {
                put("goal", profile.goal.name)
                put("experience", profile.experience.name)
                put("gender", profile.gender.name)
                put("daysPerWeek", profile.daysPerWeek)
                put("sessionMinutes", profile.sessionMinutes)
                put("preferredUnit", profile.preferredUnit.name)
                put("defaultRestSeconds", profile.defaultRestSeconds)
                put("injuriesNotes", profile.injuriesNotes)
                put("bodyweightKg", profile.bodyweightKg ?: -1.0)
                put("weightGoalType", profile.weightGoalType.name)
                put("targetWeightKg", profile.targetWeightKg ?: -1.0)
            }

            putJsonObject("stats_overview") {
                put("totalWorkouts", overview.totalWorkouts)
                put("totalVolumeKg", overview.totalVolumeKg)
                put("totalSets", overview.totalSets)
                put("totalDurationMs", overview.totalDurationMillis)
                put("avgVolumePerWorkout", overview.avgVolumePerWorkout)
                put("workoutsThisWeek", overview.workoutsThisWeek)
                put("workoutsThisMonth", overview.workoutsThisMonth)
                put("currentStreakWeeks", streak.current)
                put("bestStreakWeeks", streak.best)
                put("weekProgressCurrent", weekProgress.current)
                put("weekProgressTarget", weekProgress.target)
            }

            putJsonArray("achievements") {
                achievements.filter { it.unlocked }.forEach { a ->
                    add(buildJsonObject {
                        put("id", a.id)
                        put("title", a.title)
                        put("description", a.description)
                    })
                }
            }

            putJsonArray("muscle_engagement_90d") {
                muscle.forEach { m ->
                    add(buildJsonObject {
                        put("muscle", m.muscle.name)
                        put("volumeKg", m.volumeKg)
                        put("totalSets", m.totalSets)
                        put("percentOfTotal", m.percentOfTotal)
                    })
                }
            }

            putJsonArray("body_measurements_recent") {
                measurements.forEach { m ->
                    add(buildJsonObject {
                        put("date", df.format(Date(m.date)))
                        m.weightKg?.let { put("weightKg", it) }
                        m.chestCm?.let { put("chestCm", it) }
                        m.waistCm?.let { put("waistCm", it) }
                        m.hipsCm?.let { put("hipsCm", it) }
                        m.armCm?.let { put("armCm", it) }
                        m.thighCm?.let { put("thighCm", it) }
                        m.calfCm?.let { put("calfCm", it) }
                        m.bodyFatPercent?.let { put("bodyFatPercent", it) }
                        if (m.notes.isNotBlank()) put("notes", m.notes)
                    })
                }
            }

            putJsonArray("strength_levels") {
                strength.filter { it.hasData }.forEach { ev ->
                    add(buildJsonObject {
                        put("exercise", ev.exercise?.name ?: ev.standard.exerciseNamePrefix)
                        put("estimated1RMKg", ev.current1RMKg)
                        put("ratioPerBodyweight", ev.ratio)
                        put("level", ev.level.name)
                        ev.nextLevelKg?.let { put("kgToNextLevel", it) }
                    })
                }
            }

            putJsonArray("plans") {
                plans.forEach { plan ->
                    add(buildJsonObject {
                        put("id", plan.id)
                        put("name", plan.name)
                        put("daysOfWeek", buildJsonArray { plan.daysOfWeek.forEach { add(it) } })
                        put("notes", plan.notes)
                        put("days", buildJsonArray {
                            val days = planExerciseDao.getDaysWithExercises(plan.id)
                            days.forEach { day ->
                                val pes = planExerciseDao.getForPlanAndDay(plan.id, day)
                                add(buildJsonObject {
                                    put("dayOfWeek", day)
                                    put("exercises", buildJsonArray {
                                        pes.forEach { pe ->
                                            val ex = exerciseDao.getById(pe.exerciseId)
                                            val sets = planSetDao.getForPlanExercise(pe.id)
                                            add(buildJsonObject {
                                                put("name", ex?.name ?: "?")
                                                put("primaryMuscle", ex?.primaryMuscle?.name ?: "")
                                                put("equipment", ex?.equipment?.name ?: "")
                                                pe.supersetGroup?.let { put("supersetGroup", it) }
                                                put("sets", buildJsonArray {
                                                    sets.forEach { s ->
                                                        add(buildJsonObject {
                                                            put("setNumber", s.setNumber)
                                                            put("reps", s.reps)
                                                            s.weightKg?.let { put("weightKg", it) }
                                                            s.restSeconds?.let { put("restSec", it) }
                                                        })
                                                    }
                                                })
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            }

            putJsonArray("recent_workouts") {
                recentWorkouts.forEach { w ->
                    val sets = setDao.getForWorkout(w.id)
                    val byExercise = sets.groupBy { it.exerciseId }
                    add(buildJsonObject {
                        put("id", w.id)
                        put("startedAt", dfTime.format(Date(w.startedAt)))
                        w.finishedAt?.let { put("finishedAt", dfTime.format(Date(it))) }
                        put("durationMin",
                            ((w.finishedAt ?: w.startedAt) - w.startedAt) / 60_000)
                        if (w.notes.isNotBlank()) put("notes", w.notes)
                        put("exercises", buildJsonArray {
                            byExercise.forEach { (exId, list) ->
                                val ex = exerciseDao.getById(exId)
                                add(buildJsonObject {
                                    put("name", ex?.name ?: "?")
                                    put("sets", buildJsonArray {
                                        list.sortedBy { it.setNumber }.forEach { s ->
                                            add(buildJsonObject {
                                                put("setNumber", s.setNumber)
                                                put("setType", s.setType.name)
                                                put("reps", s.reps)
                                                put("weightKg", s.weightKg)
                                                put("isCompleted", s.isCompleted)
                                                s.rpe?.let { put("rpe", it) }
                                                s.rir?.let { put("rir", it) }
                                                s.tempo?.let { put("tempo", it) }
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            }

            putJsonArray("progress_photos") {
                photos.take(20).forEach { p ->
                    add(buildJsonObject {
                        put("date", df.format(Date(p.date)))
                        put("type", p.photoType.name)
                    })
                }
            }

            // Lista nazw ćwiczeń z biblioteki - LLM musi je używać dokładnie
            putJsonArray("available_exercises") {
                allExercises.forEach { ex ->
                    add(buildJsonObject {
                        put("name", ex.name)
                        put("muscle", ex.primaryMuscle.name)
                        put("equipment", ex.equipment.name)
                    })
                }
            }
        }

        return pretty.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }

    /**
     * Krótki kontekst dla quick actions — bez listy 200 ćwiczeń (LLM może o nią poprosić jeśli potrzeba).
     */
    suspend fun buildShortContextJson(recentWorkoutsLimit: Int = 10): String {
        val full = buildContextJson(recentWorkoutsLimit)
        // skróć — usuń available_exercises blok
        return full
    }
}
