package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.MealType

/**
 * Preferencje stylu posiłków przekazywane do generatora AI.
 *
 * v1.29: styl globalny rozdzielony na DWA pojęcia:
 *  - [character] — charakter dania (forma na talerzu), wybierasz JEDEN,
 *  - [modifiers] — modyfikatory (czas / koktajle / wsadowo), dowolnie wiele.
 * Klasyczny + Miski fit naraz = sprzeczność, dlatego charakter jest pojedynczy.
 *
 * Jeśli wszystko domyślne (CLASSIC, brak modyfikatorów, slotPrefs=empty)
 * → AI generuje klasyczny plan.
 */
data class MealStylePreferences(
    /** Charakter dania — jeden, z [PlanStyle] o [StyleKind.CHARACTER]. */
    val character: PlanStyle = PlanStyle.CLASSIC,
    /** Modyfikatory — dowolny podzbiór [PlanStyle] o [StyleKind.MODIFIER]. */
    val modifiers: Set<PlanStyle> = emptySet(),
    /** Per-slot preferencja stylu pojedynczego posiłku — np. śniadanie=SMOOTHIE. */
    val slotStyles: Map<MealType, MealStyle> = emptyMap(),
    /** Urządzenia kuchenne usera — AI pisze instrukcje pod posiadany sprzęt. */
    val devices: Set<CookingDevice> = setOf(CookingDevice.PAN_OVEN),
    /** Wolne pole — np. "Mam tylko 5 min na śniadanie", "wegetariańska kolacja". */
    val freeText: String = "",
    /**
     * Gdy true — AI komponuje posiłki PRIORYTETOWO z produktów oznaczonych ❤.
     * Jeśli z samych ulubionych nie wychodzi makro/kcal/styl — dobiera pozostałe.
     * Domyślnie false (opt-in z okna generowania).
     */
    val preferFavorites: Boolean = false
)

/** Rodzaj stylu — charakter (jeden) vs modyfikator (wiele). */
enum class StyleKind { CHARACTER, MODIFIER }

/**
 * Styl planu. [StyleKind.CHARACTER] — forma dania (wyklucza się wzajemnie).
 * [StyleKind.MODIFIER] — nakładka czasowa/sprzętowa (łączy się dowolnie).
 *
 * v1.24.50: PL labele (label widoczny w UI; promptHint kierowany do AI).
 */
enum class PlanStyle(val label: String, val promptHint: String, val kind: StyleKind) {
    // === CHARAKTER ===
    CLASSIC(
        "Klasyczny",
        "tradycyjne dania (owsianka, kurczak+ryż, twaróg) — bezpieczne, sycące",
        StyleKind.CHARACTER
    ),
    FIT_BOWL(
        "Miski fit",
        "miski (bowls): białko + węgle + warzywa w jednej misce, ładnie ułożone, lekkie",
        StyleKind.CHARACTER
    ),
    COMFORT(
        "Domowe sycące",
        "domowe i sycące: zapiekanki, gulasze, naleśniki proteinowe, makarony pełnoziarniste",
        StyleKind.CHARACTER
    ),
    // === MODYFIKATORY ===
    SHAKES_FRIENDLY(
        "Z koktajlami",
        "preferuj koktajle/smoothies tam gdzie sensowne (zwłaszcza po treningu i przekąski), reszta klasyk",
        StyleKind.MODIFIER
    ),
    QUICK(
        "Szybko ≤10 min",
        "wszystkie posiłki gotowe ≤10 min — kanapki, twaróg, jaja, gotowe składniki",
        StyleKind.MODIFIER
    ),
    MEAL_PREP(
        "Na zapas",
        "porcje gotujące się raz na 2-3 dni: zapiekanki, gulasze, ryż+kurczak w boxach",
        StyleKind.MODIFIER
    );

    companion object {
        val characters: List<PlanStyle> get() = entries.filter { it.kind == StyleKind.CHARACTER }
        val modifiers: List<PlanStyle> get() = entries.filter { it.kind == StyleKind.MODIFIER }
    }
}

/**
 * Urządzenie kuchenne usera. AI dobiera instrukcje pod posiadany sprzęt
 * i oznacza przepis tagiem urządzenia ([tag]) — TAM GDZIE danie pasuje.
 *
 * [promptHint] — możliwości sprzętu wstrzykiwane do promptu AI.
 * [tag] — krótka etykieta na chip przy przepisie (pusta = zwykły przepis).
 */
enum class CookingDevice(val label: String, val tag: String, val promptHint: String) {
    PAN_OVEN(
        "Patelnia / piekarnik",
        "",
        "klasyczne gotowanie: patelnia, garnek, piekarnik — uniwersalne"
    ),
    COSORI_TURBO_TOWER(
        "Cosori Turbo Tower",
        "Cosori",
        "Cosori Turbo Tower Pro — dwukomorowa frytkownica beztłuszczowa (gorące " +
            "powietrze), tryby: AirFry, Roast, Bake, Grill, Reheat, Dry, Proof; " +
            "w instrukcji podaj TRYB, temperaturę °C i czas; świetna do chrupiącego " +
            "mięsa, warzyw, zapiekanek, pieczenia; NIE nadaje się do zup, sosów, " +
            "koktajli, dań płynnych"
    ),
    THERMOMIX_TM5(
        "Thermomix TM5",
        "Thermomix",
        "robot kuchenny — blendowanie, gotowanie, para, ważenie; świetny do zup, " +
            "sosów, koktajli, risotto, past, kremów; w instrukcji podaj prędkość " +
            "ostrza, temperaturę i czas; NIE nadaje się do chrupiących/smażonych potraw"
    )
}

/** Preferencja stylu pojedynczego posiłku — nadpisuje [MealStylePreferences.character] dla slotu. */
/** v1.24.50: PL labele (Smoothie → Koktajl, Wrap → Tortilla, Bowl → Miska). */
enum class MealStyle(val label: String, val promptHint: String) {
    DEFAULT("Domyślny", ""),
    SMOOTHIE("Koktajl", "płynny posiłek w blenderze (białko + owoce + napój roślinny lub mleko)"),
    OATMEAL("Owsianka", "płatki owsiane jako baza, z dodatkami białka i owocu"),
    EGGS("Jajeczne", "jajecznica/omlet/gotowane jaja jako baza"),
    SANDWICH("Kanapka/Wrap", "pieczywo razowe lub tortilla + białko + warzywa"),  // Wrap przyjęte w PL
    SALAD("Sałatka", "warzywa jako baza (>200g) + białko + dressing oliwa/jogurt"),
    BOWL("Miska", "ładnie ułożone w misce: białko + węgle + warzywa + sos"),
    SOUP("Zupa", "zupa krem lub zupa z mięsem/strączkami jako baza"),
    MEAT_RICE("Mięso + ryż/kasza", "klasyczne: mięso/ryba + węgiel (ryż/kasza/ziemniaki) + warzywa"),
    PASTA("Makaron", "makaron pełnoziarnisty + sos białkowy + warzywa"),
    COTTAGE("Twaróg/Skyr", "twaróg/skyr jako baza (lekka kolacja)"),  // Skyr to nazwa produktu spożywczego
    FISH("Ryba", "ryba pieczona/duszona jako białko"),
    NO_PREFERENCE("Bez preferencji", "")
}
