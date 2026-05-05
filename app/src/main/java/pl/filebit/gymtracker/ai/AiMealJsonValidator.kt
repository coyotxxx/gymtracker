package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.ConstraintPriority
import pl.filebit.gymtracker.data.repository.ConstraintResolver
import pl.filebit.gymtracker.data.repository.DietConstraint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

enum class ValidationSeverity { ERROR, WARNING }

data class ValidationIssue(
    val severity: ValidationSeverity,
    val mealIndex: Int?,           // null = global (cały plan)
    val code: String,
    val message: String
)

data class CorrectedMacros(
    val totalKcal: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int
)

data class ValidationResult(
    val isValid: Boolean,                  // false = HARD errors
    val errors: List<ValidationIssue>,     // HARD violations — odrzuć/retry
    val warnings: List<ValidationIssue>,   // SOFT issues — wstaw + ostrzeż
    val correctedTotal: CorrectedMacros,
    /** Mapping AI productName → matched FoodProduct (null jeśli nie znaleziono). */
    val productMatches: Map<String, FoodProduct?>
)

data class ValidationContext(
    val expectedMealsCount: Int,
    val targetKcal: Int,
    val targetProteinG: Int,
    val perMealProteinMinG: Int,
    val maxCookingMinutesPerMeal: Int,
    val ketoMaxCarbsG: Int? = null,
    val lowCarbDinnerMaxG: Int = 30,
    val productsByName: Map<String, FoodProduct>,  // klucz = name.lowercase()
    val constraints: List<DietConstraint>
)

/**
 * Walidator odpowiedzi AI dla planu dnia żywieniowego.
 *
 * Polityka:
 *  - AI deklarowane kcal/makro IGNORUJEMY w obliczeniach końcowych — bierzemy z bazy
 *  - HARD constraints (alergie/preferencje/medical/safety) → ERROR, retry/odrzuć
 *  - SOFT constraints (lubi/nie lubi, czas, budżet, wariancja) → WARNING, wstaw plan
 *  - Niedopasowane produkty (Levenshtein <3 nie znalazł) → WARNING, slot pominięty
 *  - kcal AI vs kcal z bazy odchylenie >10% → WARNING (info), używamy bazy
 */
