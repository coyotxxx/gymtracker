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
    /** v2.0.0 — canonical slug ćwiczenia. Lookup deterministyczny po slug. */
    val exerciseSlug: String,
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
    /** Pull-up slug — używany do specjalnego liczenia ratio (BW + dodany ciężar) / BW. */
    const val SLUG_PULL_UP = "pull-up"

    private val ALL = listOf(
        // barbell-bench-press = "Wyciskanie sztangi na ławce poziomej"
        StrengthStandard(
            exerciseSlug = "barbell-bench-press",
            maleRatios = doubleArrayOf(0.5, 0.75, 1.25, 1.75, 2.25),
            femaleRatios = doubleArrayOf(0.25, 0.50, 0.75, 1.10, 1.50)
        ),
        // barbell-back-squat = "Przysiad ze sztangą"
        StrengthStandard(
            exerciseSlug = "barbell-back-squat",
            maleRatios = doubleArrayOf(0.75, 1.25, 1.75, 2.25, 2.75),
            femaleRatios = doubleArrayOf(0.50, 0.85, 1.25, 1.75, 2.25)
        ),
        // barbell-deadlift = "Martwy ciąg klasyczny"
        StrengthStandard(
            exerciseSlug = "barbell-deadlift",
            maleRatios = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0),
            femaleRatios = doubleArrayOf(0.65, 1.0, 1.5, 2.0, 2.5)
        ),
        // barbell-overhead-press = "Wyciskanie sztangi nad głowę (stojąc)"
        StrengthStandard(
            exerciseSlug = "barbell-overhead-press",
            maleRatios = doubleArrayOf(0.35, 0.55, 0.85, 1.20, 1.55),
            femaleRatios = doubleArrayOf(0.20, 0.35, 0.55, 0.80, 1.05)
        ),
        // barbell-bent-over-row = "Wiosłowanie sztangą w opadzie"
        StrengthStandard(
            exerciseSlug = "barbell-bent-over-row",
            maleRatios = doubleArrayOf(0.5, 0.75, 1.0, 1.5, 2.0),
            femaleRatios = doubleArrayOf(0.30, 0.50, 0.75, 1.10, 1.50)
        ),
        // pull-up = "Podciąganie nachwytem". Ratio = (BW + dodany ciężar) / BW (1.0 = pure BW).
        StrengthStandard(
            exerciseSlug = SLUG_PULL_UP,
            maleRatios = doubleArrayOf(1.0, 1.15, 1.35, 1.65, 2.05),
            femaleRatios = doubleArrayOf(0.85, 1.0, 1.15, 1.40, 1.75)
        )
    )

    fun forExerciseSlug(slug: String?): StrengthStandard? =
        if (slug == null) null else ALL.firstOrNull { it.exerciseSlug == slug }

    fun all(): List<StrengthStandard> = ALL

    /**
     * Fallback display label gdy w bazie nie ma jeszcze ćwiczenia po danym slugu
     * (np. canonical bootstrap nie ukończył). Używane w UI i AI context.
     */
    fun displayLabel(slug: String): String = when (slug) {
        "barbell-bench-press" -> "Wyciskanie sztangi leżąc"
        "barbell-back-squat" -> "Przysiad ze sztangą"
        "barbell-deadlift" -> "Martwy ciąg klasyczny"
        "barbell-overhead-press" -> "Wyciskanie żołnierskie (OHP)"
        "barbell-bent-over-row" -> "Wiosłowanie sztangą"
        SLUG_PULL_UP -> "Podciąganie nachwytem"
        else -> slug
    }
}
