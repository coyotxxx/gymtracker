package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
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
    private val adherenceCalc: AdherenceCalculator
) {
    suspend fun analyzeNow(): AdjustmentDecision {
        val profile = profileRepo.get()
        val dietProfile = dietProfileRepo.get()
        val config = dietPrefs.load()
        val currentGoal = computeDailyGoal(
            profile = profile,
            manualKcalOverride = config.manualKcal,
            customDeficit = config.customDeficit,
            dietProfile = dietProfile
        )

        // Trend wagi z BodyMeasurement
        val measurements = bodyDao.getAllAsc()
        val trend = TrendAnalyzer.analyze(measurements)

        // Adherence summaries
        val adherence14 = adherenceCalc.avgAdherenceLastDays(14)
        val adherence7 = adherenceCalc.avgAdherenceLastDays(7)

        // Silnik regułowy
        val raw = CalorieAdjustmentEngine.analyze(
            profile = profile,
            currentKcal = currentGoal.kcal,
            weightTrend = trend,
            adherence14d = adherence14,
            adherence7d = adherence7
        )

        // SafetyGuard cap
        if (raw.action == AdjustmentAction.DECREASE_KCAL || raw.action == AdjustmentAction.INCREASE_KCAL) {
            val weight = profile.bodyweightKg ?: 75.0
            val safetyResult = SafetyGuard.validateKcal(raw.newKcal, profile, weight)
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
     * Aplikuje decyzję do DietPreferences (zmienia customDeficit żeby compute
     * dał nową wartość kcal). NIE wywołane automatycznie — tylko z UI po
     * zatwierdzeniu przez usera.
     */
    suspend fun applyDecision(decision: AdjustmentDecision) {
        if (decision.kcalDeltaProposed == 0) return
        val config = dietPrefs.load()
        // Ustaw nowy customDeficit żeby (TDEE + deficit) = newKcal
        val profile = profileRepo.get()
        val dietProfile = dietProfileRepo.get()
        val currentGoal = computeDailyGoal(
            profile = profile,
            customDeficit = null, // bez override żeby zobaczyć baseline
            dietProfile = dietProfile
        )
        val newDeficit = decision.newKcal - currentGoal.breakdown.tdeeKcal
        dietPrefs.save(config.copy(customDeficit = newDeficit, manualKcal = null))
    }
}
