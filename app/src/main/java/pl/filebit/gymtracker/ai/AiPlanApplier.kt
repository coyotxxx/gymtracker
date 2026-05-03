package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.repository.PlanRepository
import javax.inject.Inject
import javax.inject.Singleton

data class AiPlanProposal(
    val name: String,
    val description: String,
    val daysOfWeek: List<Int>,
    val days: List<AiPlanDay>
) {
    val totalExercises: Int get() = days.sumOf { it.exercises.size }
}

data class AiPlanDay(
    val dayOfWeek: Int,
    val exercises: List<AiPlanExercise>
)

data class AiPlanExercise(
    val exerciseName: String,
    val sets: List<AiPlanSet>,
    val supersetGroup: String? = null
)

data class AiPlanSet(
    val reps: Int,
    val weightKg: Double? = null,
    val restSec: Int? = null
)

/**
 * Wynik walidacji proposal — zanim zaaplikujemy, sprawdzamy czy każde ćwiczenie
 * AI pokrywa się z biblioteką. Jeśli AI wymyśliło nazwę spoza listy, user dostaje
 * ostrzeżenie zamiast cichego pominięcia.
 */
data class ProposalValidation(
    val totalExercises: Int,
    val matchedCount: Int,
    val unmatchedNames: List<String>,
    val totalDays: Int
) {
    val isFullyValid: Boolean get() = unmatchedNames.isEmpty() && totalExercises > 0
    val matchRatio: Float get() = if (totalExercises == 0) 0f
        else matchedCount.toFloat() / totalExercises
}

