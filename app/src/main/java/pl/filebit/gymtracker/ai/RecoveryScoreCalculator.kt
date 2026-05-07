package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.RecoveryLogDao
import pl.filebit.gymtracker.data.entity.RecoveryLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Status zbierania danych — pierwsze 14 dni to "quiet mode" (tylko zbieramy, brak alertów).
 */
enum class DataMaturity {
    /** <7 dni danych. Pokazujemy tylko "Zbieram dane (X/14)" */
    LEARNING,
    /** 7-13 dni. Łagodne sugestie, brak czerwonych alertów. */
    EARLY,
    /** 14-27 dni. Pełen Recovery Score, sugestie. */
    READY,
    /** 28+ dni. Pełna analiza z Acute:Chronic, długoterminowe trendy. */
    MATURE
}

/**
 * Recovery Score 0-100 inspirowany WHOOP/Garmin/Oura.
 *
 * **Filozofia:**
 * - Bazuje na PERSONAL baseline (rolling 28-day median per metric), nie na ogólnych progach
 * - Nie alarmuje na pojedyncze odchylenia — tylko na trwałe trendy
 * - Pierwsze 14 dni = quiet mode (zbieramy dane, brak konkretnych sugestii)
 *
 * **Skład score (max 100 pkt):**
 * - Sen vs baseline: 30 pkt (najważniejsze)
 * - HRV vs baseline: 30 pkt
 * - Tętno spoczynkowe vs baseline: 20 pkt (im niższe tym lepiej)
 * - Subiektywne sygnały (stres + sen quality): 20 pkt
 */
data class RecoveryScore(
    val score: Int,                  // 0-100
    val maturity: DataMaturity,
    val daysOfData: Int,
    /** Przyczyny niskiego score — prezentowane userowi. */
    val factors: List<RecoveryFactor>,
    /** Personal baselines obliczone z 28d. Pomocne w UI ("twoja norma"). */
    val baselines: PersonalBaselines,
    /** Stała dni z rzędu obniżonego score (dla decyzji o trwałym trendzie). */
    val daysBelowThreshold: Int
) {
    val zone: RecoveryZone get() = when {
        score >= 75 -> RecoveryZone.GREEN
        score >= 50 -> RecoveryZone.YELLOW
        score >= 30 -> RecoveryZone.ORANGE
        else -> RecoveryZone.RED
    }
}

enum class RecoveryZone { GREEN, YELLOW, ORANGE, RED }

data class PersonalBaselines(
    val sleepHours: Double? = null,         // mediana 28d
    val hrvMs: Double? = null,
    val restingHrBpm: Double? = null,
    val sleepQuality: Double? = null         // 1-5
)

data class RecoveryFactor(
    val label: String,                       // "Sen poniżej twojej normy"
    val severity: RecoveryFactorSeverity,
    val deltaText: String                    // "-22% vs baseline 4.5h"
)

enum class RecoveryFactorSeverity { INFO, WARNING, CRITICAL }

