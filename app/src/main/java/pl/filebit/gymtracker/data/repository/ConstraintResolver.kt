package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pojedyncze ograniczenie diety. HARD = nie wolno łamać, SOFT = preferencja.
 *
 * Filozofia: AI dostaje listę i instrukcję — jeśli musi złamać SOFT,
 * ma to wyjaśnić. HARD nigdy nie do złamania (alergie, choroby, weganizm).
 */
sealed class DietConstraint {
    abstract val priority: ConstraintPriority
    abstract val description: String

    /** Sprawdza czy posiłek (lista produktów) narusza to ograniczenie. */
    abstract fun isViolated(products: List<FoodProduct>): Boolean

    // === HARD ===

    data class Allergy(val allergen: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Alergia: $allergen — BEZWZGLĘDNIE unikaj"
        override fun isViolated(products: List<FoodProduct>): Boolean {
            val key = allergen.trim().lowercase()
            return products.any { p ->
                val pname = p.name.lowercase()
                allergenAliases(key).any { alias -> pname.contains(alias) }
            }
        }

        private fun allergenAliases(key: String): List<String> = when (key) {
            "laktoza" -> listOf("mleko", "śmietan", "twaróg", "ser", "jogurt", "skyr", "kefir", "masło")
            "gluten" -> listOf("pszenn", "pszenicy", "pszenica", "makaron", "pieczywo", "kasza pęczak", "kasza orkiszowa", "mąka", "wafle ryżowe")
            "jaja", "jajka" -> listOf("jajk", "jaja", "białko jaja")
            "orzechy" -> listOf("orzech", "migdał", "nerkowiec", "masło orzech")
            "ryby", "ryba" -> listOf("łosoś", "dorsz", "tuńczyk", "ryba", "śledź", "halibut", "makrela")
            "owoce_morza" -> listOf("krewetk", "kalmar", "ostryga", "małż")
            "soja" -> listOf("tofu", "soj", "edamame")
            "sezam" -> listOf("sezam", "tahini")
            else -> listOf(key)
        }
    }

    data class Intolerance(val substance: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Nietolerancja: $substance"
        override fun isViolated(products: List<FoodProduct>): Boolean =
            Allergy(substance).isViolated(products) // ten sam mechanizm
    }

    data class DietPreferenceConstraint(val preference: DietPreference) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = when (preference) {
            DietPreference.STANDARD -> "—"
            DietPreference.VEGETARIAN -> "Wegetarianizm: BEZ mięsa i ryb"
            DietPreference.VEGAN -> "Weganizm: BEZ produktów odzwierzęcych (mięso, ryby, nabiał, jaja, miód)"
            DietPreference.PESCATARIAN -> "Pescetarianizm: ryby OK, BEZ mięsa"
            DietPreference.KETO -> "Keto: max 30g węgli netto/dzień"
            DietPreference.MEDITERRANEAN -> "Śródziemnomorska — preferuj oliwę, ryby, warzywa"
        }

        override fun isViolated(products: List<FoodProduct>): Boolean = when (preference) {
            DietPreference.STANDARD, DietPreference.MEDITERRANEAN -> false
            DietPreference.VEGETARIAN -> products.any { isMeatOrFish(it) }
            DietPreference.VEGAN -> products.any { isAnimalOrigin(it) }
            DietPreference.PESCATARIAN -> products.any { isMeat(it) && !isFish(it) }
            DietPreference.KETO -> false // sprawdzane na poziomie sumy dnia, nie pojedynczego produktu
        }

        private fun isMeat(p: FoodProduct): Boolean {
            val n = p.name.lowercase()
            return listOf("kurczak", "indyk", "wołowin", "wieprzow", "schab", "polędwicz", "mięs", "udko", "bażant", "kaczka", "królik", "dziczyzn").any { n.contains(it) }
        }

        private fun isFish(p: FoodProduct): Boolean {
            val n = p.name.lowercase()
            return listOf("łosoś", "dorsz", "tuńczyk", "śledź", "halibut", "makrela", "ryba").any { n.contains(it) }
        }

        private fun isMeatOrFish(p: FoodProduct): Boolean = isMeat(p) || isFish(p) ||
            p.name.lowercase().let { n -> n.contains("krewetk") || n.contains("kalmar") }

