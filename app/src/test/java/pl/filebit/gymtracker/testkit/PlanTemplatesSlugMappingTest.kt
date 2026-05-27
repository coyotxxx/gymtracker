package pl.filebit.gymtracker.testkit

import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import pl.filebit.gymtracker.data.template.PlanTemplates

/**
 * v2.2.0 — testy mapowania exerciseName → canonical slug w PlanTemplates.
 * Pokrycie: 6 ćwiczeń bazowych ze StrengthStandards musi się zmatchować,
 * 80/83 unique template names ma slug, 3 brakujące zwracają null.
 */
class PlanTemplatesSlugMappingTest {

    @Test
    fun `base 6 strength standards exercises map to canonical slugs`() {
        assertEquals("barbell-back-squat", PlanTemplates.resolveSlug("Przysiad ze sztangą (back squat)"))
        assertEquals("barbell-bench-press", PlanTemplates.resolveSlug("Wyciskanie sztangi leżąc"))
        assertEquals("barbell-deadlift", PlanTemplates.resolveSlug("Martwy ciąg klasyczny"))
        assertEquals("barbell-overhead-press", PlanTemplates.resolveSlug("Wyciskanie żołnierskie (OHP)"))
        assertEquals("barbell-bent-over-row", PlanTemplates.resolveSlug("Wiosłowanie sztangą"))
        assertEquals("pull-up", PlanTemplates.resolveSlug("Podciąganie nachwytem"))
    }

    @Test
    fun `cardio names map to canonical slugs`() {
        assertNotNull(PlanTemplates.resolveSlug("Bieżnia (bieg)"))
        assertNotNull(PlanTemplates.resolveSlug("Skakanka"))
        assertNotNull(PlanTemplates.resolveSlug("Burpees"))
    }

    @Test
    fun `bodyweight basics map to canonical`() {
        assertEquals("push-up", PlanTemplates.resolveSlug("Pompki"))
        assertEquals("crunch-floor", PlanTemplates.resolveSlug("Brzuszki"))
        assertEquals("russian-twist", PlanTemplates.resolveSlug("Russian twist"))
        assertEquals("walking-lunge", PlanTemplates.resolveSlug("Wykrok kroczący (walking lunge)"))
    }

    @Test
    fun `unknown name returns null - fallback to create-custom in PlanListViewModel`() {
        assertNull(PlanTemplates.resolveSlug("Jakieś nieistniejące ćwiczenie"))
        assertNull(PlanTemplates.resolveSlug(""))
    }
}
