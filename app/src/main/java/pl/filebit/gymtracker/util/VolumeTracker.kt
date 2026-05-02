package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.TrainingGoal

/**
 * Tracking volume (liczba setów roboczych) per grupa mięśniowa per tydzień.
 * Bazuje na zaleceniach z literatury (Schoenfeld, RP):
 *  - Hipertrofia: 10–20 setów / partia / tydzień (MEV–MAV)
 *  - Siła:        8–14 setów (mniej, ale cięższe)
 *  - Fitness:      6–12 setów
 *
 * Pure function — nie zależy od DB.
 */

data class VolumeRange(val low: Int, val high: Int)

/**
 * Zwraca optymalny zakres setów / tydzień dla danej grupy mięśniowej i celu.
 * Większe partie (klatka, plecy, nogi) mają wyższy zakres niż małe (biceps, łydki).
 */
fun recommendedVolumeRange(muscle: MuscleGroup, goal: TrainingGoal): VolumeRange {
    val (lowBase, highBase) = when (muscle) {
        // Duże partie
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS,
        MuscleGroup.HAMSTRINGS, MuscleGroup.SHOULDERS -> 10 to 20
        // Średnie partie
        MuscleGroup.GLUTES, MuscleGroup.BICEPS, MuscleGroup.TRICEPS -> 8 to 16
        // Małe partie
        MuscleGroup.CALVES, MuscleGroup.CORE -> 6 to 14
        // Pozostałe — neutralnie
        MuscleGroup.CARDIO, MuscleGroup.OTHER -> 0 to 100
    }
    return when (goal) {
        TrainingGoal.STRENGTH -> VolumeRange((lowBase * 0.8).toInt(), (highBase * 0.8).toInt())
        TrainingGoal.HYPERTROPHY, TrainingGoal.MIX -> VolumeRange(lowBase, highBase)
        TrainingGoal.GENERAL_FITNESS -> VolumeRange((lowBase * 0.7).toInt(), (highBase * 0.7).toInt())
        TrainingGoal.CARDIO_LIFTING -> VolumeRange((lowBase * 0.6).toInt(), (highBase * 0.6).toInt())
    }
}

enum class VolumeStatus { UNDER, OK, OVER }

data class MuscleVolumeReport(
    val muscle: MuscleGroup,
    val sets: Int,
    val range: VolumeRange,
    val status: VolumeStatus
)

fun classifyVolume(sets: Int, range: VolumeRange): VolumeStatus = when {
    sets < range.low -> VolumeStatus.UNDER
    sets > range.high -> VolumeStatus.OVER
    else -> VolumeStatus.OK
}

/**
 * Podsumowuje volume tygodnia per partia mięśniowa.
 * @param setsByMuscle mapa MuscleGroup → liczba zaliczonych setów roboczych w tygodniu
 * @param goal cel treningowy usera
 * @return lista raportów posortowana wg liczby setów malejąco
 */
fun reportWeeklyVolume(
    setsByMuscle: Map<MuscleGroup, Int>,
    goal: TrainingGoal
): List<MuscleVolumeReport> {
    return MuscleGroup.entries
        .filter { it != MuscleGroup.CARDIO && it != MuscleGroup.OTHER }
        .map { muscle ->
            val sets = setsByMuscle[muscle] ?: 0
            val range = recommendedVolumeRange(muscle, goal)
            MuscleVolumeReport(
                muscle = muscle,
                sets = sets,
                range = range,
                status = classifyVolume(sets, range)
            )
        }
        .sortedByDescending { it.sets }
}
