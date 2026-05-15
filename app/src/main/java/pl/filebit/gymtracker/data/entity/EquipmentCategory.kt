package pl.filebit.gymtracker.data.entity

import androidx.annotation.DrawableRes
import pl.filebit.gymtracker.R

/**
 * v1.26.5 — praktyczne kategorie sprzętu dla wyboru przez użytkownika.
 *
 * Wcześniej `Equipment` enum miał tylko 5 ogólnych typów (BARBELL/DUMBBELLS/
 * MACHINE/CABLE/BODYWEIGHT) — za mało konkretne. ExerciseDB rozróżnia 28 typów.
 *
 * `EquipmentCategory` to warstwa pośrednia: 11 kategorii które user rozpoznaje
 * (gryf, gryf łamany, hantle, kettlebell, maszyny, wyciąg, drążek, gumy,
 * piłki, cardio, inne), każda mapuje na 1+ surowych ExerciseDB equipment
 * stringów (`Exercise.equipmentDbCsv`).
 *
 * Wybór usera (CSV nazw enum) trzymany w `UserProfile.equipmentCategoriesCsv`.
 * AI generator planu filtruje ćwiczenia po tym.
 */
enum class EquipmentCategory(
    @DrawableRes val iconRes: Int,
    val label: String,
    /** Surowe equipment stringi z ExerciseDB (`Exercise.equipmentDbCsv`). */
    val dbEquipments: Set<String>
) {
    BARBELL(R.drawable.eq_barbell, "Sztanga (gryf prosty)",
        setOf("barbell", "olympic barbell", "trap bar")),
    EZ_BAR(R.drawable.eq_barbell, "Gryf łamany (EZ)",
        setOf("ez barbell")),
    DUMBBELL(R.drawable.eq_dumbbell, "Hantle / sztangielki",
        setOf("dumbbell")),
    KETTLEBELL(R.drawable.eq_kettlebell, "Kettlebell",
        setOf("kettlebell")),
    MACHINE(R.drawable.eq_machine, "Maszyny",
        setOf("leverage machine", "smith machine", "sled machine", "hammer")),
    CABLE(R.drawable.eq_cable, "Wyciąg / linki",
        setOf("cable")),
    BODYWEIGHT(R.drawable.eq_bodyweight, "Brama / drążek / masa ciała",
        setOf("body weight", "assisted", "weighted")),
    BANDS(R.drawable.eq_bands, "Gumy oporowe",
        setOf("band", "resistance band")),
    BALL(R.drawable.eq_ball, "Piłki (gimnastyczna/lekarska)",
        setOf("stability ball", "bosu ball", "medicine ball")),
    CARDIO(R.drawable.eq_cardio, "Cardio (bieżnia/rower/orbitrek)",
        setOf("stationary bike", "elliptical machine", "stepmill machine",
              "skierg machine", "upper body ergometer")),
    OTHER_GEAR(R.drawable.eq_other, "Inne (rolka/lina/opona)",
        setOf("roller", "wheel roller", "rope", "tire"));

    companion object {
        /** Parsuje CSV nazw enum (z UserProfile) na zbiór kategorii. */
        fun parse(csv: String): Set<EquipmentCategory> =
            csv.split(",")
                .mapNotNull { token -> runCatching { valueOf(token.trim()) }.getOrNull() }
                .toSet()

        /** Wszystkie surowe ExerciseDB equipment stringi dla zbioru kategorii. */
        fun dbEquipmentsFor(categories: Set<EquipmentCategory>): Set<String> =
            categories.flatMap { it.dbEquipments }.toSet()

        /** Preset "siłownia kompletna" — wszystkie kategorie. */
        val FULL_GYM: Set<EquipmentCategory> = entries.toSet()

        /** Preset "dom" — typowy sprzęt domowy. */
        val HOME: Set<EquipmentCategory> = setOf(BARBELL, EZ_BAR, DUMBBELL, BODYWEIGHT, BANDS)

        /** Preset "tylko masa ciała". */
        val BODYWEIGHT_ONLY: Set<EquipmentCategory> = setOf(BODYWEIGHT)
    }
}
