package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.WorkoutSet
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class OverviewStats(
    val totalWorkouts: Int,
    val totalDurationMillis: Long,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val avgVolumePerWorkout: Double,
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int
)

data class ExercisePr(
    val maxWeightKg: Double,
    val repsAtMaxWeight: Int,
    val maxVolumeKg: Double,
    val estimated1RM: Double,
    val totalSetsLogged: Int,
    // v1.29.19 — cardio: gdy isCardio=true, pola kg/1RM nie mają sensu;
    // UI pokazuje prędkość/czas/dystans zamiast ciężaru.
    val isCardio: Boolean = false,
    val bestSpeedKmh: Double = 0.0,
    val maxDurationSec: Int = 0,
    val totalDurationSec: Int = 0,
    val totalDistanceM: Double = 0.0
)

@Singleton
class StatsRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: pl.filebit.gymtracker.data.db.dao.ExerciseDao,
    private val bodyDao: pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao,
    private val goalDao: pl.filebit.gymtracker.data.db.dao.GoalDao,
    private val unlockedDao: pl.filebit.gymtracker.data.db.dao.UnlockedAchievementDao,
    private val userProfileDao: pl.filebit.gymtracker.data.db.dao.UserProfileDao,
    // v1.18.0 — achievements PERIODIZATION (completed cycles, deloads, distinct phases)
    private val mesoDao: pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
) {

    /**
     * Epley 1RM formula: weight × (1 + reps/30). Działa dobrze do ~10 powt.
     */
    fun epley1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0) return 0.0
        return weightKg * (1 + reps / 30.0)
    }

    fun brzycki1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || reps >= 37 || weightKg <= 0) return 0.0
        return weightKg * 36.0 / (37.0 - reps)
    }

    fun lombardi1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0) return 0.0
        return weightKg * Math.pow(reps.toDouble(), 0.10)
    }

    /**
     * Streak tygodniowy: liczba kolejnych tygodni (ISO) z minimum 1 zakończonym treningiem,
     * licząc wstecz od bieżącego tygodnia.
     */
    suspend fun streakInfo(): StreakInfo {
        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .map { it.startedAt }
        if (finished.isEmpty()) return StreakInfo(0, 0)
        val weeks = finished.map { weekKey(it) }.toSet()

        // Aktualny streak: licząc wstecz od dziś
        var current = 0
        var cursor = System.currentTimeMillis()
        while (true) {
            val key = weekKey(cursor)
            if (key in weeks) {
                current++
                cursor -= 7L * 24L * 60L * 60L * 1000L
            } else if (current == 0) {
                // jeśli w bieżącym tygodniu nic — sprawdź poprzedni (streak nie pęka jeszcze)
                cursor -= 7L * 24L * 60L * 60L * 1000L
                val prevKey = weekKey(cursor)
                if (prevKey in weeks) {
                    current++
                } else break
            } else break
        }

        // Best streak: przeszukaj wszystkie tygodnie po kolei
        val sortedWeeks = weeks.sorted()
        var best = 0
        var run = 0
        var prevKey: String? = null
        for (k in sortedWeeks) {
            if (prevKey == null) {
                run = 1
            } else if (areConsecutiveWeeks(prevKey, k)) {
                run++
            } else {
                run = 1
            }
            if (run > best) best = run
            prevKey = k
        }
        return StreakInfo(current = current, best = best.coerceAtLeast(current))
    }

    private fun weekKey(epochMillis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMillis
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4 // ISO week
        val year = cal.get(java.util.Calendar.YEAR)
        val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(year, week)
    }

    private fun areConsecutiveWeeks(a: String, b: String): Boolean {
        // Czy b = a+1 tydzień
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        val (ya, wa) = a.split("-W").let { it[0].toInt() to it[1].toInt() }
        cal.clear()
        cal.set(java.util.Calendar.YEAR, ya)
        cal.set(java.util.Calendar.WEEK_OF_YEAR, wa)
        cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
        val nextYear = cal.get(java.util.Calendar.YEAR)
        val nextWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(nextYear, nextWeek) == b
    }

    /**
     * Cel tygodniowy: liczba ukończonych treningów w bieżącym tygodniu vs target.
     */
    suspend fun weekProgress(target: Int): WeekProgress {
        val now = System.currentTimeMillis()
        val key = weekKey(now)
        val count = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && weekKey(it.startedAt) == key }
            .size
        return WeekProgress(current = count, target = target)
    }

    /**
     * Pełen system odznak z kategoriami, poziomami i persystencją momentu odblokowania.
     * Sprawdza po każdym wezwaniu — jeśli odznaka właśnie została osiągnięta i nie ma
     * jej w DB, zapisuje wpis (z timestampem teraz).
     */
    suspend fun unlockedAchievements(weeklyTarget: Int): List<Achievement> {
        // === BULK FETCH — eliminacja N+1 query (300+ → ~10 zapytań DB) ===
        val o = overview()
        val streak = streakInfo()
        val allWorkouts = workoutDao.observeAllOnce()
        val finishedWorkoutIds = allWorkouts.filter { it.finishedAt != null }.map { it.id }.toSet()
        val allExercises = exerciseDao.getAll()
        val exerciseById = allExercises.associateBy { it.id }
        // Jeden SQL zamiast 30× getForWorkout w pętli
        val allSetsRaw = setDao.getAll()
        val allSets = allSetsRaw.filter {
            it.workoutId in finishedWorkoutIds && it.isCompleted && it.setType != SetType.WARMUP
        }
        val distinctExercises = allSets.map { it.exerciseId }.distinct().size
        // Map w pamięci zamiast 500× exerciseDao.getById
        val musclesTrained = allSets
            .mapNotNull { exerciseById[it.exerciseId]?.primaryMuscle }
            .distinct().size
        val customExercises = allExercises.count { it.isCustom }

        // PRs — ile rekordów ciężaru ustanowiono (count distinct exerciseIds gdzie jest set z 1RM > 0)
        val prCount = run {
            var count = 0
            val byExercise = allSets.groupBy { it.exerciseId }
            for ((_, sets) in byExercise) {
                val maxW = sets.maxOf { it.weightKg }
                if (maxW > 0) count++
            }
            count
        }

        // Pomiary — pierwszy/ostatni
        val bodyAll = bodyDao.getAllAsc()
        val firstBody = bodyAll.firstOrNull()
        val lastBody = bodyAll.lastOrNull()
        val bodyCount = bodyAll.size

        // Cele — zrealizowane
        val achievedGoals = goalDao.getAll().count { it.achieved }

        // Profil — bodyweight i goal type
        val profile = userProfileDao.get()
        val targetWeightKg = profile?.targetWeightKg
        val currentBodyweight = profile?.bodyweightKg ?: lastBody?.weightKg
        val startBodyweight = firstBody?.weightKg ?: currentBodyweight
        val weightGoal = profile?.weightGoalType?.name ?: "NONE"

        val bodyweightDelta = if (currentBodyweight != null && startBodyweight != null) {
            currentBodyweight - startBodyweight
        } else 0.0

        // Najlepsze obwody (delta od pierwszego pomiaru)
        fun delta(getter: (pl.filebit.gymtracker.data.entity.BodyMeasurement) -> Double?): Double {
            val first = firstBody?.let(getter) ?: return 0.0
            val last = lastBody?.let(getter) ?: return 0.0
            return last - first
        }
        val chestDelta = delta { it.chestCm }
        val armDelta = delta { it.armCm }
        val thighDelta = delta { it.thighCm }
        val waistDrop = -delta { it.waistCm }   // dodatnie gdy spadł
        val bodyFatDrop = -delta { it.bodyFatPercent }

        // Strength — z BULK allSets + allExercises (zamiast 4× DAO queries)
        fun bestWeightFor(prefix: String): Double {
            val ex = allExercises.firstOrNull { it.name.startsWith(prefix, ignoreCase = true) }
                ?: return 0.0
            return allSets.filter { it.exerciseId == ex.id }
                .maxOfOrNull { it.weightKg } ?: 0.0
        }
        val benchMax = bestWeightFor("Wyciskanie sztangi leżąc")
        val squatMax = bestWeightFor("Przysiad ze sztangą")
        val deadliftMax = bestWeightFor("Martwy ciąg klasyczny")
        val ohpMax = bestWeightFor("Wyciskanie żołnierskie")
        val bw = currentBodyweight ?: 0.0

        // v1.18.0 — PERIODIZATION metrics z TrainingMesocycle
        val allMesocycles = runCatching { mesoDao.getRecent(limit = 200) }.getOrNull().orEmpty()
        val completed = allMesocycles.filter {
            it.status == pl.filebit.gymtracker.data.entity.MesocycleStatus.COMPLETED
        }
        val completedCycles = completed.size.toLong()
        val deloadsExecuted = completed.count {
            it.phase == pl.filebit.gymtracker.data.entity.MesocyclePhase.DELOAD
        }.toLong()
        val phasesCompleted = completed.map { it.phase }.distinct().size.toLong()

        val all = AchievementDefinitions.all(
            workoutsCount = o.totalWorkouts.toLong(),
            totalVolume = o.totalVolumeKg.toLong(),
            streakBestWeeks = streak.best.toLong(),
            distinctExercises = distinctExercises.toLong(),
            musclesTrained = musclesTrained.toLong(),
            customExercises = customExercises.toLong(),
            prCount = prCount.toLong(),
            bodyMeasurementsCount = bodyCount.toLong(),
            achievedGoalsCount = achievedGoals.toLong(),
            bodyweightDelta = bodyweightDelta,
            weightGoalType = weightGoal,
            chestDelta = chestDelta,
            armDelta = armDelta,
            thighDelta = thighDelta,
            waistDrop = waistDrop,
            bodyFatDrop = bodyFatDrop,
            benchMaxKg = benchMax,
            squatMaxKg = squatMax,
            deadliftMaxKg = deadliftMax,
            ohpMaxKg = ohpMax,
            bodyweightKg = bw,
            completedCycles = completedCycles,
            deloadsExecuted = deloadsExecuted,
            phasesCompleted = phasesCompleted
        )

        // Persyst odblokowania
        val now = System.currentTimeMillis()
        val unlockedDb = unlockedDao.getAll().associateBy { it.code }
        val results = all.map { def ->
            val current = def.currentValue
            val target = def.targetValue
            val unlocked = current >= target
            val unlockedAt = unlockedDb[def.code]?.unlockedAt
                ?: if (unlocked) {
                    runCatching {
                        unlockedDao.insertIfNew(
                            pl.filebit.gymtracker.data.entity.UnlockedAchievement(
                                code = def.code, unlockedAt = now, valueAt = current.toDouble()
                            )
                        )
                    }
                    now
                } else null
            Achievement(
                id = def.code, emoji = def.emoji,
                title = def.title, description = def.description,
                category = def.category, level = def.level,
                unlocked = unlocked,
                progress = if (target > 0) ((current * 100) / target).toInt().coerceAtMost(100) else 0,
                currentValue = current, targetValue = target,
                unlockedAt = unlockedAt
            )
        }
        return results
    }

    /**
     * Suma volume (kg × reps, bez warm-upów) per ISO tydzień, ostatnie [weeks] tygodni
     * w kolejności chronologicznej (najstarszy → najnowszy). Index ostatniego tygodnia
     * = bieżący tydzień (może być częściowy).
     */
    suspend fun volumePerWeek(weeks: Int): List<Double> {
        val all = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
        if (all.isEmpty()) return List(weeks) { 0.0 }

        // Mapa weekKey → volume
        val byWeek = mutableMapOf<String, Double>()
        for (w in all) {
            val sets = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
            val vol = sets.sumOf { it.reps * it.weightKg }
            val key = weekKey(w.startedAt)
            byWeek[key] = (byWeek[key] ?: 0.0) + vol
        }

        // Ostatnie N tygodni licząc wstecz od dzisiaj — kolejność old → new
        val nowMs = System.currentTimeMillis()
        val result = ArrayList<Double>(weeks)
        for (i in (weeks - 1) downTo 0) {
            val key = weekKey(nowMs - i * 7L * 24L * 60L * 60L * 1000L)
            result.add(byWeek[key] ?: 0.0)
        }
        return result
    }

    /** Najlepszy ciężar dla przysiadu (do hero karty PR PRZYSIADU). */
    suspend fun bestSquatWeight(): Double = bestWeightForExerciseLike("przysiad")

    /**
     * Najlepszy ciężar dla pierwszego ćwiczenia którego nazwa zawiera podany prefix.
     */
    private suspend fun bestWeightForExerciseLike(namePrefix: String): Double {
        val ex = exerciseDao.getAll()
            .firstOrNull { it.name.startsWith(namePrefix, ignoreCase = true) } ?: return 0.0
        val sets = setDao.getAllForExercise(ex.id)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        return sets.maxOfOrNull { it.weightKg } ?: 0.0
    }

    /**
     * Pełna analiza mięśniowa: dla każdej głównej grupy mięśniowej zwraca aktualny
     * udział, zalecany udział, status (zaniedbany/balans/przetrenowany) oraz dni
     * od ostatniego treningu. Daje sensowne dane także po długim okresie — pokazuje
     * GDZIE są braki, nie tylko że "wszystko trenowane".
     */
    suspend fun muscleAnalysis(periodDays: Int): MuscleAnalysisReport {
        val now = System.currentTimeMillis()
        val cutoff = if (periodDays > 0) now - periodDays.toLong() * 86_400_000L else 0L
        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }

        // Per mięsień: (volumeSum, setsCount, lastTrainedAtMs)
        val perMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Triple<Double, Int, Long>>()
        for (w in finished) {
            val sets = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
            for (s in sets) {
                val ex = exerciseDao.getById(s.exerciseId) ?: continue
                val muscle = ex.primaryMuscle
                val vol = s.reps * s.weightKg
                val (v, c, lastTs) = perMuscle.getOrDefault(muscle, Triple(0.0, 0, 0L))
                perMuscle[muscle] = Triple(v + vol, c + 1, maxOf(lastTs, w.startedAt))
            }
        }

        val total = perMuscle.values.sumOf { it.first }.coerceAtLeast(0.0001)
        val analyses = MAIN_MUSCLES.map { muscle ->
            val (vol, sets, lastTs) = perMuscle[muscle] ?: Triple(0.0, 0, 0L)
            val actualPct = ((vol * 100.0) / total).toInt().coerceIn(0, 100)
            val recommendedPct = RECOMMENDED_DISTRIBUTION[muscle] ?: 0
            val daysSinceLast = if (lastTs > 0) ((now - lastTs) / 86_400_000L).toInt() else null
            val status = computeStatus(
                actualPct = actualPct,
                recommendedPct = recommendedPct,
                daysSinceLast = daysSinceLast,
                periodDays = periodDays
            )
            MuscleAnalysis(
                muscle = muscle,
                volumeKg = vol,
                totalSets = sets,
                actualPercent = actualPct,
                recommendedPercent = recommendedPct,
                daysSinceLast = daysSinceLast,
                status = status
            )
        }
        return MuscleAnalysisReport(
            periodDays = periodDays,
            totalVolumeKg = total,
            analyses = analyses.sortedWith(compareBy({ it.status.priority }, { -it.actualPercent }))
        )
    }

    /**
     * MuscleAnalysisFast — używa StatsSnapshot zamiast N+1 queries DAO.
     * Logika identyczna z muscleAnalysis() — test w MuscleAnalysisFastTest.kt.
     */
    fun muscleAnalysisFast(periodDays: Int, snapshot: StatsSnapshot): MuscleAnalysisReport =
        computeMuscleAnalysisFromSnapshot(periodDays, snapshot, System.currentTimeMillis())

    private fun computeStatus(
        actualPct: Int,
        recommendedPct: Int,
        daysSinceLast: Int?,
        periodDays: Int
    ): MuscleStatus {
        // Brak treningu w ogóle lub > 21 dni temu — zaniedbany
        if (daysSinceLast == null) return MuscleStatus.NEGLECTED
        if (daysSinceLast > 21) return MuscleStatus.NEGLECTED
        // Powyżej 1.8× zalecanego — przetrenowany
        if (recommendedPct > 0 && actualPct > recommendedPct * 1.8) return MuscleStatus.OVER
        // Poniżej 0.5× zalecanego — undertrained
        if (recommendedPct > 0 && actualPct < recommendedPct * 0.5) return MuscleStatus.UNDER
        // W przeciwnym razie — w granicach normy
        return MuscleStatus.BALANCED
    }

    /**
     * Zaangażowanie mięśni w danym okresie (dni wstecz). Working sets, bez warm-upów.
     * Każde ćwiczenie liczone tylko do swojej `primaryMuscle` (sekundarne na razie pomijane).
     */
    suspend fun muscleEngagement(periodDays: Int): List<MuscleEngagement> {
        val now = System.currentTimeMillis()
        val cutoff = if (periodDays > 0) now - periodDays.toLong() * 24L * 60L * 60L * 1000L else 0L
        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }
        if (finished.isEmpty()) return emptyList()

        val perMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Pair<Double, Int>>()
        // (volumeSum, setsCount)

        for (w in finished) {
            val sets = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
            for (s in sets) {
                val ex = exerciseDao.getById(s.exerciseId) ?: continue
                val vol = s.reps * s.weightKg
                val (v, c) = perMuscle.getOrDefault(ex.primaryMuscle, 0.0 to 0)
                perMuscle[ex.primaryMuscle] = (v + vol) to (c + 1)
            }
        }

        val total = perMuscle.values.sumOf { it.first }
        if (total <= 0) return emptyList()

        return perMuscle.entries
            .map { (muscle, vc) ->
                MuscleEngagement(
                    muscle = muscle,
                    volumeKg = vc.first,
                    totalSets = vc.second,
                    percentOfTotal = ((vc.first * 100) / total).toInt().coerceIn(0, 100)
                )
            }
            .sortedByDescending { it.volumeKg }
    }

    /**
     * MuscleEngagementFast — używa StatsSnapshot zamiast N+1 queries.
     * Logika identyczna z muscleEngagement().
     */
    fun muscleEngagementFast(periodDays: Int, snapshot: StatsSnapshot): List<MuscleEngagement> =
        computeMuscleEngagementFromSnapshot(periodDays, snapshot, System.currentTimeMillis())

    /** v1.11.42 — overview() z snapshot (zero N+1). */
    fun overviewFast(snapshot: StatsSnapshot): OverviewStats =
        computeOverviewFromSnapshot(snapshot, System.currentTimeMillis())

    /** v1.11.42 — volumePerWeek z snapshot. */
    fun volumePerWeekFast(weeks: Int, snapshot: StatsSnapshot): List<Double> =
        computeVolumePerWeekFromSnapshot(weeks, snapshot, System.currentTimeMillis())

    /** v1.11.42 — calendarHeatmap z snapshot. */
    fun calendarHeatmapFast(days: Int, snapshot: StatsSnapshot): Map<Long, Double> =
        computeCalendarHeatmapFromSnapshot(days, snapshot, System.currentTimeMillis())

    /** v1.11.43 — recoveryByMuscle z snapshot (N+1 → 0 queries). */
    fun recoveryByMuscleFast(snapshot: StatsSnapshot): List<MuscleRecovery> =
        computeRecoveryByMuscleFromSnapshot(snapshot, System.currentTimeMillis())

    /** v1.11.50 — detectNewPRs z snapshot (do użycia w HistoryViewModel). */
    fun detectNewPRsFast(currentWorkoutId: Long, snapshot: StatsSnapshot): List<NewPr> =
        computeNewPRsFromSnapshot(currentWorkoutId, snapshot) { w, r -> epley1RM(w, r) }

    /** v1.11.44 — allPersonalRecords z snapshot. */
    fun allPersonalRecordsFast(snapshot: StatsSnapshot): List<PersonalRecordRow> =
        computeAllPersonalRecordsFromSnapshot(snapshot) { weight, reps -> epley1RM(weight, reps) }

    /** v1.11.44 — allStagnations z snapshot. */
    fun allStagnationsFast(snapshot: StatsSnapshot, threshold: Int = 3): List<StagnationAlert> =
        computeAllStagnationsFromSnapshot(snapshot, threshold)

    suspend fun overview(): OverviewStats {
        val all = workoutDao.observeAllOnce()
        val finished = all.filter { it.finishedAt != null }
        val totalDuration = finished.sumOf { it.durationMillis }
        var totalVolume = 0.0
        var totalSets = 0
        for (w in finished) {
            val sets = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
            totalVolume += sets.sumOf { it.reps * it.weightKg }
            totalSets += sets.size
        }
        val avg = if (finished.isNotEmpty()) totalVolume / finished.size else 0.0
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
        val monthAgo = now - 30L * 24L * 60L * 60L * 1000L
        val thisWeek = finished.count { it.startedAt >= weekAgo }
        val thisMonth = finished.count { it.startedAt >= monthAgo }
        return OverviewStats(
            totalWorkouts = finished.size,
            totalDurationMillis = totalDuration,
            totalVolumeKg = totalVolume,
            totalSets = totalSets,
            avgVolumePerWorkout = avg,
            workoutsThisWeek = thisWeek,
            workoutsThisMonth = thisMonth
        )
    }

    suspend fun prForExercise(exerciseId: Long): ExercisePr? {
        val sets = setDao.getAllForExercise(exerciseId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (sets.isEmpty()) return null
        val ex = exerciseDao.getById(exerciseId)
        val isCardio = ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
            ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
        if (isCardio) {
            val bestSpeed = sets.mapNotNull {
                pl.filebit.gymtracker.util.cardioSpeedKmh(it.durationSec, it.distanceM)
            }.maxOrNull() ?: 0.0
            return ExercisePr(
                maxWeightKg = 0.0,
                repsAtMaxWeight = 0,
                maxVolumeKg = 0.0,
                estimated1RM = 0.0,
                totalSetsLogged = sets.size,
                isCardio = true,
                bestSpeedKmh = bestSpeed,
                maxDurationSec = sets.maxOf { it.durationSec ?: 0 },
                totalDurationSec = sets.sumOf { it.durationSec ?: 0 },
                totalDistanceM = sets.sumOf { it.distanceM ?: 0.0 }
            )
        }
        val maxWeightSet = sets.maxByOrNull { it.weightKg } ?: return null
        // pogrupuj per workout, wybierz max objętości
        val perWorkoutVol = sets.groupBy { it.workoutId }
            .mapValues { (_, list) -> list.sumOf { it.reps * it.weightKg } }
        val maxVolume = perWorkoutVol.values.maxOrNull() ?: 0.0
        val best1RM = sets.maxOf { epley1RM(it.weightKg, it.reps) }
        return ExercisePr(
            maxWeightKg = maxWeightSet.weightKg,
            repsAtMaxWeight = maxWeightSet.reps,
            maxVolumeKg = maxVolume,
            estimated1RM = (best1RM * 10).roundToInt() / 10.0,
            totalSetsLogged = sets.size
        )
    }

    /**
     * Punkty wykresu progresji per ćwiczenie — po jednym na trening.
     */
    suspend fun progressionForExercise(exerciseId: Long): List<ExerciseProgressionPoint> {
        val all = setDao.getAllForExercise(exerciseId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (all.isEmpty()) return emptyList()
        val ex = exerciseDao.getById(exerciseId)
        val isCardio = ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
            ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
        val workoutsById = workoutDao.observeAllOnce().associateBy { it.id }
        return all.groupBy { it.workoutId }
            .mapNotNull { (workoutId, sets) ->
                val w = workoutsById[workoutId] ?: return@mapNotNull null
                if (w.finishedAt == null) return@mapNotNull null
                if (isCardio) {
                    val totalDur = sets.sumOf { it.durationSec ?: 0 }
                    val totalDist = sets.sumOf { it.distanceM ?: 0.0 }
                    return@mapNotNull ExerciseProgressionPoint(
                        workoutId = workoutId,
                        workoutDate = w.startedAt,
                        maxWeightKg = 0.0,
                        repsAtMax = 0,
                        volumeKg = 0.0,
                        estimated1RM = 0.0,
                        avgSpeedKmh = pl.filebit.gymtracker.util.cardioSpeedKmh(totalDur, totalDist) ?: 0.0,
                        totalDurationSec = totalDur,
                        totalDistanceM = totalDist
                    )
                }
                val maxWeight = sets.maxOf { it.weightKg }
                val repsAtMax = sets.filter { it.weightKg == maxWeight }.maxOf { it.reps }
                val volume = sets.sumOf { it.reps * it.weightKg }
                val best1Rm = sets.maxOf { epley1RM(it.weightKg, it.reps) }
                ExerciseProgressionPoint(
                    workoutId = workoutId,
                    workoutDate = w.startedAt,
                    maxWeightKg = maxWeight,
                    repsAtMax = repsAtMax,
                    volumeKg = volume,
                    estimated1RM = (best1Rm * 10).roundToInt() / 10.0
                )
            }
            .sortedBy { it.workoutDate }
    }

    /**
     * Pełna historia setów ćwiczenia (po zakończonych treningach), posortowane od najnowszych.
     */
    suspend fun historyForExercise(exerciseId: Long): List<WorkoutSet> {
        val finishedIds = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .map { it.id }
            .toSet()
        return setDao.getAllForExercise(exerciseId)
            .filter { it.workoutId in finishedIds }
            .sortedByDescending { it.createdAt }
    }

    /**
     * RPE-aware progression. Patrzy na faktyczny wysiłek (RPE) + czy reps planowane się powiodły:
     *
     * - RPE średnie ≤ 7 + wszystkie reps zrobione → +2.5 kg (INCREASE_WEIGHT)
     * - RPE średnie 7.5-8.5 + wszystkie reps zrobione → +1 powt. (INCREASE_REPS)
     * - RPE średnie ≥ 9 → bez zmian (NO_CHANGE)
     * - Reps niedokończone w 2 sesjach pod rząd → -2.5 kg (DELOAD)
     * - Brak RPE → fallback do "all reps done → +2.5 kg" (legacy)
     */
    suspend fun progressionTipsForWorkout(currentWorkoutId: Long): List<ProgressionTip> {
        val curSets = setDao.getForWorkout(currentWorkoutId)
            .filter { it.setType != SetType.WARMUP }
        if (curSets.isEmpty()) return emptyList()
        val byExercise = curSets.groupBy { it.exerciseId }
        val tips = mutableListOf<ProgressionTip>()

        for ((exId, list) in byExercise) {
            val ex = exerciseDao.getById(exId)
            val name = ex?.name ?: "?"
            // Progresja oparta na ciężarze/powtórzeniach ma sens tylko dla ćwiczeń
            // siłowych. Cardio (czas/dystans) pomijamy — inaczej silnik proponowałby
            // bezsensowne "+2.5 kg" dla bieżni czy roweru.
            if (ex?.metricType != pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS) continue
            val workingSets = list.filter { it.isCompleted }
            if (workingSets.isEmpty()) continue

            val curMaxWeight = workingSets.maxOf { it.weightKg }
            val setsAtMax = workingSets.filter { it.weightKg == curMaxWeight }
            val curMinRepsAtMax = setsAtMax.minOf { it.reps }

            // poprzedni ten sam set (z planu) — dla porównania faktycznych vs zaplanowanych reps
            val plannedReps = setsAtMax.maxOf { it.reps }  // dziś użyte = planowane
            val avgRpe = setsAtMax.mapNotNull { it.rpe?.takeIf { r -> r > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()

            // historia ostatnich 2 sesji dla tego ćwiczenia (bez bieżącej)
            val previousAll = setDao.getAllForExercise(exId)
                .filter { it.workoutId != currentWorkoutId && it.isCompleted && it.setType != SetType.WARMUP }
            val previousByWorkout = previousAll.groupBy { it.workoutId }
            val sortedWorkoutIds = previousByWorkout.keys.sortedByDescending { wid ->
                previousByWorkout[wid]!!.maxOf { it.createdAt }
            }

            // === DELOAD branch: niedokończone reps w 2 sesjach pod rząd ===
            val curAllDone = list.all { it.isCompleted }
            if (!curAllDone && sortedWorkoutIds.isNotEmpty()) {
                val prevSets = previousByWorkout[sortedWorkoutIds[0]]!!
                val prevAllDone = prevSets.all { it.isCompleted }
                if (!prevAllDone) {
                    val suggested = (curMaxWeight - 2.5).coerceAtLeast(0.0)
                    tips += ProgressionTip(
                        exerciseId = exId,
                        exerciseName = name,
                        currentWeightKg = curMaxWeight,
                        suggestedWeightKg = suggested,
                        reason = "2 sesje pod rząd niedokończone — deload",
                        kind = ProgressionKind.DELOAD,
                        currentReps = plannedReps,
                        suggestedReps = plannedReps
                    )
                    continue
                }
            }
            if (!curAllDone) continue  // niedokończone w 1 sesji — bez sugestii

            // === RPE-aware branche (gdy mamy RPE) ===
            if (avgRpe != null) {
                when {
                    avgRpe <= 7.0 -> {
                        tips += ProgressionTip(
                            exerciseId = exId,
                            exerciseName = name,
                            currentWeightKg = curMaxWeight,
                            suggestedWeightKg = curMaxWeight + 2.5,
                            reason = "RPE ${"%.1f".format(avgRpe)} (lekko) — czas na +2.5 kg",
                            kind = ProgressionKind.INCREASE_WEIGHT,
                            currentReps = plannedReps,
                            suggestedReps = plannedReps
                        )
                    }
                    avgRpe <= 8.5 -> {
                        tips += ProgressionTip(
                            exerciseId = exId,
                            exerciseName = name,
                            currentWeightKg = curMaxWeight,
                            suggestedWeightKg = curMaxWeight,
                            reason = "RPE ${"%.1f".format(avgRpe)} (dobrze) — dorzuć 1 powt.",
                            kind = ProgressionKind.INCREASE_REPS,
                            currentReps = plannedReps,
                            suggestedReps = plannedReps + 1
                        )
                    }
                    else -> {
                        tips += ProgressionTip(
                            exerciseId = exId,
                            exerciseName = name,
                            currentWeightKg = curMaxWeight,
                            suggestedWeightKg = curMaxWeight,
                            reason = "RPE ${"%.1f".format(avgRpe)} (max) — utrzymaj plan",
                            kind = ProgressionKind.NO_CHANGE,
                            currentReps = plannedReps,
                            suggestedReps = plannedReps
                        )
                    }
                }
                continue
            }

            // === Fallback: brak RPE — legacy logika porównania z poprzednim treningiem ===
            if (sortedWorkoutIds.isEmpty()) continue
            val prevSets = previousByWorkout[sortedWorkoutIds[0]]!!
            val prevMaxWeight = prevSets.maxOf { it.weightKg }
            val prevMinRepsAtMax = prevSets.filter { it.weightKg == prevMaxWeight }.minOf { it.reps }
            if (curMaxWeight >= prevMaxWeight && curMinRepsAtMax >= prevMinRepsAtMax && curMinRepsAtMax >= 8) {
                tips += ProgressionTip(
                    exerciseId = exId,
                    exerciseName = name,
                    currentWeightKg = curMaxWeight,
                    suggestedWeightKg = curMaxWeight + 2.5,
                    reason = "wszystkie serie ✓ — +2.5 kg",
                    kind = ProgressionKind.INCREASE_WEIGHT,
                    currentReps = plannedReps,
                    suggestedReps = plannedReps
                )
            }
        }
        return tips
    }

    /**
     * Pobiera sety z najnowszej sesji ukończonej PRZED danym workoutem (chronologicznie).
     * Używane w WorkoutDetail — gdy patrzysz na trening z 10 marca, "ostatnio" musi
     * być treningiem z 5 marca, nie z 20 marca (nawet jeśli ten 20-go już istnieje w bazie).
     */
    suspend fun getSessionBefore(
        exerciseId: Long,
        beforeStartedAtMs: Long
    ): PreviousSession? {
        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt < beforeStartedAtMs }
        if (finished.isEmpty()) return null
        val byId = finished.associateBy { it.id }
        val all = setDao.getAllForExercise(exerciseId)
            .filter { it.workoutId in byId }
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (all.isEmpty()) return null

        val grouped = all.groupBy { it.workoutId }
            .toList()
            .sortedByDescending { (wid, _) -> byId[wid]!!.startedAt }

        val (wid, sets) = grouped.firstOrNull() ?: return null
        val w = byId[wid]!!
        return PreviousSession(
            workoutId = wid,
            workoutDate = w.startedAt,
            sets = sets.sortedBy { it.setNumber }
        )
    }

    /**
     * Pobiera sety z OSTATNIEGO ukończonego treningu zawierającego dane ćwiczenie,
     * z wykluczeniem currentWorkoutId. Zwraca null gdy brak.
     */
    suspend fun getPreviousSessionForExercise(
        exerciseId: Long,
        excludeWorkoutId: Long? = null
    ): PreviousSession? {
        val all = setDao.getAllForExercise(exerciseId)
            .filter { excludeWorkoutId == null || it.workoutId != excludeWorkoutId }
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (all.isEmpty()) return null

        // weź sety z najnowszego ukończonego treningu
        val finished = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val byId = finished.associateBy { it.id }
        val grouped = all.groupBy { it.workoutId }
            .filterKeys { it in byId }
            .toList()
            .sortedByDescending { (wid, _) -> byId[wid]!!.startedAt }

        val (wid, sets) = grouped.firstOrNull() ?: return null
        val w = byId[wid]!!
        return PreviousSession(
            workoutId = wid,
            workoutDate = w.startedAt,
            sets = sets.sortedBy { it.setNumber }
        )
    }

    /**
     * Sugeruje następną wagę × powt. dla danego ćwiczenia bazując na poprzedniej sesji
     * + celu treningowym usera. Heurystyka:
     * - Jeśli RPE ≤ 7 lub brak RPE z planem zrealizowanym → +Δ kg
     * - Jeśli RPE 8-9 i plan zrealizowany → utrzymaj wagę, +1 rep
     * - Jeśli RPE 10 lub plan nie zrealizowany → utrzymaj
     * - Δ zależy od celu: STRENGTH → +2.5kg, HYPERTROPHY → +1.25kg, inne → +1kg
     */
    suspend fun suggestNextSet(
        exerciseId: Long,
        excludeWorkoutId: Long? = null,
        goal: pl.filebit.gymtracker.data.entity.TrainingGoal
    ): NextSetSuggestion? {
        val prev = getPreviousSessionForExercise(exerciseId, excludeWorkoutId) ?: return null
        val sets = prev.sets
        if (sets.isEmpty()) return null

        // Bazuj na ostatnim secie roboczym z najwyższą wagą
        val ref = sets.maxByOrNull { it.weightKg } ?: return null
        val refWeight = ref.weightKg
        val refReps = ref.reps
        val avgRpe = sets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()

        val delta = when (goal) {
            pl.filebit.gymtracker.data.entity.TrainingGoal.STRENGTH -> 2.5
            pl.filebit.gymtracker.data.entity.TrainingGoal.HYPERTROPHY -> 1.25
            pl.filebit.gymtracker.data.entity.TrainingGoal.MIX -> 1.25
            pl.filebit.gymtracker.data.entity.TrainingGoal.GENERAL_FITNESS -> 1.0
            pl.filebit.gymtracker.data.entity.TrainingGoal.CARDIO_LIFTING -> 1.0
        }

        // Logika autoregulacji wyciągnięta jako pure function w util/Autoregulation.kt
        // — testowana w AutoregulationTest. Tu tylko mapujemy dane z poprzedniej sesji.
        val r = pl.filebit.gymtracker.util.computeProgression(
            refWeight = refWeight,
            refReps = refReps,
            avgRpe = avgRpe,
            delta = delta
        )
        return NextSetSuggestion(
            suggestedWeightKg = r.weightKg,
            suggestedReps = r.reps,
            rationale = r.rationale,
            previousWeightKg = refWeight,
            previousReps = refReps
        )
    }

    /**
     * Stagnacja: dla każdego ćwiczenia w bieżącym treningu sprawdź czy max waga
     * w ostatnich 3+ treningach nie urosła (jest dokładnie taka sama).
     *
     * FIX v1.23.1: dodatkowo wymagamy by w ostatnich 3-5 treningach przed lastN
     * waga była **taka sama lub WYŻSZA**. Inaczej "ostatnie 3 z tą samą wagą"
     * to po prostu **świeży awans** (np. 80→82.5kg w obrębie 1 tygodnia 3×/tydz),
     * nie stagnacja. To bug: liniowa progresja "+2.5kg/tydz" wyglądała jak
     * stagnacja na każdym świeżym poziomie.
     */
    suspend fun detectStagnation(currentWorkoutId: Long, threshold: Int = 3): List<StagnationAlert> {
        val curSets = setDao.getForWorkout(currentWorkoutId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (curSets.isEmpty()) return emptyList()
        val byExercise = curSets.groupBy { it.exerciseId }
        val results = mutableListOf<StagnationAlert>()

        // Tylko zakończone treningi
        val finishedIds = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .associateBy { it.id }
        if (finishedIds.size < threshold) return emptyList()

        for ((exId, _) in byExercise) {
            val allSets = setDao.getAllForExercise(exId)
                .filter { it.isCompleted && it.setType != SetType.WARMUP && it.workoutId in finishedIds.keys }
            if (allSets.isEmpty()) continue

            // Pogrupuj per workout, weź max wagę i datę startu.
            // Window: threshold + 3 żeby móc sprawdzić "czy waga rosła niedawno"
            val perWorkoutMaxWeight = allSets.groupBy { it.workoutId }
                .map { (wid, list) ->
                    val w = finishedIds[wid]!!
                    w.startedAt to list.maxOf { it.weightKg }
                }
                .sortedByDescending { it.first }   // od najnowszych
                .map { it.second }
                .take(threshold + 3)

            if (perWorkoutMaxWeight.size < threshold) continue

            // Sprawdź czy ostatnie `threshold` treningów mają tę samą max wagę
            val lastN = perWorkoutMaxWeight.take(threshold)
            val firstWeight = lastN.first()
            if (firstWeight <= 0) continue
            val allEqual = lastN.all { it == firstWeight }
            if (!allEqual) continue

            // FIX: sprawdź czy treningi PRZED lastN miały niższą wagę.
            // Jeśli tak → user właśnie awansował, to nie stagnacja.
            val priorWeights = perWorkoutMaxWeight.drop(threshold)
            val recentlyAdvanced = priorWeights.any { it in 0.001..(firstWeight - 0.001) }
            if (recentlyAdvanced) continue

            val name = exerciseDao.getById(exId)?.name ?: "?"
            results.add(
                StagnationAlert(
                    exerciseId = exId,
                    exerciseName = name,
                    stuckAtKg = firstWeight,
                    workoutsAtSameWeight = threshold
                )
            )
        }
        return results
    }

    /**
     * Czy któryś set z bieżącego treningu pobił max wagę × powt dla swojego ćwiczenia
     * w porównaniu do innych zakończonych treningów (excl. obecnego).
     */
    suspend fun detectNewPRs(currentWorkoutId: Long): List<NewPr> {
        val curSets = setDao.getForWorkout(currentWorkoutId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (curSets.isEmpty()) return emptyList()
        val byExercise = curSets.groupBy { it.exerciseId }
        val results = mutableListOf<NewPr>()
        for ((exId, list) in byExercise) {
            val previousMax1RM = setDao.getAllForExercise(exId)
                .filter {
                    it.workoutId != currentWorkoutId &&
                        it.isCompleted && it.setType != SetType.WARMUP
                }
                .maxOfOrNull { epley1RM(it.weightKg, it.reps) } ?: 0.0
            val curMax = list.maxOfOrNull { epley1RM(it.weightKg, it.reps) } ?: 0.0
            if (curMax > previousMax1RM && curMax > 0.0) {
                val bestSet = list.maxByOrNull { epley1RM(it.weightKg, it.reps) }!!
                results.add(
                    NewPr(
                        exerciseId = exId,
                        weightKg = bestSet.weightKg,
                        reps = bestSet.reps,
                        previousBest1RM = previousMax1RM,
                        new1RM = curMax
                    )
                )
            }
        }
        return results
    }

    /**
     * Lista wszystkich PR-ów (per ćwiczenie) — używa istniejącej prForExercise().
     * Sortowane wg estimated 1RM malejąco.
     */
    suspend fun allPersonalRecords(): List<PersonalRecordRow> {
        val allExercises = exerciseDao.observeAll().first()
        return allExercises.mapNotNull { ex ->
            // Rekordy osobiste = leaderboard 1RM/ciężaru — cardio nie ma 1RM.
            if (ex.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
                ex.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
            ) return@mapNotNull null
            val pr = prForExercise(ex.id) ?: return@mapNotNull null
            PersonalRecordRow(
                exerciseId = ex.id,
                exerciseName = ex.name,
                muscle = ex.primaryMuscle,
                pr = pr
            )
        }.sortedByDescending { it.pr.estimated1RM }
    }

    /**
     * Heatmap kalendarzowa — ostatnich N dni, sumaryczna objętość kg per dzień.
     * Mapa epochDay → totalVolume.
     */
    suspend fun calendarHeatmap(days: Int = 84): Map<Long, Double> {
        val now = System.currentTimeMillis()
        val cutoff = now - days * 86_400_000L
        val workouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoff }
        if (workouts.isEmpty()) return emptyMap()
        val sets = workouts.flatMap { w ->
            setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
        }
        return sets.groupBy { (it.createdAt / 86_400_000L) }
            .mapValues { (_, list) -> list.sumOf { it.reps * it.weightKg } }
    }

    /**
     * Recovery: ostatni trening per partia mięśniowa (dni temu).
     */
    suspend fun recoveryByMuscle(): List<MuscleRecovery> {
        val now = System.currentTimeMillis()
        val workouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .associateBy { it.id }
        val allSets = workouts.keys.flatMap { wid ->
            setDao.getForWorkout(wid)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
        }
        if (allSets.isEmpty()) return emptyList()
        val exMap = allSets.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it) }

        val byMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Long>()
        allSets.forEach { s ->
            val muscle = exMap[s.exerciseId]?.primaryMuscle ?: return@forEach
            val workout = workouts[s.workoutId] ?: return@forEach
            val day = workout.startedAt
            val current = byMuscle[muscle]
            if (current == null || day > current) byMuscle[muscle] = day
        }
        return byMuscle.map { (muscle, lastTraining) ->
            val daysAgo = ((now - lastTraining) / 86_400_000L).toInt()
            MuscleRecovery(muscle = muscle, lastTrainingMillis = lastTraining, daysAgo = daysAgo)
        }.sortedByDescending { it.daysAgo }
    }

    /**
     * Wszystkie wykryte stagnacje — agreguje detectStagnation dla
     * ostatnich treningów (wystarczy ostatni żeby zobaczyć aktualne).
     */
    suspend fun allStagnations(): List<StagnationAlert> {
        val lastFinished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .maxByOrNull { it.startedAt } ?: return emptyList()
        return detectStagnation(lastFinished.id, threshold = 3)
    }
}

data class NewPr(
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val previousBest1RM: Double,
    val new1RM: Double
)

/**
 * Punkt na wykresie: jeden trening, max waga × max powt × suma volume tego ćwiczenia.
 */
data class ExerciseProgressionPoint(
    val workoutId: Long,
    val workoutDate: Long,
    val maxWeightKg: Double,
    val repsAtMax: Int,
    val volumeKg: Double,
    val estimated1RM: Double,
    // v1.29.19 — cardio: średnia prędkość / łączny czas / dystans tej sesji
    val avgSpeedKmh: Double = 0.0,
    val totalDurationSec: Int = 0,
    val totalDistanceM: Double = 0.0
)

enum class ProgressionKind {
    INCREASE_WEIGHT,   // RPE niskie + reps wykonane → +waga
    INCREASE_REPS,     // RPE średnie + reps wykonane → +1 powt.
    NO_CHANGE,         // RPE wysokie albo niedokończone reps → ten sam plan
    DELOAD             // niedokończone reps × 2 sesje → -waga
}

/**
 * Sugestia progresji: w jakim kierunku zmienić plan w danym ćwiczeniu.
 * - currentWeightKg/Reps to wartości z **planu** (aktualne)
 * - suggestedWeightKg/Reps to docelowe wartości po zastosowaniu sugestii
 */
data class ProgressionTip(
    val exerciseId: Long,
    val exerciseName: String,
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val reason: String,
    val kind: ProgressionKind = ProgressionKind.INCREASE_WEIGHT,
    val currentReps: Int = 0,
    val suggestedReps: Int = 0
)

data class StreakInfo(
    val current: Int,    // tygodnie z rzędu (włącznie z bieżącym jeśli jest trening)
    val best: Int        // najlepszy streak w historii
)

data class WeekProgress(
    val current: Int,
    val target: Int
) {
    val percent: Int get() = if (target > 0) (current * 100 / target).coerceAtMost(100) else 0
}

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progress: Int = 0,    // 0-100
    val targetValue: Long = 0,
    val currentValue: Long = 0,
    val category: AchievementCategory = AchievementCategory.GENERAL,
    val level: AchievementLevel = AchievementLevel.BRONZE,
    val unlockedAt: Long? = null
)

