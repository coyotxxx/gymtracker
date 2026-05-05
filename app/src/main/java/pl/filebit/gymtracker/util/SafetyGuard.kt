package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile

/**
 * Hard-limity bezpieczeństwa dietetycznego.
 *
 * Inspiracja: ISSN, ACE, EFSA — minimalne i maksymalne wartości dla zdrowego
 * dorosłego (bez stanów medycznych). Dla osób z medical flags wartości
 * konserwatywniejsze, oznaczamy do konsultacji ze specjalistą.
 *
 * Wszystkie funkcje są pure (testable) i deterministic.
 */
object SafetyGuard {

    /** Minimalne kcal — RMR + minimum aktywności życiowej. */
    fun minKcal(profile: UserProfile): Int =
        if (profile.gender == Gender.FEMALE) 1200 else 1500

    /** Maksymalne kcal — bez sensu jeść więcej (efekty: rozstrój żołądka, niepotrzebny tłuszcz). */
    fun maxKcal(weightKg: Double): Int = (weightKg * 60).toInt()

    /** Min białko — ochrona masy mięśniowej. */
    fun minProteinG(weightKg: Double): Int = (weightKg * 0.8).toInt()
    /** Max białko — przekroczenie nie daje korzyści, obciąża nerki przy diecie wysokobiałkowej. */
    fun maxProteinG(weightKg: Double): Int = (weightKg * 3.0).toInt()

    /** Min tłuszcz — zdrowie hormonalne (testosteron, estrogen). */
    fun minFatG(weightKg: Double): Int = (weightKg * 0.6).toInt()
    fun maxFatG(weightKg: Double): Int = (weightKg * 2.5).toInt()

    /** Max tempo redukcji (kg/tydz). >1.5 = ryzyko utraty mięśni i metabolic damage. */
    const val MAX_LOSS_KG_PER_WEEK = 1.5
    const val MAX_GAIN_KG_PER_WEEK = 0.5

    /**
     * Główna walidacja kcal. Zwraca:
     * - Pass — wszystko OK
     * - Warn — działa ale ryzyko (np. agresywny deficyt)
     * - Block — wartość niebezpieczna, użyj capped
     */
    fun validateKcal(target: Int, profile: UserProfile, weightKg: Double): SafetyResult {
        val min = minKcal(profile)
        val max = maxKcal(weightKg)
        return when {
            target < min -> SafetyResult.Block(
                message = "Cel $target kcal poniżej bezpiecznego minimum ($min kcal). " +
                    "Zbyt niskie kalorie powodują utratę masy mięśniowej, problemy hormonalne, spadek energii.",
                cappedValue = min
            )
            target > max -> SafetyResult.Warn(
                message = "$target kcal to dużo. Trudne do zjedzenia bez śmieciowego jedzenia."
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateProtein(g: Int, weightKg: Double): SafetyResult {
        val min = minProteinG(weightKg)
        val max = maxProteinG(weightKg)
        return when {
            g < min -> SafetyResult.Block(
                message = "Białko ${g}g poniżej bezpiecznego minimum (${min}g, 0.8 g/kg). " +
                    "Ryzyko utraty masy mięśniowej.",
                cappedValue = min
            )
            g > max -> SafetyResult.Warn(
                message = "Białko ${g}g powyżej rozsądnego maximum (${max}g, 3 g/kg). " +
                    "Brak dodatkowych korzyści, obciąża nerki przy długotrwałym stosowaniu."
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateFat(g: Int, weightKg: Double): SafetyResult {
        val min = minFatG(weightKg)
        return when {
            g < min -> SafetyResult.Block(
                message = "Tłuszcz ${g}g poniżej bezpiecznego minimum (${min}g, 0.6 g/kg). " +
                    "Niezbędne dla syntezy hormonów (testosteron, estrogen).",
                cappedValue = min
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateDeficitRate(weeklyKgChange: Double): SafetyResult {
        return when {
            weeklyKgChange < -MAX_LOSS_KG_PER_WEEK -> SafetyResult.Warn(
                message = "Tempo redukcji ${"%.1f".format(-weeklyKgChange)} kg/tydz jest agresywne. " +
                    "Zalecane max 1.5 kg/tydz — szybsza utrata to ryzyko mięśni i metabolizmu."
            )
            weeklyKgChange > MAX_GAIN_KG_PER_WEEK -> SafetyResult.Warn(
                message = "Tempo przyrostu ${"%.1f".format(weeklyKgChange)} kg/tydz to dużo. " +
                    "Większość przyrostu = tłuszcz. Lean bulk = max 0.3-0.5 kg/tydz."
            )
            else -> SafetyResult.Pass
        }
    }
}

sealed class SafetyResult {
    /** OK, nic nie robimy. */
    object Pass : SafetyResult()
    /** Działa ale ostrzegamy. */
    data class Warn(val message: String) : SafetyResult()
    /** Blokujemy — używamy cappedValue zamiast oryginalnej wartości. */
    data class Block(val message: String, val cappedValue: Int) : SafetyResult()

    val isBlocking: Boolean get() = this is Block
    val warningMessage: String? get() = when (this) {
        is Warn -> message
        is Block -> message
        Pass -> null
    }
}