@Singleton
class AiMealJsonValidator @Inject constructor(
    private val constraintResolver: ConstraintResolver
) {

    fun validate(plan: AiDayPlan, ctx: ValidationContext): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val productMatches = HashMap<String, FoodProduct?>()

        var totalKcalReal = 0
        var totalProteinReal = 0
        var totalCarbsReal = 0
        var totalFatReal = 0

        // === Walidacja liczby posiłków ===
        if (plan.meals.size != ctx.expectedMealsCount) {
            warnings += ValidationIssue(
                severity = ValidationSeverity.WARNING,
                mealIndex = null,
                code = "meals_count_mismatch",
                message = "AI zwróciło ${plan.meals.size} posiłków, oczekiwano ${ctx.expectedMealsCount}."
            )
        }

        // === Walidacja każdego posiłku ===
        plan.meals.forEachIndexed { mIdx, meal ->
            // Pusta lista składników
            if (meal.ingredients.isEmpty()) {
                errors += ValidationIssue(
                    ValidationSeverity.ERROR, mIdx, "empty_ingredients",
                    "Posiłek '${meal.name}' nie ma składników."
                )
                return@forEachIndexed
            }

            // Czas gotowania
            if (meal.prepMinutes > ctx.maxCookingMinutesPerMeal) {
                warnings += ValidationIssue(
                    ValidationSeverity.WARNING, mIdx, "cooking_time_exceeded",
                    "Posiłek '${meal.name}' wymaga ${meal.prepMinutes} min, max ${ctx.maxCookingMinutesPerMeal} min."
                )
            }

            // Walidacja składników
            val matchedProducts = mutableListOf<Pair<FoodProduct, Double>>() // (product, grams)
            var mealKcalReal = 0.0
            var mealProteinReal = 0.0
            var mealCarbsReal = 0.0
            var mealFatReal = 0.0

            meal.ingredients.forEach { ing ->
                // Sanity gramatury
                if (ing.grams < 5 || ing.grams > 1000) {
                    warnings += ValidationIssue(
                        ValidationSeverity.WARNING, mIdx, "grams_out_of_range",
                        "${ing.productName}: ${ing.grams}g poza rozsądnym zakresem [5-1000g]."
                    )
                }

                // Match produktu
                val match = matchProduct(ing.productName, ctx.productsByName)
                productMatches[ing.productName] = match
                if (match == null) {
                    warnings += ValidationIssue(
                        ValidationSeverity.WARNING, mIdx, "unknown_product",
                        "Produkt '${ing.productName}' nie znaleziony w bazie — pominięty."
                    )
                    return@forEach
                }
                matchedProducts += match to ing.grams.toDouble()

                // Liczenie real kcal/macro z bazy
                val factor = ing.grams / 100.0
                mealKcalReal += match.kcalPer100g * factor
                mealProteinReal += match.proteinPer100g * factor
                mealCarbsReal += match.carbsPer100g * factor
                mealFatReal += match.fatPer100g * factor
            }

            // Walidacja vs deklarowane przez AI
            if (meal.kcal > 0 && abs(meal.kcal - mealKcalReal) > meal.kcal * 0.10) {
                warnings += ValidationIssue(
                    ValidationSeverity.WARNING, mIdx, "ai_kcal_mismatch",
                    "AI deklaruje ${meal.kcal} kcal, real (z bazy): ${mealKcalReal.toInt()} kcal. Używam realnej."
                )
            }

            // Białko per posiłek (cel ~70% perMealProteinMin)
            if (mealProteinReal < ctx.perMealProteinMinG) {
                warnings += ValidationIssue(
                    ValidationSeverity.WARNING, mIdx, "low_protein",
                    "Posiłek '${meal.name}' ma tylko ${mealProteinReal.toInt()}g białka, cel ≥${ctx.perMealProteinMinG}g."
                )
            }

            // === HARD constraints check ===
            val productsInMeal = matchedProducts.map { it.first }
            ctx.constraints
                .filter { it.priority == ConstraintPriority.HARD }
                .forEach { constraint ->
                    val violators = productsInMeal.filter { p -> constraint.isViolated(listOf(p)) }
                    if (violators.isNotEmpty()) {
                        errors += ValidationIssue(
                            ValidationSeverity.ERROR, mIdx, "hard_constraint_violation",
                            "Posiłek '${meal.name}' łamie HARD: ${constraint.description}. Produkty: ${violators.joinToString(", ") { it.name }}"
                        )
                    }
                }

            // SOFT constraints check
            ctx.constraints
                .filter { it.priority == ConstraintPriority.SOFT }
                .forEach { constraint ->
                    val violators = productsInMeal.filter { p -> constraint.isViolated(listOf(p)) }
                    if (violators.isNotEmpty()) {
                        warnings += ValidationIssue(
                            ValidationSeverity.WARNING, mIdx, "soft_constraint_violation",
                            "Posiłek '${meal.name}' narusza SOFT: ${constraint.description}. Produkty: ${violators.joinToString(", ") { it.name }}"
                        )
                    }
                }

            totalKcalReal += mealKcalReal.toInt()
            totalProteinReal += mealProteinReal.toInt()
            totalCarbsReal += mealCarbsReal.toInt()
            totalFatReal += mealFatReal.toInt()
        }

        // === Walidacja sumy dnia ===
        if (totalKcalReal > 0) {
            val kcalDeviation = abs(totalKcalReal - ctx.targetKcal).toDouble() / ctx.targetKcal
            if (kcalDeviation > 0.10) {
                warnings += ValidationIssue(
                    ValidationSeverity.WARNING, null, "daily_kcal_off_target",
                    "Suma dnia: ${totalKcalReal} kcal vs cel ${ctx.targetKcal} kcal (odchylenie ${(kcalDeviation * 100).toInt()}%)."
                )
            }
        }

        // Białko dnia
        if (totalProteinReal < ctx.targetProteinG * 0.85) {
            warnings += ValidationIssue(
                ValidationSeverity.WARNING, null, "daily_protein_low",
                "Białko dnia: ${totalProteinReal}g (cel ${ctx.targetProteinG}g)."
            )
        }

        // Keto check
        ctx.ketoMaxCarbsG?.let { maxCarbs ->
            if (totalCarbsReal > maxCarbs) {
                errors += ValidationIssue(
                    ValidationSeverity.ERROR, null, "keto_carbs_exceeded",
                    "Keto: węgli ${totalCarbsReal}g > limit ${maxCarbs}g."
                )
            }
        }

        // === Safety kcal min ===
        ctx.constraints.filterIsInstance<DietConstraint.SafetyKcalMin>().firstOrNull()?.let { safety ->
            if (totalKcalReal in 1 until safety.minKcal) {
                errors += ValidationIssue(
                    ValidationSeverity.ERROR, null, "safety_kcal_too_low",
                    "Plan ma ${totalKcalReal} kcal — poniżej bezpiecznego min ${safety.minKcal} kcal."
                )
            }
        }

        // Kolacja low-carb (jeśli ostatni meal ma >30g węgli)
        plan.meals.lastOrNull()?.let { dinner ->
            val dinnerProductsCarbs = dinner.ingredients.sumOf { ing ->
                val product = matchProduct(ing.productName, ctx.productsByName)
                if (product != null) product.carbsPer100g * ing.grams / 100.0 else 0.0
            }
            if (dinnerProductsCarbs > ctx.lowCarbDinnerMaxG) {
                warnings += ValidationIssue(
                    ValidationSeverity.WARNING, plan.meals.lastIndex, "dinner_high_carb",
                    "Kolacja '${dinner.name}': ${dinnerProductsCarbs.toInt()}g węgli (zalecane <${ctx.lowCarbDinnerMaxG}g, regeneracja+sen)."
                )
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            correctedTotal = CorrectedMacros(totalKcalReal, totalProteinReal, totalCarbsReal, totalFatReal),
            productMatches = productMatches
        )
    }

    /**
     * Match produktu po nazwie:
     *  1. Exact (lowercase)
     *  2. Najbliższe Levenshtein <=2 (do 3 dla długich nazw)
     */
    private fun matchProduct(query: String, productsByName: Map<String, FoodProduct>): FoodProduct? {
        val key = query.trim().lowercase()
        productsByName[key]?.let { return it }

        // Najpierw szukaj częściowego dopasowania (substring)
        productsByName.entries
            .firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }
            ?.let { return it.value }

        // Levenshtein
        val maxDist = if (key.length > 12) 3 else 2
        var bestMatch: FoodProduct? = null
        var bestDist = Int.MAX_VALUE
        for ((k, product) in productsByName) {
            val dist = levenshtein(key, k)
            if (dist < bestDist && dist <= maxDist) {
                bestDist = dist
                bestMatch = product
            }
        }
        return bestMatch
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return curr[b.length]
    }

    /**
     * Buduje feedback do AI gdy chcemy retry — wskazuje co poszło źle.
     */
    fun buildRetryFeedback(result: ValidationResult): String = buildString {
        if (result.errors.isEmpty()) return@buildString
        appendLine("Twoja poprzednia odpowiedź zawierała poważne błędy. Wygeneruj plan ponownie unikając:")
        result.errors.forEach { e ->
            appendLine("- [${e.code}] ${e.message}")
        }
    }
}
