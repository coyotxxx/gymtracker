package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.FoodProduct
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * QuickComposeService — filozofia z xlsx Macieja:
 *
 *   1 POSIŁEK = 1× WĘGLOWODAN + 1× BIAŁKO + 1× TŁUSZCZ + warzywa do woli
 *
 * User wybiera 3 produkty (po jednym z każdej kategorii) + opcjonalnie warzywa,
 * a serwis MATEMATYCZNIE rozwiązuje gramatury żeby trafić w target makro slotu
 * (kcal + białko + tłuszcz, węgle wychodzą z bilansu).
 *
 * Algorytm:
 *   - Białko slotu: gramy_białka = target_białka / białko_w_100g_produktu
 *   - Tłuszcz slotu: gramy_tłuszczu = (target_tłuszczu - tłuszcz_z_białka) / tłuszcz_w_100g_produktu
 *   - Węgle slotu: pokrywają resztę kcal (kcal_z_białka + kcal_z_tłuszczu) → reszta_kcal / 4 = gramy_węgli (4 kcal/g)
 *   - Cap [5g, 800g], minimum 5g jeśli wynik <0
 *   - Warzywa = 200g flat (nie liczone do makro w celach upraszczania)
 */
@Singleton
class QuickComposeService @Inject constructor() {

    data class ComposeResult(
        val proteinGrams: Int,
        val carbGrams: Int,
        val fatGrams: Int,
        val vegetableGrams: Int,
        val realKcal: Int,
        val realProteinG: Int,
        val realCarbsG: Int,
        val realFatG: Int,
        /** Jeśli czegoś nie udało się dopasować — informacja dla usera. */
        val warnings: List<String>
    )

    /**
     * Komponuje posiłek żeby trafić w target slotu.
     *
     * @param protein produkt białkowy (np. pierś z kurczaka)
     * @param carb produkt węglowodanowy (np. ryż basmati)
     * @param fat produkt tłuszczowy (np. oliwa)
     * @param vegetable opcjonalne warzywa (na sztywno 200g jeśli wybrane)
     * @param targetKcal target kcal dla slotu (np. 750)
     * @param targetProteinG target białka g dla slotu (np. 50)
     * @param targetFatG target tłuszczu g dla slotu (np. 25)
     */
    fun compose(
        protein: FoodProduct,
        carb: FoodProduct,
        fat: FoodProduct,
        vegetable: FoodProduct?,
        targetKcal: Int,
        targetProteinG: Int,
        targetFatG: Int
    ): ComposeResult {
        val warnings = mutableListOf<String>()

        // === KROK 1: gramatura białka ===
        // Wybieramy ile białka żeby trafić w target_białka, uwzględniając że produkt białkowy
        // zwykle MA już trochę tłuszczu (kurczak 3.6g/100g) więc przy normalizacji uwzględnimy.
        val proteinGrams = if (protein.proteinPer100g > 0) {
            (targetProteinG / (protein.proteinPer100g / 100.0)).roundToInt()
                .coerceIn(20, 400)
        } else {
            warnings += "Produkt '${protein.name}' nie zawiera białka — używam 200g."
            200
        }

        // Realne makro z białkowego (zawiera też tłuszcz/węgle "ubocznie")
        val factor_p = proteinGrams / 100.0
        val protKcal = protein.kcalPer100g * factor_p
        val protProtein = protein.proteinPer100g * factor_p
        val protFat = protein.fatPer100g * factor_p
        val protCarbs = protein.carbsPer100g * factor_p

        // === KROK 2: gramatura tłuszczu ===
        // Cel tłuszczu - tłuszcz już dostarczony przez białko = brakujący tłuszcz
        val remainingFatNeeded = (targetFatG - protFat).coerceAtLeast(0.0)
        val fatGrams = if (fat.fatPer100g > 0 && remainingFatNeeded > 0) {
            (remainingFatNeeded / (fat.fatPer100g / 100.0)).roundToInt()
                .coerceIn(5, 100)
        } else if (remainingFatNeeded <= 0) {
            warnings += "Białkowy produkt już pokrywa cel tłuszczu — minimalna porcja '${fat.name}' (5g)."
            5
        } else {
            5
        }

        val factor_f = fatGrams / 100.0
        val fatKcal = fat.kcalPer100g * factor_f
        val fatProtein = fat.proteinPer100g * factor_f
        val fatFat = fat.fatPer100g * factor_f
        val fatCarbs = fat.carbsPer100g * factor_f

        // === KROK 3: gramatura węgli ===
        // Reszta kcal (target - kcal z białka - kcal z tłuszczu) → węgle
        val remainingKcalNeeded = (targetKcal - protKcal - fatKcal).coerceAtLeast(0.0)
        val carbGrams = if (carb.kcalPer100g > 0 && remainingKcalNeeded > 0) {
            (remainingKcalNeeded / (carb.kcalPer100g / 100.0)).roundToInt()
                .coerceIn(10, 600)
        } else {
            warnings += "Tłuszcz/białko już pokryły kcal slotu — minimalna porcja '${carb.name}' (10g)."
            10
        }

        // Realne sumy
        val factor_c = carbGrams / 100.0
        val carbKcal = carb.kcalPer100g * factor_c
        val carbProtein = carb.proteinPer100g * factor_c
        val carbFat = carb.fatPer100g * factor_c
        val carbCarbs = carb.carbsPer100g * factor_c

        // Warzywa — nie liczymy do makro (używamy w celu sytości, do woli; 200g flat)
        val vegGrams = if (vegetable != null) 200 else 0
        val vegKcal = if (vegetable != null) vegetable.kcalPer100g * 2.0 else 0.0
        val vegProtein = if (vegetable != null) vegetable.proteinPer100g * 2.0 else 0.0
        val vegCarbs = if (vegetable != null) vegetable.carbsPer100g * 2.0 else 0.0
        val vegFat = if (vegetable != null) vegetable.fatPer100g * 2.0 else 0.0

        val realKcal = (protKcal + carbKcal + fatKcal + vegKcal).roundToInt()
        val realProtein = (protProtein + carbProtein + fatProtein + vegProtein).roundToInt()
        val realCarbs = (protCarbs + carbCarbs + fatCarbs + vegCarbs).roundToInt()
        val realFat = (protFat + carbFat + fatFat + vegFat).roundToInt()

        // Diagnostyka jakości dopasowania
        val kcalDev = ((realKcal - targetKcal).toDouble() / targetKcal * 100).roundToInt()
        if (kotlin.math.abs(kcalDev) > 10) {
            warnings += "Trafienie kcal: $realKcal vs target $targetKcal (${if (kcalDev > 0) "+" else ""}$kcalDev%). Spróbuj innego węgla/tłuszczu."
        }

        return ComposeResult(
            proteinGrams = proteinGrams,
            carbGrams = carbGrams,
            fatGrams = fatGrams,
            vegetableGrams = vegGrams,
            realKcal = realKcal,
            realProteinG = realProtein,
            realCarbsG = realCarbs,
            realFatG = realFat,
            warnings = warnings
        )
    }
}
