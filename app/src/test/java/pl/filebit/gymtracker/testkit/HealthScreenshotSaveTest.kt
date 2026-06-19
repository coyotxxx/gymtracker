package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pl.filebit.gymtracker.ai.HealthScreenshotAnalyzer
import pl.filebit.gymtracker.ai.HealthScreenshotData
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.ui.health.AnalyzedScreenshot
import pl.filebit.gymtracker.ui.health.HealthScreenshotViewModel
import java.util.Calendar

/**
 * v2.70.0 — regresja: „Skan zdrowotny" odczytywał tkankę/masę mięśniową, ale saveAll
 * zapisywał do BodyMeasurement TYLKO wagę. Test pilnuje, że teraz zapisuje komplet
 * i scala z istniejącym pomiarem dnia (nie gubi obwodów, nie tworzy duplikatu).
 */
class HealthScreenshotSaveTest : TestHarness() {

    private fun newVm(): HealthScreenshotViewModel {
        val kit = ViewModelKit(db, context)
        val analyzer = HealthScreenshotAnalyzer(kit.aiClient, kit.aiPrefs)
        return HealthScreenshotViewModel(analyzer, db.recoveryLogDao(), db.bodyMeasurementDao())
    }

    private fun awaitBf(): BodyMeasurement? {
        var tries = 0
        var saved = runBlocking { db.bodyMeasurementDao().getLatest() }
        while (saved?.bodyFatPercent == null && tries++ < 200) {
            Thread.sleep(15); saved = runBlocking { db.bodyMeasurementDao().getLatest() }
        }
        return saved
    }

    @Test
    fun `saveAll zapisuje tkanke i mase miesniowa, nie tylko wage`() {
        val data = HealthScreenshotData(
            weightKg = 83.7, bodyFatPercent = 24.9, muscleMassKg = 34.2, detectedDate = null
        )
        newVm().saveAll(listOf(AnalyzedScreenshot(index = 0, data = data)))

        val saved = awaitBf()
        assertNotNull(saved)
        assertEquals(83.7, saved!!.weightKg!!, 0.001)
        assertEquals(24.9, saved.bodyFatPercent!!, 0.001)
        assertEquals(34.2, saved.muscleMassKg!!, 0.001)
    }

    @Test
    fun `saveAll scala z pomiarem dnia (zachowuje obwody, jeden wpis)`() {
        val today = startOfToday()
        runBlocking { db.bodyMeasurementDao().upsert(BodyMeasurement(date = today, waistCm = 85.0)) }

        val data = HealthScreenshotData(weightKg = 83.7, bodyFatPercent = 24.9, detectedDate = null)
        newVm().saveAll(listOf(AnalyzedScreenshot(index = 0, data = data)))

        val saved = awaitBf()!!
        val all = runBlocking { db.bodyMeasurementDao().getAllAsc() }
        assertEquals("scalono w jeden wpis dnia", 1, all.size)
        assertEquals("obwód talii zachowany", 85.0, saved.waistCm!!, 0.001)
        assertEquals("tkanka dopisana", 24.9, saved.bodyFatPercent!!, 0.001)
        assertEquals("waga dopisana", 83.7, saved.weightKg!!, 0.001)
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
