package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.repository.QuickComposeService
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SuccessGreen

/**
 * Quick Compose — szybkie komponowanie posiłku w stylu wymienników Macieja.
 *
 * Krok 1: wybierz slot (śniadanie/obiad/kolacja)
 * Krok 2: wybierz po jednym z każdej kategorii (1× białko, 1× węgiel, 1× tłuszcz)
 *         + opcjonalnie warzywa
 * Krok 3: aplikacja sama oblicza gramatury żeby trafić w kcal/białko/tłuszcz slotu
 * Krok 4: "Dodaj do dziennika" zapisuje 4 osobne MealEntry
 */
@Composable
fun QuickComposeDialog(
    products: List<FoodProduct>,
    mealsCount: Int,
    targetKcalPerSlot: Int,
    targetProteinPerSlot: Int,
    targetFatPerSlot: Int,
    composeService: QuickComposeService,
    onAccept: (slot: Int, picks: List<Pair<FoodProduct, Int>>) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(QcStep.MEAL_TYPE) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var selectedProtein by remember { mutableStateOf<FoodProduct?>(null) }
    var selectedCarb by remember { mutableStateOf<FoodProduct?>(null) }
    var selectedFat by remember { mutableStateOf<FoodProduct?>(null) }
    var selectedVeg by remember { mutableStateOf<FoodProduct?>(null) }
    var pickerCategory by remember { mutableStateOf<FoodCategory?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🥗 Szybki kompozytor posiłku",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                }
                Text(
                    "Filozofia: 1 białko + 1 węgiel + 1 tłuszcz + warzywa do woli. " +
                        "Aplikacja sama dobiera gramy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (pickerCategory != null) {
                    // EKRAN PICKERA PRODUKTU
                    Text(
                        "Wybierz ${categoryLabelPL(pickerCategory!!)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = DarkOnSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    val categoryProducts = products
                        .filter { it.category == pickerCategory }
                        .sortedWith(compareByDescending<FoodProduct> { it.isFavorite }.thenBy { it.name })
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categoryProducts, key = { it.id }) { p ->
                            ProductPickRow(p, onClick = {
                                when (pickerCategory) {
                                    FoodCategory.PROTEIN, FoodCategory.DAIRY -> selectedProtein = p
                                    FoodCategory.CARBS -> selectedCarb = p
                                    FoodCategory.FAT -> selectedFat = p
                                    FoodCategory.VEGETABLE -> selectedVeg = p
                                    else -> {}
                                }
                                pickerCategory = null
                            })
                        }
                    }
                    TextButton(onClick = { pickerCategory = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("← Wróć", color = DarkOnSurfaceVariant)
                    }
                    return@Card
                }

                when (step) {
                    QcStep.MEAL_TYPE -> {
                        Text("1. Wybierz posiłek:", color = DarkOnSurface, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // v2.73.0: sloty dynamicznie 1..N (Posiłek N), nie sztywne 4 typy.
                            (1..mealsCount).forEach { slot ->
                                BigChoiceButton(
                                    "🍽️ ${pl.filebit.gymtracker.util.MealSlots.label(slot)}",
                                    selected = selectedSlot == slot
                                ) {
                                    selectedSlot = slot
                                    step = QcStep.PICK_PRODUCTS
                                }
                            }
                        }
                    }
                    QcStep.PICK_PRODUCTS -> {
                        Text("Slot: ${pl.filebit.gymtracker.util.MealSlots.label(selectedSlot!!)}", color = AccentOrange, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Cel: $targetKcalPerSlot kcal · B${targetProteinPerSlot}g T${targetFatPerSlot}g",
                            color = DarkOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CategoryPickerRow(
                                label = "Białko",
                                emoji = "🥩",
                                product = selectedProtein,
                                required = true,
                                onClick = { pickerCategory = FoodCategory.PROTEIN }
                            )
                            CategoryPickerRow(
                                label = "Węgle",
                                emoji = "🍚",
                                product = selectedCarb,
                                required = true,
                                onClick = { pickerCategory = FoodCategory.CARBS }
                            )
                            CategoryPickerRow(
                                label = "Tłuszcz",
                                emoji = "🥑",
                                product = selectedFat,
                                required = true,
                                onClick = { pickerCategory = FoodCategory.FAT }
                            )
                            CategoryPickerRow(
                                label = "Warzywa (200g flat)",
                                emoji = "🥦",
                                product = selectedVeg,
                                required = false,
                                onClick = { pickerCategory = FoodCategory.VEGETABLE }
                            )

                            // Pokaż wynik kalkulacji jeśli mamy 3 produkty
                            if (selectedProtein != null && selectedCarb != null && selectedFat != null) {
                                val result = composeService.compose(
                                    protein = selectedProtein!!,
                                    carb = selectedCarb!!,
                                    fat = selectedFat!!,
                                    vegetable = selectedVeg,
                                    targetKcal = targetKcalPerSlot,
                                    targetProteinG = targetProteinPerSlot,
                                    targetFatG = targetFatPerSlot
                                )
                                Spacer(Modifier.height(8.dp))
                                ResultCard(result, selectedProtein!!, selectedCarb!!, selectedFat!!, selectedVeg)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { step = QcStep.MEAL_TYPE }) {
                                Text("← Wróć", color = DarkOnSurfaceVariant)
                            }
                            val canAccept = selectedProtein != null && selectedCarb != null && selectedFat != null
                            TextButton(
                                onClick = {
                                    val result = composeService.compose(
                                        protein = selectedProtein!!,
                                        carb = selectedCarb!!,
                                        fat = selectedFat!!,
                                        vegetable = selectedVeg,
                                        targetKcal = targetKcalPerSlot,
                                        targetProteinG = targetProteinPerSlot,
                                        targetFatG = targetFatPerSlot
                                    )
                                    val picks = mutableListOf<Pair<FoodProduct, Int>>()
                                    picks += selectedProtein!! to result.proteinGrams
                                    picks += selectedCarb!! to result.carbGrams
                                    picks += selectedFat!! to result.fatGrams
                                    if (selectedVeg != null) picks += selectedVeg!! to result.vegetableGrams
                                    onAccept(selectedSlot!!, picks)
                                },
                                enabled = canAccept
                            ) {
                                Text(
                                    if (canAccept) "✓ Dodaj do dziennika" else "Wybierz produkty",
                                    color = if (canAccept) AccentOrange else DarkOnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class QcStep { MEAL_TYPE, PICK_PRODUCTS }

@Composable
private fun BigChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurface
        ),
        border = BorderStroke(1.dp, if (selected) AccentOrange else DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(16.dp),
            color = if (selected) AccentOrange else DarkOnSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun CategoryPickerRow(
    label: String,
    emoji: String,
    product: FoodProduct?,
    required: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, if (product != null) AccentOrange.copy(alpha = 0.4f) else DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label + (if (required) " *" else ""), color = DarkOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(
                    product?.name ?: "Wybierz…",
                    color = if (product != null) DarkOnSurface else DarkOnSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text("›", color = DarkOnSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ResultCard(
    result: QuickComposeService.ComposeResult,
    protein: FoodProduct,
    carb: FoodProduct,
    fat: FoodProduct,
    vegetable: FoodProduct?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("📋 Wynik kalkulacji:", color = AccentOrange, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            ResultLine(protein.name, result.proteinGrams)
            ResultLine(carb.name, result.carbGrams)
            ResultLine(fat.name, result.fatGrams)
            if (vegetable != null) ResultLine("${vegetable.name} (warzywo)", result.vegetableGrams)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroBubble("kcal", result.realKcal.toString(), AccentOrange)
                MacroBubble("B", "${result.realProteinG}g", DarkOnSurface)
                MacroBubble("W", "${result.realCarbsG}g", SuccessGreen)
                MacroBubble("T", "${result.realFatG}g", DarkOnSurface)
            }
            if (result.warnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                result.warnings.forEach { w ->
                    Text("⚠ $w", color = DarkOnSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
private fun ResultLine(name: String, grams: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = DarkOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("${grams}g", color = AccentOrange, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MacroBubble(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold))
        Text(label, color = DarkOnSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
    }
}

@Composable
private fun ProductPickRow(p: FoodProduct, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.isFavorite) {
                Text("❤", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, color = DarkOnSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "${p.kcalPer100g.toInt()} kcal · B${p.proteinPer100g.toInt()}/W${p.carbsPer100g.toInt()}/T${p.fatPer100g.toInt()} (na 100g)",
                    color = DarkOnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
        }
    }
}

private fun categoryLabelPL(c: FoodCategory): String = when (c) {
    FoodCategory.PROTEIN -> "produkt białkowy"
    FoodCategory.CARBS -> "źródło węglowodanów"
    FoodCategory.FAT -> "źródło tłuszczu"
    FoodCategory.VEGETABLE -> "warzywo"
    FoodCategory.FRUIT -> "owoc"
    FoodCategory.DAIRY -> "produkt mleczny"
    FoodCategory.OTHER -> "produkt"
}