@Singleton
class AiPlanApplier @Inject constructor(
    private val planRepo: PlanRepository,
    private val exerciseDao: ExerciseDao
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Wykrywa blok ```json {…}``` w odpowiedzi AI i parsuje do AiPlanProposal.
     */
    fun extractProposal(aiText: String): AiPlanProposal? {
        // Fenced block ```json ... ``` — najczęściej. Jeśli AI zapomni, próbujemy
        // też goły JSON (pierwszy balansowany blok {...}).
        val fenced = Regex("```json\\s*([\\s\\S]+?)```", RegexOption.MULTILINE).find(aiText)
        val raw = fenced?.groupValues?.get(1)?.trim()
            ?: extractBalancedJson(aiText)
            ?: run {
                Log.w("AiPlanApplier", "extractProposal: no JSON block found")
                return null
            }
        return runCatching { parse(raw) }
            .onFailure { Log.e("AiPlanApplier", "parse failed: ${it.message}\n--- raw ---\n$raw", it) }
            .getOrNull()
    }

    /** Znajduje pierwszy obiekt JSON {…} z poprawnie zbalansowanymi nawiasami. */
    private fun extractBalancedJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun parse(jsonText: String): AiPlanProposal {
        val obj = json.parseToJsonElement(jsonText).jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: "Plan AI"
        val description = obj["description"]?.jsonPrimitive?.content ?: ""
        val days = obj["daysOfWeek"]?.jsonArray
            ?.map { it.jsonPrimitive.int } ?: emptyList()

        val daysList = mutableListOf<AiPlanDay>()
        // Format A: { exercises: [{ dayOfWeek, exerciseName, sets [{reps, weightKg, restSec}] }, ...] }
        // Format B: { days: [{ dayOfWeek, exercises: [...] }, ...] }
        obj["days"]?.jsonArray?.forEach { dayEl ->
            val day = dayEl.jsonObject
            val dow = day["dayOfWeek"]?.jsonPrimitive?.int ?: 1
            val exs = day["exercises"]?.jsonArray?.mapNotNull { parseExercise(it.jsonObject) } ?: emptyList()
            daysList += AiPlanDay(dow, exs)
        }
        if (daysList.isEmpty()) {
            // Format A — flat list of exercises with dayOfWeek field
            obj["exercises"]?.jsonArray?.let { arr ->
                val grouped = arr.mapNotNull {
                    val o = it.jsonObject
                    val dow = o["dayOfWeek"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    val ex = parseExercise(o) ?: return@mapNotNull null
                    dow to ex
                }.groupBy({ it.first }, { it.second })
                grouped.forEach { (dow, list) -> daysList += AiPlanDay(dow, list) }
            }
        }
        return AiPlanProposal(name, description, days, daysList.sortedBy { it.dayOfWeek })
    }

    private fun parseExercise(o: kotlinx.serialization.json.JsonObject): AiPlanExercise? {
        val name = o["exerciseName"]?.jsonPrimitive?.content
            ?: o["name"]?.jsonPrimitive?.content
            ?: return null
        val sets = o["sets"]?.jsonArray?.map { sEl ->
            val s = sEl.jsonObject
            AiPlanSet(
                reps = s["reps"]?.jsonPrimitive?.intOrNull ?: 8,
                weightKg = s["weightKg"]?.jsonPrimitive?.doubleOrNull,
                restSec = s["restSec"]?.jsonPrimitive?.intOrNull
            )
        } ?: emptyList()
        // jsonPrimitive.content na JsonNull zwraca string "null" — trzeba odfiltrować
        val sg = o["supersetGroup"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return AiPlanExercise(name, sets, sg)
    }

    /**
     * Sprawdza ile ćwiczeń z proposal pokrywa się z biblioteką (case-insensitive
     * equals → startsWith → contains). Każde nieznalezione cytuje na liście —
     * user widzi co AI zmyśliło i decyduje: cofnij apply / akceptuj częściowy.
     */
    suspend fun validateProposal(proposal: AiPlanProposal): ProposalValidation {
        val library = exerciseDao.getAll()
        val total = proposal.totalExercises
        val unmatched = mutableListOf<String>()
        var matched = 0
        proposal.days.forEach { day ->
            day.exercises.forEach { aiEx ->
                val match = library.firstOrNull {
                    it.name.equals(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.startsWith(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.contains(aiEx.exerciseName, ignoreCase = true)
                }
                if (match != null) matched++
                else unmatched += aiEx.exerciseName
            }
        }
        return ProposalValidation(
            totalExercises = total,
            matchedCount = matched,
            unmatchedNames = unmatched.distinct(),
            totalDays = proposal.days.size
        )
    }

    /**
     * Nadpisuje istniejący plan zachowując jego id (kasuje wszystkie PlanExercise+sety,
     * potem wstawia nowe na podstawie proposal). Nazwa, opis i daysOfWeek są aktualizowane.
     */
    suspend fun replaceExistingPlan(planId: Long, proposal: AiPlanProposal): Result<Unit> = runCatching {
        val current = planRepo.getPlan(planId)
            ?: throw NoSuchElementException("Plan $planId nie istnieje")
        planRepo.updatePlan(
            current.copy(
                name = proposal.name.ifBlank { current.name },
                daysOfWeek = if (proposal.daysOfWeek.isNotEmpty()) proposal.daysOfWeek
                else proposal.days.map { it.dayOfWeek }.distinct(),
                notes = proposal.description.ifBlank { current.notes }
            )
        )
        planRepo.deleteAllPlanExercises(planId)
        insertExercises(planId, proposal)
    }

    /**
     * Tworzy nowy plan jako kopię proposal (z nazwą '<oryginalny> (poprawiony)').
     * Oryginalny plan pozostaje nietknięty.
     */
    suspend fun applyAsCopy(originalPlanId: Long, proposal: AiPlanProposal): Result<Long> = runCatching {
        val original = planRepo.getPlan(originalPlanId)
        val baseName = proposal.name.ifBlank { original?.name ?: "Plan AI" }
        val finalName = if (baseName.contains("poprawiony")) baseName else "$baseName (poprawiony)"
        val newPlanId = planRepo.upsertPlan(
            TrainingPlan(
                name = finalName,
                daysOfWeek = if (proposal.daysOfWeek.isNotEmpty()) proposal.daysOfWeek
                else proposal.days.map { it.dayOfWeek }.distinct(),
                notes = proposal.description,
                createdByAi = true
            )
        )
        insertExercises(newPlanId, proposal)
        newPlanId
    }

    /** Helper — wspólny dla applyProposal/replaceExistingPlan/applyAsCopy. */
    private suspend fun insertExercises(planId: Long, proposal: AiPlanProposal) {
        val library = exerciseDao.getAll()
        proposal.days.forEach { day ->
            day.exercises.forEachIndexed { idx, aiEx ->
                val match = library.firstOrNull {
                    it.name.equals(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.startsWith(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.contains(aiEx.exerciseName, ignoreCase = true)
                } ?: run {
                    Log.w("AiPlanApplier", "no match for exercise '${aiEx.exerciseName}'")
                    return@forEachIndexed
                }
                val peId = planRepo.upsertPlanExercise(
                    PlanExercise(
                        planId = planId,
                        exerciseId = match.id,
                        dayOfWeek = day.dayOfWeek,
                        orderIndex = idx,
                        supersetGroup = aiEx.supersetGroup
                    )
                )
                aiEx.sets.forEachIndexed { sIdx, set ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = sIdx + 1,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            restSeconds = set.restSec
                        )
                    )
                }
            }
        }
    }

    /**
     * Mapuje exerciseName na Exercise.id (case-insensitive prefix matching) i tworzy plan.
     * Zwraca id utworzonego TrainingPlan lub error.
     */
    suspend fun applyProposal(proposal: AiPlanProposal): Result<Long> = runCatching {
        Log.d("AiPlanApplier", "applyProposal name='${proposal.name}' daysOfWeek=${proposal.daysOfWeek} days=${proposal.days.size} totalEx=${proposal.totalExercises}")
        val library = exerciseDao.getAll()
        Log.d("AiPlanApplier", "exercise library size=${library.size}")
        val planId = planRepo.upsertPlan(
            TrainingPlan(
                name = proposal.name.ifBlank { "Plan AI" },
                daysOfWeek = if (proposal.daysOfWeek.isNotEmpty()) proposal.daysOfWeek
                else proposal.days.map { it.dayOfWeek }.distinct(),
                notes = proposal.description,
                createdByAi = true
            )
        )
        Log.d("AiPlanApplier", "plan created id=$planId")

        proposal.days.forEach { day ->
            day.exercises.forEachIndexed { idx, aiEx ->
                val match = library.firstOrNull {
                    it.name.equals(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.startsWith(aiEx.exerciseName, ignoreCase = true)
                } ?: library.firstOrNull {
                    it.name.contains(aiEx.exerciseName, ignoreCase = true)
                } ?: run {
                    Log.w("AiPlanApplier", "no match for exercise '${aiEx.exerciseName}'")
                    return@forEachIndexed
                }

                val peId = planRepo.upsertPlanExercise(
                    PlanExercise(
                        planId = planId,
                        exerciseId = match.id,
                        dayOfWeek = day.dayOfWeek,
                        orderIndex = idx,
                        supersetGroup = aiEx.supersetGroup
                    )
                )
                aiEx.sets.forEachIndexed { sIdx, set ->
                    planRepo.upsertPlanSet(
                        PlanExerciseSet(
                            planExerciseId = peId,
                            setNumber = sIdx + 1,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            restSeconds = set.restSec
                        )
                    )
                }
            }
        }
        planId
    }
}
