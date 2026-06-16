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
    private val trainingEventDao: pl.filebit.gymtracker.data.db.dao.TrainingEventDao,
    private val weeklyRollupDao: pl.filebit.gymtracker.data.db.dao.WeeklyRollupDao,
    private val monthlyRollupDao: pl.filebit.gymtracker.data.db.dao.MonthlyRollupDao,
    private val quarterlyRollupDao: pl.filebit.gymtracker.data.db.dao.QuarterlyRollupDao,
    // v2.59.0 (U9+U10): zapamiętana przyczyna przerwy w treningach — Trener AI ma ją znać.
    private val deloadPrefs: pl.filebit.gymtracker.data.repository.DeloadPreferences
) {

    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    /**
     * v1.11.61: bezpieczne zaokraglenie - locale-free.
     * Powod: '%.1f'.format(x) na polskim Androidzie zwraca '1,5' (przecinek)
     * -> .toDouble() rzuca NumberFormatException -> caly buildContextJson
     * pada -> kontekst dla AI = '{}' (krytyczny bug v1.11.60).
     */
    private fun Double.roundTo(decimals: Int): Double {
        if (this.isNaN() || this.isInfinite()) return 0.0
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }

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
        targetPlanId: Long? = null,
        // v1.11.62: niezalezna flaga - wstrzyknac biblioteke cwiczen?
        // true gdy uzytkownik prosi o NOWY plan ('wygeneruj plan') - nie ma
        // targetPlanId ale potrzebuje znac dostepne cwiczenia. Domyslnie false.
        includeExerciseLibrary: Boolean = false
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
        // v1.11.66: period rollups - kompresja historii (last 4 weeks / 6 months / 4 quarters)
        val recentWeekRollups = weeklyRollupDao.getRecent(limit = 4)
        val recentMonthRollups = monthlyRollupDao.getRecent(limit = 6)
        val recentQuarterRollups = quarterlyRollupDao.getRecent(limit = 4)
        // v1.11.70: interpretation hints (statyczne + dynamiczne na podstawie stanu)
        val interpretationHints = computeInterpretationHints(
            weeklyTrend = volumePerWeek12,
            recentEvents = recentEvents,
            recentWorkouts = snapshot.finishedWorkouts.take(5)
        )
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
                // U9+U10: user JUŻ powiedział dlaczego nie trenuje — NIE namawiaj „idź ćwiczyć",
                // reaguj adekwatnie (kontuzja=ostrożnie; brak czasu=NEAT+białko+mini-trening).
                runCatching { deloadPrefs.trainingPause() }.getOrNull()?.let { pause ->
                    putJsonObject("training_pause") {
                        put("reason", pause.reasonEnum().label)
                        put("resume_in_days", pause.resumeInDays(System.currentTimeMillis()))
                        put("hint", "Przerwa z podanego powodu. NIE nagabuj o trening; chroń mięśnie dietą/białkiem.")
                    }
                }
            }

            putJsonObject("stats_overview") {
                put("totalWorkouts", overview.totalWorkouts)
                // v1.11.60: zaokraglenia float (zamiast 281542.5000000002)
                put("totalVolumeKg", overview.totalVolumeKg.roundTo(0))
                put("totalSets", overview.totalSets)
                put("totalDurationMin", overview.totalDurationMillis / 60_000)
                put("avgVolumePerWorkout", overview.avgVolumePerWorkout.roundTo(0))
                put("workoutsThisWeek", overview.workoutsThisWeek)
                put("workoutsThisMonth", overview.workoutsThisMonth)
                put("currentStreakWeeks", streak.current)
                put("bestStreakWeeks", streak.best)
                put("weekProgressCurrent", weekProgress.current)
                put("weekProgressTarget", weekProgress.target)
            }

            // v1.11.60: achievements -> compact summary (zamiast 25 obiektow z pelnym opisem)
            putJsonObject("achievements_summary") {
                val unlocked = achievements.filter { it.unlocked }
                put("unlockedCount", unlocked.size)
                put("totalCount", achievements.size)
                putJsonArray("latestIds") {
                    unlocked.takeLast(5).forEach { add(it.id) }
                }
            }

            putJsonArray("muscle_engagement_90d") {
                muscle.forEach { m ->
                    add(buildJsonObject {
                        put("muscle", m.muscle.name)
                        put("volumeKg", m.volumeKg.roundTo(0))
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
                        put("exercise", ev.exercise?.name ?: pl.filebit.gymtracker.data.strength.StrengthStandards.displayLabel(ev.standard.exerciseSlug))
                        put("estimated1RMKg", ev.current1RMKg.roundTo(1))
                        put("ratioPerBodyweight", ev.ratio.roundTo(2))
                        put("level", ev.level.name)
                        ev.nextLevelKg?.let { put("kgToNextLevel", it.roundTo(1)) }
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

            // v1.11.60: kompresja recent_workouts - tylko zakonczone treningi,
            // tylko ukonczone sety, bez WARMUP, pomijaj null/default pola
            putJsonArray("recent_workouts") {
                workoutsData
                    .filter { it.workout.finishedAt != null }
                    .forEach { wwe ->
                        add(buildJsonObject {
                            put("id", wwe.workout.id)
                            put("startedAt", dfTime.format(Date(wwe.workout.startedAt)))
                            wwe.workout.finishedAt?.let { put("finishedAt", dfTime.format(Date(it))) }
                            put(
                                "durationMin",
                                ((wwe.workout.finishedAt ?: wwe.workout.startedAt) - wwe.workout.startedAt) / 60_000
                            )
                            if (wwe.workout.notes.isNotBlank()) put("notes", wwe.workout.notes)
                            wwe.workout.wellbeingRating?.let { put("wellbeing", it) }
                            wwe.workout.painArea?.let { put("painArea", it) }
                            wwe.workout.painNotes?.let { put("painNotes", it) }
                            put("exercises", buildJsonArray {
                                wwe.byExercise.forEach { (ex, list) ->
                                    val completedSets = list
                                        .filter { it.isCompleted && it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP }
                                    if (completedSets.isEmpty()) return@forEach
                                    add(buildJsonObject {
                                        put("name", ex?.name ?: "?")
                                        put("sets", buildJsonArray {
                                            completedSets.sortedBy { it.setNumber }.forEach { s ->
                                                add(buildJsonObject {
                                                    put("setNumber", s.setNumber)
                                                    if (s.setType != pl.filebit.gymtracker.data.entity.SetType.NORMAL) {
                                                        put("setType", s.setType.name)
                                                    }
                                                    put("reps", s.reps)
                                                    if (s.weightKg > 0.0) {
                                                        put("weightKg", s.weightKg.roundTo(1))
                                                    }
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

            // v1.11.70: interpretation_hints - wskazowki dla AI jak interpretowac dane
            // (statyczne reguly + dynamiczne na podstawie aktualnego stanu).
            putJsonArray("interpretation_hints") {
                interpretationHints.forEach { add(it) }
            }

            // v1.11.66: historical_summary - prekomputowane rollupy (week/month/quarter).
            // Daje AI obraz dlugoterminowy bez ladowania surowych danych.
            // Total ~2-3 KB pokrywa 1+ rok historii.
            putJsonObject("historical_summary") {
                putJsonArray("weeks") {
                    recentWeekRollups.forEach { w ->
                        add(buildJsonObject {
                            put("weekStart", df.format(Date(w.weekStartMs)))
                            put("totalVolumeKg", w.totalVolumeKg.roundTo(0))
                            put("sessions", w.sessionsCount)
                            put("avgRpe", w.avgRpe.roundTo(1))
                            w.avgWellbeing?.let { put("avgWellbeing", it.roundTo(1)) }
                        })
                    }
                }
                putJsonArray("months") {
                    recentMonthRollups.forEach { m ->
                        add(buildJsonObject {
                            put("monthStart", df.format(Date(m.monthStartMs)))
                            put("totalVolumeKg", m.totalVolumeKg.roundTo(0))
                            put("sessions", m.sessionsCount)
                            put("avgRpe", m.avgRpe.roundTo(1))
                            put("prCount", m.prCount)
                            put("planChanges", m.planChanges)
                            put("deloadCount", m.deloadCount)
                            m.bodyWeightDeltaKg?.let { put("bodyWeightDeltaKg", it.roundTo(1)) }
                        })
                    }
                }
                putJsonArray("quarters") {
                    recentQuarterRollups.forEach { q ->
                        add(buildJsonObject {
                            put("quarterStart", df.format(Date(q.quarterStartMs)))
                            put("totalVolumeKg", q.totalVolumeKg.roundTo(0))
                            put("sessions", q.sessionsCount)
                            put("prCount", q.prCount)
                            put("planChanges", q.planChanges)
                            put("deloadCount", q.deloadCount)
                            q.bodyWeightDeltaKg?.let { put("bodyWeightDeltaKg", it.roundTo(1)) }
                        })
                    }
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
            // v1.11.62: rozdzielenie - includeExerciseLibrary niezalezne od targetPlanId,
            // zeby user proszacy o NOWY plan tez dostal biblioteke (wczesniej:
            // tylko targetPlanId set => brak biblioteki przy generowaniu nowego planu).
            // v1.25.2: smart filter + CSV format. Po imporcie 1500 ćw z ExerciseDB
            // pełna biblioteka = ~30k tokenów per call. Filter redukuje do ~5-10k:
            //   1. Pomiń isAvoided=true (user wprost zaznaczył "unikam")
            //   2. Jeśli UserProfile.availableEquipmentCsv niepusta — tylko z tym sprzętem
            //   3. Sort: favorites first, alfabetycznie reszta
            //   4. Limit 500 (AI nie potrzebuje 1500 do dobrego planu)
            //   5. Format CSV pipe-separated zamiast JSON array (50% mniej tokenów)
            if (targetPlanId != null || includeExerciseLibrary) {
                // v1.26.5: filtr po praktycznych kategoriach sprzętu (EquipmentCategory)
                // → surowe ExerciseDB equipment stringi (Exercise.equipmentDbCsv).
                val allowedDbEquipment = pl.filebit.gymtracker.data.entity.EquipmentCategory
                    .dbEquipmentsFor(
                        pl.filebit.gymtracker.data.entity.EquipmentCategory
                            .parse(profile.equipmentCategoriesCsv)
                    )
                // v1.25.3 koszt-aware default: jeśli user oznaczył ≥10 ulubionych,
                // domyślnie używaj TYLKO ich (drastyczna redukcja kosztów AI).
                // Inaczej (mało ulubionych albo brak) — pełna biblioteka z filtrem.
                val favoritesCount = allExercises.count { it.isFavorite && !it.isAvoided }
                val useFavoritesOnly = favoritesCount >= 10
                val baseSequence = allExercises.asSequence()
                    .filter { !it.isAvoided }
                    .filter { !useFavoritesOnly || it.isFavorite }
                val filtered = baseSequence
                    .filter { ex ->
                        if (allowedDbEquipment.isEmpty()) return@filter true
                        val dbEq = ex.equipmentDbCsv
                        if (dbEq.isNullOrBlank()) {
                            true  // user-defined bez ExerciseDB equipment — nie blokuj
                        } else {
                            dbEq.split(",").any { it.trim().lowercase() in allowedDbEquipment }
                        }
                    }
                    .sortedWith(compareByDescending<pl.filebit.gymtracker.data.entity.Exercise> { it.isFavorite }
                        .thenBy { it.name.lowercase() })
                    .take(500)
                    .toList()
                put(
                    "exercise_library_mode",
                    if (useFavoritesOnly)
                        "favorites_only ($favoritesCount ulubionych — user oznaczył wystarczająco)"
                    else
                        "full_filtered (mało ulubionych — używamy pełnej biblioteki z filtrem)"
                )
                put("available_exercises_format", "csv: name|muscle|equipment (pipe-separated)")
                val csv = buildString {
                    filtered.forEach { ex ->
                        append(ex.name).append('|')
                            .append(ex.primaryMuscle.name).append('|')
                            .append(ex.equipment.name).append('\n')
                    }
                }.trimEnd('\n')
                put("available_exercises_csv", csv)
                put("available_exercises_count", filtered.size)
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
 * v1.11.70: Generuje liste wskazowek interpretacyjnych dla AI.
 *
 * Powod: AI dostaje wiele zrodel danych (recent_workouts, weekly_trend, event_log,
 * historical_summary). Latwo o sprzeczne interpretacje (np. niski volume biezacego
 * tygodnia myli AI - sugeruje "dodaj tonaz" mimo ze tydzien dopiero sie zaczal lub
 * jest deload). Te hints prostuja interpretacje.
 *
 * Mix:
 * - Static hints (zawsze) - reguly interpretacyjne dla pol kontekstu
 * - Dynamic hints (warunkowe) - gdy konkretny stan wymaga uwagi
 */
fun computeInterpretationHints(
    weeklyTrend: List<Double>,             // weekly_volume_trend_12w (last = bieżący)
    recentEvents: List<pl.filebit.gymtracker.data.entity.TrainingEvent>,
    recentWorkouts: List<pl.filebit.gymtracker.data.entity.Workout>,
    nowMs: Long = System.currentTimeMillis()
): List<String> {
    val hints = mutableListOf<String>()

    // === STATIC HINTS (zawsze) ===
    hints.add("weekly_volume_trend_12w[weeksAgo=0] to BIEŻĄCY niezakończony tydzień — nie oceniaj jako deload bo dopiero się zaczął")
    hints.add("Eventy DELOAD_DETECTED w event_log oznaczają tygodnie celowo z niskim tonażem")
    hints.add("MuscleRecovery (regeneracja PARTII) i ACWR (TOTAL volume) to różne wymiary — mogą być oba 'wysokie' jednocześnie (partie świeże ALE total tonnage podwyższony). Wtedy: NIE dodawaj sesji, obniż objętość per sesja")
    hints.add("Plan_history_summary zawiera wszystkie plany (aktywne + zakończone). 'Aktywny' poznasz po nazwie lub fakcie że recent_workouts używają jego ćwiczeń")

    // === DYNAMIC HINTS (warunkowe) ===

    // 1. Bieżący tydzień znacznie niższy niż poprzedni
    if (weeklyTrend.size >= 2) {
        val current = weeklyTrend.last()
        val previous = weeklyTrend[weeklyTrend.size - 2]
        if (previous > 0 && current < previous * 0.5) {
            val currentInt = current.toInt()
            val previousInt = previous.toInt()
            hints.add("Bieżący tydzień ma volume <50% poprzedniego ($currentInt vs $previousInt kg) — może być deload, brak czasu, lub dopiero początek tygodnia. Nie zakładaj automatycznie deloadu.")
        }
    }

    // 2. Niedawny DELOAD_DETECTED event
    val msPerDay = 24L * 3600 * 1000
    val mostRecentDeload = recentEvents.firstOrNull {
        it.type == pl.filebit.gymtracker.data.entity.TrainingEventType.DELOAD_DETECTED
    }
    if (mostRecentDeload != null) {
        val daysAgo = (nowMs - mostRecentDeload.date) / msPerDay
        if (daysAgo in 0..14) {
            hints.add("Wykryto DELOAD_DETECTED $daysAgo dni temu — jeśli user pyta o akumulację/intensyfikację, zweryfikuj czy deload się zakończył (>=7 dni od jego daty).")
        }
    }

    // 3. Niski wellbeing w ostatnich sesjach
    val lastThreeWorkouts = recentWorkouts.take(3)
    val lowWellbeingCount = lastThreeWorkouts.count {
        it.wellbeingRating != null && it.wellbeingRating!! <= 2
    }
    if (lowWellbeingCount >= 2) {
        hints.add("Wellbeing ≤2 w $lowWellbeingCount z ostatnich 3 sesji — sygnał przemęczenia. Bez względu na inne metryki, sugeruj rest lub lżejszy tydzień.")
    }

    // 4. Pain area w ostatnich sesjach
    val recentPainAreas = recentWorkouts.take(5)
        .mapNotNull { it.painArea }
        .filter { it.isNotBlank() }
        .distinct()
    if (recentPainAreas.isNotEmpty()) {
        hints.add("Niedawne painArea (z recent_workouts): ${recentPainAreas.joinToString(", ")} — sugeruj alternatywy lub unikaj ćwiczeń obciążających te partie.")
    }

    // 5. Niedawna kontuzja (INJURY event)
    val recentInjuries = recentEvents.filter {
        it.type == pl.filebit.gymtracker.data.entity.TrainingEventType.INJURY &&
            (nowMs - it.date) / msPerDay <= 30
    }
    if (recentInjuries.isNotEmpty()) {
        val areas = recentInjuries.mapNotNull { it.area }.distinct().take(3)
        hints.add("Kontuzje w ostatnich 30 dniach: ${areas.joinToString(", ")} — utrzymuj alternatywy bezpieczne dla tych obszarów.")
    }

    // 6. Ostatni gap_resumed (powrót po przerwie)
    val recentGap = recentEvents.firstOrNull {
        it.type == pl.filebit.gymtracker.data.entity.TrainingEventType.GAP_RESUMED
    }
    if (recentGap != null) {
        val daysAgo = (nowMs - recentGap.date) / msPerDay
        if (daysAgo in 0..7) {
            val weeks = recentGap.weeksContext ?: 0
            hints.add("User wrócił z przerwy ${weeks} tyg ($daysAgo dni temu) — pierwsze 1-2 tyg po powrocie obniż obciążenia o 15-20% vs przed-przerwowe.")
        }
    }

    return hints
}

/**
 * v1.11.60: Heurystyka czy uzytkownik pyta o modyfikacje/generowanie planu.
 * Jezeli TAK -> AI dostaje pelny plan + biblioteke cwiczen w kontekscie.
 * Jezeli NIE -> tylko plan_history_summary (bez detali, bez biblioteki).
 *
 * Bez tego: kazde pytanie wstrzykuje 200+ KB pelnego planu i listy cwiczen,
 * mimo ze pytanie nie dotyczy planu (np. analiza progresu).
 */
fun isPlanRelatedPrompt(prompt: String): Boolean {
    val lower = prompt.lowercase()
    val keywords = listOf(
        // Plan ogolnie
        "plan", "trening na ", "rozpiska", "rozkład", "split",
        // Modyfikacje
        "modyfik", "edytuj",
        // Akcje na cwiczeniach (standalone — lapie 'zamien przysiad', 'usun wykrok' etc)
        "dodaj cwiczenie", "dodaj ćwiczenie",
        "zamien", "zamień", "wymien", "wymień", "usun ", "usuń ",
        "podziel trening", "podzielic trening", "podzielić trening",
        // Generowanie
        "zapropon", "wygeneruj", "stwórz", "stworz", "uloz", "ułóż"
    )
    return keywords.any { it in lower }
}

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
