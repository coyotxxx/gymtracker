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
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.GoalRepository
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
    private val goalRepo: GoalRepository,
    private val planDao: TrainingPlanDao,
    private val planExerciseDao: PlanExerciseDao,
    private val planSetDao: PlanExerciseSetDao,
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService,
    private val trainingEventDao: pl.filebit.gymtracker.data.db.dao.TrainingEventDao
) {

    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    private data class PlanWithDays(
        val plan: TrainingPlan,
        val days: List<DayWithExercises>
    )
    private data class DayWithExercises(
        val dayOfWeek: Int,
        val exercises: List<ExerciseWithSets>
    )
    private data class ExerciseWithSets(
        val planExercise: PlanExercise,
        val exercise: Exercise?,
        val sets: List<PlanExerciseSet>
    )

    private data class WorkoutWithExercises(
        val workout: Workout,
        val byExercise: List<Pair<Exercise?, List<WorkoutSet>>>
    )

    suspend fun buildContextJson(
        recentWorkoutsLimit: Int = 5,
        targetPlanId: Long? = null
    ): String {
        val profile = profileRepo.get()
        val allMeasurements = bodyRepo.getAllAsc()
        val measurements = allMeasurements.takeLast(7)
        // v1.11.45: snapshot raz dla overview/muscleEngagement + workouts data
        val snapshot = statsCacheService.snapshot()
        val overview = statsRepo.overviewFast(snapshot)
        val streak = statsRepo.streakInfo()
        val weekProgress = statsRepo.weekProgress(profile.daysPerWeek)
        val achievements = statsRepo.unlockedAchievements(profile.daysPerWeek)
        val muscle = statsRepo.muscleEngagementFast(periodDays = 90, snapshot = snapshot)
        // v1.11.58: nowe sekcje analityczne (lekkie - czytane z snapshot)
        val volumePerWeek12 = statsRepo.volumePerWeekFast(weeks = 12, snapshot = snapshot)
        val bodyInflections = computeBodyInflections(allMeasurements)
        val painLog90d = computePainLog90d(snapshot.finishedWorkouts)
        // v1.11.59: event log (PR-y, kontuzje, deloady, zmiany planu, gap_resumed)
        val recentEvents = trainingEventDao.getRecent(limit = 15)
        val strength = strengthRepo.evaluateAll()
        val photos = photoRepo.observeAll().first()
        val goalProgresses = goalRepo.computeAllActiveProgress()
        val allExercises = snapshot.allExercises  // pre-fetched w snapshot

        // v1.11.58: Pre-collect plans — TYLKO targetPlan z pelnymi detalami;
        // pozostale plany jako summary (nazwa + dni + notes), zeby zmniejszyc prompt
        val allPlans = planDao.getAll()
        val plansData: List<PlanWithDays> = if (targetPlanId != null) {
            allPlans.filter { it.id == targetPlanId }.map { plan ->
                val days = planExerciseDao.getDaysWithExercises(plan.id)
                val daysData = days.map { day ->
                    val pes = planExerciseDao.getForPlanAndDay(plan.id, day)
                    val exercisesData = pes.map { pe ->
                        ExerciseWithSets(
                            planExercise = pe,
                            exercise = exerciseDao.getById(pe.exerciseId),
                            sets = planSetDao.getForPlanExercise(pe.id)
                        )
                    }
                    DayWithExercises(day, exercisesData)
                }
                PlanWithDays(plan, daysData)
            }
        } else emptyList()

        // v1.11.45: Pre-collect recent workouts z snapshot (zero N+1 queries)
        val recentWorkouts = snapshot.finishedWorkouts.take(recentWorkoutsLimit)
        val workoutsData: List<WorkoutWithExercises> = recentWorkouts.map { w ->
            val sets = snapshot.setsByWorkoutId[w.id] ?: emptyList()
            val byExercise = sets.groupBy { it.exerciseId }.map { (exId, list) ->
                snapshot.exercisesById[exId] to list
            }
            WorkoutWithExercises(w, byExercise)
        }

        val obj = buildJsonObject {
            put("now", dfTime.format(Date()))
            // Gdy user kliknął 'modyfikuj plan X' — AI ma instrukcję żeby
            // generować JSON jako MODYFIKACJĘ tego planu (zamiast nowego)
            targetPlanId?.let { tid ->
                val targetPlan = plansData.firstOrNull { it.plan.id == tid }
                if (targetPlan != null) {
                    putJsonObject("target_plan_to_modify") {
                        put("id", targetPlan.plan.id)
                        put("name", targetPlan.plan.name)
                        put("hint", "Użytkownik prosi o MODYFIKACJĘ tego planu. " +
                            "Jeśli generujesz JSON propozycji, zachowaj nazwę '${targetPlan.plan.name}' " +
                            "lub jej wariant — tworzymy poprawioną wersję, nie nowy plan.")
                    }
                }
            }

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

            putJsonArray("active_goals") {
                goalProgresses.forEach { gp ->
                    add(buildJsonObject {
                        put("type", gp.goal.type.name)
                        put("title", gp.goal.title)
                        put("description", gp.goal.description)
                        put("unit", gp.goal.unit.name)
                        put("startValue", gp.goal.startValue)
                        put("targetValue", gp.goal.targetValue)
                        put("currentValue", gp.currentValue)
                        put("startDate", df.format(Date(gp.goal.startDate)))
                        put("deadline", df.format(Date(gp.goal.deadline)))
                        put("daysElapsed", gp.daysElapsed)
                        put("daysTotal", gp.daysTotal)
                        put("daysRemaining", gp.daysRemaining)
                        put("percentDone", gp.percentDone)
                        put("pacePercent", gp.pacePercent)
                        put("onTrack", gp.onTrack)
                        put("achieved", gp.achieved)
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

            // v1.11.58: plans -> tylko summary (nazwy/daty/notes) + opcjonalny target_plan z pelnymi detalami
            putJsonArray("plan_history_summary") {
                allPlans.forEach { plan ->
                    add(buildJsonObject {
                        put("id", plan.id)
                        put("name", plan.name)
                        put("daysOfWeek", buildJsonArray { plan.daysOfWeek.forEach { add(it) } })
                        if (plan.notes.isNotBlank()) put("notes", plan.notes)
                    })
                }
            }
            // Pelne detale TYLKO targetPlanId
            if (plansData.isNotEmpty()) {
                putJsonArray("target_plan_full") {
                    plansData.forEach { pwd ->
                        add(buildJsonObject {
                            put("id", pwd.plan.id)
                            put("name", pwd.plan.name)
                            put("daysOfWeek", buildJsonArray { pwd.plan.daysOfWeek.forEach { add(it) } })
                            put("notes", pwd.plan.notes)
                            put("days", buildJsonArray {
                                pwd.days.forEach { dwe ->
                                    add(buildJsonObject {
                                        put("dayOfWeek", dwe.dayOfWeek)
                                        put("exercises", buildJsonArray {
                                            dwe.exercises.forEach { ews ->
                                                add(buildJsonObject {
                                                    put("name", ews.exercise?.name ?: "?")
                                                    put("primaryMuscle", ews.exercise?.primaryMuscle?.name ?: "")
                                                    put("equipment", ews.exercise?.equipment?.name ?: "")
                                                    ews.planExercise.supersetGroup?.let {
                                                        put("supersetGroup", it)
                                                    }
                                                    put("sets", buildJsonArray {
                                                        ews.sets.forEach { s ->
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
            }

            putJsonArray("recent_workouts") {
                workoutsData.forEach { wwe ->
                    add(buildJsonObject {
                        put("id", wwe.workout.id)
                        put("startedAt", dfTime.format(Date(wwe.workout.startedAt)))
                        wwe.workout.finishedAt?.let { put("finishedAt", dfTime.format(Date(it))) }
                        put(
                            "durationMin",
                            ((wwe.workout.finishedAt ?: wwe.workout.startedAt) - wwe.workout.startedAt) / 60_000
                        )
                        if (wwe.workout.notes.isNotBlank()) put("notes", wwe.workout.notes)
                        // Post-workout feedback (v0.82.0+): wellbeing 1-5, ból
                        wwe.workout.wellbeingRating?.let { put("wellbeing", it) }
                        wwe.workout.painArea?.let { put("painArea", it) }
                        wwe.workout.painNotes?.let { put("painNotes", it) }
                        put("exercises", buildJsonArray {
                            wwe.byExercise.forEach { (ex, list) ->
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

            // v1.11.58: punkty inflexji wagi (zamiast 15 dziennych pomiarow)
            putJsonObject("body_inflections") {
                bodyInflections.startWeightKg?.let {
                    put("startWeightKg", it)
                    put("startDate", df.format(Date(bodyInflections.startDate!!)))
                }
                bodyInflections.minWeightKg?.let {
                    put("minWeightKg", it)
                    put("minDate", df.format(Date(bodyInflections.minDate!!)))
                }
                bodyInflections.maxWeightKg?.let {
                    put("maxWeightKg", it)
                    put("maxDate", df.format(Date(bodyInflections.maxDate!!)))
                }
                bodyInflections.currentWeightKg?.let {
                    put("currentWeightKg", it)
                    put("currentDate", df.format(Date(bodyInflections.currentDate!!)))
                }
                bodyInflections.totalChangeKg?.let { put("totalChangeKg", it) }
            }

            // v1.11.58: trend objetosci tygodniowej (12 tyg) - dla AI to widzi cykl
            putJsonArray("weekly_volume_trend_12w") {
                volumePerWeek12.forEachIndexed { idx, vol ->
                    add(buildJsonObject {
                        put("weeksAgo", 11 - idx)
                        put("volumeKg", vol)
                    })
                }
            }

            // v1.11.59: event log - kluczowe wydarzenia w cyklu treningowym
            // (PR-y, kontuzje, deloady, zmiany planu, gap_resumed). To jest "pamiec
            // epizodyczna" AI - eventy trzymane na zawsze, niezalezne od agregacji
            // surowych danych w v1.11.60+.
            putJsonArray("event_log") {
                recentEvents.forEach { e ->
                    add(buildJsonObject {
                        put("date", df.format(Date(e.date)))
                        put("type", e.type.name)
                        e.exerciseName?.let { put("exercise", it) }
                        e.weightKg?.let { put("weightKg", it) }
                        e.reps?.let { put("reps", it) }
                        e.e1rmKg?.let { put("e1rmKg", it) }
                        e.area?.let { put("area", it) }
                        e.planName?.let { put("planName", it) }
                        e.weeksContext?.let { put("weeksContext", it) }
                        if (e.notes.isNotBlank()) put("notes", e.notes)
                    })
                }
            }

            // v1.11.58: log bolu z 90 dni (zagregowany per area)
            putJsonObject("pain_log_90d") {
                put("workoutsWithPain", painLog90d.totalWorkoutsWithPain)
                put("workoutsAnalyzed", painLog90d.totalWorkoutsAnalyzed)
                putJsonArray("areas") {
                    painLog90d.areas.forEach { p ->
                        add(buildJsonObject {
                            put("area", p.area)
                            put("count", p.count)
                            put("lastOccurrence", df.format(Date(p.lastOccurrenceMs)))
                        })
                    }
                }
            }

            // v1.11.58: available_exercises tylko gdy AI generuje/modyfikuje plan
            // (200+ pozycji = ~30 KB samych nazw, bez sensu w kazdej rozmowie)
            if (targetPlanId != null) {
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
        }

        return pretty.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }
}

// ============================================================================
// v1.11.58 — Pure functions dla nowych sekcji kontekstu AI
// Testowalne bez DI — biora primitives, zwracaja JSON-ready data klasy.
// ============================================================================

/**
 * Wynik [computeBodyInflections] — punkty inflexji wagi w calej historii uzytkownika.
 * Zamiast 15 dziennych pomiarow wystarczy te 4 wartosci do zrozumienia trajektorii.
 */
data class BodyInflections(
    val startWeightKg: Double?,
    val startDate: Long?,
    val minWeightKg: Double?,
    val minDate: Long?,
    val maxWeightKg: Double?,
    val maxDate: Long?,
    val currentWeightKg: Double?,
    val currentDate: Long?,
    val totalChangeKg: Double?
)

/**
 * Liczy punkty inflexji wagi z calej historii pomiarow.
 * Zamiast wysylac AI 15 dziennych wpisow - wysylamy 4 kluczowe momenty.
 */
fun computeBodyInflections(measurements: List<pl.filebit.gymtracker.data.entity.BodyMeasurement>): BodyInflections {
    val withWeight = measurements.filter { it.weightKg != null }.sortedBy { it.date }
    if (withWeight.isEmpty()) {
        return BodyInflections(null, null, null, null, null, null, null, null, null)
    }
    val first = withWeight.first()
    val last = withWeight.last()
    val minMeasurement = withWeight.minBy { it.weightKg!! }
    val maxMeasurement = withWeight.maxBy { it.weightKg!! }
    return BodyInflections(
        startWeightKg = first.weightKg,
        startDate = first.date,
        minWeightKg = minMeasurement.weightKg,
        minDate = minMeasurement.date,
        maxWeightKg = maxMeasurement.weightKg,
        maxDate = maxMeasurement.date,
        currentWeightKg = last.weightKg,
        currentDate = last.date,
        totalChangeKg = if (first.weightKg != null && last.weightKg != null)
            last.weightKg - first.weightKg else null
    )
}

/**
 * Wynik [computePainLog90d] — agregat dolegliwosci z ostatnich 90 dni.
 * Zamiast wysylac AI kazdy painArea osobno - liczymy unique area + frequency.
 */
data class PainLogSummary(
    val totalWorkoutsWithPain: Int,
    val totalWorkoutsAnalyzed: Int,
    val areas: List<PainAreaCount>
)

data class PainAreaCount(
    val area: String,
    val count: Int,
    val lastOccurrenceMs: Long
)

/**
 * Liczy log bolu z ostatnich 90 dni - groupowane per area + sortowane po liczbie wystapien.
 */
fun computePainLog90d(
    workouts: List<pl.filebit.gymtracker.data.entity.Workout>,
    nowMs: Long = System.currentTimeMillis()
): PainLogSummary {
    val cutoff = nowMs - 90L * 24 * 60 * 60 * 1000
    val recent = workouts.filter { it.startedAt >= cutoff && it.finishedAt != null }
    val withPain = recent.filter { !it.painArea.isNullOrBlank() }
    val grouped = withPain
        .groupBy { it.painArea!! }
        .map { (area, list) ->
            PainAreaCount(
                area = area,
                count = list.size,
                lastOccurrenceMs = list.maxOf { it.startedAt }
            )
        }
        .sortedByDescending { it.count }
    return PainLogSummary(
        totalWorkoutsWithPain = withPain.size,
        totalWorkoutsAnalyzed = recent.size,
        areas = grouped
    )
}
