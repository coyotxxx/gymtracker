package pl.filebit.gymtracker.data.strength

import pl.filebit.gymtracker.data.entity.Gender

enum class StrengthLevel {
    BELOW_BEGINNER,    // poniżej progu początkującego
    BEGINNER,          // ~50% percentyl wśród początkujących
    NOVICE,            // ~lifter trenujący 6 mies.
    INTERMEDIATE,      // ~2 lata systematycznie
    ADVANCED,          // ~5 lat
    ELITE              // top ~5%
}

/**
 * Standardy 1RM jako multiplikator wagi ciała (mnożone przez bodyweight).
 * Bazują na uśrednionych ratiosach StrengthLevel.org / ExRx.net (Bodyweight × ratio).
 *
 * Każdy stadnard to 5 progów: BEGINNER, NOVICE, INTERMEDIATE, ADVANCED, ELITE.
 * Pozycja 0 = próg BEGINNER (poniżej tego = BELOW_BEGINNER).
 */
data class StrengthStandard(
    val exerciseNamePrefix: String,    // dopasowanie po początku nazwy ćwiczenia (case-insensitive)
    val maleRatios: DoubleArray,        // [BEGINNER, NOVICE, INTERMEDIATE, ADVANCED, ELITE]
    val femaleRatios: DoubleArray
) {
    fun ratiosFor(gender: Gender): DoubleArray =
        if (gender == Gender.FEMALE) femaleRatios else maleRatios

    /**
     * Klasyfikuje 1RM/bodyweight ratio na poziom.
     */
    fun classify(ratio: Double, gender: Gender): StrengthLevel {
        val r = ratiosFor(gender)
        return when {
            ratio < r[0] -> StrengthLevel.BELOW_BEGINNER
            ratio < r[1] -> StrengthLevel.BEGINNER
            ratio < r[2] -> StrengthLevel.NOVICE
            ratio < r[3] -> StrengthLevel.INTERMEDIATE
            ratio < r[4] -> StrengthLevel.ADVANCED
            else -> StrengthLevel.ELITE
        }
    }
}

/**
 * Wbudowany zestaw standardów dla 6 głównych podnoszeń.
 * Dopasowanie po prefix nazwy ćwiczenia (z seedu) — case-insensitive.
 */
object StrengthStandards {
    private val ALL = listOf(
        StrengthStandard(
            exerciseNamePrefix = "Wyciskanie sztangi leżąc",
            maleRatios = doubleArrayOf(0.5, 0.75, 1.25, 1.75, 2.25),
            femaleRatios = doubleArrayOf(0.25, 0.50, 0.75, 1.10, 1.50)
        ),
        StrengthStandard(
            exerciseNamePrefix = "Przysiad ze sztangą",
            maleRatios = doubleArrayOf(0.75, 1.25, 1.75, 2.25, 2.75),
            femaleRatios = doubleArrayOf(0.50, 0.85, 1.25, 1.75, 2.25)
        ),
        StrengthStandard(
            exerciseNamePrefix = "Martwy ciąg klasyczny",
            maleRatios = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0),
            femaleRatios = doubleArrayOf(0.65, 1.0, 1.5, 2.0, 2.5)
        ),
        StrengthStandard(
            exerciseNamePrefix = "Wyciskanie żołnierskie",
            maleRatios = doubleArrayOf(0.35, 0.55, 0.85, 1.20, 1.55),
            femaleRatios = doubleArrayOf(0.20, 0.35, 0.55, 0.80, 1.05)
        ),
        StrengthStandard(
            exerciseNamePrefix = "Wiosłowanie sztangą",
            maleRatios = doubleArrayOf(0.5, 0.75, 1.0, 1.5, 2.0),
            femaleRatios = doubleArrayOf(0.30, 0.50, 0.75, 1.10, 1.50)
        ),
        // Pull-up: ratio = (BW + dodany ciężar) / BW. 1.0 = pure BW.
        StrengthStandard(
            exerciseNamePrefix = "Podciąganie nachwytem",
            maleRatios = doubleArrayOf(1.0, 1.15, 1.35, 1.65, 2.05),
            femaleRatios = doubleArrayOf(0.85, 1.0, 1.15, 1.40, 1.75)
        )
    )

    fun forExerciseName(name: String): StrengthStandard? =
        ALL.firstOrNull { name.startsWith(it.exerciseNamePrefix, ignoreCase = true) }

    fun all(): List<StrengthStandard> = ALL
}
