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
 * v1.18.0 — limity dla wywołań AI tools (rate + size).
 */
data class ToolLimits(
    val maxWorkouts: Int = 30,
    val maxEvents: Int = 100,
    val maxRollups: Int = 52,
    val maxWeeksBack: Int = 52,
    val maxBodyHistory: Int = 365,
    /** Max rozmiar wyniku JSON — chroni context AI przed overflow. */
    val maxResultSizeBytes: Int = 500 * 1024,
    /** Max wywołań tools per minuta — chroni przed runaway AI loop. */
    val maxCallsPerMinute: Int = 30
)

/**
 * v1.11.67 — Handler wykonujący narzędzia (tools) AI.
 *
 * Odpowiada na żądanie tool_use z Anthropic API → wykonuje query do DB → zwraca
 * wynik jako JSON string (do tool_result).
 *
 * v1.18.0: rozszerzony zestaw tools periodyzacyjnych + rate limit + size cap.
 */
@Singleton
class AiToolHandler @Inject constructor(
    private val statsCacheService: StatsCacheService,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val eventDao: TrainingEventDao,
    private val weeklyDao: WeeklyRollupDao,
    private val monthlyDao: MonthlyRollupDao,
    private val quarterlyDao: QuarterlyRollupDao,
    // v1.15.0: propose_periodization_action tool
    private val mesoDao: pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao,
    private val pendingDecisionDao: pl.filebit.gymtracker.data.db.dao.PendingPeriodizationDecisionDao
) {
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val json = Json { encodeDefaults = false }

    private val limits = ToolLimits()
    // v1.18.0 — sliding window timestamps (epoch ms) ostatnich wywołań tools.
    private val callTimestamps = ArrayDeque<Long>()

    private fun Double.roundTo(decimals: Int): Double {
        if (isNaN() || isInfinite()) return 0.0
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }

    /**
     * Wykonuje tool po nazwie z argumentami JSON. Zwraca wynik jako JSON string.
     *
     * v1.18.0: dodano rate limit (30 calls/min sliding window) i size cap (500KB).
     */
    suspend fun execute(toolName: String, input: JsonObject): String {
        // === RATE LIMIT (v1.18.0) — sliding window 60s ===
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000L
        while (callTimestamps.isNotEmpty() && callTimestamps.first() < windowStart) {
            callTimestamps.removeFirst()
        }
        if (callTimestamps.size >= limits.maxCallsPerMinute) {
            return """{"error":"rate_limit_exceeded","limit_per_minute":${limits.maxCallsPerMinute},"retry_after_seconds":${
                ((callTimestamps.first() + 60_000L - now) / 1000).coerceAtLeast(1)
            }}"""
        }
        callTimestamps.addLast(now)

        val result = when (toolName) {
            "get_workouts" -> execGetWorkouts(input)
            "get_events" -> execGetEvents(input)
            "get_rollups" -> execGetRollups(input)
            "get_exercise_history" -> execGetExerciseHistory(input)
            "get_body_history" -> execGetBodyHistory(input)
            "propose_periodization_action" -> execProposePeriodizationAction(input)  // v1.15.0
            // v1.18.0
            "propose_deload" -> execProposeDeload(input)
            "transition_phase" -> execTransitionPhase(input)
            "schedule_next_cycle" -> execScheduleNextCycle(input)
            "get_pending_decisions" -> execGetPendingDecisions(input)
            else -> "{\"error\":\"Unknown tool: $toolName\"}"
        }

        // === SIZE CAP (v1.18.0) — 500KB JSON ===
        if (result.toByteArray(Charsets.UTF_8).size > limits.maxResultSizeBytes) {
            return """{"error":"result_too_large","size_kb":${result.length / 1024},"max_kb":${limits.maxResultSizeBytes / 1024},"hint":"Zwęź zapytanie — mniejszy zakres dat lub mniej rekordów."}"""
        }
        return result
    }

    /**
     * v1.15.0 — zapisuje propozycję AI jako PendingPeriodizationDecision (status=PENDING).
     * NIE wykonuje akcji w bazie — user musi explicit zaakceptować przez UI.
     */
    private suspend fun execProposePeriodizationAction(input: JsonObject): String {
        val action = input["action"]?.jsonPrimitive?.content
            ?: return """{"error":"missing action"}"""
        val recommendedPhase = input["recommended_next_phase"]?.jsonPrimitive?.content
            ?: return """{"error":"missing recommended_next_phase"}"""
        val plannedStartStr = input["planned_start_date"]?.jsonPrimitive?.content
            ?: return """{"error":"missing planned_start_date"}"""
        val durationWeeks = input["planned_duration_weeks"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return """{"error":"missing or invalid planned_duration_weeks"}"""
        val confidence = input["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return """{"error":"missing or invalid confidence"}"""
        val reasoning = input["reasoning"]?.jsonPrimitive?.content
            ?: return """{"error":"missing reasoning"}"""
        val volumeMod = input["volume_modifier"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val intensityMod = input["intensity_modifier"]?.jsonPrimitive?.content?.toDoubleOrNull()

        // Validate phase
        val phase = runCatching {
            pl.filebit.gymtracker.data.entity.MesocyclePhase.valueOf(recommendedPhase)
        }.getOrNull() ?: return """{"error":"invalid phase: $recommendedPhase"}"""

        // Validate date
        val plannedStartMs = runCatching { df.parse(plannedStartStr)!!.time }.getOrNull()
            ?: return """{"error":"invalid planned_start_date format (expected YYYY-MM-DD)"}"""

        val currentMeso = mesoDao.getActive()
            ?: return """{"error":"no active mesocycle — cannot propose transition"}"""

        // Algorithm proposal JSON (z computed PeriodizationOrchestrator — może być pominięte jeśli
        // tool wywołany manualnie z AiTrainer; w ProactiveAiCheckWorker będzie passed jako kontekst)
        val algorithmProposalJson = """{"fromPhase":"${currentMeso.phase}","plannedStartFromAlgorithm":${currentMeso.plannedEndDateMs}}"""

        val aiDecisionJson = buildJsonObject {
            put("action", action)
            put("recommended_next_phase", recommendedPhase)
            put("planned_start_date", plannedStartStr)
            put("planned_duration_weeks", durationWeeks)
            put("confidence", confidence)
            volumeMod?.let { put("volume_modifier", it) }
            intensityMod?.let { put("intensity_modifier", it) }
        }.toString()

        val decision = pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision(
            currentMesoId = currentMeso.id,
            algorithmProposalJson = algorithmProposalJson,
            aiDecisionJson = aiDecisionJson,
            aiReasoning = reasoning,
            confidence = confidence.coerceIn(0.0, 1.0),
            status = pl.filebit.gymtracker.data.entity.DecisionStatus.PENDING
        )
        val id = pendingDecisionDao.upsert(decision)

        return buildJsonObject {
            put("success", true)
            put("decision_id", id)
            put("status", "PENDING")
            put("message", "Propozycja zapisana. User zobaczy na Home (karta 'AI TRENER PROPONUJE') i może [Zastosuj] lub [Odrzuć].")
        }.toString()
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

    // === v1.18.0 — propose_deload ===
    /**
     * Zapisuje PendingPeriodizationDecision z konkretną propozycją deloadu.
     * Tool RO — user akceptuje przez UI.
     */
    private suspend fun execProposeDeload(input: JsonObject): String {
        val reason = input["reason"]?.jsonPrimitive?.content
            ?: return """{"error":"missing reason"}"""
        val startDate = input["start_date"]?.jsonPrimitive?.content
            ?: return """{"error":"missing start_date"}"""
        val durationDays = input["duration_days"]?.jsonPrimitive?.content?.toIntOrNull()
            ?.coerceIn(4, 14)
            ?: return """{"error":"missing or invalid duration_days (4-14)"}"""
        val volumeReduction = input["volume_reduction_pct"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.coerceIn(0.2, 0.5)
            ?: return """{"error":"missing or invalid volume_reduction_pct (0.2-0.5)"}"""
        val intensityReduction = input["intensity_reduction_pct"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.coerceIn(0.0, 0.2)
            ?: 0.1
        val reasoning = input["reasoning"]?.jsonPrimitive?.content
            ?: return """{"error":"missing reasoning"}"""

        val startMs = runCatching { df.parse(startDate)!!.time }.getOrNull()
            ?: return """{"error":"invalid start_date format (expected YYYY-MM-DD)"}"""

        val currentMeso = mesoDao.getActive()
            ?: return """{"error":"no active mesocycle"}"""

        val aiDecisionJson = buildJsonObject {
            put("action", "PROPOSE_DELOAD")
            put("recommended_next_phase", "DELOAD")
            put("planned_start_date", startDate)
            put("planned_duration_days", durationDays)
            put("volume_reduction_pct", volumeReduction)
            put("intensity_reduction_pct", intensityReduction)
            put("reason", reason)
        }.toString()

        val algorithmProposalJson = """{"trigger":"$reason","fromPhase":"${currentMeso.phase}"}"""

        val decision = pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision(
            currentMesoId = currentMeso.id,
            algorithmProposalJson = algorithmProposalJson,
            aiDecisionJson = aiDecisionJson,
            aiReasoning = reasoning,
            confidence = 0.85,
            status = pl.filebit.gymtracker.data.entity.DecisionStatus.PENDING
        )
        val id = pendingDecisionDao.upsert(decision)

        return buildJsonObject {
            put("success", true)
            put("decision_id", id)
            put("status", "PENDING")
            put("start_date", startDate)
            put("duration_days", durationDays)
            put("volume_reduction_pct", volumeReduction)
            put("message", "Propozycja deloadu zapisana. User zobaczy na Home.")
        }.toString()
    }

    // === v1.18.0 — transition_phase ===
    /**
     * Zapisuje PendingPeriodizationDecision z przejściem fazy.
     */
    private suspend fun execTransitionPhase(input: JsonObject): String {
        val fromPhase = input["from_phase"]?.jsonPrimitive?.content
            ?: return """{"error":"missing from_phase"}"""
        val toPhase = input["to_phase"]?.jsonPrimitive?.content
            ?: return """{"error":"missing to_phase"}"""
        val startDate = input["start_date"]?.jsonPrimitive?.content
            ?: return """{"error":"missing start_date"}"""
        val durationWeeks = input["duration_weeks"]?.jsonPrimitive?.content?.toIntOrNull()
            ?.coerceIn(1, 6)
            ?: return """{"error":"missing or invalid duration_weeks (1-6)"}"""
        val confidence = input["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?: return """{"error":"missing or invalid confidence"}"""
        val reasoning = input["reasoning"]?.jsonPrimitive?.content
            ?: return """{"error":"missing reasoning"}"""

        val toPhaseEnum = runCatching {
            pl.filebit.gymtracker.data.entity.MesocyclePhase.valueOf(toPhase)
        }.getOrNull() ?: return """{"error":"invalid to_phase: $toPhase"}"""

        runCatching { df.parse(startDate)!! }
            ?: return """{"error":"invalid start_date format"}"""

        val currentMeso = mesoDao.getActive()
            ?: return """{"error":"no active mesocycle"}"""

        if (currentMeso.phase.name != fromPhase) {
            return """{"error":"from_phase ($fromPhase) doesn't match active mesocycle phase (${currentMeso.phase.name})"}"""
        }

        val aiDecisionJson = buildJsonObject {
            put("action", "TRANSITION_PHASE")
            put("from_phase", fromPhase)
            put("recommended_next_phase", toPhaseEnum.name)
            put("planned_start_date", startDate)
            put("planned_duration_weeks", durationWeeks)
            put("confidence", confidence)
        }.toString()

        val algorithmProposalJson = """{"fromPhase":"${currentMeso.phase}","plannedEndDateMs":${currentMeso.plannedEndDateMs}}"""

        val decision = pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision(
            currentMesoId = currentMeso.id,
            algorithmProposalJson = algorithmProposalJson,
            aiDecisionJson = aiDecisionJson,
            aiReasoning = reasoning,
            confidence = confidence,
            status = pl.filebit.gymtracker.data.entity.DecisionStatus.PENDING
        )
        val id = pendingDecisionDao.upsert(decision)

        return buildJsonObject {
            put("success", true)
            put("decision_id", id)
            put("status", "PENDING")
            put("from_phase", fromPhase)
            put("to_phase", toPhaseEnum.name)
            put("start_date", startDate)
            put("duration_weeks", durationWeeks)
        }.toString()
    }

    // === v1.18.0 — schedule_next_cycle ===
    /**
     * Tworzy listę PendingPeriodizationDecision (po jednej per faza) ze status=PENDING.
     * User widzi wszystkie naraz na Home i może akceptować/odrzucać sekwencyjnie.
     */
    private suspend fun execScheduleNextCycle(input: JsonObject): String {
        val startDate = input["start_date"]?.jsonPrimitive?.content
            ?: return """{"error":"missing start_date"}"""
        val phasesJson = input["phases"]
            ?: return """{"error":"missing phases array"}"""
        val goal = input["goal"]?.jsonPrimitive?.content
            ?: return """{"error":"missing goal"}"""
        val reasoning = input["reasoning"]?.jsonPrimitive?.content
            ?: return """{"error":"missing reasoning"}"""

        val phasesArray = runCatching {
            (phasesJson as kotlinx.serialization.json.JsonArray).map { el ->
                val obj = el.jsonObject
                val phase = obj["phase"]!!.jsonPrimitive.content
                val weeks = obj["duration_weeks"]!!.jsonPrimitive.content.toInt()
                phase to weeks
            }
        }.getOrNull() ?: return """{"error":"invalid phases — expected array of {phase, duration_weeks}"}"""

        if (phasesArray.size < 2 || phasesArray.size > 5) {
            return """{"error":"phases must contain 2-5 entries, got ${phasesArray.size}"}"""
        }

        val startMs = runCatching { df.parse(startDate)!!.time }.getOrNull()
            ?: return """{"error":"invalid start_date format"}"""

        val currentMeso = mesoDao.getActive()
            ?: return """{"error":"no active mesocycle — cannot schedule next cycle from current state"}"""

        // Walidacja faz
        for ((phaseName, _) in phasesArray) {
            runCatching { pl.filebit.gymtracker.data.entity.MesocyclePhase.valueOf(phaseName) }
                .getOrNull() ?: return """{"error":"invalid phase: $phaseName"}"""
        }

        // Tworzymy PendingDecision dla pierwszej fazy (kolejne phases dodaje sekwencyjnie po akceptacji)
        // — alternatywnie 1 PendingDecision dla całego planu z phases w JSON.
        val phasesJsonString = buildJsonArray {
            phasesArray.forEach { (p, w) ->
                add(buildJsonObject {
                    put("phase", p)
                    put("duration_weeks", w)
                })
            }
        }.toString()

        val aiDecisionJson = buildJsonObject {
            put("action", "SCHEDULE_NEXT_CYCLE")
            put("planned_start_date", startDate)
            put("goal", goal)
            put("phases_json", phasesJsonString)
            put("total_weeks", phasesArray.sumOf { it.second })
        }.toString()

        val algorithmProposalJson = """{"fromPhase":"${currentMeso.phase}","cycleGoal":"$goal","totalWeeks":${phasesArray.sumOf { it.second }}}"""

        val decision = pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision(
            currentMesoId = currentMeso.id,
            algorithmProposalJson = algorithmProposalJson,
            aiDecisionJson = aiDecisionJson,
            aiReasoning = reasoning,
            confidence = 0.8,
            status = pl.filebit.gymtracker.data.entity.DecisionStatus.PENDING
        )
        val id = pendingDecisionDao.upsert(decision)

        return buildJsonObject {
            put("success", true)
            put("decision_id", id)
            put("status", "PENDING")
            put("start_date", startDate)
            put("total_weeks", phasesArray.sumOf { it.second })
            put("phases_count", phasesArray.size)
        }.toString()
    }

    // === v1.18.0 — get_pending_decisions ===
    /**
     * Zwraca wszystkie oczekujące PendingPeriodizationDecision (status=PENDING).
     * Read-only — AI sprawdza przed nową propozycją.
     */
    private suspend fun execGetPendingDecisions(input: JsonObject): String {
        val pending = pendingDecisionDao.getPending()
        return buildJsonObject {
            put("count", pending.size)
            putJsonArray("decisions") {
                pending.forEach { d ->
                    add(buildJsonObject {
                        put("id", d.id)
                        put("created_at", df.format(Date(d.createdAt)))
                        put("status", d.status.name)
                        put("ai_decision_json", d.aiDecisionJson)
                        put("ai_reasoning", d.aiReasoning)
                        put("confidence", d.confidence)
                    })
                }
            }
        }.toString()
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
