package pl.filebit.gymtracker.ai

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
import pl.filebit.gymtracker.data.repository.ConstraintResolver

class AiMealJsonValidatorTest {

    private val resolver = ConstraintResolver()
    private val validator = AiMealJsonValidator(resolver)

    private fun product(
        id: Long, name: String, cat: FoodCategory,
        kcal: Double = 100.0, protein: Double = 10.0, carbs: Double = 10.0, fat: Double = 2.0
    ) = FoodProduct(
        id = id, name = name, category = cat,
        kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat
    )

    private val rice = product(1, "Ryż basmati gotowany", FoodCategory.CARBS, 121.0, 3.0, 25.0, 0.4)
    private val chicken = product(2, "Pierś z kurczaka", FoodCategory.PROTEIN, 165.0, 31.0, 0.0, 3.6)
    private val broccoli = product(3, "Brokuły gotowane", FoodCategory.VEGETABLE, 35.0, 2.4, 7.2, 0.4)
    private val curd = product(4, "Twaróg chudy", FoodCategory.PROTEIN, 100.0, 19.0, 3.5, 0.5)
    private val tofu = product(5, "Tofu naturalne", FoodCategory.PROTEIN, 144.0, 17.0, 2.8, 8.7)
    private val almonds = product(6, "Migdały", FoodCategory.FAT, 579.0, 21.2, 21.6, 49.9)
    private val eggWhole = product(7, "Jajko całe", FoodCategory.PROTEIN, 155.0, 13.0, 1.1, 11.0)
    private val eggWhite = product(8, "Białko jaja", FoodCategory.PROTEIN, 52.0, 10.9, 0.7, 0.2)

    private val productMap: Map<String, FoodProduct> =
        listOf(rice, chicken, broccoli, curd, tofu, almonds, eggWhole, eggWhite)
            .associateBy { it.name.lowercase() }

    private val profileMale = UserProfile(
        id = 1, gender = Gender.MALE, bodyweightKg = 80.0,
        weightGoalType = WeightGoalType.CUT, goal = TrainingGoal.HYPERTROPHY
    )

    // Testy jednostkowe testują pojedyncze aspekty (constraints, count, gramatura).
    // Wyłączamy enforceDailyKcal — nowy twardy check ±7% jest pokryty przez
    // dedykowany test poniżej.
    private fun ctx(
        meals: Int = 3,
        kcal: Int = 2000,
        protein: Int = 160,
        constraints: List<pl.filebit.gymtracker.data.repository.DietConstraint> = emptyList(),
        enforceDailyKcal: Boolean = false,
        enforceDailyMacros: Boolean = false
    ) = ValidationContext(
        expectedMealsCount = meals,
        targetKcal = kcal,
        targetProteinG = protein,
        perMealProteinMinG = 30,
        maxCookingMinutesPerMeal = 20,
        productsByName = productMap,
        constraints = constraints,
        enforceDailyKcal = enforceDailyKcal,
        enforceDailyMacros = enforceDailyMacros
    )

    private fun goodPlan() = AiDayPlan(
        meals = listOf(
            AiMealRecipe(
                name = "Owsianka",
                ingredients = listOf(
                    AiRecipeIngredient("Ryż basmati gotowany", 200),
                    AiRecipeIngredient("Twaróg chudy", 200)
                ),
                instructions = "...",
                prepMinutes = 10,
                kcal = 442, proteinG = 44, carbsG = 57, fatG = 1
            ),
            AiMealRecipe(
                name = "Obiad",
                ingredients = listOf(
                    AiRecipeIngredient("Pierś z kurczaka", 200),
                    AiRecipeIngredient("Ryż basmati gotowany", 200),
                    AiRecipeIngredient("Brokuły gotowane", 200)
                ),
                instructions = "...",
                prepMinutes = 15,
                kcal = 642, proteinG = 75, carbsG = 64, fatG = 9
            ),
            AiMealRecipe(
                name = "Kolacja",
                ingredients = listOf(
                    AiRecipeIngredient("Twaróg chudy", 250),
                    AiRecipeIngredient("Brokuły gotowane", 200)
                ),
                instructions = "...",
                prepMinutes = 5,
                kcal = 320, proteinG = 53, carbsG = 23, fatG = 2
            )
        )
    )

    @Test
    fun `valid plan with no constraints passes`() {
        val result = validator.validate(goodPlan(), ctx())
        assertTrue("isValid should be true", result.isValid)
        assertTrue("no errors", result.errors.isEmpty())
    }

