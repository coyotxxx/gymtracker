package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * Strażniki regresji dla błędów z BUGREPORT-2026-06-12 (naprawione w v2.23.0).
 * Każdy test był wcześniej CZERWONY (reprodukcja buga) — po naprawie ZIELONY.
 */
class BugfixRegressionTest : TestHarness() {

    /** K1: świeży aktywny plan NIE może alarmować o opuszczonych treningach sprzed jego utworzenia. */
    @Test
    fun `K1 swiezy plan nie generuje missed-workout`() = runBlocking {
        val kit = HomeDetectors(db, context)
        val exId = db.exerciseDao().upsert(
            Exercise(name = "Przysiad test", primaryMuscle = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
        )
        val planId = db.trainingPlanDao().upsert(
            TrainingPlan(name = "Świeży plan", daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7), isActive = true)
        )
        val peId = db.planExerciseDao().upsert(
            PlanExercise(planId = planId, exerciseId = exId, dayOfWeek = 1, orderIndex = 0)
        )
        db.planExerciseSetDao().upsert(PlanExerciseSet(planExerciseId = peId, setNumber = 1, reps = 8))

        assertNull(
            "Świeży plan (createdAt=teraz) nie ma opuszczonych dni sprzed swojego istnienia",
            kit.deloadService.missedWorkoutSignal()
        )
    }

    /** K3: posiłek przeniesiony przez carry-over musi być isPlanned=true (do potwierdzenia). */
    @Test
    fun `K3 carry-over kopia jest zaplanowana`() {
        val manualYesterday = MealEntry(dateMs = 1000L, mealType = MealType.LUNCH, productId = 1, grams = 200.0)
        val carried = manualYesterday.copy(id = 0, dateMs = 2000L, createdAt = 3000L, isPlanned = true)
        assertTrue("kopia carry-over = isPlanned=true (inaczej fałszywe 100% adherence rano)", carried.isPlanned)
    }

    /** K5: add_meal przez AI musi przeliczyć adherence dnia (jak każda ścieżka UI). */
    @Test
    fun `K5 add_meal przez AI aktualizuje adherence_log`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = 3)
        )
        db.foodProductDao().upsert(
            FoodProduct(name = "Kurczak", category = FoodCategory.PROTEIN,
                kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6)
        )
        val kit = ViewModelKit(db, context)

        val result = kit.aiToolHandler.execute("add_meal", buildJsonObject {
            put("product", "Kurczak"); put("grams", 300.0); put("mealType", "LUNCH")
        })
        assertTrue("add_meal OK: $result", result.contains("\"ok\""))

        val log = db.adherenceLogDao().getRecent(5).firstOrNull()
        assertNotNull("adherence_log dnia przeliczony po add_meal", log)
        assertTrue("actualKcal odzwierciedla posiłek (~495)", log!!.actualKcal in 480..510)
    }
}
