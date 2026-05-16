package pl.filebit.gymtracker.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SelectableChip
import kotlin.math.roundToInt

@Composable
fun FoodDatabaseScreen(
    onBack: () -> Unit,
    vm: FoodDatabaseViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAddInfo by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Baza produktów", onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // === DODAJ PRODUKT (Etap 2 — Open Food Facts) ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .border(1.5.dp, AccentOrange, RoundedCornerShape(12.dp))
                        .clickable { showAddInfo = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Dodaj produkt",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = AccentOrange
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // === WYSZUKIWARKA ===
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
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

                // === FILTR KATEGORII + ULUBIONE ===
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SelectableChip(
                            text = "❤ Ulubione",
                            selected = state.favoritesOnly,
                            onClick = { vm.toggleFavoritesOnly() }
                        )
                    }
                    item {
                        SelectableChip(
                            text = "Wszystkie",
                            selected = state.category == null,
                            onClick = { vm.setCategory(null) }
                        )
                    }
                    items(FoodCategory.entries.filter { it != FoodCategory.OTHER }) { c ->
                        SelectableChip(
                            text = categoryLabelFood(c),
                            selected = state.category == c,
                            onClick = { vm.setCategory(c) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // === SORTOWANIE ===
                LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Sortuj:",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                    items(FoodDatabaseViewModel.ProductSort.entries) { s ->
                        SelectableChip(
                            text = s.label,
                            selected = state.sort == s,
                            onClick = { vm.setSort(s) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (state.products.size == state.totalCount)
                        "${state.totalCount} produktów w bazie"
                    else
                        "${state.products.size} z ${state.totalCount} produktów",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
            }

            // === LISTA PRODUKTÓW ===
            if (state.products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Brak produktów dla wybranych filtrów",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        FoodProductRow(
                            product = product,
                            onToggleFavorite = { vm.toggleFavorite(product) }
                        )
                    }
                }
            }
        }
    }

    if (showAddInfo) {
        AlertDialog(
            onDismissRequest = { showAddInfo = false },
            title = { Text("Dodawanie produktów", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Dodawanie produktów z zewnętrznej bazy Open Food Facts " +
                        "pojawi się w kolejnej aktualizacji. Wyszukasz tam produkt " +
                        "i dodasz go do swojej bazy — AI będzie z niego korzystać przy generowaniu planów."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAddInfo = false }) {
                    Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun FoodProductRow(
    product: FoodProduct,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${categoryLabelFood(product.category)} · " +
                        "${product.kcalPer100g.roundToInt()} kcal/100g · " +
                        "B${product.proteinPer100g.roundToInt()} " +
                        "W${product.carbsPer100g.roundToInt()} " +
                        "T${product.fatPer100g.roundToInt()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (product.isFavorite) "❤" else "🤍",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

internal fun categoryLabelFood(c: FoodCategory): String = when (c) {
    FoodCategory.CARBS -> "Węglowodany"
    FoodCategory.PROTEIN -> "Białko"
    FoodCategory.FAT -> "Tłuszcze"
    FoodCategory.VEGETABLE -> "Warzywa"
    FoodCategory.DAIRY -> "Nabiał"
    FoodCategory.FRUIT -> "Owoce"
    FoodCategory.OTHER -> "Inne"
}