@Singleton
class RecoveryScoreCalculator @Inject constructor(
    private val recoveryLogDao: RecoveryLogDao
) {
    companion object {
        const val MATURE_THRESHOLD_DAYS = 28
        const val READY_THRESHOLD_DAYS = 14
        const val EARLY_THRESHOLD_DAYS = 7
        /** Liczba dni z score <50 z rzędu by uznać za trwały trend. */
        const val PERSISTENT_TREND_DAYS = 5
    }

    suspend fun calculate(): RecoveryScore {
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 3600 * 1000
        val cutoff28 = now - 28 * msPerDay
        val logs = runCatching {
            recoveryLogDao.getSince(cutoff28, 28)
        }.getOrNull().orEmpty().sortedByDescending { it.dateMs }

        val baselines = computeBaselines(logs)
        val daysOfData = logs.size
        val maturity = when {
            daysOfData < EARLY_THRESHOLD_DAYS -> DataMaturity.LEARNING
            daysOfData < READY_THRESHOLD_DAYS -> DataMaturity.EARLY
            daysOfData < MATURE_THRESHOLD_DAYS -> DataMaturity.READY
            else -> DataMaturity.MATURE
        }

        // Quiet mode — za mało danych, score "neutralny" 75 (zielony) ale UI pokaże LEARNING
        if (maturity == DataMaturity.LEARNING) {
            return RecoveryScore(
                score = 75,
                maturity = maturity,
                daysOfData = daysOfData,
                factors = emptyList(),
                baselines = baselines,
                daysBelowThreshold = 0
            )
        }

        // Najnowszy log — to jest "dziś"
        val today = logs.firstOrNull()
        val factors = mutableListOf<RecoveryFactor>()

        // Sen — 30 pkt
        val sleepPoints = scoreSleep(today, baselines.sleepHours, factors)

        // HRV — 30 pkt
        val hrvPoints = scoreHrv(today, baselines.hrvMs, factors)

        // Tętno spoczynkowe — 20 pkt (im wyższe tym gorzej)
        val hrPoints = scoreRestingHr(today, baselines.restingHrBpm, factors)

        // Subiektywne — 20 pkt (stres + jakość snu)
        val subjPoints = scoreSubjective(today, factors)

        val totalScore = (sleepPoints + hrvPoints + hrPoints + subjPoints).coerceIn(0, 100)

        // Liczba dni z rzędu poniżej 50 — sygnał trwałego problemu
        val daysBelowThreshold = countConsecutiveLowScores(logs, baselines)

        return RecoveryScore(
            score = totalScore,
            maturity = maturity,
            daysOfData = daysOfData,
            factors = factors,
            baselines = baselines,
            daysBelowThreshold = daysBelowThreshold
        )
    }

    private fun computeBaselines(logs: List<RecoveryLog>): PersonalBaselines {
        val sleeps = logs.mapNotNull { it.sleepHours }.filter { it > 0 }
        val hrvs = logs.mapNotNull { it.hrvMs }.filter { it > 0 }
        val hrs = logs.mapNotNull { it.restingHeartRateBpm?.toDouble() }.filter { it > 0 }
        val sleepQs = logs.mapNotNull { it.sleepQuality?.toDouble() }
        return PersonalBaselines(
            sleepHours = median(sleeps),
            hrvMs = median(hrvs),
            restingHrBpm = median(hrs),
            sleepQuality = median(sleepQs)
        )
    }

    private fun scoreSleep(
        today: RecoveryLog?,
        baseline: Double?,
        factors: MutableList<RecoveryFactor>
    ): Int {
        val sleep = today?.sleepHours ?: return 22  // brak danych = ~75% z 30
        if (baseline == null) return 22
        val delta = sleep - baseline
        val deltaPct = (delta / baseline) * 100
        return when {
            // Sen >= twoja baseline → max
            deltaPct >= -5 -> 30
            // -10% — drobne
            deltaPct >= -10 -> {
                factors.add(RecoveryFactor(
                    "Sen lekko poniżej twojej normy",
                    RecoveryFactorSeverity.INFO,
                    "%.1fh vs baseline %.1fh (%.0f%%)".format(sleep, baseline, deltaPct)
                ))
                25
            }
            // -20% — uwaga
            deltaPct >= -20 -> {
                factors.add(RecoveryFactor(
                    "Sen poniżej twojej normy",
                    RecoveryFactorSeverity.WARNING,
                    "%.1fh vs baseline %.1fh (%.0f%%)".format(sleep, baseline, deltaPct)
                ))
                17
            }
            // -30% — krytyczne
            deltaPct >= -30 -> {
                factors.add(RecoveryFactor(
                    "Sen znacznie poniżej normy",
                    RecoveryFactorSeverity.CRITICAL,
                    "%.1fh vs baseline %.1fh (%.0f%%)".format(sleep, baseline, deltaPct)
                ))
                10
            }
            else -> {
                factors.add(RecoveryFactor(
                    "Sen drastycznie poniżej normy",
                    RecoveryFactorSeverity.CRITICAL,
                    "%.1fh vs baseline %.1fh (%.0f%%)".format(sleep, baseline, deltaPct)
                ))
                5
            }
        }
    }

    private fun scoreHrv(
        today: RecoveryLog?,
        baseline: Double?,
        factors: MutableList<RecoveryFactor>
    ): Int {
        val hrv = today?.hrvMs ?: return 22
        if (baseline == null || baseline <= 0) return 22
        val deltaPct = ((hrv - baseline) / baseline) * 100
        return when {
            deltaPct >= -5 -> 30
            deltaPct >= -15 -> {
                factors.add(RecoveryFactor(
                    "HRV poniżej baseline",
                    RecoveryFactorSeverity.INFO,
                    "%.0f ms vs baseline %.0f ms (%.0f%%)".format(hrv, baseline, deltaPct)
                ))
                23
            }
            deltaPct >= -25 -> {
                factors.add(RecoveryFactor(
                    "HRV znacząco niskie — CNS pod stresem",
                    RecoveryFactorSeverity.WARNING,
                    "%.0f ms vs baseline %.0f ms (%.0f%%)".format(hrv, baseline, deltaPct)
                ))
                15
            }
            else -> {
                factors.add(RecoveryFactor(
                    "HRV bardzo niskie — możliwe przemęczenie/choroba",
                    RecoveryFactorSeverity.CRITICAL,
                    "%.0f ms vs baseline %.0f ms (%.0f%%)".format(hrv, baseline, deltaPct)
                ))
                5
            }
        }
    }

    private fun scoreRestingHr(
        today: RecoveryLog?,
        baseline: Double?,
        factors: MutableList<RecoveryFactor>
    ): Int {
        val hr = today?.restingHeartRateBpm?.toDouble() ?: return 15
        if (baseline == null) return 15
        val delta = hr - baseline   // dodatni = gorzej
        return when {
            delta <= 2 -> 20
            delta <= 7 -> {
                factors.add(RecoveryFactor(
                    "Tętno spoczynkowe lekko podwyższone",
                    RecoveryFactorSeverity.INFO,
                    "%.0f bpm vs baseline %.0f bpm (+%.0f)".format(hr, baseline, delta)
                ))
                15
            }
            delta <= 12 -> {
                factors.add(RecoveryFactor(
                    "Tętno spoczynkowe podwyższone — sygnał stresu",
                    RecoveryFactorSeverity.WARNING,
                    "%.0f bpm vs baseline %.0f bpm (+%.0f)".format(hr, baseline, delta)
                ))
                8
            }
            else -> {
                factors.add(RecoveryFactor(
                    "Tętno znacznie podwyższone — możliwa choroba/przemęczenie",
                    RecoveryFactorSeverity.CRITICAL,
                    "%.0f bpm vs baseline %.0f bpm (+%.0f)".format(hr, baseline, delta)
                ))
                3
            }
        }
    }

    private fun scoreSubjective(
        today: RecoveryLog?,
        factors: MutableList<RecoveryFactor>
    ): Int {
        if (today == null) return 15
        var pts = 20
        today.stressLevel?.let { stress ->
            if (stress >= 4) {
                factors.add(RecoveryFactor(
                    "Wysoki stres",
                    RecoveryFactorSeverity.WARNING,
                    "$stress/5"
                ))
                pts -= 8
            } else if (stress >= 3) {
                pts -= 4
            }
        }
        today.sleepQuality?.let { q ->
            if (q <= 2) {
                factors.add(RecoveryFactor(
                    "Niska jakość snu",
                    RecoveryFactorSeverity.WARNING,
                    "$q/5"
                ))
                pts -= 6
            }
        }
        return max(0, pts)
    }

    private fun countConsecutiveLowScores(
        logs: List<RecoveryLog>,
        baselines: PersonalBaselines
    ): Int {
        // Uproszczone: licz dni z rzędu gdzie sen <80% baseline LUB tętno >+10
        var count = 0
        for (log in logs) {
            val isLow = (baselines.sleepHours != null && log.sleepHours != null &&
                log.sleepHours < baselines.sleepHours * 0.8) ||
                (baselines.restingHrBpm != null && log.restingHeartRateBpm != null &&
                    log.restingHeartRateBpm > baselines.restingHrBpm + 10)
            if (isLow) count++ else break
        }
        return count
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
}
