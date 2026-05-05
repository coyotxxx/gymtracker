package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType

class ConstraintResolverTest {

    private val resolver = ConstraintResolver()

    private fun product(id: Long, name: String, cat: FoodCategory) = FoodProduct(
        id = id, name = name, category = cat,
        kcalPer100g = 100.0, proteinPer100g = 10.0, carbsPer100g = 10.0, fatPer100g = 2.0
    )

    private val profileMale = UserProfile(
        id = 1, gender = Gender.MALE, bodyweightKg = 80.0,
        weightGoalType = WeightGoalType.CUT, goal = TrainingGoal.HYPERTROPHY
    )
    private val profileFemale = profileMale.copy(gender = Gender.FEMALE)

    @Test
    fun `safety kcal min depends on gender`() {
        val maleConstraints = resolver.resolve(profileMale, dietProfile = null)
        val femaleConstraints = resolver.resolve(profileFemale, dietProfile = null)
        val maleSafety = maleConstraints.filterIsInstance<DietConstraint.SafetyKcalMin>().first()
        val femaleSafety = femaleConstraints.filterIsInstance<DietConstraint.SafetyKcalMin>().first()
        assertEquals(1500, maleSafety.minKcal)
        assertEquals(1200, femaleSafety.minKcal)
    }

    @Test
    fun `vegan + meat in plan → HARD violation`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.VEGAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(
            product(1, "Pierś z kurczaka", FoodCategory.PROTEIN),
            product(2, "Brokuły gotowane", FoodCategory.VEGETABLE)
        )
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.any { it.constraint is DietConstraint.DietPreferenceConstraint })
        // Nazwa kurczaka w producentach (case insensitive)
        assertTrue(hardViolations.flatMap { it.productNames }.any { it.contains("kurczak", ignoreCase = true) })
    }

    @Test
    fun `vegan + dairy → HARD violation (animal origin)`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.VEGAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Mleko 0,5%", FoodCategory.DAIRY))
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.isNotEmpty())
    }

    @Test
    fun `vegetarian + fish → HARD violation (vegetarian = no meat AND no fish)`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.VEGETARIAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Łosoś świeży", FoodCategory.PROTEIN))
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.isNotEmpty())
    }

    @Test
    fun `pescatarian + fish OK, but meat NOT OK`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.PESCATARIAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)

        val planFish = listOf(product(1, "Łosoś świeży", FoodCategory.PROTEIN))
        assertTrue(resolver.checkAllHard(constraints, planFish).isEmpty())

        val planMeat = listOf(product(2, "Pierś z kurczaka", FoodCategory.PROTEIN))
        assertTrue(resolver.checkAllHard(constraints, planMeat).isNotEmpty())
    }

    @Test
    fun `allergy laktoza + dairy → HARD violation`() {
        val dietProfile = UserDietProfile(
            id = 1, allergies = "laktoza", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Twaróg chudy", FoodCategory.PROTEIN))
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.any { it.constraint is DietConstraint.Allergy })
    }

    @Test
    fun `allergy gluten + bread → HARD violation`() {
        val dietProfile = UserDietProfile(
            id = 1, allergies = "gluten", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Pieczywo razowe", FoodCategory.CARBS))
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.isNotEmpty())
    }

    @Test
    fun `allergy nuts + almonds → HARD violation`() {
        val dietProfile = UserDietProfile(
            id = 1, allergies = "orzechy", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Migdały", FoodCategory.FAT))
        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue(hardViolations.isNotEmpty())
    }

    @Test
    fun `disliked food → SOFT violation, not HARD`() {
        val dietProfile = UserDietProfile(
            id = 1, dislikedFoods = "tofu", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(product(1, "Tofu naturalne", FoodCategory.PROTEIN))

        val hardViolations = resolver.checkAllHard(constraints, plan)
        assertTrue("Disliked food should NOT be HARD", hardViolations.none { it.constraint is DietConstraint.DislikedFood })

        val softViolations = resolver.checkAllSoft(constraints, plan)
        assertTrue(softViolations.any { it.constraint is DietConstraint.DislikedFood })
    }

    @Test
    fun `prompt text contains both HARD and SOFT sections`() {
        val dietProfile = UserDietProfile(
            id = 1,
            dietPreference = DietPreference.VEGAN,
            allergies = "orzechy",
            dislikedFoods = "tofu",
            ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val text = resolver.toPromptText(constraints)
        assertTrue(text.contains("HARD"))
        assertTrue(text.contains("SOFT"))
        assertTrue(text.contains("orzechy"))
        assertTrue(text.contains("tofu"))
        assertTrue(text.contains("Weganizm"))
    }

    @Test
    fun `standard profile + no diet profile = only safety constraint`() {
        val constraints = resolver.resolve(profileMale, dietProfile = null)
        assertEquals(1, constraints.size)
        assertTrue(constraints[0] is DietConstraint.SafetyKcalMin)
    }

    @Test
    fun `clean plan = no violations`() {
        val dietProfile = UserDietProfile(id = 1, ageYears = 30, heightCm = 180)
        val constraints = resolver.resolve(profileMale, dietProfile)
        val plan = listOf(
            product(1, "Pierś z kurczaka", FoodCategory.PROTEIN),
            product(2, "Ryż basmati gotowany", FoodCategory.CARBS),
            product(3, "Brokuły gotowane", FoodCategory.VEGETABLE)
        )
        assertTrue(resolver.checkAllHard(constraints, plan).isEmpty())
    }
}
