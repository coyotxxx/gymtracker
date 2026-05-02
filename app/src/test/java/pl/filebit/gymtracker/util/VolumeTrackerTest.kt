package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.TrainingGoal

class VolumeTrackerTest {

    @Test
    fun `chest hypertrophy is 10 to 20`() {
        val r = recommendedVolumeRange(MuscleGroup.CHEST, TrainingGoal.HYPERTROPHY)
        assertEquals(10, r.low)
        assertEquals(20, r.high)
    }

    @Test
    fun `chest strength is reduced (80 percent)`() {
        val r = recommendedVolumeRange(MuscleGroup.CHEST, TrainingGoal.STRENGTH)
        assertEquals(8, r.low)
        assertEquals(16, r.high)
    }

    @Test
    fun `biceps medium range`() {
        val r = recommendedVolumeRange(MuscleGroup.BICEPS, TrainingGoal.HYPERTROPHY)
        assertEquals(8, r.low)
        assertEquals(16, r.high)
    }

    @Test
    fun `calves small range`() {
        val r = recommendedVolumeRange(MuscleGroup.CALVES, TrainingGoal.HYPERTROPHY)
        assertEquals(6, r.low)
        assertEquals(14, r.high)
    }

    @Test
    fun `under volume status`() {
        assertEquals(VolumeStatus.UNDER, classifyVolume(5, VolumeRange(10, 20)))
    }

    @Test
    fun `over volume status`() {
        assertEquals(VolumeStatus.OVER, classifyVolume(25, VolumeRange(10, 20)))
    }

    @Test
    fun `ok volume status at boundaries`() {
        assertEquals(VolumeStatus.OK, classifyVolume(10, VolumeRange(10, 20)))
        assertEquals(VolumeStatus.OK, classifyVolume(20, VolumeRange(10, 20)))
        assertEquals(VolumeStatus.OK, classifyVolume(15, VolumeRange(10, 20)))
    }

    @Test
    fun `report classifies all muscles correctly`() {
        val sets = mapOf(
            MuscleGroup.CHEST to 12,    // OK (10-20)
            MuscleGroup.BACK to 5,      // UNDER
            MuscleGroup.BICEPS to 22,   // OVER (8-16)
            MuscleGroup.TRICEPS to 0    // UNDER
        )
        val report = reportWeeklyVolume(sets, TrainingGoal.HYPERTROPHY)
        val byMuscle = report.associateBy { it.muscle }
        assertEquals(VolumeStatus.OK, byMuscle[MuscleGroup.CHEST]!!.status)
        assertEquals(VolumeStatus.UNDER, byMuscle[MuscleGroup.BACK]!!.status)
        assertEquals(VolumeStatus.OVER, byMuscle[MuscleGroup.BICEPS]!!.status)
        assertEquals(VolumeStatus.UNDER, byMuscle[MuscleGroup.TRICEPS]!!.status)
    }

    @Test
    fun `report excludes CARDIO and OTHER`() {
        val report = reportWeeklyVolume(emptyMap(), TrainingGoal.HYPERTROPHY)
        assertTrue(report.none { it.muscle == MuscleGroup.CARDIO })
        assertTrue(report.none { it.muscle == MuscleGroup.OTHER })
    }

    @Test
    fun `report sorted descending by sets`() {
        val sets = mapOf(
            MuscleGroup.CHEST to 5,
            MuscleGroup.BACK to 15,
            MuscleGroup.QUADS to 10
        )
        val report = reportWeeklyVolume(sets, TrainingGoal.HYPERTROPHY)
        // Pierwsze trzy wpisy (z setami) powinny być posortowane malejąco
        val withSets = report.filter { it.sets > 0 }
        assertEquals(15, withSets[0].sets)
        assertEquals(10, withSets[1].sets)
        assertEquals(5, withSets[2].sets)
    }
}