enum class AchievementCategory(val labelPl: String, val emoji: String) {
    CONSISTENCY("Wytrwałość", "🔥"),
    VOLUME("Objętość", "🏋️"),
    STRENGTH("Siła", "💪"),
    EXPLORATION("Eksploracja", "🧭"),
    BODY("Sylwetka", "📏"),
    GOALS("Cele", "🎯"),
    PERIODIZATION("Periodyzacja", "🔄"), // v1.18.0
    GENERAL("Ogólne", "✨")
}

enum class AchievementLevel(val labelPl: String) {
    BRONZE("Brąz"),
    SILVER("Srebro"),
    GOLD("Złoto"),
    PLATINUM("Platyna")
}

data class MuscleEngagement(
    val muscle: pl.filebit.gymtracker.data.entity.MuscleGroup,
    val volumeKg: Double,       // suma reps × weight (working sets, bez warm-upów)
    val totalSets: Int,         // ile working sets dotknęło tej grupy
    val percentOfTotal: Int     // 0-100, udział w całym wolumenie okresu
)

enum class MuscleStatus(val priority: Int) {
    NEGLECTED(0),   // 0 treningów lub > 21 dni — pokazujemy najwyżej
    UNDER(1),       // poniżej 50% zalecanego udziału
    OVER(2),        // powyżej 180% zalecanego udziału (przetrenowany)
    BALANCED(3)     // w granicach
}

