package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile

enum class MedicalSeverity { INFO, WARN, CRITICAL }

data class MedicalFlag(
    val code: String,
    val severity: MedicalSeverity,
    val message: String,
    val recommendation: String
)

/**
 * Wykrywa flagi zdrowotne wymagające ostrożności / konsultacji ze specjalistą.
 *
 * Kryteria:
 * - BMI <18.5 (niedowaga) lub >35 (otyłość III stopnia)
 * - Stany medyczne zadeklarowane przez usera (UserDietProfile.medicalConditions)
 * - Ekstremalne tempo redukcji
 *
 * Ważne: aplikacja NIE jest narzędziem medycznym. Przy CRITICAL flag pokazujemy
 * disclaimer i zalecamy konsultację z lekarzem/dietetykiem klinicznym.
 */
object MedicalFlagger {

    fun analyze(
        userProfile: UserProfile,
        dietProfile: UserDietProfile?,
        currentWeightKg: Double?
    ): List<MedicalFlag> {
        val flags = mutableListOf<MedicalFlag>()

        // BMI (jeśli mamy wagę i wzrost)
        val weight = currentWeightKg ?: userProfile.bodyweightKg
        val height = dietProfile?.heightCm
        if (weight != null && height != null && height > 0) {
            val heightM = height / 100.0
            val bmi = weight / (heightM * heightM)
            when {
                bmi < 16.0 -> flags += MedicalFlag(
                    "very_low_bmi", MedicalSeverity.CRITICAL,
                    "BMI %.1f — bardzo niska masa ciała.".format(bmi),
                    "Skontaktuj się z lekarzem przed rozpoczęciem jakiejkolwiek diety. Apka nie jest dla Ciebie odpowiednia."
                )
                bmi < 18.5 -> flags += MedicalFlag(
                    "low_bmi", MedicalSeverity.WARN,
                    "BMI %.1f — niedowaga.".format(bmi),
                    "Cel: budowa masy. Skonsultuj plan z dietetykiem klinicznym."
                )
                bmi >= 35.0 -> flags += MedicalFlag(
                    "high_bmi", MedicalSeverity.WARN,
                    "BMI %.1f — otyłość znaczna.".format(bmi),
                    "Apka pomoże ale zalecana konsultacja z dietetykiem + lekarzem (badania, ciśnienie)."
                )
            }
        }

        // Stany medyczne z profile
        val conditions = dietProfile?.parsedMedicalConditions().orEmpty()
        for (cond in conditions) {
            val flag = when (cond.lowercase()) {
                "cukrzyca", "diabetes" -> MedicalFlag(
                    "diabetes", MedicalSeverity.CRITICAL,
                    "Cukrzyca — dieta wymaga kontroli węgli i indeksu glikemicznego.",
                    "Plan diety MUSI być zatwierdzony przez lekarza diabetologa. Apka traktowana wyłącznie pomocniczo."
                )
                "choroby_nerek", "kidney" -> MedicalFlag(
                    "kidney_disease", MedicalSeverity.CRITICAL,
                    "Choroba nerek — wysokie białko może być szkodliwe.",
                    "Konsultacja z nefrologiem konieczna przed dietą wysokobiałkową."
                )
                "choroby_wątroby", "liver" -> MedicalFlag(
                    "liver_disease", MedicalSeverity.CRITICAL,
                    "Choroba wątroby — wymaga specjalnej diety.",
                    "Plan tylko z dietetykiem klinicznym."
                )
                "choroby_serca", "heart" -> MedicalFlag(
                    "heart_disease", MedicalSeverity.WARN,
                    "Choroba serca — kontrola sodu i tłuszczy nasyconych.",
                    "Konsultacja z kardiologiem zalecana."
                )
                "nadciśnienie", "hypertension" -> MedicalFlag(
                    "hypertension", MedicalSeverity.WARN,
                    "Nadciśnienie — ograniczenie sodu (<2300 mg/dzień).",
                    "Apka będzie sugerować mniej soli, ale konsultacja z lekarzem zalecana."
                )
                "ciąża", "pregnancy" -> MedicalFlag(
                    "pregnancy", MedicalSeverity.CRITICAL,
                    "Ciąża — apka NIE jest narzędziem dla kobiet w ciąży.",
                    "Plan żywienia tylko z lekarzem prowadzącym i dietetykiem ciążowym."
                )
                "karmienie_piersią", "breastfeeding" -> MedicalFlag(
                    "breastfeeding", MedicalSeverity.WARN,
                    "Karmienie piersią — zwiększone zapotrzebowanie kaloryczne (+500 kcal).",
                    "Apka nie wzięła tego pod uwagę. Konsultacja z dietetykiem laktacyjnym."
                )
                "zaburzenia_odżywiania", "eating_disorder" -> MedicalFlag(
                    "eating_disorder", MedicalSeverity.CRITICAL,
                    "Zaburzenia odżywiania — apki śledzące dietę mogą pogorszyć stan.",
                    "Apka nie jest dla Ciebie. Skonsultuj się z psychiatrą / psychologiem specjalizującym się w zaburzeniach odżywiania (NEFA, https://nefa.pl)."
                )
                else -> null
            }
            flag?.let { flags += it }
        }

        return flags
    }

    fun anyCritical(flags: List<MedicalFlag>): Boolean =
        flags.any { it.severity == MedicalSeverity.CRITICAL }
}
