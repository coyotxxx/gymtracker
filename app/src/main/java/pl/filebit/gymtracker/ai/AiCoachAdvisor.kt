package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.Workout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.11.71 — AI Coach Advisor.
 *
 * Decyduje co user ma robić DZIŚ. Łączy sygnały z wszystkich analyzerów (faza,
 * gotowość, sen/HRV, recovery mięśni, plan na dziś, ostatni trening) w
 * **jedną decyzyjną poradę**.
 *
 * **Deterministic** — nie wywołuje API LLM (szybko + tanio + offline).
 *
 * Priorytet decyzji (od najsilniejszego sygnału):
 * 1. Health REST_RECOMMENDED → REST (sen <5h × 2 noce krytycznie)
 * 2. Wellbeing ≤2 w 2/3 ostatnich sesji → REST (przemęczenie)
 * 3. Phase=DELOAD → CAUTION (utrzymuj plan, niskie obciążenia)
 * 4. Readiness=REST → REST (kompozyt)
 * 5. Last workout <16h temu → REST (regeneracja)
 * 6. todaysPlan + Readiness=GOOD/PEAK → GO (trenuj plan)
 * 7. todaysPlan + Readiness=MODERATE → CAUTION (plan, ale lżej)
 * 8. brak todaysPlan, jest nextPlannedDay → INFO (rest day)
 * 9. brak planu → INFO (dodaj plan)
 */
data class CoachAdvice(
    val severity: AdviceSeverity,
    val title: String,         // "Dziś trenuj LEGS" / "Dziś rest"
    val reason: String,        // krótki kontekst (1-2 zdania)
    val ctaLabel: String? = null,  // np. "Otwórz plan" / "Zaloguj sen"
    val ctaAction: AdviceCta? = null
)

enum class AdviceSeverity { GO, CAUTION, REST, INFO }

enum class AdviceCta {
    OPEN_PLAN,
    OPEN_SETTINGS_AI,
    OPEN_BODY_MEASUREMENTS,
    NONE
}

/**
 * Pure function — computuje poradę z aktualnego stanu user'a.
 */
