package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.MealType

/**
 * Kanoniczne sloty posiłkowe dla N posiłków/dzień + polskie etykiety.
 * Jedno źródło prawdy — używane przez generator planu (DietAiService) i raport zgodności.
 */
object MealSlots {

    /** Lista typów posiłków dla N slotów (z powtórzeniami — np. 5 posiłków = 2 przekąski). */
    fun typesFor(mealsCount: Int): List<MealType> = when (mealsCount) {
        2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
        5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
        else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    }

    /** Etykieta w mianowniku (do list: „śniadanie", „obiad", „kolacja", „przekąska"). */
    fun polishLabel(t: MealType): String = when (t) {
        MealType.BREAKFAST -> "śniadanie"
        MealType.LUNCH -> "obiad"
        MealType.DINNER -> "kolacja"
        MealType.SNACK -> "przekąska"
    }
}
