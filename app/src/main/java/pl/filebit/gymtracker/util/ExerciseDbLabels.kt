package pl.filebit.gymtracker.util

/**
 * v1.25.1 — statyczne PL labele dla mięśni / sprzętu / partii ciała z ExerciseDB.
 *
 * Filozofia: ExerciseDB używa ~30 unikalnych słów EN dla muscles/equipment/bodyparts.
 * Tłumaczymy je statycznie (zero AI cost), instrukcje (długie zdania) zostają na AI.
 */
object ExerciseDbLabels {

    /**
     * Mięsień (targetMuscle lub secondaryMuscle): EN → PL.
     * Fallback: capitalize EN.
     */
    fun muscle(en: String): String {
        val key = en.trim().lowercase()
        return MUSCLES[key] ?: en.trim().replaceFirstChar { it.uppercase() }
    }

    /** Sprzęt (equipment z ExerciseDB): EN → PL. */
    fun equipment(en: String): String {
        val key = en.trim().lowercase()
        return EQUIPMENT[key] ?: en.trim().replaceFirstChar { it.uppercase() }
    }

    /** Partia ciała (bodyPart): EN → PL. */
    fun bodyPart(en: String): String {
        val key = en.trim().lowercase()
        return BODY_PARTS[key] ?: en.trim().replaceFirstChar { it.uppercase() }
    }

    private val MUSCLES = mapOf(
        // klatka
        "pectorals" to "Mięsień piersiowy",
        "chest" to "Klatka piersiowa",
        // plecy
        "lats" to "Najszerszy grzbietu",
        "upper back" to "Górna część pleców",
        "lower back" to "Dolna część pleców",
        "spine" to "Prostowniki grzbietu",
        "trapezius" to "Czworoboczny (kapturowy)",
        "traps" to "Czworoboczny",
        "rhomboids" to "Równoległoboczne",
        // barki
        "delts" to "Naramienne",
        "shoulders" to "Barki",
        "rotator cuff" to "Stożek rotatorów",
        // biceps / triceps
        "biceps" to "Biceps",
        "triceps" to "Triceps",
        "brachialis" to "Ramienny",
        // przedramię
        "forearms" to "Przedramiona",
        // brzuch / core
        "abs" to "Brzuch",
        "abdominals" to "Brzuch",
        "obliques" to "Skośne brzucha",
        "core" to "Core (stabilizacja)",
        "serratus anterior" to "Zębaty przedni",
        "waist" to "Talia",
        // nogi
        "quads" to "Czworogłowe uda",
        "quadriceps" to "Czworogłowe uda",
        "hamstrings" to "Dwugłowe uda",
        "glutes" to "Pośladki",
        "gluteus" to "Pośladki",
        "hip flexors" to "Zginacze biodra",
        "abductors" to "Odwodziciele",
        "adductors" to "Przywodziciele",
        "calves" to "Łydki",
        "soleus" to "Płaszczkowaty",
        "gastrocnemius" to "Brzuchaty łydki",
        // szyja
        "neck" to "Szyja",
        "sternocleidomastoid" to "Mostkowo-obojczykowo-sutkowy",
        // cardio
        "cardiovascular system" to "Układ krążenia",
        "heart" to "Serce"
    )

    private val EQUIPMENT = mapOf(
        "barbell" to "Sztanga",
        "ez barbell" to "Łamana sztanga (EZ)",
        "trap bar" to "Sztanga trap (heksagonalna)",
        "dumbbell" to "Hantle",
        "dumbbells" to "Hantle",
        "kettlebell" to "Kettlebell (odważnik)",
        "cable" to "Wyciąg",
        "rope" to "Linka (wyciąg)",
        "smith machine" to "Maszyna Smitha",
        "leverage machine" to "Maszyna dźwigniowa",
        "sled machine" to "Maszyna saneczkowa (sled)",
        "hammer" to "Hammer Strength",
        "body weight" to "Ciężar ciała",
        "bodyweight" to "Ciężar ciała",
        "band" to "Guma oporowa",
        "resistance band" to "Guma oporowa",
        "weighted" to "Z obciążeniem",
        "stability ball" to "Piłka gimnastyczna",
        "bosu ball" to "BOSU",
        "medicine ball" to "Piłka lekarska",
        "battle rope" to "Liny bojowe",
        "trx" to "TRX (taśmy)",
        "roller" to "Roller",
        "wheel roller" to "Roller (kółko)",
        "skierg machine" to "SkiErg",
        "rower" to "Wioślarz",
        "elliptical machine" to "Orbitrek",
        "stationary bike" to "Rower stacjonarny",
        "stepmill machine" to "Stepper / Stairmaster",
        "tire" to "Opona",
        "olympic barbell" to "Sztanga olimpijska",
        "ez barbell olympic" to "Łamana sztanga (EZ)",
        "" to "Brak / ciężar ciała"
    )

    private val BODY_PARTS = mapOf(
        "chest" to "Klatka piersiowa",
        "back" to "Plecy",
        "shoulders" to "Barki",
        "upper arms" to "Ramiona",
        "lower arms" to "Przedramiona",
        "upper legs" to "Uda",
        "lower legs" to "Łydki",
        "waist" to "Brzuch / talia",
        "neck" to "Szyja",
        "cardio" to "Cardio"
    )
}
