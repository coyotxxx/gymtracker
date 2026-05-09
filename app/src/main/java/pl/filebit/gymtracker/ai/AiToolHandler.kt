package pl.filebit.gymtracker.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.MonthlyRollupDao
import pl.filebit.gymtracker.data.db.dao.QuarterlyRollupDao
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.WeeklyRollupDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.repository.StatsCacheService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.11.67 — Handler wykonujący narzędzia (tools) AI.
 *
 * Odpowiada na żądanie tool_use z Anthropic API → wykonuje query do DB → zwraca
 * wynik jako JSON string (do tool_result).
 */
@Singleton
class AiToolHandler @Inject constructor(
    private val statsCacheService: StatsCacheService,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val eventDao: TrainingEventDao,
    private val weeklyDao: WeeklyRollupDao,
    private val monthlyDao: MonthlyRollupDao,
    private val quarterlyDao: QuarterlyRollupDao
) {
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val json = Json { encodeDefaults = false }

    private fun Double.roundTo(decimals: Int): Double {
        if (isNaN() || isInfinite()) return 0.0
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }

    /**
     * Wykonuje tool po nazwie z argumentami JSON. Zwraca wynik jako JSON string.
     */
    suspend fun execute(toolName: String, input: JsonObject): String {
        return when (toolName) {
            "get_workouts" -> execGetWorkouts(input)
            "get_events" -> execGetEvents(input)
            "get_rollups" -> execGetRollups(input)
            "get_exercise_history" -> execGetExerciseHistory(input)
            "get_body_history" -> execGetBodyHistory(input)
            else -> "{\"error\":\"Unknown tool: $toolName\"}"
        }
    }

    private suspend fun execGetWorkouts(input: JsonObject): String {
        val fromDate = input["from_date"]?.jsonPrimitive?.content ?: return "{\"error\":\"missing from_date\"}"
        val toDate = input["to_date"]?.jsonPrimitive?.content ?: return "{\"error\":\"missing to_date\"}"
        val exerciseFilter = input["exercise_name"]?.jsonPrimitive?.content
        val fromMs = runCatching { df.parse(fromDate)!!.time }.getOrNull() ?: return "{\"error\":\"invalid from_date\"}"
        val toMs = runCatching { df.parse(toDate)!!.time + 24 * 3600_000 }.getOrNull() ?: return "{\"error\":\"invalid to_date\"}"

        val snapshot = statsCacheService.snapshot()
        val workouts = snapshot.finishedWorkouts
            .filter { it.startedAt in fromMs until toMs }
            .sortedBy { it.startedAt }
            .take(30)

        val result = buildJsonObject {
            put("count", workouts.size)
            putJsonArray("workouts") {
                workouts.forEach { w ->
                    val sets = (snapshot.completedSetsByWorkoutId[w.id] ?: emptyList())
                        .filter { it.setType != SetType.WARMUP && it.weightKg > 0.0 }
                    val byExercise = sets.groupBy { it.exerciseId }
                    add(buildJsonObject {
                        put("id", w.id)
                        put("date", dfTime.format(Date(w.startedAt)))
                        w.notes.takeIf { it.isNotBlank() }?.let { put("notes", it) }
                        w.wellbeingRating?.let { put("wellbeing", it) }
                        w.painArea?.let { put("painArea", it) }
                        putJsonArray("exercises") {
                            byExercise.forEach { (exId, exSets) ->
                                val ex = snapshot.exercisesById[exId] ?: return@forEach
                                if (exerciseFilter != null && !ex.name.contains(exerciseFilter, ignoreCase = true)) return@forEach
                                add(buildJsonObject {
                                    put("name", ex.name)
                                    putJsonArray("sets") {
                                        exSets.sortedBy { it.setNumber }.forEach { s ->
                                            add(buildJsonObject {
                                                put("reps", s.reps)
                                                put("weightKg", s.weightKg.roundTo(1))
                                                s.rpe?.let { put("rpe", it) }
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    })
                }
            }
        }
        return result.toString()
    }

    private suspend fun execGetEvents(input: JsonObject): String {
        val type = input["type"]?.jsonPrimitive?.content ?: return "{\"error\":\"missing type\"}"
        val daysBack = input["days_back"]?.jsonPrimitive?.content?.toIntOrNull() ?: 365
        val limit = input["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50
        val fromMs = System.currentTimeMillis() - daysBack * 24L * 3600_000
        val toMs = System.currentTimeMillis()

        val events = if (type.uppercase() == "ALL") {
            eventDao.getInRange(fromMs, toMs).take(limit)
        } else {
            val eventType = runCatching { TrainingEventType.valueOf(type.uppercase()) }
                .getOrNull() ?: return "{\"error\":\"invalid type\"}"
            eventDao.getByType(eventType, limit = limit)
                .filter { it.date in fromMs..toMs }
        }

        val result = buildJsonObject {
            put("count", events.size)
            putJsonArray("events") {
                events.forEach { e ->
                    add(buildJsonObject {
                        put("date", df.format(Date(e.date)))
                        put("type", e.type.name)
                        e.exerciseName?.let { put("exercise", it) }
                        e.weightKg?.let { put("weightKg", it.roundTo(1)) }
                        e.reps?.let { put("reps", it) }
                        e.e1rmKg?.let { put("e1rmKg", it.roundTo(1)) }
                        e.area?.let { put("area", it) }
                        e.planName?.let { put("planName", it) }
                        e.weeksContext?.let { put("weeksContext", it) }
                        if (e.notes.isNotBlank()) put("notes", e.notes)
                    })
                }
            }
        }
        return result.toString()
    }

    private suspend fun execGetRollups(input: JsonObject): String {
        val period = input["period"]?.jsonPrimitive?.content?.uppercase() ?: return "{\"error\":\"missing period\"}"
        val count = input["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 12

        val result = buildJsonObject {
            put("period", period)
            when (period) {
                "WEEK" -> {
                    val rollups = weeklyDao.getRecent(limit = count)
                    put("count", rollups.size)
                    putJsonArray("rollups") {
                        rollups.forEach { r ->
                            add(buildJsonObject {
                                put("weekStart", df.format(Date(r.weekStartMs)))
                                put("totalVolumeKg", r.totalVolumeKg.roundTo(0))
                                put("sessions", r.sessionsCount)
                                put("totalSets", r.totalSets)
                                put("avgRpe", r.avgRpe.roundTo(1))
                                r.avgWellbeing?.let { put("avgWellbeing", it.roundTo(1)) }
                                if (r.mainLiftsBestJson.isNotBlank()) put("mainLiftsBest", r.mainLiftsBestJson)
                                if (r.muscleVolumePctsJson.isNotBlank()) put("muscleVolumePcts", r.muscleVolumePctsJson)
                            })
                        }
                    }
                }
                "MONTH" -> {
                    val rollups = monthlyDao.getRecent(limit = count)
                    put("count", rollups.size)
                    putJsonArray("rollups") {
                        rollups.forEach { r ->
                            add(buildJsonObject {
                                put("monthStart", df.format(Date(r.monthStartMs)))
                                put("totalVolumeKg", r.totalVolumeKg.roundTo(0))
                                put("sessions", r.sessionsCount)
                                put("avgRpe", r.avgRpe.roundTo(1))
                                put("prCount", r.prCount)
                                put("planChanges", r.planChanges)
                                put("deloadCount", r.deloadCount)
                                r.bodyWeightDeltaKg?.let { put("bodyWeightDeltaKg", it.roundTo(1)) }
                                if (r.mainLiftsE1rmEndJson.isNotBlank()) put("mainLiftsE1rmEnd", r.mainLiftsE1rmEndJson)
                            })
                        }
                    }
                }
                "QUARTER" -> {
                    val rollups = quarterlyDao.getRecent(limit = count)
                    put("count", rollups.size)
                    putJsonArray("rollups") {
                        rollups.forEach { r ->
                            add(buildJsonObject {
                                put("quarterStart", df.format(Date(r.quarterStartMs)))
                                put("totalVolumeKg", r.totalVolumeKg.roundTo(0))
                                put("sessions", r.sessionsCount)
                                put("prCount", r.prCount)
                                put("planChanges", r.planChanges)
                                put("deloadCount", r.deloadCount)
                                r.bodyWeightDeltaKg?.let { put("bodyWeightDeltaKg", it.roundTo(1)) }
                                if (r.mainLiftsE1rmStartJson.isNotBlank()) put("mainLiftsE1rmStart", r.mainLiftsE1rmStartJson)
                                if (r.mainLiftsE1rmEndJson.isNotBlank()) put("mainLiftsE1rmEnd", r.mainLiftsE1rmEndJson)
                                if (r.highlightsJson.isNotBlank()) put("highlights", r.highlightsJson)
                            })
                        }
                    }
                }
                else -> put("error", "Invalid period: $period (expected WEEK/MONTH/QUARTER)")
            }
        }
        return result.toString()
    }

    private suspend fun execGetExerciseHistory(input: JsonObject): String {
        val name = input["exercise_name"]?.jsonPrimitive?.content ?: return "{\"error\":\"missing exercise_name\"}"
        val weeksBack = input["weeks_back"]?.jsonPrimitive?.content?.toIntOrNull() ?: 12
        val fromMs = System.currentTimeMillis() - weeksBack * 7L * 24 * 3600_000

        val snapshot = statsCacheService.snapshot()
        val matchingExercise = snapshot.allExercises.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return "{\"error\":\"Exercise not found: $name\"}"

        // Wszystkie sety dla tego ćwiczenia z okresu, pogrupowane per workout
        val byWorkout = snapshot.completedSets
            .filter { it.exerciseId == matchingExercise.id && it.setType != SetType.WARMUP && it.weightKg > 0.0 }
            .groupBy { it.workoutId }
            .mapNotNull { (wid, sets) ->
                val workout = snapshot.workoutsById[wid] ?: return@mapNotNull null
                if (workout.startedAt < fromMs || workout.finishedAt == null) return@mapNotNull null
                workout to sets
            }
            .sortedBy { it.first.startedAt }

        val result = buildJsonObject {
            put("exercise", matchingExercise.name)
            put("count", byWorkout.size)
            putJsonArray("sessions") {
                byWorkout.forEach { (w, sets) ->
                    val topSet = sets.maxByOrNull { it.weightKg * (1 + it.reps / 30.0) }!!
                    val e1rm = topSet.weightKg * (1 + topSet.reps / 30.0)
                    add(buildJsonObject {
                        put("date", df.format(Date(w.startedAt)))
                        put("topWeightKg", topSet.weightKg.roundTo(1))
                        put("topReps", topSet.reps)
                        topSet.rpe?.let { put("topRpe", it) }
                        put("e1rmKg", e1rm.roundTo(1))
                        put("totalSets", sets.size)
                        put("totalVolumeKg", sets.sumOf { it.weightKg * it.reps }.roundTo(0))
                    })
                }
            }
        }
        return result.toString()
    }

    private suspend fun execGetBodyHistory(input: JsonObject): String {
        val weeksBack = input["weeks_back"]?.jsonPrimitive?.content?.toIntOrNull() ?: 26
        val fromMs = System.currentTimeMillis() - weeksBack * 7L * 24 * 3600_000

        val measurements = bodyMeasurementDao.getAllAsc().filter { it.date >= fromMs }

        val result = buildJsonObject {
            put("count", measurements.size)
            putJsonArray("measurements") {
                measurements.forEach { m ->
                    add(buildJsonObject {
                        put("date", df.format(Date(m.date)))
                        m.weightKg?.let { put("weightKg", it.roundTo(1)) }
                        m.waistCm?.let { put("waistCm", it.roundTo(1)) }
                        m.chestCm?.let { put("chestCm", it.roundTo(1)) }
                        m.hipsCm?.let { put("hipsCm", it.roundTo(1)) }
                        m.armCm?.let { put("armCm", it.roundTo(1)) }
                        m.thighCm?.let { put("thighCm", it.roundTo(1)) }
                        m.bodyFatPercent?.let { put("bodyFatPercent", it.roundTo(1)) }
                        if (m.notes.isNotBlank()) put("notes", m.notes)
                    })
                }
            }
        }
        return result.toString()
    }
}