data class MuscleAnalysis(
    val muscle: pl.filebit.gymtracker.data.entity.MuscleGroup,
    val volumeKg: Double,
    val totalSets: Int,
    val actualPercent: Int,
    val recommendedPercent: Int,
    val daysSinceLast: Int?,
    val status: MuscleStatus
)

data class MuscleAnalysisReport(
    val periodDays: Int,
    val totalVolumeKg: Double,
    val analyses: List<MuscleAnalysis>
)

/** Główne grupy mięśniowe brane pod uwagę w analizie balansu. */
internal val MAIN_MUSCLES = listOf(
    pl.filebit.gymtracker.data.entity.MuscleGroup.BACK,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST,
    pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS,
    pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS,
    pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES,
    pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS,
    pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS,
    pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CORE
)

/**
 * Zalecany rozkład objętości treningowej dla harmonijnej sylwetki (suma=100).
 * Bazuje na typowych rekomendacjach hipertroficznych — punkt odniesienia,
 * nie sztywna reguła.
 */
internal val RECOMMENDED_DISTRIBUTION = mapOf(
    pl.filebit.gymtracker.data.entity.MuscleGroup.BACK to 22,
    pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS to 18,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST to 16,
    pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS to 10,
    pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES to 10,
    pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS to 10,
    pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS to 5,
    pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS to 5,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES to 2,
    pl.filebit.gymtracker.data.entity.MuscleGroup.CORE to 2
)

