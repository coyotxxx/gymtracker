package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Powiadomienia w aplikacji — zbierają live alerty z wszystkich analyzerów
 * w jedno miejsce (dzwonek na HomeScreen).
 *
 * **Filozofia:** powiadomienia są COMPUTED on-demand z aktualnego stanu, nie
 * zapisywane do DB. Każdy alert ma:
 *  - severity (INFO/WARNING/CRITICAL) — kolor + sortowanie
 *  - actionType — co user może kliknąć (np. APPLY_DELOAD, AUDIT_PLAN, ADD_WEIGHT)
 *  - timestamp — gdy alert się aktywował (do sortowania)
 *
 * Anti-pattern: nie spamować — pokazujemy max 5-10 najistotniejszych. Lepiej 3
 * krytyczne niż 20 ogólnych.
 */
data class AppNotification(
    val id: String,                          // unikalny key dla LazyColumn
    val severity: NotificationSeverity,
    val title: String,
    val message: String,
    val actionType: NotificationAction,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationSeverity { INFO, WARNING, CRITICAL }

enum class NotificationAction {
    NONE,                  // tylko info
    APPLY_DELOAD,          // klik → confirm dialog z deloadem
    AUDIT_PLAN,            // klik → /plans/X audyt
    ADD_WEIGHT,            // klik → measurements
    SEND_HEALTH_SCREEN,    // klik → health/screenshot
    START_WORKOUT          // klik → start treningu
}

@Singleton
class NotificationCenter @Inject constructor(
    private val recoveryScoreCalculator: RecoveryScoreCalculator,
    private val trainingLoadAnalyzer: TrainingLoadAnalyzer,
    private val phaseAnalyzer: TrainingPhaseAnalyzer,
    private val workoutDao: WorkoutDao,
    private val bodyDao: BodyMeasurementDao
) {
    suspend fun computeNotifications(): List<AppNotification> {
        val list = mutableListOf<AppNotification>()
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 3600 * 1000

        // === Recovery Score ===
        val score = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        if (score != null) {
            when (score.zone) {
                RecoveryZone.RED -> {
                    if (score.daysBelowThreshold >= 5) {
                        list.add(AppNotification(
                            id = "recovery_critical",
                            severity = NotificationSeverity.CRITICAL,
                            title = "🔴 Krytyczna regeneracja",
                            message = "Recovery Score ${score.score}/100 poniżej 50 od ${score.daysBelowThreshold} dni. Trwały trend — czas na deload.",
                            actionType = NotificationAction.APPLY_DELOAD
                        ))
                    } else if (score.maturity != DataMaturity.LEARNING) {
                        list.add(AppNotification(
                            id = "recovery_low_today",
                            severity = NotificationSeverity.WARNING,
                            title = "❌ Niski recovery dziś",
                            message = "Score ${score.score}/100. Jeśli trend się utrzyma 5+ dni, zaproponuję deload.",
                            actionType = NotificationAction.NONE
                        ))
                    }
                }
                RecoveryZone.ORANGE -> {
                    if (score.daysBelowThreshold >= 5) {
                        list.add(AppNotification(
                            id = "recovery_orange_persistent",
                            severity = NotificationSeverity.WARNING,
                            title = "🟠 Słaba regeneracja od ${score.daysBelowThreshold} dni",
                            message = "Recovery Score ${score.score}/100 utrzymuje się nisko. Rozważ delikatny deload (-15-20%).",
                            actionType = NotificationAction.APPLY_DELOAD
                        ))
                    }
                }
                else -> { /* GREEN/YELLOW — brak alertów */ }
            }
        }

        // === ACWR (training load) ===
        val load = runCatching { trainingLoadAnalyzer.analyze() }.getOrNull()
        if (load != null && load.isReliable) {
            when (load.zone) {
                LoadZone.RISKY -> list.add(AppNotification(
                    id = "acwr_risky",
                    severity = NotificationSeverity.CRITICAL,
                    title = "⚠️ Wysokie ryzyko kontuzji",
                    message = "ACWR ${"%.2f".format(load.acwr)} (norma 0.8-1.3). Tonaż 7d zbyt wysoki vs twoja zwykła średnia. Zalecana redukcja -20%.",
                    actionType = NotificationAction.AUDIT_PLAN
                ))
                LoadZone.OVERREACHING -> list.add(AppNotification(
                    id = "acwr_overreach",
                    severity = NotificationSeverity.WARNING,
                    title = "🔶 Tonaż podwyższony",
                    message = "ACWR ${"%.2f".format(load.acwr)}. Tydzień ciężki — rozważ lżejszy następny.",
                    actionType = NotificationAction.NONE
                ))
                else -> { /* OPTIMAL/DETRAINING — brak alertu */ }
            }
        }

        // === Faza cyklu — NEEDS_DELOAD ===
        val phase = runCatching { phaseAnalyzer.analyze() }.getOrNull()
        if (phase?.phase == TrainingPhase.NEEDS_DELOAD) {
            list.add(AppNotification(
                id = "phase_needs_deload",
                severity = NotificationSeverity.WARNING,
                title = "🔋 Czas na deload",
                message = "${phase.weeksSinceLastDeload} tyg bez deloadu. Tydzień lekki pozwoli CNS się zregenerować.",
                actionType = NotificationAction.APPLY_DELOAD
            ))
        }

        // === Brak treningów >5 dni ===
        val finished = workoutDao.observeAllOnce().filter { it.finishedAt != null }
        val lastWorkout = finished.maxByOrNull { it.startedAt }
        val daysSinceLast = lastWorkout?.let { (now - it.startedAt) / msPerDay } ?: 999L
        if (daysSinceLast in 5..30) {
            list.add(AppNotification(
                id = "no_workout_${daysSinceLast}d",
                severity = NotificationSeverity.INFO,
                title = "🏋️ ${daysSinceLast} dni bez treningu",
                message = "Czas wrócić do regularności. Nawet 30 min lekkiej sesji uruchomi z powrotem rytm.",
                actionType = NotificationAction.START_WORKOUT
            ))
        }

        // === Brak wagi >10 dni ===
        val lastWeight = bodyDao.getAllAsc().filter { it.weightKg != null }.maxByOrNull { it.date }
        val daysSinceWeight = lastWeight?.let { (now - it.date) / msPerDay } ?: 999L
        if (daysSinceWeight in 10..60) {
            list.add(AppNotification(
                id = "no_weight_${daysSinceWeight}d",
                severity = NotificationSeverity.INFO,
                title = "⚖️ ${daysSinceWeight} dni bez wagi",
                message = "Trend wagi pomaga AI dostosować kalorie. Zważ się i wgraj zrzut.",
                actionType = NotificationAction.SEND_HEALTH_SCREEN
            ))
        }

        // Sortuj: CRITICAL → WARNING → INFO
        return list.sortedWith(compareBy({ it.severity.ordinal * -1 }, { -it.timestamp }))
    }
}
