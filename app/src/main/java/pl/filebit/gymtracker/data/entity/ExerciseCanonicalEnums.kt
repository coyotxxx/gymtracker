package pl.filebit.gymtracker.data.entity

enum class ExerciseCategory {
    STRENGTH, CARDIO, MOBILITY, PLYO, OLYMPIC, STRONGMAN,
    GYMNASTICS, STRETCHING, BALANCE, WARMUP, COOLDOWN, OTHER;

    fun displayName(): String = when (this) {
        STRENGTH -> "Siłowe"
        CARDIO -> "Cardio"
        MOBILITY -> "Mobilność"
        PLYO -> "Plyometria"
        OLYMPIC -> "Olimpijskie"
        STRONGMAN -> "Strongman"
        GYMNASTICS -> "Gimnastyczne"
        STRETCHING -> "Rozciąganie"
        BALANCE -> "Równowaga"
        WARMUP -> "Rozgrzewka"
        COOLDOWN -> "Wyciszenie"
        OTHER -> "Inne"
    }

    companion object {
        fun fromString(s: String?): ExerciseCategory = when (s?.lowercase()) {
            "strength" -> STRENGTH
            "cardio" -> CARDIO
            "mobility" -> MOBILITY
            "plyo", "plyometric", "plyometrics" -> PLYO
            "olympic", "olympic_lift", "olympic_weightlifting" -> OLYMPIC
            "strongman" -> STRONGMAN
            "gymnastics", "calisthenics" -> GYMNASTICS
            "stretching", "stretch" -> STRETCHING
            "balance" -> BALANCE
            "warmup", "warm_up" -> WARMUP
            "cooldown", "cool_down" -> COOLDOWN
            else -> OTHER
        }
    }
}

enum class MovementPattern {
    SQUAT, HINGE, LUNGE,
    PUSH_HORIZONTAL, PUSH_VERTICAL,
    PULL_HORIZONTAL, PULL_VERTICAL,
    CARRY, ROTATION, ANTI_ROTATION,
    CORE_FLEXION, CORE_EXTENSION, ANTI_EXTENSION, ANTI_LATERAL_FLEXION,
    GAIT, JUMP, THROW, OTHER;

    fun displayName(): String = when (this) {
        SQUAT -> "Przysiad"
        HINGE -> "Zawias biodrowy"
        LUNGE -> "Wykrok"
        PUSH_HORIZONTAL -> "Pchanie poziome"
        PUSH_VERTICAL -> "Pchanie pionowe"
        PULL_HORIZONTAL -> "Ciągnięcie poziome"
        PULL_VERTICAL -> "Ciągnięcie pionowe"
        CARRY -> "Noszenie"
        ROTATION -> "Rotacja"
        ANTI_ROTATION -> "Anty-rotacja"
        CORE_FLEXION -> "Zgięcie tułowia"
        CORE_EXTENSION -> "Wyprost tułowia"
        ANTI_EXTENSION -> "Anty-wyprost"
        ANTI_LATERAL_FLEXION -> "Anty-zgięcie boczne"
        GAIT -> "Chód/bieg"
        JUMP -> "Skok"
        THROW -> "Rzut"
        OTHER -> "Inny"
    }

    companion object {
        fun fromString(s: String?): MovementPattern = when (s?.lowercase()?.replace("-", "_")) {
            "squat" -> SQUAT
            "hinge", "hip_hinge" -> HINGE
            "lunge", "split_stance" -> LUNGE
            "push_horizontal", "horizontal_push" -> PUSH_HORIZONTAL
            "push_vertical", "vertical_push" -> PUSH_VERTICAL
            "pull_horizontal", "horizontal_pull" -> PULL_HORIZONTAL
            "pull_vertical", "vertical_pull" -> PULL_VERTICAL
            "carry", "loaded_carry" -> CARRY
            "rotation" -> ROTATION
            "anti_rotation" -> ANTI_ROTATION
            "core_flexion", "flexion" -> CORE_FLEXION
            "core_extension", "extension" -> CORE_EXTENSION
            "anti_extension" -> ANTI_EXTENSION
            "anti_lateral_flexion" -> ANTI_LATERAL_FLEXION
            "gait", "run", "walk" -> GAIT
            "jump", "plyometric" -> JUMP
            "throw" -> THROW
            else -> OTHER
        }
    }
}

enum class Mechanic { COMPOUND, ISOLATION, OTHER;
    fun displayName() = when (this) { COMPOUND -> "Złożone"; ISOLATION -> "Izolowane"; OTHER -> "Inne" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "compound" -> COMPOUND; "isolation", "isolated" -> ISOLATION; else -> OTHER
    } }
}

enum class Force { PUSH, PULL, STATIC, HOLD, OTHER;
    fun displayName() = when (this) { PUSH -> "Pchanie"; PULL -> "Ciągnięcie"; STATIC -> "Statyka"; HOLD -> "Wytrzymanie"; OTHER -> "Inne" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "push" -> PUSH; "pull" -> PULL; "static", "isometric" -> STATIC; "hold" -> HOLD; else -> OTHER
    } }
}

enum class KineticChain { OPEN, CLOSED, OTHER;
    fun displayName() = when (this) { OPEN -> "Otwarty"; CLOSED -> "Zamknięty"; OTHER -> "Inny" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "open" -> OPEN; "closed" -> CLOSED; else -> OTHER
    } }
}

enum class Plane { SAGITTAL, FRONTAL, TRANSVERSE, MULTI, OTHER;
    fun displayName() = when (this) { SAGITTAL -> "Strzałkowa"; FRONTAL -> "Czołowa"; TRANSVERSE -> "Poprzeczna"; MULTI -> "Wielopłaszczyznowa"; OTHER -> "Inna" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "sagittal" -> SAGITTAL; "frontal" -> FRONTAL; "transverse" -> TRANSVERSE; "multi", "multiplanar" -> MULTI; else -> OTHER
    } }
}

enum class Laterality { UNILATERAL, BILATERAL, ALTERNATING, OTHER;
    fun displayName() = when (this) { UNILATERAL -> "Jednostronne"; BILATERAL -> "Obustronne"; ALTERNATING -> "Naprzemienne"; OTHER -> "Inne" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "unilateral" -> UNILATERAL; "bilateral" -> BILATERAL; "alternating" -> ALTERNATING; else -> OTHER
    } }
}

enum class Level { BEGINNER, INTERMEDIATE, ADVANCED, ELITE;
    fun displayName() = when (this) { BEGINNER -> "Początkujący"; INTERMEDIATE -> "Średniozaawansowany"; ADVANCED -> "Zaawansowany"; ELITE -> "Elite" }
    companion object { fun fromString(s: String?) = when (s?.lowercase()) {
        "beginner", "novice" -> BEGINNER
        "intermediate" -> INTERMEDIATE
        "advanced" -> ADVANCED
        "elite", "expert" -> ELITE
        else -> BEGINNER
    } }
}
