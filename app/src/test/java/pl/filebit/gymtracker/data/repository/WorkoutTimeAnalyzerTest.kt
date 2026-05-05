package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.WorkoutContext

class WorkoutTimeAnalyzerTest {

    private val analyzer = WorkoutTimeAnalyzer()

    @Test
    fun `null trainingHour = all NORMAL`() {
        val slots = analyzer.classifySlots(
            mealHoursDecimal = listOf(8.0, 12.0, 18.0),
            mealTypesForSlots = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
            trainingHour = null
        )
        assertTrue(slots.all { it.workoutContext == WorkoutContext.NORMAL })
    }

    @Test
    fun `training at 18 - obiad jest PRE (16), kolacja POST (19)`() {
        // 4 sloty 12, 14, 16, 19 — pre = 16 (target 16), post = 19 (target 19)
        val slots = analyzer.classifySlots(
            mealHoursDecimal = listOf(12.0, 14.0, 16.0, 19.0),
            mealTypesForSlots = listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER),
            trainingHour = 18
        )
        assertEquals(WorkoutContext.NORMAL, slots[0].workoutContext)   // 12:00
        assertEquals(WorkoutContext.NORMAL, slots[1].workoutContext)   // 14:00
        assertEquals(WorkoutContext.PRE_WORKOUT, slots[2].workoutContext)   // 16:00
        assertEquals(WorkoutContext.POST_WORKOUT, slots[3].workoutContext)  // 19:00
    }

    @Test
    fun `training at 7 - early - sniadanie jest PRE 5, brak POST jeśli brak slotu w oknie`() {
        // Sloty 8, 13, 19 — pre target = 5 (poza oknem), post target = 8 (najbliższy 8)
        val slots = analyzer.classifySlots(
            mealHoursDecimal = listOf(8.0, 13.0, 19.0),
            mealTypesForSlots = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
            trainingHour = 7
        )
        // Brak slotu w pobliżu pre=5 → wszystkie NORMAL oprócz POST=8
        assertEquals(WorkoutContext.POST_WORKOUT, slots[0].workoutContext)
        assertEquals(WorkoutContext.NORMAL, slots[1].workoutContext)
        assertEquals(WorkoutContext.NORMAL, slots[2].workoutContext)
    }

    @Test
    fun `training at midday - lunch is post if close enough`() {
        // 3 sloty 8, 12, 18, training 12 → pre = 10 (brak slotu blisko), post = 13 (lunch 12 najbliższy w oknie 1.5h)
        val slots = analyzer.classifySlots(
            mealHoursDecimal = listOf(8.0, 12.0, 18.0),
            mealTypesForSlots = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
            trainingHour = 12
        )
        assertEquals(WorkoutContext.NORMAL, slots[0].workoutContext)   // 8:00
        // 12 to ten sam slot — najpierw match jako PRE (target 10, diff 2.0 — poza oknem 1.5)
        // Właściwie 12 jest 2h od preTarget 10 → poza ±1.5
        // 12 jest 1h od postTarget 13 → w oknie → POST
        assertEquals(WorkoutContext.POST_WORKOUT, slots[1].workoutContext)
        assertEquals(WorkoutContext.NORMAL, slots[2].workoutContext)
    }

    @Test
    fun `detectUsualTrainingHour returns null below 5 trainings`() {
        val ts = listOf(toMs(18), toMs(19), toMs(18))
        assertNull(analyzer.detectUsualTrainingHour(ts))
    }

    @Test
    fun `detectUsualTrainingHour finds modal hour`() {
        val ts = listOf(toMs(18), toMs(18), toMs(18), toMs(18), toMs(19), toMs(7))
        assertEquals(18, analyzer.detectUsualTrainingHour(ts))
    }

    @Test
    fun `detectUsualTrainingHour returns null when scattered (under 30 percent)`() {
        // Każda godzina max 1 raz z 10 → najczęstsza ma <30% (10%) → null
        val ts = listOf(toMs(7), toMs(8), toMs(9), toMs(10), toMs(11), toMs(12), toMs(13), toMs(14), toMs(15), toMs(16))
        assertNull(analyzer.detectUsualTrainingHour(ts))
    }

    private fun toMs(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