data class StagnationAlert(
    val exerciseId: Long,
    val exerciseName: String,
    val stuckAtKg: Double,
    val workoutsAtSameWeight: Int   // ile treningów z rzędu ta sama max waga
)

/**
 * Sugestia progresji dla następnego setu/sesji ćwiczenia.
 */
data class NextSetSuggestion(
    val suggestedWeightKg: Double,
    val suggestedReps: Int,
    val rationale: String,   // krótki opis dlaczego (np. "RPE 7 + cel siła = +2.5kg")
    val previousWeightKg: Double,
    val previousReps: Int
)

/**
 * Snapshot poprzedniej sesji ćwiczenia — sety z poprzedniego ukończonego treningu.
 */
data class PreviousSession(
    val workoutId: Long,
    val workoutDate: Long,
    val sets: List<pl.filebit.gymtracker.data.entity.WorkoutSet>
)


data class PersonalRecordRow(
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: pl.filebit.gymtracker.data.entity.MuscleGroup,
    val pr: ExercisePr
)

data class MuscleRecovery(
    val muscle: pl.filebit.gymtracker.data.entity.MuscleGroup,
    val lastTrainingMillis: Long,
    val daysAgo: Int
)

/**
 * Pure function — testable bez DAO. Logika identyczna z StatsRepository.muscleAnalysis().
 *
 * Wywoływana przez StatsRepository.muscleAnalysisFast() — `now` przekazywane jako
 * parameter dla deterministycznych testów.
 */
