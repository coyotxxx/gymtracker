package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
    QUADS, HAMSTRINGS, GLUTES, CALVES,
    CORE, CARDIO, OTHER;

    /**
     * v1.19.0 — polski label grupy mięśniowej (centralny — zamiast duplikatów w wielu miejscach).
     */
    fun displayName(): String = when (this) {
        CHEST -> "Klatka"
        BACK -> "Plecy"
        SHOULDERS -> "Barki"
        BICEPS -> "Biceps"
        TRICEPS -> "Triceps"
        QUADS -> "Czworogłowe"
        HAMSTRINGS -> "Dwugłowe"
        GLUTES -> "Pośladki"
        CALVES -> "Łydki"
        CORE -> "Brzuch"
        CARDIO -> "Cardio"
        OTHER -> "Inne"
    }
}

enum class Equipment {
    BARBELL, DUMBBELLS, MACHINE, CABLE, BODYWEIGHT, OTHER;

    /** v1.20.2 — polski label sprzętu (centralizacja, zamiast duplikatów w UI screens). */
    fun displayName(): String = when (this) {
        BARBELL -> "Sztanga"
        DUMBBELLS -> "Hantle"
        MACHINE -> "Maszyna"
        CABLE -> "Wyciąg"
        BODYWEIGHT -> "Ciężar ciała"
        OTHER -> "Inne"
    }
}

/**
 * Typ metryki — co loguje user dla danego ćwiczenia.
 */
enum class MetricType {
    WEIGHT_REPS,        // klasyczne: kg + powt. (siłowe, większość)
    REPS_ONLY,          // tylko powt. (np. pompki BW, sit-up)
    DURATION,           // tylko czas (plank, deska, izometryczne)
    DISTANCE_DURATION,  // dystans + czas (bieżnia, rower, bieg)
    DURATION_WEIGHT     // czas + ciężar (farmer's walk, plank z obciążeniem)
}

@Entity(
    tableName = "exercises",
    indices = [
        androidx.room.Index("name"),
        androidx.room.Index("primaryMuscle"),
        androidx.room.Index("isFavorite"),
        androidx.room.Index("externalId"),
        // v2.0.0 — canonical schema
        androidx.room.Index(value = ["slug"], unique = true),
        androidx.room.Index("category"),
        androidx.room.Index("movementPattern")
    ]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
    val notes: String = "",
    val metricType: MetricType = MetricType.WEIGHT_REPS,
    val description: String = "",
    val isFavorite: Boolean = false,
    val isAvoided: Boolean = false,
    /** v2.0.0 LEGACY (drop w v2.0.1): historyczne ExerciseDB ID. W canonical = slug. */
    val externalId: String? = null,
    /** URL GIF animacji — w v2.0.0 z R2 CDN. */
    val gifUrl: String? = null,
    /** Instrukcje EN — JSON list. */
    val instructionsEnJson: String? = null,
    /** Instrukcje PL — JSON list. */
    val instructionsPlJson: String? = null,
    /** v2.0.0 LEGACY (drop w v2.0.1): mięśnie primary CSV — derived z muscleIntensityJson. */
    val targetMusclesCsv: String? = null,
    /** v2.0.0 LEGACY (drop w v2.0.1): mięśnie secondary CSV — derived. */
    val secondaryMusclesCsv: String? = null,
    /** v2.0.0 LEGACY (drop w v2.0.1): sprzęt CSV — derived z equipment.required+optional. */
    val equipmentDbCsv: String? = null,
    /** v2.0.0 LEGACY (drop w v2.0.1): body parts CSV — derived. */
    val bodyPartCsv: String? = null,
    val searchAliases: String? = null,

    // === v2.0.0 CANONICAL FIELDS ===
    /** Canonical slug ćwiczenia (np. "3-4-sit-up", "barbell-bench-press"). UNIQUE. */
    val slug: String? = null,
    /** Polska nazwa ćwiczenia z canonical. */
    val namePl: String? = null,
    /** Polski opis edukacyjny (długa wersja). */
    val descriptionPl: String? = null,
    /** Aliasy EN — JSON list (synonimy ang.). */
    val aliasesEnJson: String? = null,
    /** Aliasy PL — JSON list (synonimy pl., literówki). */
    val aliasesPlJson: String? = null,
    /** URL prefix do folderu z klatkami PNG (frame_0_start.png itp.). */
    val framesDirUrl: String? = null,
    /** Kategoria ćwiczenia (strength/cardio/mobility/...). */
    val category: ExerciseCategory? = null,
    /** Wzorzec ruchowy (squat/push_horizontal/pull_vertical/...). */
    val movementPattern: MovementPattern? = null,
    val mechanic: Mechanic? = null,
    val force: Force? = null,
    val kineticChain: KineticChain? = null,
    val plane: Plane? = null,
    val laterality: Laterality? = null,
    /** Minimalny poziom: beginner/intermediate/advanced/elite. */
    val levelMin: Level? = null,
    /** Trudność 1-10. */
    val difficulty1To10: Int? = null,
    /** Intensywność mięśniowa — JSON map {muscle: "P10|S7|T4"}. */
    val muscleIntensityJson: String? = null,
    /** Wskazówki coaching — JSON {setup_cues:[], execution_cues:[], breathing:""}. */
    val coachingCuesJson: String? = null,
    /** Częste błędy — JSON list [{fault, cause, cue}]. */
    val commonFaultsJson: String? = null,
    /** Przeciwwskazania — JSON list ["acute_lower_back_pain", ...]. */
    val contraindicationsJson: String? = null,
    /** Prerequisites — JSON list of slugs. */
    val prerequisitesJson: String? = null,
    /** Progresje (cięższe wersje) — JSON list of slugs. */
    val progressionToJson: String? = null,
    /** Alternatywy — JSON list of slugs. */
    val alternativesJson: String? = null,
    /** Tagi — JSON list. */
    val tagsJson: String? = null,
    /** Sprzęt opcjonalny CSV (np. "mat,bench"). */
    val equipmentOptionalCsv: String? = null,

    /**
     * v2.3.0 — pre-computowany index do wyszukiwania PL+EN+ASCII-fold+tokeny.
     * Zawiera: name, namePl, każdy alias EN+PL, ASCII-fold dla PL, tokeny słów.
     * Pozwala SQLite LIKE jednym query znaleźć ćwiczenie po dowolnej formie wpisu
     * (np. "podciaganie" znajdzie "Podciąganie nachwytem"). UNIQUE index nie potrzebny.
     */
    val searchIndex: String? = null
)
