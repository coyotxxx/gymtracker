package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.ui.diet.DietOnboardingViewModel
import pl.filebit.gymtracker.ui.diet.DietViewModel
import pl.filebit.gymtracker.ui.diet.MealPreferencesViewModel

/**
 * v1.27 — FAZA 3.4 — snapshoty ekranów diety: onboarding + preferencje.
 *
 * DietOnboarding: wizard profilu diety (pre-fill z UserProfile, zapis
 * UserDietProfile). MealPreferences: lista ocenionych dań.
 * Główny ekran Diet (DietViewModel, 31 zależności) — patrz nota w planie.
 */
class DietCoreSnapshotTest : TestHarness() {


    @Test
    fun `DietOnboarding pre-fill z profilu i zapis profilu diety`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 82.0, gender = Gender.MALE, daysPerWeek = 4))
        val kit = ViewModelKit(db, context)
        val vm = DietOnboardingViewModel(kit.dietProfileRepo, kit.userProfileRepo)
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }

        TraceReport("diet-onboarding")
            .section("PRE-FILL z UserProfile")
            .kv("knownWeightKg", s.knownWeightKg?.toString() ?: "null")
            .kv("knownDaysPerWeek", s.knownDaysPerWeek.toString())
            .section("DOMYŚLNE")
            .kv("wiek/wzrost", "${s.ageYears} lat / ${s.heightCm} cm")
            .kv("cel", s.goalType.name)
            .emit()

        assertFalse("onboarding załadowany", s.isLoading)
        assertEquals("waga pre-fill z UserProfile", 82.0, s.knownWeightKg)

        // user wypełnia wizard
        vm.setAge(28)
        vm.setHeight(183)
        vm.setGoalType(DietGoalType.FAT_LOSS)
        vm.complete { }

        // complete() zapisuje przez viewModelScope.launch — czekamy na zapis
        val saved = withTimeout(5_000) {
            var p = kit.dietProfileRepo.get()
            while (p == null) { kotlinx.coroutines.delay(20); p = kit.dietProfileRepo.get() }
            p
        }
        assertEquals("wiek zapisany", 28, saved.ageYears)
        assertEquals("wzrost zapisany", 183, saved.heightCm)
        assertEquals("cel zapisany", DietGoalType.FAT_LOSS, saved.goalType)
    }

    @Test
    fun `MealPreferences pokazuje ocenione dania`() = runBlocking {
        db.mealFeedbackDao().insert(
            MealFeedback(dishKey = "owsianka", displayName = "Owsianka", rating = 5))
        db.mealFeedbackDao().insert(
            MealFeedback(dishKey = "brokul", displayName = "Brokuł na parze", rating = 2))
        val vm = MealPreferencesViewModel(MealFeedbackRepoFor())
        val items = withTimeout(5_000) { vm.items.first { it.isNotEmpty() } }

        TraceReport("meal-preferences")
            .section("CO WIDZI USER")
            .apply { items.forEach { kv(it.displayName, "ocena ${it.rating}/5") } }
            .emit()

        assertEquals("2 ocenione dania", 2, items.size)
        assertTrue("ulubione danie ma wysoką ocenę",
            items.any { it.rating >= 4 })
    }

    private fun MealFeedbackRepoFor() =
        pl.filebit.gymtracker.data.repository.MealFeedbackRepository(db.mealFeedbackDao())

    @Test
    fun `Diet glowny ekran sklada cel kcal i posilki dnia`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = 4))
        db.userDietProfileDao().upsert(UserDietProfile(
            ageYears = 30, heightCm = 180, activityLevel = ActivityLevel.MODERATE,
            goalType = DietGoalType.MAINTAIN,
            onboardingCompletedAt = System.currentTimeMillis()))
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = System.currentTimeMillis(), mealType = MealType.LUNCH,
            productId = chickenId, grams = 250.0))

        val kit = ViewModelKit(db, context)
        val vm = DietViewModel(
            kit.dietRepo, kit.userProfileRepo, kit.dietProfileRepo, kit.dietPrefs,
            kit.dietReminderScheduler, kit.dietAiService, kit.trainingDietBridge,
            kit.adherenceCalc, kit.autoAdjust, kit.dietAutoAdjustmentScheduler,
            kit.mealFeedbackRepo, kit.substituteService, kit.hydrationRepo,
            kit.hydrationCalc, kit.recoveryRepo, kit.qualityScorer, kit.weeklyBudgetCalc,
            kit.activityRepo, kit.dietPhaseRepo, kit.phaseManager, kit.recoveryAnalyzer,
            kit.dietVolatilityAnalyzer, kit.emergencyMealGen, kit.damageControl,
            kit.healthConnectManager, kit.healthConnectScheduler, kit.mealConsumptionRepo,
            kit.quickComposeService, kit.cardioKcalEstimator, db.bodyMeasurementDao(),
            db.trainingMesocycleDao()
        )
        val s = withTimeout(8_000) { vm.state.first { !it.loading } }

        TraceReport("diet-main")
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "false = załadowano")
            .kv("cel kcal", s.goal.kcal.toString())
            .kv("cel B/W/T", "${s.goal.proteinG}/${s.goal.carbsG}/${s.goal.fatG} g")
            .kv("posiłki dnia", "${s.mealsConfirmed}/${s.mealsTotal}")
            .kv("grupy posiłków", s.groups.size.toString())
            .kv("produkty w bazie", s.productsAll.size.toString())
            .verdict("needsOnboarding", vm.needsOnboarding.value.toString(),
                "false = profil diety gotowy")
            .emit()

        assertFalse("ekran diety załadowany", s.loading)
        assertTrue("cel kcal policzony (>2000 dla 80 kg M)", s.goal.kcal > 2000)
        assertTrue("makra policzone", s.goal.proteinG > 0)
        assertFalse("onboarding niepotrzebny — profil diety istnieje",
            vm.needsOnboarding.value)
    }
}