fun computeMuscleAnalysisFromSnapshot(
    periodDays: Int,
    snapshot: StatsSnapshot,
    now: Long
): MuscleAnalysisReport {
    val cutoff = if (periodDays > 0) now - periodDays.toLong() * 86_400_000L else 0L
    val finished = snapshot.finishedWorkouts.filter { it.startedAt >= cutoff }

    val perMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Triple<Double, Int, Long>>()
    for (w in finished) {
        val sets = snapshot.completedSetsFor(w.id)
        for (s in sets) {
            val ex = snapshot.exerciseForSet(s) ?: continue
            val muscle = ex.primaryMuscle
            val vol = s.reps * s.weightKg
            val (v, c, lastTs) = perMuscle.getOrDefault(muscle, Triple(0.0, 0, 0L))
            perMuscle[muscle] = Triple(v + vol, c + 1, maxOf(lastTs, w.startedAt))
        }
    }

    val total = perMuscle.values.sumOf { it.first }.coerceAtLeast(0.0001)
    val analyses = MAIN_MUSCLES.map { muscle ->
        val (vol, sets, lastTs) = perMuscle[muscle] ?: Triple(0.0, 0, 0L)
        val actualPct = ((vol * 100.0) / total).toInt().coerceIn(0, 100)
        val recommendedPct = RECOMMENDED_DISTRIBUTION[muscle] ?: 0
        val daysSinceLast = if (lastTs > 0) ((now - lastTs) / 86_400_000L).toInt() else null
        val status = computeMuscleStatusForSnapshot(actualPct, recommendedPct, daysSinceLast)
        MuscleAnalysis(
            muscle = muscle,
            volumeKg = vol,
            totalSets = sets,
            actualPercent = actualPct,
            recommendedPercent = recommendedPct,
            daysSinceLast = daysSinceLast,
            status = status
        )
    }
    return MuscleAnalysisReport(
        periodDays = periodDays,
        totalVolumeKg = total,
        analyses = analyses.sortedWith(compareBy({ it.status.priority }, { -it.actualPercent }))
    )
}