fun computeCoachAdvice(
    phase: TrainingPhase,
    readiness: TrainingReadiness?,
    health: HealthInsight?,
    muscleReport: MuscleRecoveryReport?,
    hasTodaysPlan: Boolean,
    todaysPlanLabel: String?,   // np. "PPL 4-dni — dzień 3 (LEGS)"
    nextPlannedDayLabel: String?,  // np. "Następnie: jutro PUSH"
    lastWorkout: Workout?,
    nowMs: Long = System.currentTimeMillis()
): CoachAdvice {
    val msPerHour = 3600_000L

    // 1. Health REST_RECOMMENDED — najsilniejszy sygnał
    if (health?.workoutAdjustment == WorkoutAdjustment.REST_RECOMMENDED) {
        return CoachAdvice(
            severity = AdviceSeverity.REST,
            title = "Dziś rest — sen krytyczny",
            reason = health.recommendation,
            ctaLabel = "Pomiar / regeneracja",
            ctaAction = AdviceCta.OPEN_BODY_MEASUREMENTS
        )
    }

    // 2. Wellbeing ≤2 w 2/3 ostatnich
    val recentLowWellbeing = (health == null || muscleReport == null) && false  // skip if no signal
    // Note: faktycznie wellbeing czytamy z lastWorkout i poprzednich, ale tutaj
    // tylko 1 lastWorkout — checking wellbeing bezpośrednio
    if (lastWorkout?.wellbeingRating != null && lastWorkout.wellbeingRating!! <= 2) {
        return CoachAdvice(
            severity = AdviceSeverity.REST,
            title = "Dziś odpuść",
            reason = "Niski wellbeing w ostatnim treningu (${lastWorkout.wellbeingRating}/5). Sygnał przemęczenia — daj sobie czas.",
            ctaLabel = null,
            ctaAction = null
        )
    }

    // 3. Phase=DELOAD → CAUTION
    if (phase == TrainingPhase.DELOAD) {
        return if (hasTodaysPlan) {
            CoachAdvice(
                severity = AdviceSeverity.CAUTION,
                title = "Dziś deload: ${todaysPlanLabel ?: "lekki trening"}",
                reason = "Tydzień deload aktywny — utrzymuj niskie obciążenia (RPE 6, krótszy rest). Po nim wracasz do akumulacji.",
                ctaLabel = "Otwórz plan",
                ctaAction = AdviceCta.OPEN_PLAN
            )
        } else {
            CoachAdvice(
                severity = AdviceSeverity.INFO,
                title = "Tydzień deload aktywny",
                reason = "Dziś plan przewiduje rest. " + (nextPlannedDayLabel ?: "Po deloadzie wrócisz do akumulacji."),
                ctaLabel = null,
                ctaAction = null
            )
        }
    }

    // 4. Readiness=REST
    if (readiness?.zone == ReadinessZone.REST) {
        return CoachAdvice(
            severity = AdviceSeverity.REST,
            title = "Dziś rest — niska gotowość",
            reason = "Training Readiness ${readiness.score}/100. " + readiness.recommendation,
            ctaLabel = null,
            ctaAction = null
        )
    }

    // 5. Last workout <16h temu — partie nie zregenerowane
    if (lastWorkout?.finishedAt != null) {
        val hoursSince = (nowMs - lastWorkout.finishedAt!!) / msPerHour
        if (hoursSince < 16) {
            return CoachAdvice(
                severity = AdviceSeverity.REST,
                title = "Dziś rest",
                reason = "Ostatni trening $hoursSince h temu — partie potrzebują regeneracji. Dziś light cardio Z1 lub odpuść.",
                ctaLabel = null,
                ctaAction = null
            )
        }
    }

    // 6. + 7. todaysPlan + Readiness
    if (hasTodaysPlan) {
        val zone = readiness?.zone
        return when (zone) {
            ReadinessZone.PEAK, ReadinessZone.GOOD, null -> CoachAdvice(
                severity = AdviceSeverity.GO,
                title = "Dziś trenuj: ${todaysPlanLabel ?: "plan"}",
                reason = readiness?.let { "Gotowość ${it.score}/100 — ${it.zone.name}." }
                    ?: "Twój plan przewiduje trening na dziś.",
                ctaLabel = "Otwórz plan",
                ctaAction = AdviceCta.OPEN_PLAN
            )
            ReadinessZone.MODERATE -> CoachAdvice(
                severity = AdviceSeverity.CAUTION,
                title = "Dziś trenuj lżej: ${todaysPlanLabel ?: "plan"}",
                reason = "Gotowość ${readiness.score}/100 — moderate. ${readiness.recommendation}",
                ctaLabel = "Otwórz plan",
                ctaAction = AdviceCta.OPEN_PLAN
            )
            ReadinessZone.REST -> CoachAdvice(
                // already handled above, but defensive
                severity = AdviceSeverity.REST,
                title = "Dziś rest",
                reason = readiness.recommendation,
                ctaLabel = null,
                ctaAction = null
            )
        }
    }

    // 8. brak todaysPlan, jest nextPlannedDay
    if (nextPlannedDayLabel != null) {
        return CoachAdvice(
            severity = AdviceSeverity.INFO,
            title = "Dziś rest day",
            reason = "Twój plan przewiduje dziś dzień bez treningu. $nextPlannedDayLabel",
            ctaLabel = null,
            ctaAction = null
        )
    }

    // 9. brak planu
    return CoachAdvice(
        severity = AdviceSeverity.INFO,
        title = "Brak aktywnego planu",
        reason = "Dodaj plan treningowy lub wygeneruj go przez Asystenta AI.",
        ctaLabel = "Plany",
        ctaAction = AdviceCta.OPEN_PLAN
    )
}

@Singleton
class AiCoachAdvisor @Inject constructor()
