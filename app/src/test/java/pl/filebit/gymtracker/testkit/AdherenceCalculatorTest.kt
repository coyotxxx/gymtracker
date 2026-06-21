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
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
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
            dateMs = now, mealType = MealType.LUNCH, mealSlot = 2, productId = chickenId, grams = 300.0))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.DINNER, mealSlot = 3, productId = riceId, grams = 200.0))

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

    @Test
    fun `mealBreakdownForDate pokazuje ktory posilek zjedzony pominiety niezalogowany`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val consRepo = MealConsumptionRepository(db.mealConsumptionDao())
        // 3 posiłki (domyślne): śniadanie ZJEDZONE, obiad nietknięty, kolacja POMINIĘTA.
        consRepo.setStatus(now, 1, MealConsumptionStatus.CONSUMED)  // Posiłek 1 (śniadanie)
        consRepo.setStatus(now, 3, MealConsumptionStatus.SKIPPED)   // Posiłek 3 (kolacja)

        val breakdown = calc().mealBreakdownForDate(now).associateBy { it.slot }

        assertEquals("Posiłek 1 zjedzony",
            pl.filebit.gymtracker.data.repository.MealSlotState.EATEN, breakdown[1]?.state)
        assertEquals("Posiłek 2 nie zalogowany",
            pl.filebit.gymtracker.data.repository.MealSlotState.MISSING, breakdown[2]?.state)
        assertEquals("Posiłek 3 pominięty",
            pl.filebit.gymtracker.data.repository.MealSlotState.SKIPPED, breakdown[3]?.state)
    }

    // === v2.11.0: adherence = realne spożycie (plan AI vs ręczny dziennik) ===

    private suspend fun chicken(): Long = db.foodProductDao().upsert(FoodProduct(
        name = "Kurczak", category = FoodCategory.PROTEIN,
        kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))

    @Test
    fun `plan AI bez potwierdzenia NIE liczy sie do adherence`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val c = chicken()
        // wpis Z PLANU AI (isPlanned=true), brak statusu konsumpcji → niezjedzony
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, mealSlot = 2, productId = c, grams = 300.0, isPlanned = true))

        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        assertEquals("plan AI niepotwierdzony → 0 kcal", 0, log.actualKcal)
        assertEquals("plan AI niepotwierdzony → 0% adherence", 0, log.kcalAdherencePct)
        assertEquals("plan AI niepotwierdzony → 0 zalogowanych posiłków", 0, log.mealsLoggedCount)
    }

    @Test
    fun `plan AI po potwierdzeniu CONSUMED liczy sie`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val c = chicken()
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, mealSlot = 2, productId = c, grams = 300.0, isPlanned = true))
        MealConsumptionRepository(db.mealConsumptionDao())
            .setStatus(now, 2, MealConsumptionStatus.CONSUMED)

        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        assertTrue("kurczak 300g = ~495 kcal (480..510)", log.actualKcal in 480..510)
        assertEquals("1 potwierdzony posiłek", 1, log.mealsLoggedCount)
    }

    @Test
    fun `reczny wpis oznaczony SKIPPED nie liczy sie`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val c = chicken()
        // wpis RĘCZNY (isPlanned=false default) ale jawnie pominięty
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, mealSlot = 2, productId = c, grams = 300.0))
        MealConsumptionRepository(db.mealConsumptionDao())
            .setStatus(now, 2, MealConsumptionStatus.SKIPPED)

        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        assertEquals("SKIPPED → 0 kcal", 0, log.actualKcal)
        assertEquals("SKIPPED → 0 posiłków", 0, log.mealsLoggedCount)
    }

    // === v2.24.0: instrumentacja — pominięty posiłek MUSI trafić do logu diagnostycznego ===

    @Test
    fun `pominieta kolacja trafia do diagnostic log (adherence_computed + meal_status)`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val c = chicken()
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.DINNER, mealSlot = 3, productId = c, grams = 300.0))

        val diag = pl.filebit.gymtracker.data.repository.DiagnosticLogger(db.diagnosticEventDao())
        // 1) zmiana statusu na SKIPPED przez repo z loggerem
        MealConsumptionRepository(db.mealConsumptionDao(), diag)
            .setStatus(now, 3, MealConsumptionStatus.SKIPPED)
        // 2) wyliczenie adherence z loggerem
        val kit = HomeDetectors(db, context)
        AdherenceCalculator(
            dietRepo = DietRepository(db.foodProductDao(), db.mealEntryDao(), db.fastingWindowDao(), db.recipeDao()),
            dietPrefs = DietPreferences(context),
            profileRepo = UserProfileRepository(db.userProfileDao()),
            dietProfileRepo = UserDietProfileRepository(UserProfileRepository(db.userProfileDao())),
            trainingDietBridge = kit.trainingDietBridge,
            bodyDao = db.bodyMeasurementDao(),
            dao = db.adherenceLogDao(),
            consumptionRepo = MealConsumptionRepository(db.mealConsumptionDao()),
            diag = diag
        ).computeForDate(now)

        // logger pisze async (Dispatchers.IO) — odpytuj z timeoutem
        var events = emptyList<pl.filebit.gymtracker.data.entity.DiagnosticEvent>()
        var waited = 0
        while (waited < 3000) {
            events = db.diagnosticEventDao().getRecent(50)
            if (events.any { it.event == "adherence_computed" } &&
                events.any { it.event == "meal_status_set" }) break
            Thread.sleep(50); waited += 50
        }

        val statusEvent = events.firstOrNull { it.event == "meal_status_set" }
        val adherenceEvent = events.firstOrNull { it.event == "adherence_computed" }
        assertTrue("log zmiany statusu posiłku istnieje", statusEvent != null)
        assertTrue("status Posiłek 3→SKIPPED w logu", statusEvent!!.message.contains("Posiłek 3") && statusEvent.message.contains("SKIPPED"))
        assertTrue("log wyliczenia adherence istnieje", adherenceEvent != null)
        assertTrue("adherence_computed zawiera pominięty Posiłek 3",
            adherenceEvent!!.dataJson?.contains("Posiłek 3") == true)
        assertEquals("pominięty posiłek = WARN", "WARN", adherenceEvent.level)
    }

    @Test
    fun `reczny wpis bez statusu liczy sie jako zjedzony`() = runBlocking {
        seedProfile()
        val now = System.currentTimeMillis()
        val c = chicken()
        // wpis RĘCZNY bez statusu → domyślnie zjedzony (nie psujemy dziennika)
        db.mealEntryDao().upsert(MealEntry(
            dateMs = now, mealType = MealType.LUNCH, mealSlot = 2, productId = c, grams = 300.0))

        calc().computeForDate(now)
        val log = db.adherenceLogDao().getRecent(5).firstOrNull()!!

        assertTrue("ręczny wpis liczy się (~495 kcal)", log.actualKcal in 480..510)
        assertEquals("1 zalogowany posiłek", 1, log.mealsLoggedCount)
    }
}
