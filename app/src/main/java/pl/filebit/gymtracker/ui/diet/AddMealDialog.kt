package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SelectableChip
import kotlin.math.roundToInt

@Composable
fun AddMealDialog(
    mealType: MealType,
    allProducts: List<FoodProduct>,
    searchQuery: String,
    categoryFilter: FoodCategory?,
    filteredProducts: List<FoodProduct>,
    onSearchQueryChange: (String) -> Unit,
    onCategoryFilterChange: (FoodCategory?) -> Unit,
    favoritesOnly: Boolean = false,
    onFavoritesOnlyChange: (Boolean) -> Unit = {},
    onAdd: (productId: Long, grams: Double) -> Unit,
    onDismiss: () -> Unit,
    onToggleFavorite: ((FoodProduct) -> Unit)? = null
) {
    var selectedProduct by remember { mutableStateOf<FoodProduct?>(null) }
    var gramsText by remember { mutableStateOf("100") }

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
                        "Dodaj do ${mealTypeLabel(mealType)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (selectedProduct == null) {
                    // === KROK 1: wybór produktu ===
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Szukaj produktu…", color = DarkOnSurfaceVariant) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = DarkOnSurfaceVariant)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = DarkOutline,
                            cursorColor = AccentOrange
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            SelectableChip(
                                text = "❤ Ulubione",
                                selected = favoritesOnly,
                                onClick = { onFavoritesOnlyChange(!favoritesOnly) }
                            )
                        }
                        item {
                            SelectableChip(
                                text = "Wszystkie",
                                selected = categoryFilter == null,
                                onClick = { onCategoryFilterChange(null) }
                            )
                        }
                        items(FoodCategory.entries.filter { it != FoodCategory.OTHER }) { c ->
                            SelectableChip(
                                text = categoryLabel(c),
                                selected = categoryFilter == c,
                                onClick = { onCategoryFilterChange(c) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { p ->
                            ProductPickerRow(
                                p,
                                onClick = { selectedProduct = p },
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                    }
                } else {
                    // === KROK 2: wpisz gramy + podgląd makro ===
                    val p = selectedProduct!!
                    val g = gramsText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val factor = g / 100.0
                    val kcal = (p.kcalPer100g * factor).roundToInt()
                    val protein = (p.proteinPer100g * factor).roundToInt()
                    val carbs = (p.carbsPer100g * factor).roundToInt()
                    val fat = (p.fatPer100g * factor).roundToInt()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkOutlineSoft),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = DarkOnSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                categoryLabel(p.category),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = gramsText,
                                onValueChange = { v ->
                                    gramsText = v.filter { it.isDigit() || it == '.' || it == ',' }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Gramatura") },
                                suffix = { Text("g", color = DarkOnSurfaceVariant) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = DarkOutline,
                                    cursorColor = AccentOrange,
                                    focusedLabelColor = AccentOrange,
                                    unfocusedLabelColor = DarkOnSurfaceVariant
                                )
                            )

                            Spacer(Modifier.height(12.dp))

                            // Live preview
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MacroPreview("kcal", kcal.toString(), AccentOrange)
                                MacroPreview("B", "${protein}g", DarkOnSurface)
                                MacroPreview("W", "${carbs}g", DarkOnSurface)
                                MacroPreview("T", "${fat}g", DarkOnSurface)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { selectedProduct = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("← Wróć", color = DarkOnSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                if (g > 0) onAdd(p.id, g)
                            },
                            enabled = g > 0,
                            modifier = Modifier.weight(2f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentOrange,
                                contentColor = androidx.compose.ui.graphics.Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "✓ Dodaj $kcal kcal",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPickerRow(
    p: FoodProduct,
    onClick: () -> Unit,
    onToggleFavorite: ((FoodProduct) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface,
                    maxLines = 1
                )
                Text(
                    "${categoryLabel(p.category)} · " +
                        "B ${p.proteinPer100g.roundToInt()}g · " +
                        "W ${p.carbsPer100g.roundToInt()}g · " +
                        "T ${p.fatPer100g.roundToInt()}g (na 100g)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                "${p.kcalPer100g.roundToInt()}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = AccentOrange
            )
            Text(
                " kcal",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
            if (onToggleFavorite != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onToggleFavorite(p) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (p.isFavorite) "❤" else "🤍",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroPreview(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )
    }
}

internal fun categoryLabel(c: FoodCategory): String = when (c) {
    FoodCategory.CARBS -> "Węglowodany"
    FoodCategory.PROTEIN -> "Białko"
    FoodCategory.FAT -> "Tłuszcze"
    FoodCategory.VEGETABLE -> "Warzywa"
    FoodCategory.DAIRY -> "Nabiał"
    FoodCategory.FRUIT -> "Owoce"
    FoodCategory.OTHER -> "Inne"
}
