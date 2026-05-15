package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.GoalAchievementService

/**
 * v1.27 — FAZA 2.2 — testy GoalAchievementService.
 *
 * checkAchieved() decyduje czy pokazać kartę "CEL OSIĄGNIĘTY" na Home.
 * Reguła: target osiągnięty + utrzymany ≥7 dni (ISSN — anti-false-positive
 * od wahań wagi). Test pokrywa 3 graniczne przypadki.
 */
class GoalAchievementServiceTest : TestHarness() {

    private val dayMs = 86_400_000L

    private fun service() = GoalAchievementService(db.bodyMeasurementDao(), db.goalDao())

    private fun addWeight(weightKg: Double, daysAgo: Int) = runBlocking {
        db.bodyMeasurementDao().upsert(
            BodyMeasurement(date = System.currentTimeMillis() - daysAgo * dayMs, weightKg = weightKg)
        )
    }

    private fun cutProfile(target: Double) = UserProfile(
        weightGoalType = WeightGoalType.CUT,
        targetWeightKg = target,
        bodyweightKg = 80.0
    )

    @Test
    fun `cel osiagniety i utrzymany 10 dni — isReached true`() = runBlocking {
        // CUT 80→74. Target 74 osiągnięty 10 dni temu, utrzymany.
        addWeight(80.0, 35); addWeight(78.0, 28); addWeight(76.0, 18)
        addWeight(74.0, 10); addWeight(73.8, 5); addWeight(73.9, 1)

        val result = service().checkAchieved(cutProfile(74.0))

        TraceReport("goal-reached-stable")
            .section("WERDYKT")
            .verdict("checkAchieved", "isReached=${result?.isReached}",
                "stableDays=${result?.stableDays}, currentWeight=${result?.currentWeight}")
            .emit()

        assertNotNull("CUT 74kg osiągnięte i utrzymane → wynik nie-null", result)
        assertTrue("utrzymane 10 dni (≥7) → isReached", result!!.isReached)
        assertTrue("stableDays ≥ 7", result.stableDays >= 7)
    }

    @Test
    fun `cel osiagniety wczoraj — isReached false bo ponizej 7 dni`() = runBlocking {
        // Target osiągnięty dopiero 2 dni temu — za krótko na isReached.
        addWeight(80.0, 30); addWeight(77.0, 20); addWeight(75.0, 8)
        addWeight(73.9, 2); addWeight(73.8, 0)

        val result = service().checkAchieved(cutProfile(74.0))

        TraceReport("goal-reached-too-fresh")
            .section("WERDYKT")
            .verdict("checkAchieved", "isReached=${result?.isReached}",
                "stableDays=${result?.stableDays}")
            .emit()

        assertNotNull(result)
        assertFalse("osiągnięte 2 dni temu (<7) → NIE isReached", result!!.isReached)
    }

    @Test
    fun `cel nieosiagniety — wynik null`() = runBlocking {
        // CUT do 74, ale waga utknęła na 78.
        addWeight(82.0, 30); addWeight(80.0, 20); addWeight(78.5, 10); addWeight(78.0, 1)

        val result = service().checkAchieved(cutProfile(74.0))

        TraceReport("goal-not-reached")
            .section("WERDYKT")
            .verdict("checkAchieved", if (result == null) "null" else "result",
                "waga 78.0 > target 74.0")
            .emit()

        assertNull("target nieosiągnięty → null", result)
    }

    @Test
    fun `brak celu wagi — wynik null`() = runBlocking {
        addWeight(80.0, 10); addWeight(74.0, 1)
        val result = service().checkAchieved(
            UserProfile(weightGoalType = WeightGoalType.NONE, targetWeightKg = null)
        )
        assertNull("brak celu CUT/BULK → null", result)
    }
}
