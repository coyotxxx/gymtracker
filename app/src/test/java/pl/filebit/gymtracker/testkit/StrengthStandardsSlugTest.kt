package pl.filebit.gymtracker.testkit

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import pl.filebit.gymtracker.data.strength.StrengthStandards

/**
 * v2.2.0 — testy lookup po canonical slug w StrengthStandards.
 */
class StrengthStandardsSlugTest {

    @Test
    fun `all 6 base standards have correct canonical slugs`() {
        val slugs = StrengthStandards.all().map { it.exerciseSlug }
        assertEquals(
            listOf(
                "barbell-bench-press",
                "barbell-back-squat",
                "barbell-deadlift",
                "barbell-overhead-press",
                "barbell-bent-over-row",
                "pull-up"
            ),
            slugs
        )
    }

    @Test
    fun `forExerciseSlug returns correct standard for valid slug`() {
        val bench = StrengthStandards.forExerciseSlug("barbell-bench-press")
        assertNotNull(bench)
        assertEquals("barbell-bench-press", bench!!.exerciseSlug)
    }

    @Test
    fun `forExerciseSlug returns null for unknown slug`() {
        assertNull(StrengthStandards.forExerciseSlug("unknown-slug"))
        assertNull(StrengthStandards.forExerciseSlug(null))
    }

    @Test
    fun `displayLabel returns Polish friendly names`() {
        assertEquals("Wyciskanie sztangi leżąc", StrengthStandards.displayLabel("barbell-bench-press"))
        assertEquals("Przysiad ze sztangą", StrengthStandards.displayLabel("barbell-back-squat"))
        assertEquals("Martwy ciąg klasyczny", StrengthStandards.displayLabel("barbell-deadlift"))
        assertEquals("Podciąganie nachwytem", StrengthStandards.displayLabel("pull-up"))
    }

    @Test
    fun `displayLabel returns slug for unknown slug`() {
        assertEquals("nieznany-slug", StrengthStandards.displayLabel("nieznany-slug"))
    }

    @Test
    fun `classify ratios produce correct StrengthLevel`() {
        val bench = StrengthStandards.forExerciseSlug("barbell-bench-press")!!
        val male = pl.filebit.gymtracker.data.entity.Gender.MALE
        // maleRatios: [0.5, 0.75, 1.25, 1.75, 2.25]
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.BELOW_BEGINNER, bench.classify(0.3, male))
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.BEGINNER, bench.classify(0.6, male))
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.NOVICE, bench.classify(1.0, male))
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.INTERMEDIATE, bench.classify(1.5, male))
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.ADVANCED, bench.classify(2.0, male))
        assertEquals(pl.filebit.gymtracker.data.strength.StrengthLevel.ELITE, bench.classify(2.5, male))
    }
}
