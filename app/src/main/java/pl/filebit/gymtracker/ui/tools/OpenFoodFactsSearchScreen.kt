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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SelectableChip
import kotlin.math.roundToInt

@Composable
fun OpenFoodFactsSearchScreen(
    onBack: () -> Unit,
    vm: OpenFoodFactsSearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Dodaj produkt", onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // === WYSZUKIWARKA ===
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nazwa produktu…", color = DarkOnSurfaceVariant) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = DarkOnSurfaceVariant)
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .background(AccentOrange, RoundedCornerShape(8.dp))
                                .clickable { vm.search() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Szukaj",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = DarkBg
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = DarkOutline,
                        cursorColor = AccentOrange
                    )
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "Ryż, makaron, kasza — wybieraj wartości SUROWE (przed " +
                        "ugotowaniem), nie wersje gotowane. Tak liczy cała aplikacja.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )

                // === KATEGORIE — szybkie wyszukiwanie produktów "pod siłownię" ===
                Spacer(Modifier.height(10.dp))
                Text(
                    "Lub wybierz kategorię — produkty wartościowe pod trening:",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FoodCategory.entries.filter { it != FoodCategory.OTHER }) { c ->
                        SelectableChip(
                            text = categoryLabelFood(c),
                            selected = state.categoryFilter == c,
                            onClick = { vm.searchCategory(c) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // === TREŚĆ ===
            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentOrange)
                    }
                }
                state.rows.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.message ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.rows, key = { it.product.barcode ?: it.product.name }) { row ->
                            OffResultRow(
                                row = row,
                                onAdd = { vm.addProduct(row.product) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OffResultRow(
    row: OpenFoodFactsSearchViewModel.ResultRow,
    onAdd: () -> Unit
) {
    val p = row.product
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
                    p.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${categoryLabelFood(p.category)} · " +
                        "${p.kcalPer100g.roundToInt()} kcal/100g · " +
                        "B${p.proteinPer100g.roundToInt()} " +
                        "W${p.carbsPer100g.roundToInt()} " +
                        "T${p.fatPer100g.roundToInt()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (row.alreadyInDb) {
                Row(
                    modifier = Modifier
                        .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "W bazie",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(AccentOrange.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.5.dp, AccentOrange, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ Dodaj",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}
