package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.ai.AiDecisionExplainer
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.DietAdjustmentDao
import pl.filebit.gymtracker.data.entity.DietAdjustment
import pl.filebit.gymtracker.util.AdjustmentAction
import pl.filebit.gymtracker.util.AdjustmentDecision
import pl.filebit.gymtracker.util.CalorieAdjustmentEngine
import pl.filebit.gymtracker.util.Confidence
import pl.filebit.gymtracker.util.SafetyGuard
import pl.filebit.gymtracker.util.SafetyResult
import pl.filebit.gymtracker.util.TrendAnalyzer
import pl.filebit.gymtracker.util.computeDailyGoal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper łączący TrendAnalyzer + AdherenceCalculator + CalorieAdjustmentEngine
 * + SafetyGuard. Wywoływany z UI ('Sprawdź czy nie czas na korektę?')
 * lub PeriodicWorker (co 14 dni).
 */
@Singleton
class AutoAdjustmentService @Inject constructor(
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val dietPrefs: DietPreferences,
    private val bodyDao: BodyMeasurementDao,
    private val adherenceCalc: AdherenceCalculator,
    private val adjustmentDao: DietAdjustmentDao,
    private val aiExplainer: AiDecisionExplainer,
    private val recoveryRepo: RecoveryRepository,
    private val recoveryAnalyzer: RecoveryAnalyzer,
    private val hydrationRepo: HydrationRepository,
    private val hydrationCalc: HydrationCalculator,
    private val activityRepo: ActivityRepository,
    private val neatAnalyzer: NeatAnalyzer
) {
    suspend fun analyzeNow(): AdjustmentDecision {
        val profile = profileRepo.get()
        val dietProfile = dietProfileRepo.get()
        val config = dietPrefs.load()
        val latestWeight = runCatching { bodyDao.getLatest()?.weightKg }.getOrNull()
        val currentGoal = computeDailyGoal(
            profile = profile,
            manualKcalOverride = config.manualKcal,
            customDeficit = config.customDeficit,
            dietProfile = dietProfile,
            latestMeasuredWeightKg = latestWeight
        )

        // Trend wagi z BodyMeasurement
        val measurements = bodyDao.getAllAsc()
        val trend = TrendAnalyzer.analyze(measurements)

        // Adherence summaries
        val adherence14 = adherenceCalc.avgAdherenceLastDays(14)
        val adherence7 = adherenceCalc.avgAdherenceLastDays(7)

        // Recovery snapshot z 7 dni RecoveryLog
        val recoveryLogs = recoveryRepo.getLast7Days()
        val recovery = recoveryAnalyzer.analyze(recoveryLogs)

        // Hydration adherence z 14 dni
        val weight = profile.bodyweightKg ?: 75.0
        val hydrationAdherence = hydrationRepo.avgAdherenceLastNDays(weight, 14, hydrationCalc)

        // NEAT snapshot z 30 dni kroków
        val activityLogs = activityRepo.getRecent(days = 30)
        val recentSteps = activityLogs.sortedByDescending { it.dateMs }.map { it.steps }
        val baselineSteps = dietProfile?.avgStepsPerDay ?: 0
        val neat = neatAnalyzer.analyze(recentSteps, baselineSteps)

        // v1.24.23 fix #1: Diet break detection.
        // cutDurationDays = od dietProfile.onboardingCompletedAt (lub 0 jeśli brak).
        // daysSinceLastRefeed = od ostatniego DietAdjustment z actionCode=REFEED_DAY.
        val nowMs = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000
        val cutDurationDays = dietProfile?.onboardingCompletedAt?.let { start ->
            ((nowMs - start) / dayMs).toInt().coerceAtLeast(0)
        } ?: 0
        val recentAdjustments = runCatching { adjustmentDao.getRecent(20) }.getOrDefault(emptyList())
        val daysSinceLastRefeed = recentAdjustments
            .firstOrNull { it.actionCode == "REFEED_DAY" && it.applied }
            ?.let { ((nowMs - it.dateMs) / dayMs).toInt().coerceAtLeast(0) }
            ?: 999

        // Silnik regułowy z pełnym kontekstem
        val raw = CalorieAdjustmentEngine.analyze(
            profile = profile,
            currentKcal = currentGoal.kcal,
            weightTrend = trend,
            adherence14d = adherence14,
            adherence7d = adherence7,
            recovery = recovery,
            hydrationAdherencePct = hydrationAdherence,
            neat = neat,
            cutDurationDays = cutDurationDays,
            daysSinceLastRefeed = daysSinceLastRefeed,
            // v1.27.0: cel diety steruje strategią korekt — wszystkie 8 typów
            // (wcześniej silnik znał tylko 4 cele wagowe, ignorował RECOMP itd.)
            dietGoal = dietProfile?.goalType
        )

        // SafetyGuard cap — v1.24.23: przekazujemy BMR z dietProfile (jeśli dostępne)
        // żeby min kcal floor uwzględnił najmniejszą bezpieczną wartość dla tego usera.
        val bmrForGuard = dietProfile?.let { dp ->
            val maleConst = if (profile.gender == pl.filebit.gymtracker.data.entity.Gender.MALE) 5.0 else -161.0
            (10.0 * weight + 6.25 * dp.heightCm - 5.0 * dp.ageYears + maleConst).toInt()
        }
        if (raw.action == AdjustmentAction.DECREASE_KCAL || raw.action == AdjustmentAction.INCREASE_KCAL) {
            val safetyResult = SafetyGuard.validateKcal(raw.newKcal, profile, weight, bmrForGuard)
            if (safetyResult is SafetyResult.Block) {
                return raw.copy(
                    action = AdjustmentAction.HOLD,
                    kcalDeltaProposed = 0,
                    newKcal = currentGoal.kcal,
                    explanation = raw.explanation + "\n\n⚠ Korekta zablokowana przez SafetyGuard: ${safetyResult.message}",
                    confidence = Confidence.LOW,
                    warnings = raw.warnings + safetyResult.message
                )
            }
        }

        return raw
    }

    /**
     * Zapisuje DietAdjustment przed zatwierdzeniem (preview) — z aiExplanation
     * (jeśli dostępne AI). Zwraca id, użyjemy do apply/dismiss.
     */
    suspend fun savePreview(decision: AdjustmentDecision): Long {
        val profile = profileRepo.get()
        val measurements = bodyDao.getAllAsc()
        val trend = TrendAnalyzer.analyze(measurements)
        val ad14 = adherenceCalc.avgAdherenceLastDays(14)
        val aiExpl = runCatching { aiExplainer.rewriteForUser(decision, profile) }.getOrNull()

        val adj = DietAdjustment(
            dateMs = System.currentTimeMillis(),
            oldKcal = decision.newKcal - decision.kcalDeltaProposed,
            newKcal = decision.newKcal,
            actionCode = decision.action.name,
            reason = decision.reason,
            engineExplanation = decision.explanation,
            aiExplanation = aiExpl,
            confidence = decision.confidence.name,
            snapshotAvgWeight7d = trend.avg7Days,
            snapshotAvgWeight14d = trend.avg14Days,
            snapshotSlopeKgPerWeek = trend.slopeKgPerWeek,
            snapshotAdherence14dKcal = ad14.avgKcalPct,
            snapshotAdherence14dProtein = ad14.avgProteinPct,
            snapshotWorkoutsDone = ad14.workoutsDone,
            snapshotWorkoutsPlanned = ad14.workoutsPlanned,
            applied = false
        )
        return adjustmentDao.insert(adj)
    }

    /**
     * Aplikuje decyzję — zapisuje do DietPreferences (customDeficit) + oznacza
     * DietAdjustment jako applied. Audytowalne.
     */
    suspend fun applyDecision(adjustmentId: Long) {
        val adj = adjustmentDao.getById(adjustmentId) ?: return
        if (adj.applied) return  // idempotent
        if (adj.actionCode == AdjustmentAction.HOLD.name ||
            adj.actionCode == AdjustmentAction.SIMPLIFY_PLAN.name ||
            adj.actionCode == AdjustmentAction.NEEDS_MORE_DATA.name) {
            // Tylko log — nic nie zmieniamy w DietPreferences
            adjustmentDao.update(adj.copy(applied = true, appliedAt = System.currentTimeMillis()))
            return
        }
        val config = dietPrefs.load()
        val profile = profileRepo.get()
        val dietProfile = dietProfileRepo.get()
        val latestWeight = runCatching { bodyDao.getLatest()?.weightKg }.getOrNull()
        val baseline = computeDailyGoal(
            profile = profile,
            customDeficit = null,
            dietProfile = dietProfile,
            latestMeasuredWeightKg = latestWeight
        )
        val newDeficit = adj.newKcal - baseline.breakdown.tdeeKcal
        dietPrefs.save(config.copy(customDeficit = newDeficit, manualKcal = null))
        adjustmentDao.update(adj.copy(applied = true, appliedAt = System.currentTimeMillis()))
    }

    suspend fun dismissAdjustment(adjustmentId: Long) {
        val adj = adjustmentDao.getById(adjustmentId) ?: return
        adjustmentDao.update(adj.copy(dismissed = true))
    }

    suspend fun getRecent(limit: Int = 50) = adjustmentDao.getRecent(limit)
}
