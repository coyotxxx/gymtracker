package pl.filebit.gymtracker.data.repository

/**
 * Statyczne szacunki kcal/makro dla popularnych dań poza domem.
 * Wartości średnie — confidence MEDIUM. Lepsze niż "AI zgadnij".
 */
data class FoodEstimate(
    val displayName: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val confidence: EstimateConfidence,
    val notes: String = ""
)

enum class EstimateConfidence { LOW, MEDIUM, HIGH }

object EmergencyFoodEstimates {

    /**
     * Tabela typowych dań/posiłków na mieście. Lookup po słowach kluczowych w nazwie.
     */
    val DISHES: List<FoodEstimate> = listOf(
        // === Fast food ===
        FoodEstimate("Kebab klasyczny (350g)", 700, 35, 65, 30, EstimateConfidence.MEDIUM,
            notes = "Mięso wołowo-jagnięce + chleb pita + warzywa + sos czosnkowy."),
        FoodEstimate("Kebab w bułce", 650, 30, 60, 28, EstimateConfidence.MEDIUM),
        FoodEstimate("Kebab XL z frytkami", 1100, 45, 100, 50, EstimateConfidence.MEDIUM),

        FoodEstimate("Pizza średnia margherita (1/2)", 700, 28, 80, 25, EstimateConfidence.MEDIUM),
        FoodEstimate("Pizza średnia pepperoni (1/2)", 850, 35, 80, 35, EstimateConfidence.MEDIUM),
        FoodEstimate("Pizza średnia hawajska (1/2)", 750, 30, 85, 25, EstimateConfidence.MEDIUM),
        FoodEstimate("Pizza średnia (cała)", 1500, 60, 170, 55, EstimateConfidence.MEDIUM,
            notes = "Średnio 30cm. Cała pizza to często dzienna nadwyżka."),

        FoodEstimate("Burger klasyczny", 600, 30, 50, 30, EstimateConfidence.MEDIUM),
        FoodEstimate("Big Mac", 540, 25, 45, 28, EstimateConfidence.HIGH),
        FoodEstimate("Whopper", 660, 28, 50, 38, EstimateConfidence.HIGH),
        FoodEstimate("Burger podwójny + frytki", 1100, 45, 100, 50, EstimateConfidence.MEDIUM),

        FoodEstimate("Sushi 8 sztuk maki", 350, 12, 60, 5, EstimateConfidence.MEDIUM),
        FoodEstimate("Sushi 12 sztuk mix", 550, 22, 90, 10, EstimateConfidence.MEDIUM),
        FoodEstimate("Sushi 20 sztuk set", 850, 35, 140, 15, EstimateConfidence.MEDIUM),

        // === Polskie ===
        FoodEstimate("Pierogi z mięsem (10 szt)", 600, 25, 75, 20, EstimateConfidence.MEDIUM),
        FoodEstimate("Pierogi ruskie (10 szt)", 550, 18, 78, 18, EstimateConfidence.MEDIUM),
        FoodEstimate("Schabowy z ziemniakami i surówką", 800, 45, 60, 35, EstimateConfidence.MEDIUM),
        FoodEstimate("Bigos", 350, 18, 20, 22, EstimateConfidence.MEDIUM),
        FoodEstimate("Zapiekanka", 450, 18, 60, 15, EstimateConfidence.MEDIUM),
        FoodEstimate("Hot-dog", 350, 12, 30, 20, EstimateConfidence.MEDIUM),

        // === Azjatyckie ===
        FoodEstimate("Wrap z kurczakiem", 500, 28, 50, 18, EstimateConfidence.MEDIUM),
        FoodEstimate("Bowl z kurczakiem i ryżem", 650, 40, 70, 18, EstimateConfidence.MEDIUM),
        FoodEstimate("Curry z ryżem", 700, 25, 80, 25, EstimateConfidence.MEDIUM),
        FoodEstimate("Pad thai", 600, 22, 75, 22, EstimateConfidence.MEDIUM),

        // === Lekkie / café ===
        FoodEstimate("Sałatka cezar z kurczakiem", 450, 30, 25, 25, EstimateConfidence.MEDIUM),
        FoodEstimate("Sałatka grecka", 350, 12, 20, 25, EstimateConfidence.MEDIUM),
        FoodEstimate("Croissant", 280, 6, 30, 15, EstimateConfidence.HIGH),
        FoodEstimate("Bagietka z szynką", 400, 18, 50, 14, EstimateConfidence.MEDIUM),

        // === Słodkie ===
        FoodEstimate("Pączek", 280, 5, 35, 14, EstimateConfidence.MEDIUM),
        FoodEstimate("Jabłecznik kawałek", 320, 4, 45, 14, EstimateConfidence.MEDIUM),
        FoodEstimate("Sernik kawałek", 380, 8, 35, 22, EstimateConfidence.MEDIUM),
        FoodEstimate("Lody waflowe", 220, 4, 30, 10, EstimateConfidence.MEDIUM),

        // === Napoje ===
        FoodEstimate("Cola/Pepsi 0.5l", 210, 0, 53, 0, EstimateConfidence.HIGH),
        FoodEstimate("Piwo 0.5l", 200, 2, 16, 0, EstimateConfidence.HIGH),
        FoodEstimate("Wino kieliszek 150ml", 125, 0, 4, 0, EstimateConfidence.HIGH),
        FoodEstimate("Wódka kieliszek 50ml", 110, 0, 0, 0, EstimateConfidence.HIGH)
    )

    /**
     * Wyszukuje najbliższe dopasowanie po słowach kluczowych w nazwie.
     */
    fun findByKeyword(query: String): List<FoodEstimate> {
        val keys = query.trim().lowercase().split(" ").filter { it.length >= 3 }
        if (keys.isEmpty()) return emptyList()
        return DISHES.filter { dish ->
            val name = dish.displayName.lowercase()
            keys.any { name.contains(it) }
        }
    }

    /**
     * Najbardziej popularne (top 10 do quick selection w UI).
     * Wybierane po nazwie zamiast hardkodowanych indeksów (odporne na zmiany kolejności).
     */
    val POPULAR: List<FoodEstimate>
        get() = listOf(
            "Kebab klasyczny",
            "Pizza średnia margherita",
            "Burger klasyczny",
            "Sushi 12 sztuk",
            "Pierogi z mięsem",
            "Schabowy",
            "Wrap z kurczakiem",
            "Sałatka cezar",
            "Pączek",
            "Cola"
        ).mapNotNull { needle ->
            DISHES.firstOrNull { it.displayName.contains(needle, ignoreCase = true) }
        }
}