        private fun isAnimalOrigin(p: FoodProduct): Boolean {
            if (isMeatOrFish(p)) return true
            val n = p.name.lowercase()
            return listOf("jajk", "jaja", "białko jaja", "mleko", "twaróg", "ser ", "serek", "jogurt", "skyr", "kefir", "masło", "miód").any { n.contains(it) } ||
                p.category == FoodCategory.DAIRY
        }
    }

    data class MedicalCondition(val condition: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Stan zdrowia: $condition — wymaga konsultacji"
        // Walidacja jest miękka — system tylko ostrzega, nie blokuje
        override fun isViolated(products: List<FoodProduct>): Boolean = false
    }

    data class SafetyKcalMin(val minKcal: Int) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Minimalne kcal/dzień: $minKcal (bezpieczeństwo)"
        override fun isViolated(products: List<FoodProduct>): Boolean = false // poziom dnia
    }

    // === SOFT ===

    data class DislikedFood(val food: String) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Nielubiane: $food"
        override fun isViolated(products: List<FoodProduct>): Boolean {
            val key = food.trim().lowercase()
            return products.any { it.name.lowercase().contains(key) }
        }
    }

    data class CookingTimeMax(val minutes: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Max czas gotowania: $minutes min"
        override fun isViolated(products: List<FoodProduct>): Boolean = false // poziom przepisu
    }

    data class WeeklyBudget(val pln: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Budżet tygodniowy: $pln zł"
        override fun isViolated(products: List<FoodProduct>): Boolean = false // bez bazy cen
    }

    data class VarietyMin(val uniqueProductsPerWeek: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Minimalna różnorodność: $uniqueProductsPerWeek produktów/tydzień"
        override fun isViolated(products: List<FoodProduct>): Boolean = false // poziom tygodnia
    }
}

enum class ConstraintPriority {
    HARD,   // ZAKAZ łamania (alergie, choroby, weganizm, safety)
    SOFT    // preferencja, można zignorować z wyjaśnieniem
}

data class ConstraintViolation(
    val constraint: DietConstraint,
    val productNames: List<String>,
    val context: String = ""
)

/**
 * Buduje listę DietConstraint z UserDietProfile + UserProfile.
 * Sprawdza posiłki/dni przeciwko liście.
 */
@Singleton
class ConstraintResolver @Inject constructor() {

    /**
     * Tworzy listę aktywnych ograniczeń dla usera. Bierze pod uwagę
     * SAFETY_KCAL_MIN per płeć (1500 mężczyzna / 1200 kobieta).
     */
    fun resolve(profile: UserProfile, dietProfile: UserDietProfile?): List<DietConstraint> {
        val list = mutableListOf<DietConstraint>()

        // === HARD ===
        val safetyKcalMin = if (profile.gender == Gender.FEMALE) 1200 else 1500
        list += DietConstraint.SafetyKcalMin(safetyKcalMin)

        dietProfile?.let { dp ->
            dp.parsedAllergies().forEach { list += DietConstraint.Allergy(it) }
            if (dp.intolerances.isNotBlank()) {
                dp.intolerances.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    list += DietConstraint.Intolerance(it)
                }
            }
            if (dp.dietPreference != DietPreference.STANDARD) {
                list += DietConstraint.DietPreferenceConstraint(dp.dietPreference)
            }
            dp.parsedMedicalConditions().forEach { list += DietConstraint.MedicalCondition(it) }

            // === SOFT ===
            dp.parsedDislikedFoods().forEach { list += DietConstraint.DislikedFood(it) }
            list += DietConstraint.CookingTimeMax(dp.cookingTimePerMealMin)
            dp.weeklyBudgetPln?.let { list += DietConstraint.WeeklyBudget(it) }
            list += DietConstraint.VarietyMin(uniqueProductsPerWeek = 10)
        }

        return list
    }

    fun checkAllHard(
        constraints: List<DietConstraint>,
        productsInPlan: List<FoodProduct>
    ): List<ConstraintViolation> = constraints
        .filter { it.priority == ConstraintPriority.HARD }
        .mapNotNull { c ->
            val violators = productsInPlan.filter { p -> c.isViolated(listOf(p)) }
            if (violators.isNotEmpty()) ConstraintViolation(c, violators.map { it.name })
            else null
        }

    fun checkAllSoft(
        constraints: List<DietConstraint>,
        productsInPlan: List<FoodProduct>
    ): List<ConstraintViolation> = constraints
        .filter { it.priority == ConstraintPriority.SOFT }
        .mapNotNull { c ->
            val violators = productsInPlan.filter { p -> c.isViolated(listOf(p)) }
            if (violators.isNotEmpty()) ConstraintViolation(c, violators.map { it.name })
            else null
        }

    /**
     * Tekst dla promptu AI.
     *
     * AI dostaje 2 sekcje: HARD (zakaz łamania) i SOFT (preferencja).
     * Dla SOFT instrukcja: "jeśli musisz złamać, dodaj softConstraintViolations w JSON".
     */
    fun toPromptText(constraints: List<DietConstraint>): String {
        val hardList = constraints.filter { it.priority == ConstraintPriority.HARD }
        val softList = constraints.filter { it.priority == ConstraintPriority.SOFT }
        return buildString {
            if (hardList.isNotEmpty()) {
                append("=== HARD CONSTRAINTS (NIE WOLNO ŁAMAĆ — naruszenie = błąd) ===\n")
                hardList.forEach { append("- ${it.description}\n") }
            }
            if (softList.isNotEmpty()) {
                append("\n=== SOFT CONSTRAINTS (preferencje — preferuj, ale jeśli kolidują z HARD lub niemożliwe — zignoruj i wyjaśnij) ===\n")
                softList.forEach { append("- ${it.description}\n") }
                append("\nJeśli musisz zignorować SOFT constraint, dodaj do odpowiedzi pole `softConstraintViolations`: tablica stringów z opisem co zignorowałeś i dlaczego.\n")
            }
        }
    }
}
