package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.MealType

/**
 * Preferencje stylu posiłków przekazywane do generatora AI.
 * Globalny "styl" planu + opcjonalne preferencje per slot (śniadanie/obiad/kolacja).
 *
 * Jeśli wszystko domyślne (CLASSIC, slotPrefs=empty) → AI generuje klasyczny plan.
 */
data class MealStylePreferences(
    val globalStyle: PlanStyle = PlanStyle.CLASSIC,
    /** Per-slot preferencja stylu pojedynczego posiłku — np. śniadanie=SMOOTHIE. */
    val slotStyles: Map<MealType, MealStyle> = emptyMap(),
    /** Wolne pole — np. "Mam tylko 5 min na śniadanie", "wegetariańska kolacja". */
    val freeText: String = ""
)

/** Globalny styl planu — wpływa na charakter wszystkich posiłków. */
enum class PlanStyle(val label: String, val promptHint: String) {
    CLASSIC(
        "Klasyczny",
        "tradycyjne dania (owsianka, kurczak+ryż, twaróg) — bezpieczne, sycące"
    ),
    FIT_BOWL(
        "Fit-bowl",
        "miski (bowls): białko + węgle + warzywa w jednej misce, ładnie ułożone, lekkie"
    ),
    COMFORT(
        "Comfort food",
        "domowe i sycące: zapiekanki, gulasze, naleśniki proteinowe, makarony pełnoziarniste"
    ),
    SHAKES_FRIENDLY(
        "Koktajl-friendly",
        "preferuj koktajle/smoothies tam gdzie sensowne (zwłaszcza po treningu i przekąski), reszta klasyk"
    ),
    QUICK(
        "Szybki (≤10 min)",
        "wszystkie posiłki gotowe ≤10 min — kanapki, twaróg, jaja, gotowe składniki"
    ),
    MEAL_PREP(
        "Meal prep",
        "porcje gotujące się raz na 2-3 dni: zapiekanki, gulasze, ryż+kurczak w boxach"
    )
}

/** Preferencja stylu pojedynczego posiłku — nadpisuje globalStyle dla tego slotu. */
enum class MealStyle(val label: String, val promptHint: String) {
    DEFAULT("Domyślny", ""),
    SMOOTHIE("Koktajl/Smoothie", "płynny posiłek w blenderze (białko + owoce + napój roślinny lub mleko)"),
    OATMEAL("Owsianka", "płatki owsiane jako baza, z dodatkami białka i owocu"),
    EGGS("Jajeczne", "jajecznica/omlet/gotowane jaja jako baza"),
    SANDWICH("Kanapka/Wrap", "pieczywo razowe lub tortilla + białko + warzywa"),
    SALAD("Sałatka", "warzywa jako baza (>200g) + białko + dressing oliwa/jogurt"),
    BOWL("Miska (bowl)", "ładnie ułożone w misce: białko + węgle + warzywa + sos"),
    SOUP("Zupa", "zupa krem lub zupa z mięsem/strączkami jako baza"),
    MEAT_RICE("Mięso + ryż/kasza", "klasyczne: mięso/ryba + węgiel (ryż/kasza/ziemniaki) + warzywa"),
    PASTA("Makaron", "makaron pełnoziarnisty + sos białkowy + warzywa"),
    COTTAGE("Twaróg/Skyr", "twaróg/skyr jako baza (lekka kolacja)"),
    FISH("Ryba", "ryba pieczona/duszona jako białko"),
    NO_PREFERENCE("Bez preferencji", "")
}
