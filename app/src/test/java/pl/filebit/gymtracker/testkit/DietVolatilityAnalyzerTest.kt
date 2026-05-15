package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.repository.DietVolatilityAnalyzer

/**
 * v1.27 — FAZA 2.3 — testy DietVolatilityAnalyzer.
 *
 * Wykrywa wahania kalorii: jednoczesny dzień CHEAT (>150% celu) i dzień
 * UNDER (<60% celu) w oknie 7 dni → karta "MOCNE WAHANIA KCAL".
 * Wymaga ≥4 dni danych. Test pokrywa 3 przypadki graniczne.
 */
class DietVolatilityAnalyzerTest : TestHarness() {

    private val dayMs = 86_400_000L

    private fun analyzer() = DietVolatilityAnalyzer(db.adherenceLogDao())

    private fun addLog(daysAgo: Int, targetKcal: Int, actualKcal: Int) = runBlocking {
        val pct = (actualKcal * 100 / targetKcal)
        db.adherenceLogDao().upsert(
            AdherenceLog(
                dateMs = System.currentTimeMillis() - daysAgo * dayMs,
                targetKcal = targetKcal, actualKcal = actualKcal, kcalAdherencePct = pct,
                targetProteinG = 150, actualProteinG = 150, proteinAdherencePct = 100,
                targetCarbsG = 200, actualCarbsG = 200, carbsAdherencePct = 100,
                targetFatG = 60, actualFatG = 60, fatAdherencePct = 100
            )
        )
    }

    @Test
    fun `cheat plus under w 7 dni — wykrywa wahania`() = runBlocking {
        // 7 dni: 1 cheat (3200=160%), 1 under (1000=50%), 5 normalnych
        addLog(1, 2000, 2050); addLog(2, 2000, 1950); addLog(3, 2000, 3200)
        addLog(4, 2000, 1000); addLog(5, 2000, 2100); addLog(6, 2000, 1980)
        addLog(7, 2000, 2020)

        val report = analyzer().analyze()

        TraceReport("diet-volatility-detected")
            .section("WERDYKT")
            .verdict("analyze", if (report != null) "WAHANIA" else "null",
                "cheat=${report?.cheatPctOfTarget}%, under=${report?.underPctOfTarget}%, " +
                    "dni=${report?.daysAnalyzed}")
            .emit()

        assertNotNull("cheat 160% + under 50% w 7 dni → raport wahań", report)
        assertTrue("cheat >150%", report!!.cheatPctOfTarget > 150)
        assertTrue("under <60%", report.underPctOfTarget < 60)
    }

    @Test
    fun `stabilna dieta — brak wahan, null`() = runBlocking {
        // 7 dni wszystkie blisko celu (90-110%)
        repeat(7) { addLog(it + 1, 2000, 2000 + (it - 3) * 50) }
        val report = analyzer().analyze()
        TraceReport("diet-volatility-stable")
            .section("WERDYKT")
            .verdict("analyze", if (report == null) "null (stabilna)" else "WAHANIA", "")
            .emit()
        assertNull("dieta stabilna → brak raportu wahań", report)
    }

    @Test
    fun `za malo dni — null`() = runBlocking {
        // tylko 3 dni — poniżej progu 4
        addLog(1, 2000, 3500); addLog(2, 2000, 900); addLog(3, 2000, 2000)
        assertNull("3 dni (<4) → null mimo cheat+under", analyzer().analyze())
    }
}
