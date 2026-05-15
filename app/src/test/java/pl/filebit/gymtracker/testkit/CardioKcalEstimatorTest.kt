package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.TrainingDaySummary

/**
 * v1.27 — FAZA 2.3 — testy CardioKcalEstimator.
 *
 * Szacuje średni dzienny dodatek kcal z cardio (TrainingDaySummary 7 dni,
 * 10 kcal/min, /7). Bez tego TDEE zaniżone dla aktywnych userów.
 */
class CardioKcalEstimatorTest : TestHarness() {

    private val dayMs = 86_400_000L

    private fun addCardioDay(daysAgo: Int, cardioMinutes: Int) = runBlocking {
        db.trainingDaySummaryDao().upsert(
            TrainingDaySummary(
                dateMs = System.currentTimeMillis() - daysAgo * dayMs,
                cardioMinutes = cardioMinutes
            )
        )
    }

    @Test
    fun `3 dni po 30 min cardio — sredni dzienny dodatek`() = runBlocking {
        addCardioDay(1, 30); addCardioDay(3, 30); addCardioDay(5, 30)
        // 90 min × 10 kcal / 7 dni = 128
        val avg = HomeDetectors(db, context).cardioKcalEstimator.avgDailyKcalLast7Days()
        TraceReport("cardio-kcal-3x30")
            .section("WERDYKT")
            .verdict("avgDailyKcalLast7Days", "$avg kcal/dzień",
                "3×30 min cardio = 900 kcal / 7 dni")
            .emit()
        assertEquals("90 min × 10 kcal / 7", 900 / 7, avg)
    }

    @Test
    fun `brak cardio — zero`() = runBlocking {
        addCardioDay(1, 0); addCardioDay(2, 0)
        assertEquals("0 minut cardio → 0 kcal", 0,
            HomeDetectors(db, context).cardioKcalEstimator.avgDailyKcalLast7Days())
    }

    @Test
    fun `brak danych — zero`() = runBlocking {
        assertEquals("pusta baza → 0", 0,
            HomeDetectors(db, context).cardioKcalEstimator.avgDailyKcalLast7Days())
    }
}
