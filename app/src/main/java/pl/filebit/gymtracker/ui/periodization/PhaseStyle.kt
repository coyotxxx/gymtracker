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

/** Polski label fazy (dla user-facing UI). */
fun MesocyclePhase.labelPl(): String = when (this) {
    MesocyclePhase.ACCUMULATION -> "Akumulacja"
    MesocyclePhase.INTENSIFICATION -> "Intensyfikacja"
    MesocyclePhase.DELOAD -> "Deload"
    MesocyclePhase.PEAKING -> "Peaking"
    MesocyclePhase.RECOVERY -> "Recovery"
}

/** Krótki opis fazy (dla tooltipów / sublabel). */
fun MesocyclePhase.shortDesc(): String = when (this) {
    MesocyclePhase.ACCUMULATION -> "Budowanie objętości"
    MesocyclePhase.INTENSIFICATION -> "Budowanie siły"
    MesocyclePhase.DELOAD -> "Regeneracja CNS"
    MesocyclePhase.PEAKING -> "Test maksów"
    MesocyclePhase.RECOVERY -> "Pełna regeneracja"
}
