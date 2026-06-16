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
    START_WORKOUT,         // klik → start treningu
    OPEN_PERIODIZATION_PLAN, // v1.18.0 — klik → ekran "Plan cyklu"
    LOG_RECOVERY           // v2.44.0 — klik → dialog oceny regeneracji (sen/stres)
}

@Singleton
class NotificationCenter @Inject constructor(
    private val recoveryScoreCalculator: RecoveryScoreCalculator,
    private val trainingLoadAnalyzer: TrainingLoadAnalyzer,
    private val phaseAnalyzer: TrainingPhaseAnalyzer,
    private val workoutDao: WorkoutDao,
    private val bodyDao: BodyMeasurementDao,
    // v1.18.0 — alert "deload kończy się za X dni"
    private val mesoDao: pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao,
    // v2.56.0 — sygnał „masz plan, ale nie potwierdzasz posiłków".
    private val adherenceCalc: pl.filebit.gymtracker.data.repository.AdherenceCalculator
) {
    suspend fun computeNotifications(): List<AppNotification> {
        val list = mutableListOf<AppNotification>()
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 3600 * 1000

        // Treningi + waga (raz, reużywane). v2.56.0: „zaangażowany" = treningi LUB waga LUB
        // historia regeneracji — bo user diety/wagi też ma dostawać reakcje (wcześniej gate
        // tylko na treningi dławił Coacha dla osób logujących wyłącznie dietę).
        val finishedWorkouts = runCatching { workoutDao.observeAllOnce() }.getOrNull()
            .orEmpty().filter { it.finishedAt != null }
        val bodyMeasurements = runCatching { bodyDao.getAllAsc() }.getOrDefault(emptyList())
        val isEngagedUser = finishedWorkouts.isNotEmpty() ||
            bodyMeasurements.any { it.weightKg != null }

        // === Recovery Score ===
        val score = runCatching { recoveryScoreCalculator.calculate() }.getOrNull()
        // v2.44.0: PROAKTYWNY sygnał luki danych. Zastępuje pasywną kartę „Zbieram dane X/7"
        // (która myliła „nowy user" z „przestał logować" i nie prosiła o akcję). Pokazujemy
        // TYLKO gdy: user JEST zaangażowany (ma treningi — świeżego instala nie nagabujemy)
        // ORAZ brak świeżego logu regeneracji (dziś/wczoraj). Jeden sygnał, niski priorytet —
        // arbiter Coacha zwija go pod ważniejsze reakcje (zero spamu).
        if (score != null && !score.isFresh && isEngagedUser) {
            val (gapTitle, gapMsg) = if (score.lastLogDaysAgo == null) {
                "🛌 Zacznij logować regenerację" to
                    "Oceń sen i samopoczucie (10 s) — nauczę się Twojej normy i zacznę pilnować regeneracji."
            } else {
                "🛌 Brak świeżych danych regeneracji" to
                    "Ostatni wpis ${score.lastLogDaysAgo} dni temu. Oceń dziś sen i stres, żebym mógł oceniać Twoją regenerację."
            }
            list.add(AppNotification(
                id = "recovery_log_gap",
                severity = NotificationSeverity.INFO,
                title = gapTitle,
                message = gapMsg,
                actionType = NotificationAction.LOG_RECOVERY
            ))
        }
        // Alerty o NISKIM recovery mają sens tylko przy ŚWIEŻYCH danych (inaczej oceniamy
        // miesięczny log jako „dziś"). Stąd gate na isFresh.
        if (score != null && score.isFresh) {
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

        // === v1.18.0 — Deload kończy się za ≤3 dni ===
        val activeMeso = runCatching { mesoDao.getActive() }.getOrNull()
        if (activeMeso != null && activeMeso.phase == pl.filebit.gymtracker.data.entity.MesocyclePhase.DELOAD) {
            val daysToEnd = activeMeso.daysRemaining(now)
            if (daysToEnd in 0..3) {
                val dayWord = when (daysToEnd) {
                    0 -> "kończy się dziś"
                    1 -> "kończy się jutro"
                    else -> "kończy się za $daysToEnd dni"
                }
                list.add(AppNotification(
                    id = "deload_ending_soon",
                    severity = NotificationSeverity.INFO,
                    title = "🔋 Deload $dayWord",
                    message = "Przygotuj plan akumulacji — AI zaproponuje konkrety w karcie 'AI Trener proponuje'.",
                    actionType = NotificationAction.OPEN_PERIODIZATION_PLAN
                ))
            }
        }

        // === Treningi: brak / dawno temu (v2.56.0: także gdy 0 treningów u zaangażowanego usera —
        // wcześniej 999 dni nie mieściło się w 5..30 i Coach milczał). ===
        val lastWorkout = finishedWorkouts.maxByOrNull { it.startedAt }
        if (isEngagedUser) {
            if (lastWorkout == null) {
                list.add(AppNotification(
                    id = "no_workout_ever",
                    severity = NotificationSeverity.INFO,
                    title = "🏋️ Zacznij logować treningi",
                    message = "Nie masz zalogowanych sesji. Zaloguj trening, żebym mógł pilnować progresji i regeneracji.",
                    actionType = NotificationAction.START_WORKOUT
                ))
            } else {
                val daysSinceLast = (now - lastWorkout.startedAt) / msPerDay
                if (daysSinceLast in 5..60) {
                    list.add(AppNotification(
                        id = "no_workout_${daysSinceLast}d",
                        severity = NotificationSeverity.INFO,
                        title = "🏋️ ${daysSinceLast} dni bez treningu",
                        message = "Czas wrócić do regularności. Nawet 30 min lekkiej sesji uruchomi z powrotem rytm.",
                        actionType = NotificationAction.START_WORKOUT
                    ))
                }
            }
        }

        // === Brak wagi >10 dni ===
        val lastWeight = bodyMeasurements.filter { it.weightKg != null }.maxByOrNull { it.date }
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

        // === v2.56.0: masz PLAN na dziś, ale nie potwierdzasz posiłków ===
        // Po południu (≥14:00) gdy są zaplanowane posiłki, a 0 oznaczonych jako zjedzone —
        // to powód, dla którego zgodność czyta 0% mimo pełnego planu (plan ≠ zjedzone).
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) >= 14) {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayLog = runCatching { adherenceCalc.getForDate(cal.timeInMillis) }.getOrNull()
            if (todayLog != null && todayLog.mealsPlannedCount > 0 && todayLog.mealsLoggedCount == 0) {
                list.add(AppNotification(
                    id = "meals_unconfirmed_today",
                    severity = NotificationSeverity.WARNING,
                    title = "🍽️ Potwierdź dzisiejsze posiłki",
                    message = "Masz plan na dziś (${todayLog.mealsPlannedCount} posiłki), ale 0 oznaczonych jako zjedzone. Oznacz, co zjadłeś — inaczej zgodność czyta 0% mimo pełnego planu.",
                    actionType = NotificationAction.NONE
                ))
            }
        }

        // v1.11.69: deduplikacja po actionType - max 1 notification per akcja (priorytet
        // CRITICAL > WARNING > INFO). Wczesniej Recovery + Phase + Stagnation moglyby
        // wszystkie wyslac APPLY_DELOAD jednoczesnie.
        val deduplicated = list
            .groupBy { it.actionType }
            .flatMap { (action, group) ->
                if (action == NotificationAction.NONE) {
                    // NONE moze pojawic sie wielokrotnie (rozne info/warning bez akcji)
                    group
                } else {
                    // Inne actiony: zostaw 1 z najwyzszym severity (CRITICAL=2, WARNING=1, INFO=0)
                    listOf(group.maxBy { it.severity.ordinal })
                }
            }
        // Sortuj: CRITICAL → WARNING → INFO
        return deduplicated.sortedWith(compareBy({ it.severity.ordinal * -1 }, { -it.timestamp }))
    }
}