/**
 * Top-level kopia logiki StatsRepository.computeStatus() — bo prywatne method
 * w klasie nie jest dostępne dla top-level computeMuscleAnalysisFromSnapshot.
 * MUSI być identyczne z private computeStatus() w klasie!
 */
private fun computeMuscleStatusForSnapshot(
    actualPct: Int,
    recommendedPct: Int,
    daysSinceLast: Int?
): MuscleStatus {
    if (daysSinceLast == null) return MuscleStatus.NEGLECTED
    if (daysSinceLast > 21) return MuscleStatus.NEGLECTED
    if (recommendedPct > 0 && actualPct > recommendedPct * 1.8) return MuscleStatus.OVER
    if (recommendedPct > 0 && actualPct < recommendedPct * 0.5) return MuscleStatus.UNDER
    return MuscleStatus.BALANCED
}

/**
 * Pure function — testable bez DAO. Logika identyczna z StatsRepository.muscleEngagement().
 */
fun computeMuscleEngagementFromSnapshot(
    periodDays: Int,
    snapshot: StatsSnapshot,
    now: Long
): List<MuscleEngagement> {
    val cutoff = if (periodDays > 0) now - periodDays.toLong() * 24L * 60L * 60L * 1000L else 0L
    val finished = snapshot.finishedWorkouts.filter { it.startedAt >= cutoff }
    if (finished.isEmpty()) return emptyList()

    val perMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Pair<Double, Int>>()

    for (w in finished) {
        val sets = snapshot.completedSetsFor(w.id)
        for (s in sets) {
            val ex = snapshot.exerciseForSet(s) ?: continue
            val vol = s.reps * s.weightKg
            // v2.2.0: ważone udziały per mięsień z canonical muscleIntensityJson
            // (P10 = 100%, S7 = 70%, T4 = 40%). Fallback: cały volume w primaryMuscle.
            val muscleShares = muscleSharesFromCanonical(ex.muscleIntensityJson)
            if (muscleShares.isEmpty()) {
                val (v, c) = perMuscle.getOrDefault(ex.primaryMuscle, 0.0 to 0)
                perMuscle[ex.primaryMuscle] = (v + vol) to (c + 1)
            } else {
                muscleShares.forEach { (group, share) ->
                    val (v, c) = perMuscle.getOrDefault(group, 0.0 to 0)
                    perMuscle[group] = (v + vol * share) to (c + 1)
                }
            }
        }
    }

    val total = perMuscle.values.sumOf { it.first }
    if (total <= 0) return emptyList()

    return perMuscle.entries
        .map { (muscle, vc) ->
            MuscleEngagement(
                muscle = muscle,
                volumeKg = vc.first,
                totalSets = vc.second,
                percentOfTotal = ((vc.first * 100) / total).toInt().coerceIn(0, 100)
            )
        }
        .sortedByDescending { it.volumeKg }
}

