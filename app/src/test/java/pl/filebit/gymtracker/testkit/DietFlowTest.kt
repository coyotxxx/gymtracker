package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v1.27 — FAZA 5.3 — flow diety (interakcja).
 *
 * User loguje posiłki w ciągu dnia → adherence rośnie z każdym posiłkiem.
 * Test wykonuje sekwencję dodawania posiłków i po każdym przelicza
 * zgodność, weryfikując że logowanie faktycznie zasila raport adherence.
 */
class DietFlowTest : TestHarness() {

    @Test
    fun `flow - logowanie posilkow podnosi adherence dnia`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = 4))
        db.userDietProfileDao().upsert(UserDietProfile(
            ageYears = 30, heightCm = 180, activityLevel = ActivityLevel.MODERATE,
            goalType = DietGoalType.MAINTAIN,
            onboardingCompletedAt = System.currentTimeMillis()))
        val kit = ViewModelKit(db, context)
        val now = System.currentTimeMillis()

        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        val riceId = db.foodProductDao().upsert(FoodProduct(
            name = "Ryż", category = FoodCategory.CARBS,
            kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3))
        val oatsId = db.foodProductDao().upsert(FoodProduct(
            name = "Owsianka", category = FoodCategory.CARBS,
            kcalPer100g = 370.0, proteinPer100g = 13.0, carbsPer100g = 60.0, fatPer100g = 7.0))

        val tr = TraceReport("flow-dieta")
        suspend fun adherenceAfterMeals(): Int {
            kit.adherenceCalc.computeForDate(now)
            return db.adherenceLogDao().getRecent(1).first().kcalAdherencePct
        }

        // KROK 1 — user loguje śniadanie
        kit.dietRepo.addMeal(MealEntry(
            dateMs = now, mealType = MealType.BREAKFAST, productId = oatsId, grams = 100.0))
        val after1 = adherenceAfterMeals()
        tr.section("KROK 1 — śniadanie")
            .kv("kcalAdherence", "$after1%")

        // KROK 2 — user loguje obiad
        kit.dietRepo.addMeal(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, productId = chickenId, grams = 250.0))
        kit.dietRepo.addMeal(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, productId = riceId, grams = 200.0))
        val after2 = adherenceAfterMeals()
        tr.section("KROK 2 — obiad")
            .kv("kcalAdherence", "$after2%")

        // KROK 3 — user loguje kolację
        kit.dietRepo.addMeal(MealEntry(
            dateMs = now, mealType = MealType.DINNER, productId = chickenId, grams = 200.0))
        val after3 = adherenceAfterMeals()
        tr.section("KROK 3 — kolacja")
            .kv("kcalAdherence", "$after3%")
            .section("OCENA")
            .verdict("adherence rośnie z każdym posiłkiem",
                if (after1 < after2 && after2 < after3) "OK" else "BŁĄD",
                "$after1% → $after2% → $after3%")
        tr.emit()

        assertTrue("po śniadaniu adherence > 0", after1 > 0)
        assertTrue("obiad podnosi adherence", after2 > after1)
        assertTrue("kolacja dalej podnosi adherence", after3 > after2)

        val log = db.adherenceLogDao().getRecent(1).first()
        assertEquals("4 posiłki zalogowane (śniadanie+2×obiad+kolacja)",
            4, log.mealsLoggedCount)
    }
}
