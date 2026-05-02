package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.ui.plans.PlanExerciseWithDetail

/**
 * Po usunięciu/przeniesieniu ćwiczenia z grupy supersetu, partner może zostać
 * sam jak palec z `supersetGroup = "A"` ale bez nikogo do sparowania.
 * Funkcja czyści takie osierocone wpisy: gdy w obrębie tego samego dnia
 * grupa ma <2 członków, ustawia `supersetGroup = null`.
 *
 * Grupy supersetowe są per-dzień (litery A/B/C generowane osobno dla każdego
 * dayOfWeek), więc liczymy occurrences w obrębie pary (day, group).
 */
fun cleanOrphanedSupersets(
    exercises: List<PlanExerciseWithDetail>
): List<PlanExerciseWithDetail> {
    val countsByDayAndGroup: Map<Pair<Int, String>, Int> = exercises
        .mapNotNull { ped ->
            val g = ped.planEx.supersetGroup ?: return@mapNotNull null
            ped.planEx.dayOfWeek to g
        }
        .groupingBy { it }
        .eachCount()

    return exercises.map { ped ->
        val group = ped.planEx.supersetGroup
        if (group != null) {
            val key = ped.planEx.dayOfWeek to group
            if ((countsByDayAndGroup[key] ?: 0) < 2) {
                return@map ped.copy(planEx = ped.planEx.copy(supersetGroup = null))
            }
        }
        ped
    }
}
