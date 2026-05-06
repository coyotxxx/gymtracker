package pl.filebit.gymtracker.ui.diet.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.Recipe
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen

@Composable
fun RecipeBrowserScreen(
    onBack: () -> Unit,
    vm: RecipeBrowserViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val addResult by vm.addResult.collectAsStateWithLifecycle()

    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(addResult) {
        if (addResult != null) {
            kotlinx.coroutines.delay(2500)
            vm.consumeAddResult()
            selectedRecipe = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Przepisy (${state.all.size})", onBack = onBack)

            // Search
            OutlinedTextField(
                value = state.search,
                onValueChange = { vm.setSearch(it) },
                label = { Text("🔍 Szukaj przepisu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filter chips: Wszystko / Śniadania / Obiady / ❤
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip("Wszystkie", state.filter == RecipeFilter.ALL) { vm.setFilter(RecipeFilter.ALL) }
                }
                item {
                    FilterChip("🌅 Śniadania", state.filter == RecipeFilter.BREAKFAST) { vm.setFilter(RecipeFilter.BREAKFAST) }
                }
                item {
                    FilterChip("🍽 Obiady", state.filter == RecipeFilter.LUNCH) { vm.setFilter(RecipeFilter.LUNCH) }
                }
                item {
                    FilterChip("❤ Ulubione", state.filter == RecipeFilter.FAVORITES) { vm.setFilter(RecipeFilter.FAVORITES) }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Category chips: Wszystko / Słodkie / Słone (tylko dla śniadań)
            if (state.filter != RecipeFilter.LUNCH) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        SmallChip("Wszystkie", state.category == CategoryFilter.ALL) { vm.setCategory(CategoryFilter.ALL) }
                    }
                    item {
                        SmallChip("🍯 Słodkie", state.category == CategoryFilter.SWEET) { vm.setCategory(CategoryFilter.SWEET) }
                    }
                    item {
                        SmallChip("🧂 Słone", state.category == CategoryFilter.SAVORY) { vm.setCategory(CategoryFilter.SAVORY) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Sort chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    SmallChip("A-Z", state.sort == SortOrder.NAME) { vm.setSort(SortOrder.NAME) }
                }
                item {
                    SmallChip("kcal ↑", state.sort == SortOrder.KCAL_ASC) { vm.setSort(SortOrder.KCAL_ASC) }
                }
                item {
                    SmallChip("kcal ↓", state.sort == SortOrder.KCAL_DESC) { vm.setSort(SortOrder.KCAL_DESC) }
                }
                item {
                    SmallChip("❤ pierwsze", state.sort == SortOrder.FAV_FIRST) { vm.setSort(SortOrder.FAV_FIRST) }
                }
            }

            Spacer(Modifier.height(8.dp))

            val filtered = state.filtered
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.all.isEmpty()) "Baza przepisów ładuje się przy pierwszym uruchomieniu."
                        else "Brak przepisów pasujących do filtrów.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered.size) { idx ->
                        RecipeRow(
                            recipe = filtered[idx],
                            onClick = { selectedRecipe = filtered[idx] },
                            onToggleFav = { vm.toggleFavorite(filtered[idx]) }
                        )
                    }
                }
            }
        }
    }

    selectedRecipe?.let { r ->
        RecipeDetailDialog(
            recipe = r,
            addResult = addResult,
            onAddToDiet = { mt -> vm.addToDiet(r, mt, System.currentTimeMillis()) },
            onToggleFav = { vm.toggleFavorite(r) },
            onDismiss = { selectedRecipe = null; vm.consumeAddResult() }
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            ),
            color = if (selected) AccentOrange else DarkOnSurface
        )
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AccentOrange.copy(alpha = 0.15f) else DarkSurfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (selected) AccentOrange else DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun RecipeRow(
    recipe: Recipe,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val emoji = when {
                        recipe.mealType == MealType.BREAKFAST && recipe.mealCategory == "sweet" -> "🍯"
                        recipe.mealType == MealType.BREAKFAST -> "🌅"
                        recipe.mealType == MealType.LUNCH -> "🍽"
                        else -> "🍴"
                    }
                    Text(
                        "$emoji ${recipe.name}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = DarkOnSurface,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${recipe.kcalPerServing} kcal · B${recipe.proteinPerServing} W${recipe.carbsPerServing} T${recipe.fatPerServing} · ⏱ ${recipe.prepMinutes} min" +
                        if (recipe.servings > 1) " · ${recipe.servings} porcji" else "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = AccentOrange
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(onClick = onToggleFav)
                    .padding(8.dp)
            ) {
                Text(
                    if (recipe.isFavorite) "❤" else "🤍",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailDialog(
    recipe: Recipe,
    addResult: RecipeBrowserViewModel.AddResult?,
    onAddToDiet: (MealType) -> Unit,
    onToggleFav: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMealType by remember(recipe.id) {
        mutableStateOf(recipe.mealType)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(recipe.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clickable(onClick = onToggleFav)
                        .padding(4.dp)
                ) {
                    Text(if (recipe.isFavorite) "❤" else "🤍", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Makro
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "${recipe.kcalPerServing} kcal · B${recipe.proteinPerServing} W${recipe.carbsPerServing} T${recipe.fatPerServing}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                            ),
                            color = AccentOrange
                        )
                        Text(
                            "⏱ ${recipe.prepMinutes} min · ${recipe.servings} ${if (recipe.servings == 1) "porcja" else "porcji"}",
                            style = MaterialTheme.typography.labelSmall, color = DarkOnSurfaceVariant
                        )
                    }
                }
                // Składniki
                Text("SKŁADNIKI", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                ), color = DarkOnSurfaceVariant)
                if (!recipe.rawIngredientsText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            recipe.rawIngredientsText.split("\n").filter { it.isNotBlank() }.forEach { line ->
                                Text("• $line", style = MaterialTheme.typography.bodySmall, color = DarkOnSurface)
                            }
                        }
                    }
                }
                // Instrukcja
                Text("PRZYGOTOWANIE", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                ), color = DarkOnSurfaceVariant)
                Text(
                    recipe.instructions.ifBlank { "Brak instrukcji." },
                    style = MaterialTheme.typography.bodySmall, color = DarkOnSurface
                )

                // Slot picker
                Text("DODAJ DO SLOTU", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                ), color = DarkOnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealType.values().forEach { mt ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (selectedMealType == mt) AccentOrange.copy(alpha = 0.18f) else DarkSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMealType = mt },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (mt) {
                                    MealType.BREAKFAST -> "🌅"
                                    MealType.LUNCH -> "🍽"
                                    MealType.DINNER -> "🌙"
                                    MealType.SNACK -> "🥨"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                // Result
                addResult?.let { res ->
                    val (text, color) = when (res) {
                        is RecipeBrowserViewModel.AddResult.Success ->
                            "✓ Dodano ${res.matched}/${res.total} składników do dziennika" to SuccessGreen
                        is RecipeBrowserViewModel.AddResult.Failed ->
                            "✗ ${res.reason}" to AccentOrange
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAddToDiet(selectedMealType) }) {
                Text("Dodaj do diety", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = DarkOnSurfaceVariant)
            }
        }
    )
}