/** Top-level helper — kopia private weekKey z StatsRepository. ISO week. */
private fun weekKeyForSnapshot(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMillis
    cal.firstDayOfWeek = java.util.Calendar.MONDAY
    cal.minimalDaysInFirstWeek = 4
    val year = cal.get(java.util.Calendar.YEAR)
    val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
    return "%04d-W%02d".format(year, week)
}

/**
 * Pure function — overview z snapshot. Logika identyczna z StatsRepository.overview().
 */
fun computeOverviewFromSnapshot(snapshot: StatsSnapshot, now: Long): OverviewStats {
    val finished = snapshot.finishedWorkouts
    val totalDuration = finished.sumOf {
        // Workout.durationMillis to (finishedAt ?: now) - startedAt
        // Snapshot ma finishedWorkouts (finishedAt != null)
        (it.finishedAt ?: now) - it.startedAt
    }
    var totalVolume = 0.0
    var totalSets = 0
    for (w in finished) {
        val sets = snapshot.completedSetsFor(w.id)
        totalVolume += sets.sumOf { it.reps * it.weightKg }
        totalSets += sets.size
    }
    val avg = if (finished.isNotEmpty()) totalVolume / finished.size else 0.0
    val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
    val monthAgo = now - 30L * 24L * 60L * 60L * 1000L
    val thisWeek = finished.count { it.startedAt >= weekAgo }
    val thisMonth = finished.count { it.startedAt >= monthAgo }
    return OverviewStats(
        totalWorkouts = finished.size,
        totalDurationMillis = totalDuration,
        totalVolumeKg = totalVolume,
        totalSets = totalSets,
        avgVolumePerWorkout = avg,
        workoutsThisWeek = thisWeek,
        workoutsThisMonth = thisMonth
    )
}

/**
 * Pure function — volumePerWeek z snapshot. Logika identyczna z StatsRepository.volumePerWeek().
 */
fun computeVolumePerWeekFromSnapshot(weeks: Int, snapshot: StatsSnapshot, now: Long): List<Double> {
    val all = snapshot.finishedWorkouts
    if (all.isEmpty()) return List(weeks) { 0.0 }

    val byWeek = mutableMapOf<String, Double>()
    for (w in all) {
        val sets = snapshot.completedSetsFor(w.id)
        val vol = sets.sumOf { it.reps * it.weightKg }
        val key = weekKeyForSnapshot(w.startedAt)
        byWeek[key] = (byWeek[key] ?: 0.0) + vol
    }

    val result = ArrayList<Double>(weeks)
    for (i in (weeks - 1) downTo 0) {
        val key = weekKeyForSnapshot(now - i * 7L * 24L * 60L * 60L * 1000L)
        result.add(byWeek[key] ?: 0.0)
    }
    return result
}

/**
 * Pure function — calendarHeatmap z snapshot. Logika identyczna z StatsRepository.calendarHeatmap().
 */
fun computeCalendarHeatmapFromSnapshot(
    days: Int,
    snapshot: StatsSnapshot,
    now: Long
): Map<Long, Double> {
    val cutoff = now - days * 86_400_000L
    val workouts = snapshot.finishedWorkouts.filter { it.startedAt >= cutoff }
    if (workouts.isEmpty()) return emptyMap()
    val sets = workouts.flatMap { snapshot.completedSetsFor(it.id) }
    return sets.groupBy { (it.createdAt / 86_400_000L) }
        .mapValues { (_, list) -> list.sumOf { it.reps * it.weightKg } }
}

/**
 * Pure function — recoveryByMuscle z snapshot.
 * Logika identyczna z StatsRepository.recoveryByMuscle().
 */
fun computeRecoveryByMuscleFromSnapshot(
    snapshot: StatsSnapshot,
    now: Long
): List<MuscleRecovery> {
    val workoutsById = snapshot.workoutsById.filterValues { it.finishedAt != null }
    val allSets = workoutsById.keys.flatMap { snapshot.completedSetsFor(it) }
    if (allSets.isEmpty()) return emptyList()

    val byMuscle = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Long>()
    allSets.forEach { s ->
        val muscle = snapshot.exerciseForSet(s)?.primaryMuscle ?: return@forEach
        val workout = workoutsById[s.workoutId] ?: return@forEach
        val day = workout.startedAt
        val current = byMuscle[muscle]
        if (current == null || day > current) byMuscle[muscle] = day
    }
    return byMuscle.map { (muscle, lastTraining) ->
        val daysAgo = ((now - lastTraining) / 86_400_000L).toInt()
        MuscleRecovery(muscle = muscle, lastTrainingMillis = lastTraining, daysAgo = daysAgo)
    }.sortedByDescending { it.daysAgo }
}

