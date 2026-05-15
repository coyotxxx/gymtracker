package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.util.SafetyGuard
import pl.filebit.gymtracker.util.SafetyResult

/**
 * v1.27 — FAZA 2.4c — testy SafetyGuard.
 *
 * Ostatnia bramka bezpieczeństwa: po decyzji silnika kcal NIGDY nie może
 * spaść poniżej bezpiecznego minimum (utrata mięśni, problemy hormonalne).
 * `AutoAdjustmentService` woła validateKcal i blokuje korektę przy Block.
 */
class SafetyGuardTest {

    private val male = UserProfile(gender = Gender.MALE, bodyweightKg = 80.0)
    private val female = UserProfile(gender = Gender.FEMALE, bodyweightKg = 60.0)

    @Test
    fun `floor kcal zalezy od plci`() {
        assertEquals("mężczyzna: floor 1500 kcal", 1500, SafetyGuard.minKcal(male))
        assertEquals("kobieta: floor 1200 kcal", 1200, SafetyGuard.minKcal(female))
    }

    @Test
    fun `zbyt niskie kcal sa blokowane z capped value`() {
        val r = SafetyGuard.validateKcal(target = 1000, profile = male, weightKg = 80.0)
        TraceReport("safetyguard-block")
            .section("WALIDACJA")
            .verdict("validateKcal(1000)", r::class.simpleName ?: "?",
                if (r is SafetyResult.Block) r.message else "")
            .emit()
        assertTrue("1000 kcal < 1500 floor → Block", r is SafetyResult.Block)
        assertEquals("capped do bezpiecznego minimum",
            1500, (r as SafetyResult.Block).cappedValue)
    }

    @Test
    fun `rozsadne kcal przechodza`() {
        val r = SafetyGuard.validateKcal(target = 2500, profile = male, weightKg = 80.0)
        assertEquals("2500 kcal dla 80 kg → Pass", SafetyResult.Pass, r)
    }

    @Test
    fun `BMR jako floor gdy wyzszy od absolutnego minimum`() {
        // bmrEstimate 1800 > absolutny floor 1500 → floor = BMR
        val r = SafetyGuard.validateKcal(
            target = 1600, profile = male, weightKg = 80.0, bmrEstimate = 1800)
        assertTrue("1600 < BMR 1800 → Block", r is SafetyResult.Block)
        assertEquals("floor podniesiony do BMR",
            1800, (r as SafetyResult.Block).cappedValue)
    }

    @Test
    fun `ekstremalnie wysokie kcal daja ostrzezenie`() {
        // maxKcal(80 kg) = 4800; 6000 > max → Warn
        val r = SafetyGuard.validateKcal(target = 6000, profile = male, weightKg = 80.0)
        assertTrue("6000 kcal powyżej max → Warn", r is SafetyResult.Warn)
    }
}
