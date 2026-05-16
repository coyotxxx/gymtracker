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
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.MealConsumptionRepository
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v1.27 — FAZA 2.4c — testy AdherenceCalculator.
 *
 * computeForDate() liczy zgodność dnia: cel kcal/makro (z UserProfile +
 * UserDietProfile przez Mifflin-St Jeor) vs faktyczne spożycie (MealEntry
 * × FoodProduct). Test sprawdza cały pipeline goal→consumption→AdherenceLog.
 */
class AdherenceCalculatorTest : TestHarness() {

    private fun calc(): AdherenceCalculator {
        val kit = HomeDetectors(db, context)
        return AdherenceCalculator(
            dietRepo = DietRepository(
                db.foodProductDao(), db.mealEntryDao(),
                db.fastingWindowDao(), db.recipeDao()),
            dietPrefs = DietPreferences(context),
            profileRepo = UserProfileRepository(db.userProfileDao()),
            dietProfileRepo = UserDietProfileRepository(UserProfileRepository(db.userProfileDao())),
            trainingDietBridge = kit.trainingDietBridge,
            bodyDao = db.bodyMeasurementDao(),
            dao = db.adherenceLogDao(),
            consumptionRepo = MealConsumptionRepository(db.mealConsumptionDao())
        )
    }

    private suspend fun seedProfile() {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = 4))
        UserDietProfileRepository(UserProfileRepository(db.userProfileDao())).save(
            UserDietProfile(
                ageYears = 30, heightCm = 180,
                activityLevel = ActivityLevel.MODERATE, goalType = DietGoalType.MAINTAIN))
    }

    @Test
    fun `computeForDate liczy cel i spozycie dla pelnego dnia`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        val riceId = db.foodProductDao().upsert(FoodProduct(
            name = "Ryż", category = FoodCategory.CARBS,
            kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, productId = chickenId, grams = 300.0))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.DINNER, productId = riceId, grams = 200.0))

        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        // kurczak 300g = 495 kcal, ryż 200g = 260 kcal → ~755 kcal, ~101 g B
        val tr = TraceReport("adherence-full-day")
            .section("CEL (computeDailyGoal)")
            .kv("targetKcal", log.targetKcal.toString())
            .kv("targetProteinG", log.targetProteinG.toString())
            .section("SPOŻYCIE")
            .kv("actualKcal", log.actualKcal.toString())
            .kv("actualProteinG", log.actualProteinG.toString())
            .kv("mealsLoggedCount", log.mealsLoggedCount.toString())
            .section("ADHERENCE")
            .kv("kcalAdherencePct", "${log.kcalAdherencePct}%")
            .kv("proteinAdherencePct", "${log.proteinAdherencePct}%")
            .verdict("overallScore", log.overallScore.toString(), "0..100")
            .emit()

        assertTrue("target kcal sensowny dla 80 kg mężczyzny (>2000)", log.targetKcal > 2000)
        assertTrue("actualKcal w okolicy 755 (700..810)", log.actualKcal in 700..810)
        assertTrue("actualProtein w okolicy 101 g (95..110)", log.actualProteinG in 95..110)
        assertEquals("2 zalogowane posiłki", 2, log.mealsLoggedCount)
        assertEquals("kcalAdherence = actual/target",
            (log.actualKcal * 100 / log.targetKcal), log.kcalAdherencePct)
    }

    @Test
    fun `computeForDate dla dnia bez posilkow daje zerowe spozycie`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        assertTrue("cel nadal policzony", log.targetKcal > 2000)
        assertEquals("brak posiłków → 0 kcal", 0, log.actualKcal)
        assertEquals("brak posiłków → 0% adherence", 0, log.kcalAdherencePct)
        assertEquals("0 zalogowanych posiłków", 0, log.mealsLoggedCount)
    }
}