    @Test
    fun `vegan + chicken in plan returns HARD error`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.VEGAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val result = validator.validate(goodPlan(), ctx(constraints = constraints))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "hard_constraint_violation" })
    }

    @Test
    fun `allergy laktoza + curd → HARD error`() {
        val dietProfile = UserDietProfile(
            id = 1, allergies = "laktoza", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val result = validator.validate(goodPlan(), ctx(constraints = constraints))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "hard_constraint_violation" })
    }

    @Test
    fun `unknown product produces warning, not error`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Mystery",
                ingredients = listOf(AiRecipeIngredient("XYZ Nieznany Produkt 123", 100)),
                instructions = "...", prepMinutes = 5,
                kcal = 100, proteinG = 10, carbsG = 10, fatG = 2
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue("Should be valid (only warning)", result.isValid)
        assertTrue(result.warnings.any { it.code == "unknown_product" })
    }

    @Test
    fun `Levenshtein matches ryz to ryż basmati`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Test",
                ingredients = listOf(AiRecipeIngredient("ryz basmati gotowany", 100)),
                instructions = "...", prepMinutes = 5,
                kcal = 121, proteinG = 3, carbsG = 25, fatG = 0
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue(result.productMatches["ryz basmati gotowany"] != null)
    }

    @Test
    fun `kcal AI mismatch with base produces warning`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Test",
                ingredients = listOf(AiRecipeIngredient("Ryż basmati gotowany", 100)), // real 121 kcal
                instructions = "...", prepMinutes = 5,
                kcal = 500, proteinG = 30, carbsG = 25, fatG = 0  // AI deklaruje 500 kcal!
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue(result.warnings.any { it.code == "ai_kcal_mismatch" })
    }

    @Test
    fun `corrected total uses base kcal not AI`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Test",
                ingredients = listOf(AiRecipeIngredient("Ryż basmati gotowany", 100)),
                instructions = "...", prepMinutes = 5,
                kcal = 9999, proteinG = 999, carbsG = 999, fatG = 999
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        // Real: 121 kcal (from base)
        assertEquals(121, result.correctedTotal.totalKcal)
        assertEquals(3, result.correctedTotal.totalProteinG)
    }

    @Test
    fun `cooking time exceeded → warning`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Slow",
                ingredients = listOf(AiRecipeIngredient("Pierś z kurczaka", 200)),
                instructions = "...", prepMinutes = 60,
                kcal = 330, proteinG = 62, carbsG = 0, fatG = 7
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue(result.warnings.any { it.code == "cooking_time_exceeded" })
    }

    @Test
    fun `safety kcal min violation → ERROR`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Tiny",
                ingredients = listOf(AiRecipeIngredient("Brokuły gotowane", 50)),
                instructions = "...", prepMinutes = 3,
                kcal = 17, proteinG = 1, carbsG = 3, fatG = 0
            )
        ))
        val constraints = resolver.resolve(profileMale, dietProfile = null) // safetyMin=1500
        val result = validator.validate(plan, ctx(meals = 1, kcal = 2000, constraints = constraints))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "safety_kcal_too_low" })
    }

    @Test
    fun `dinner high carb → warning`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Śniadanie",
                ingredients = listOf(AiRecipeIngredient("Twaróg chudy", 200)),
                instructions = "...", prepMinutes = 5,
                kcal = 200, proteinG = 38, carbsG = 7, fatG = 1
            ),
            AiMealRecipe(
                name = "Kolacja",
                ingredients = listOf(AiRecipeIngredient("Ryż basmati gotowany", 300)), // 75g węgli
                instructions = "...", prepMinutes = 10,
                kcal = 363, proteinG = 9, carbsG = 75, fatG = 1
            )
        ))
        val result = validator.validate(plan, ctx(meals = 2))
        assertTrue(result.warnings.any { it.code == "dinner_high_carb" })
    }

    @Test
    fun `gramatura over 1000g produces warning`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Big",
                ingredients = listOf(AiRecipeIngredient("Brokuły gotowane", 1500)),
                instructions = "...", prepMinutes = 5,
                kcal = 525, proteinG = 36, carbsG = 108, fatG = 6
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue(result.warnings.any { it.code == "grams_out_of_range" })
    }

    @Test
    fun `meal count mismatch produces ERROR (hard violation)`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe("X", listOf(AiRecipeIngredient("Brokuły gotowane", 100)), "...", 5, 35, 2, 7, 0)
        ))
        val result = validator.validate(plan, ctx(meals = 3))
        assertTrue(result.errors.any { it.code == "meals_count_mismatch" })
        assertFalse(result.isValid)
    }

    @Test
    fun `disliked food → SOFT warning, not ERROR`() {
        val dietProfile = UserDietProfile(
            id = 1, dislikedFoods = "tofu", ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        // Plan musi mieć >=1500 kcal (safety) — 1100g tofu = 1584 kcal
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Tofu meal",
                ingredients = listOf(AiRecipeIngredient("Tofu naturalne", 800)),
                instructions = "...", prepMinutes = 8,
                kcal = 1152, proteinG = 136, carbsG = 22, fatG = 70
            ),
            AiMealRecipe(
                name = "Sałatka",
                ingredients = listOf(AiRecipeIngredient("Brokuły gotowane", 200)),
                instructions = "...", prepMinutes = 5,
                kcal = 70, proteinG = 5, carbsG = 14, fatG = 1
            ),
            AiMealRecipe(
                name = "Drugi tofu",
                ingredients = listOf(AiRecipeIngredient("Tofu naturalne", 300)),
                instructions = "...", prepMinutes = 5,
                kcal = 432, proteinG = 51, carbsG = 8, fatG = 26
            )
        ))
        val result = validator.validate(plan, ctx(meals = 3, constraints = constraints))
        assertTrue("isValid should be true (only SOFT violations): errors=${result.errors}", result.isValid)
        assertTrue(result.warnings.any { it.code == "soft_constraint_violation" })
    }

    @Test
    fun `empty ingredients → ERROR`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe("Empty", emptyList(), "...", 5, 0, 0, 0, 0)
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "empty_ingredients" })
    }

    @Test
    fun `retry feedback contains all errors`() {
        val dietProfile = UserDietProfile(
            id = 1, dietPreference = DietPreference.VEGAN, ageYears = 30, heightCm = 180
        )
        val constraints = resolver.resolve(profileMale, dietProfile)
        val result = validator.validate(goodPlan(), ctx(constraints = constraints))
        val feedback = validator.buildRetryFeedback(result)
        assertTrue(feedback.contains("hard_constraint_violation"))
    }

    @Test
    fun `low protein per meal → warning`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Low protein",
                ingredients = listOf(AiRecipeIngredient("Brokuły gotowane", 200)),
                instructions = "...", prepMinutes = 5,
                kcal = 70, proteinG = 5, carbsG = 14, fatG = 1
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue(result.warnings.any { it.code == "low_protein" })
    }

    @Test
    fun `daily kcal under target by 20 percent → ERROR`() {
        // Plan ma realnie 1404 kcal (z bazy), target 2000 → odchylenie 30% > 7% = ERROR
        val result = validator.validate(
            goodPlan(),
            ctx(kcal = 2000, enforceDailyKcal = true)
        )
        assertFalse("isValid powinno być false", result.isValid)
        assertTrue(
            "powinien być ERROR daily_kcal_off_target",
            result.errors.any { it.code == "daily_kcal_off_target" }
        )
    }

    @Test
    fun `daily kcal within 5 percent → no error`() {
        // goodPlan = 1404 kcal real, target 1400 → odchylenie ~0.3% = OK
        val result = validator.validate(
            goodPlan(),
            ctx(kcal = 1400, enforceDailyKcal = true)
        )
        assertTrue("daily kcal blisko targetu — brak ERROR", result.errors.none { it.code == "daily_kcal_off_target" })
    }

    @Test
    fun `protein WAY too low produces ERROR`() {
        // Mało białka, dużo węgli — niedobór białka
        val lowProteinPlan = AiDayPlan(
            meals = listOf(
                AiMealRecipe(
                    name = "Czysty ryż",
                    ingredients = listOf(
                        AiRecipeIngredient("Ryż basmati gotowany", 800)   // 24g białka
                    ),
                    instructions = "...", prepMinutes = 10,
                    kcal = 968, proteinG = 24, carbsG = 200, fatG = 3
                )
            )
        )
        val customCtx = ValidationContext(
            expectedMealsCount = 1,
            targetKcal = 968,
            targetProteinG = 100,    // 24g real vs 100g target = -76% niedobór
            targetCarbsG = 200,
            targetFatG = 20,
            perMealProteinMinG = 30,
            maxCookingMinutesPerMeal = 20,
            productsByName = productMap,
            constraints = emptyList(),
            enforceDailyMacros = true
        )
        val result = validator.validate(lowProteinPlan, customCtx)
        assertFalse(result.isValid)
        assertTrue(
            "powinien być ERROR daily_protein_too_low",
            result.errors.any { it.code == "daily_protein_too_low" }
        )
    }

    @Test
    fun `protein over target by 50 percent passes (nadmiar bialka nie szkodzi)`() {
        // Filozofia: cel białka to MINIMUM, nadmiar OK
        val highProteinPlan = AiDayPlan(
            meals = listOf(
                AiMealRecipe(
                    name = "Białkowa bomba",
                    ingredients = listOf(
                        AiRecipeIngredient("Pierś z kurczaka", 400),  // 124g
                        AiRecipeIngredient("Twaróg chudy", 400)         // 76g
                    ),
                    instructions = "...", prepMinutes = 10,
                    kcal = 1060, proteinG = 200, carbsG = 14, fatG = 16
                )
            )
        )
        val customCtx = ValidationContext(
            expectedMealsCount = 1,
            targetKcal = 1060,
            targetProteinG = 130,    // 200g vs cel 130 = +54% — nadmiar
            targetCarbsG = 100,
            targetFatG = 30,
            perMealProteinMinG = 30,
            maxCookingMinutesPerMeal = 20,
            productsByName = productMap,
            constraints = emptyList(),
            enforceDailyMacros = true
        )
        val result = validator.validate(highProteinPlan, customCtx)
        assertTrue("nadmiar białka NIE powinien być ERROR", result.errors.none { it.code.contains("protein") })
    }

    // ── v1.27.1 — limit tłuszczu per posiłek (regresja: kolacja B42/W12/T47) ──

    @Test
    fun `posilek z ponad 55 procent kcal z tluszczu → ERROR slot_fat_too_high`() {
        // 100 g migdałów = 579 kcal, 50 g tłuszczu → 77% kcal z tłuszczu.
        // To wzorzec patologii "kolacja jajka+awokado+oliwa" (B42/W12/T47).
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Tłusta kolacja",
                ingredients = listOf(AiRecipeIngredient("Migdały", 100)),
                instructions = "...", prepMinutes = 5,
                kcal = 579, proteinG = 21, carbsG = 22, fatG = 50
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertFalse("posiłek za tłusty → niewalidny", result.isValid)
        assertTrue("ERROR slot_fat_too_high",
            result.errors.any { it.code == "slot_fat_too_high" })
    }

    @Test
    fun `posilek o normalnym tluszczu nie triggeruje slot_fat_too_high`() {
        // goodPlan: posiłki mają 1/9/2 g tłuszczu — daleko poniżej 55% kcal
        val result = validator.validate(goodPlan(), ctx())
        assertTrue("normalne posiłki nie są flagowane jako za tłuste",
            result.errors.none { it.code == "slot_fat_too_high" })
    }

    // ── v1.27.6 — AI nie rozdziela jajek na same białka ──

    @Test
    fun `posilek z Bialko jaja → ERROR egg_white_split_not_allowed`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Omlet białkowy",
                ingredients = listOf(
                    AiRecipeIngredient("Jajko całe", 100),
                    AiRecipeIngredient("Białko jaja", 150)
                ),
                instructions = "...", prepMinutes = 8,
                kcal = 233, proteinG = 29, carbsG = 2, fatG = 11
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertFalse("rozdzielanie jajek na białka → niewalidny", result.isValid)
        assertTrue("ERROR egg_white_split_not_allowed",
            result.errors.any { it.code == "egg_white_split_not_allowed" })
    }

    @Test
    fun `posilek z samym Jajko cale nie triggeruje egg_white error`() {
        val plan = AiDayPlan(meals = listOf(
            AiMealRecipe(
                name = "Jajecznica",
                ingredients = listOf(AiRecipeIngredient("Jajko całe", 200)),
                instructions = "...", prepMinutes = 6,
                kcal = 310, proteinG = 26, carbsG = 2, fatG = 22
            )
        ))
        val result = validator.validate(plan, ctx(meals = 1))
        assertTrue("całe jaja są w porządku",
            result.errors.none { it.code == "egg_white_split_not_allowed" })
    }

    @Test
    fun `fat too low produces ERROR`() {
        // Niedobór tłuszczu — istotny problem hormonalny
        val noFatPlan = AiDayPlan(
            meals = listOf(
                AiMealRecipe(
                    name = "Beztłuszczowy",
                    ingredients = listOf(
                        AiRecipeIngredient("Pierś z kurczaka", 200),
                        AiRecipeIngredient("Ryż basmati gotowany", 200),
                        AiRecipeIngredient("Brokuły gotowane", 200)
                    ),
                    instructions = "...", prepMinutes = 10,
                    kcal = 642, proteinG = 75, carbsG = 64, fatG = 9
                )
            )
        )
        val customCtx = ValidationContext(
            expectedMealsCount = 1,
            targetKcal = 642,
            targetProteinG = 70,
            targetCarbsG = 60,
            targetFatG = 30,    // 9g real vs cel 30 = -70% niedobór
            perMealProteinMinG = 30,
            maxCookingMinutesPerMeal = 20,
            productsByName = productMap,
            constraints = emptyList(),
            enforceDailyMacros = true
        )
        val result = validator.validate(noFatPlan, customCtx)
        assertFalse(result.isValid)
        assertTrue(
            "powinien być ERROR daily_fat_too_low",
            result.errors.any { it.code == "daily_fat_too_low" }
        )
    }
}
