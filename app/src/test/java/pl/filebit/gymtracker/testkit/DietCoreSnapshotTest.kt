package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.ui.diet.DietOnboardingViewModel
import pl.filebit.gymtracker.ui.diet.MealPreferencesViewModel

/**
 * v1.27 — FAZA 3.4 — snapshoty ekranów diety: onboarding + preferencje.
 *
 * DietOnboarding: wizard profilu diety (pre-fill z UserProfile, zapis
 * UserDietProfile). MealPreferences: lista ocenionych dań.
 * Główny ekran Diet (DietViewModel, 31 zależności) — patrz nota w planie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietCoreSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

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
}
