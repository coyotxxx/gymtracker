package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.WeeklyPlanOverride

/**
 * Slot harmonogramu dla konkretnego dnia tygodnia.
 *
 * @param sourceDayOfWeek dzień z którego pochodzi trening (oryginalny w planie).
 *                        Może być różny od slotu jeśli to przeniesienie.
 */
data class ScheduleSlot(
    val planId: Long,
    val sourceDayOfWeek: Int,    // dzień w planie (1-7)
    val targetDayOfWeek: Int,    // dzień w którym faktycznie wypada (1-7)
    val isMoved: Boolean         // true gdy source != target (przeniesione)
)

/**
 * Łączy bazowy harmonogram planu (originalSlots: dla każdego planu lista dni z ćwiczeniami)
 * z overrides per-tygodniowymi. Zwraca mapę: dayOfWeek → lista slotów planowanych.
 *
 * Reguły:
 *  - Jeśli dzień ma override z planu i targetDay == SKIPPED → trening pomijany
 *  - Jeśli dzień ma override z innego dnia → przesunięty trening trafia do targetDay
 *  - W innym razie zostaje oryginalna pozycja
 *
 * Pure function — testowalna bez DB.
 */
fun resolveWeekSchedule(
    plansBaseSlots: Map<Long, Set<Int>>,   // planId → dni gdzie plan ma ćwiczenia (z DB)
    overrides: List<WeeklyPlanOverride>
): Map<Int, List<ScheduleSlot>> {
    val byDay = mutableMapOf<Int, MutableList<ScheduleSlot>>()
    val overridesByOrigin: Map<Pair<Long, Int>, WeeklyPlanOverride> =
        overrides.associateBy { it.planId to it.originalDayOfWeek }

    plansBaseSlots.forEach { (planId, days) ->
        days.forEach { sourceDay ->
            val override = overridesByOrigin[planId to sourceDay]
            val effectiveTarget = when {
                override == null -> sourceDay
                override.targetDayOfWeek == WeeklyPlanOverride.SKIPPED -> -1
                else -> override.targetDayOfWeek
            }
            if (effectiveTarget != -1) {
                byDay.getOrPut(effectiveTarget) { mutableListOf() }.add(
                    ScheduleSlot(
                        planId = planId,
                        sourceDayOfWeek = sourceDay,
                        targetDayOfWeek = effectiveTarget,
                        isMoved = effectiveTarget != sourceDay
                    )
                )
            }
        }
    }
    return byDay
}

/**
 * Oblicza początek bieżącego tygodnia (poniedziałek 00:00 lokalnej strefy).
 */
fun currentWeekStartMillis(now: Long = System.currentTimeMillis()): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        firstDayOfWeek = java.util.Calendar.MONDAY
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val daysToMonday = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -daysToMonday)
    return cal.timeInMillis
}