/**
 * Pure function — allPersonalRecords z snapshot.
 * Logika identyczna z StatsRepository.allPersonalRecords() + prForExercise().
 */
fun computeAllPersonalRecordsFromSnapshot(
    snapshot: StatsSnapshot,
    epley: (Double, Int) -> Double = { w, r -> if (w > 0 && r > 0) w * (1 + r / 30.0) else 0.0 }
): List<PersonalRecordRow> {
    // Pre-group: completedSets by exerciseId (raz, dla wszystkich exercises)
    val setsByExercise = snapshot.completedSets.groupBy { it.exerciseId }
    return snapshot.allExercises.mapNotNull { ex ->
        // Rekordy osobiste = leaderboard 1RM/ciężaru — cardio nie ma 1RM.
        if (ex.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
            ex.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
        ) return@mapNotNull null
        val sets = setsByExercise[ex.id] ?: return@mapNotNull null
        if (sets.isEmpty()) return@mapNotNull null
        val maxWeightSet = sets.maxByOrNull { it.weightKg } ?: return@mapNotNull null
        val perWorkoutVol = sets.groupBy { it.workoutId }
            .mapValues { (_, list) -> list.sumOf { it.reps * it.weightKg } }
        val maxVolume = perWorkoutVol.values.maxOrNull() ?: 0.0
        val best1RM = sets.maxOf { epley(it.weightKg, it.reps) }
        val pr = ExercisePr(
            maxWeightKg = maxWeightSet.weightKg,
            repsAtMaxWeight = maxWeightSet.reps,
            maxVolumeKg = maxVolume,
            estimated1RM = (best1RM * 10).roundToInt() / 10.0,
            totalSetsLogged = sets.size
        )
        PersonalRecordRow(
            exerciseId = ex.id,
            exerciseName = ex.name,
            muscle = ex.primaryMuscle,
            pr = pr
        )
    }.sortedByDescending { it.pr.estimated1RM }
}

/**
 * Pure function — allStagnations z snapshot.
 * Logika identyczna z StatsRepository.allStagnations() → detectStagnation().
 */
fun computeAllStagnationsFromSnapshot(
    snapshot: StatsSnapshot,
    threshold: Int = 3
): List<StagnationAlert> {
    // Najnowszy zakończony trening (analog allStagnations: lastFinished)
    val lastFinished = snapshot.finishedWorkouts.maxByOrNull { it.startedAt }
        ?: return emptyList()

    // Wewnętrzny detectStagnation z snapshot
    val curSets = snapshot.completedSetsFor(lastFinished.id)
    if (curSets.isEmpty()) return emptyList()
    val byExercise = curSets.groupBy { it.exerciseId }
    val results = mutableListOf<StagnationAlert>()

    val finishedById = snapshot.finishedWorkouts.associateBy { it.id }
    if (finishedById.size < threshold) return emptyList()

    // Pre-group all completed sets by exerciseId — raz dla wszystkich
    val setsByExercise = snapshot.completedSets
        .filter { it.workoutId in finishedById.keys }
        .groupBy { it.exerciseId }

    for ((exId, _) in byExercise) {
        val allSets = setsByExercise[exId] ?: continue
        if (allSets.isEmpty()) continue

        val perWorkoutMaxWeight = allSets.groupBy { it.workoutId }
            .map { (wid, list) ->
                val w = finishedById[wid]!!
                w.startedAt to list.maxOf { it.weightKg }
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .take(threshold + 1)

        if (perWorkoutMaxWeight.size < threshold) continue

        val lastN = perWorkoutMaxWeight.take(threshold)
        val firstWeight = lastN.first()
        if (firstWeight <= 0) continue
        val allEqual = lastN.all { it == firstWeight }
        if (allEqual) {
            val name = snapshot.exercisesById[exId]?.name ?: "?"
            results.add(
                StagnationAlert(
                    exerciseId = exId,
                    exerciseName = name,
                    stuckAtKg = firstWeight,
                    workoutsAtSameWeight = threshold
                )
            )
        }
    }
    return results
}

/**
 * Pure function — detectNewPRs z snapshot.
 * Logika identyczna z StatsRepository.detectNewPRs().
 */
fun computeNewPRsFromSnapshot(
    currentWorkoutId: Long,
    snapshot: StatsSnapshot,
    epley: (Double, Int) -> Double = { w, r -> if (w > 0 && r > 0) w * (1 + r / 30.0) else 0.0 }
): List<NewPr> {
    val curSets = snapshot.completedSetsFor(currentWorkoutId)
    if (curSets.isEmpty()) return emptyList()

    val byExercise = curSets.groupBy { it.exerciseId }
    val results = mutableListOf<NewPr>()

    // Pre-group: completed sets by exerciseId (raz dla wszystkich exercises)
    val completedSetsByExercise = snapshot.completedSets.groupBy { it.exerciseId }

    for ((exId, list) in byExercise) {
        val allCompletedForEx = completedSetsByExercise[exId] ?: continue
        val previousMax1RM = allCompletedForEx
            .filter { it.workoutId != currentWorkoutId }
            .maxOfOrNull { epley(it.weightKg, it.reps) } ?: 0.0
        val curMax = list.maxOfOrNull { epley(it.weightKg, it.reps) } ?: 0.0
        if (curMax > previousMax1RM && curMax > 0.0) {
            val bestSet = list.maxByOrNull { epley(it.weightKg, it.reps) }!!
            results.add(
                NewPr(
                    exerciseId = exId,
                    weightKg = bestSet.weightKg,
                    reps = bestSet.reps,
                    previousBest1RM = previousMax1RM,
                    new1RM = curMax
                )
            )
        }
    }
    return results
}

// v2.2.0 canonical muscle intensity helpers
fun muscleSharesFromCanonical(json: String?): Map<pl.filebit.gymtracker.data.entity.MuscleGroup, Double> {
    if (json.isNullOrBlank() || json == "{}") return emptyMap()
    return try {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject
            ?: return emptyMap()
        val raw = mutableMapOf<pl.filebit.gymtracker.data.entity.MuscleGroup, Double>()
        for ((muscle, intensity) in obj.entries) {
            val intensityStr = (intensity as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
            val ratio = parseCanonicalIntensity(intensityStr)
            val group = canonicalMuscleToGroup(muscle) ?: continue
            raw[group] = (raw[group] ?: 0.0) + ratio
        }
        val sum = raw.values.sum()
        if (sum <= 0) emptyMap() else raw.mapValues { it.value / sum }
    } catch (_: Throwable) {
        emptyMap()
    }
}

private fun parseCanonicalIntensity(s: String): Double {
    val grade = s.firstOrNull()?.uppercaseChar() ?: return 0.0
    val num = s.drop(1).toIntOrNull() ?: 5
    val base = when (grade) {
        'P' -> 1.0
        'S' -> 0.6
        'T' -> 0.3
        else -> 0.5
    }
    return base * (num / 10.0)
}

private fun canonicalMuscleToGroup(name: String): pl.filebit.gymtracker.data.entity.MuscleGroup? {
    val lower = name.lowercase()
    // fully-qualified G
    return when {
        lower.contains("pectoralis") || lower == "chest" -> pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST
        lower.contains("latissimus") || lower.contains("trapezius") || lower.contains("rhomboid") ||
            lower.contains("erector_spinae") || lower.contains("teres") || lower.contains("infraspinatus") -> pl.filebit.gymtracker.data.entity.MuscleGroup.BACK
        lower.contains("deltoid") -> pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS
        lower.contains("biceps_brachii") || lower.contains("brachialis") || lower.contains("brachioradialis") -> pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS
        lower.contains("triceps") -> pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS
        lower.contains("quadriceps") -> pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS
        lower.contains("hamstrings") || lower.contains("biceps_femoris") ||
            lower.contains("semitendinosus") || lower.contains("semimembranosus") -> pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS
        lower.contains("gluteus") -> pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES
        lower.contains("gastrocnemius") || lower.contains("soleus") -> pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES
        lower.contains("rectus_abdominis") || lower.contains("obliques") ||
            lower.contains("transverse_abdominis") || lower == "abs" -> pl.filebit.gymtracker.data.entity.MuscleGroup.CORE
        lower.contains("cardiovascular") || lower == "heart" -> pl.filebit.gymtracker.data.entity.MuscleGroup.CARDIO
        else -> null
    }
}
