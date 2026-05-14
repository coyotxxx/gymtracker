package pl.filebit.gymtracker.ui.periodization

import androidx.compose.ui.graphics.Color
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.ui.theme.AccumulationBlue
import pl.filebit.gymtracker.ui.theme.DeloadGreen
import pl.filebit.gymtracker.ui.theme.IntensificationOrange
import pl.filebit.gymtracker.ui.theme.PeakingPurple
import pl.filebit.gymtracker.ui.theme.RecoveryTeal

/**
 * v1.16.0 — pomocnik stylów UI dla MesocyclePhase.
 *
 * Konsystentny mapping fazy → kolor + emoji + label PL.
 * Używany przez: PeriodizationPlanScreen, MesocycleCard, TrainingPhaseCard (w przyszłych release'ach).
 */

/** Kolor akcentu dla fazy. */
fun MesocyclePhase.color(): Color = when (this) {
    MesocyclePhase.ACCUMULATION -> AccumulationBlue
    MesocyclePhase.INTENSIFICATION -> IntensificationOrange
    MesocyclePhase.DELOAD -> DeloadGreen
    MesocyclePhase.PEAKING -> PeakingPurple
    MesocyclePhase.RECOVERY -> RecoveryTeal
}

/** Emoji ikona dla fazy. */
fun MesocyclePhase.emoji(): String = when (this) {
    MesocyclePhase.ACCUMULATION -> "📈"
    MesocyclePhase.INTENSIFICATION -> "⚡"
    MesocyclePhase.DELOAD -> "🛌"
    MesocyclePhase.PEAKING -> "🎯"
    MesocyclePhase.RECOVERY -> "🌱"
}

// v1.20.2 — labelPl() przeniesiony do enum MesocyclePhase w data/entity/TrainingMesocycle.kt
// (single source of truth). Tutaj zostają tylko UI-specific helpers: color, emoji, shortDesc, shortLabelPl.

/** Krótki opis fazy (dla tooltipów / sublabel). */
fun MesocyclePhase.shortDesc(): String = when (this) {
    MesocyclePhase.ACCUMULATION -> "Budowanie objętości"
    MesocyclePhase.INTENSIFICATION -> "Budowanie siły"
    MesocyclePhase.DELOAD -> "Regeneracja CNS"
    MesocyclePhase.PEAKING -> "Test maksów"
    MesocyclePhase.RECOVERY -> "Pełna regeneracja"
}

/**
 * v1.20.1 — krótki skrót dla badges / pill UI (max 6 znaków).
 * Centralizacja zamiast hardcoded w HistoryScreen.
 */
fun MesocyclePhase.shortLabelPl(): String = when (this) {
    // v1.24.50: PEAK / DELOAD → polskie skróty
    MesocyclePhase.ACCUMULATION -> "AKUM"
    MesocyclePhase.INTENSIFICATION -> "INTEN"
    MesocyclePhase.DELOAD -> "LŻEJ"      // tydzień lżejszy / regeneracyjny
    MesocyclePhase.PEAKING -> "SZCZYT"   // peaking
    MesocyclePhase.RECOVERY -> "REGEN"
}
