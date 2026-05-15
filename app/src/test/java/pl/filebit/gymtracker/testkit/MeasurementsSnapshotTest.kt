package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.repository.ProgressPhotoRepository
import pl.filebit.gymtracker.ui.measurements.MeasurementsViewModel
import pl.filebit.gymtracker.ui.photos.ProgressPhotosViewModel

/**
 * v1.27 — FAZA 3.7 — snapshoty ekranów pomiarów ciała.
 *
 * Measurements (lista + trendy wagi/talii/BF). ProgressPhotos (galeria).
 * MeasurementAdd i BodyMap to ekrany formularza/wizualizacji bez własnego
 * ViewModelu — korzystają z MeasurementsViewModel.
 */
class MeasurementsSnapshotTest : TestHarness() {

    private val day = 86_400_000L


    @Test
    fun `Measurements pokazuje pomiary i trend wagi`() = runBlocking {
        val now = System.currentTimeMillis()
        db.bodyMeasurementDao().upsert(BodyMeasurement(date = now - 30 * day, weightKg = 84.0))
        db.bodyMeasurementDao().upsert(BodyMeasurement(date = now - 15 * day, weightKg = 82.5))
        db.bodyMeasurementDao().upsert(BodyMeasurement(date = now - 1 * day, weightKg = 81.0))

        val kit = ViewModelKit(db, context)
        val vm = MeasurementsViewModel(
            db.bodyMeasurementDao(), db.progressPhotoDao(), kit.userProfileRepo)
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }

        TraceReport("measurements")
            .section("CO WIDZI USER")
            .verdict("isLoading", s.isLoading.toString(), "false = załadowano")
            .kv("liczba pomiarów", s.measurements.size.toString())
            .kv("ostatnia waga", s.latestWeight?.toString() ?: "—")
            .kv("trend wagi 30d", s.weightTrend30d?.toString() ?: "—")
            .emit()

        assertFalse("pomiary załadowane", s.isLoading)
        assertEquals("3 pomiary wagi", 3, s.measurements.size)
        assertEquals("ostatnia waga = 81 kg", 81.0, s.latestWeight)
    }

    @Test
    fun `Measurements empty state - brak pomiarow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = MeasurementsViewModel(
            db.bodyMeasurementDao(), db.progressPhotoDao(), kit.userProfileRepo)
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }

        assertTrue("brak pomiarów = pusta lista", s.measurements.isEmpty())
        assertTrue("brak ostatniej wagi", s.latestWeight == null)
    }

    @Test
    fun `ProgressPhotos empty state - brak zdjec`() = runBlocking {
        val vm = ProgressPhotosViewModel(
            ProgressPhotoRepository(db.progressPhotoDao(), context))
        val photos = vm.photos.first()
        assertTrue("brak zdjęć postępu na starcie", photos.isEmpty())
    }
}
