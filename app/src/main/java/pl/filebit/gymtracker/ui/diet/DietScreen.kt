package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.MealEntryWithMacros
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import kotlin.math.roundToInt

@Composable
fun DietScreen(
    onBack: () -> Unit,
    vm: DietViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var addMealForType by remember { mutableStateOf<MealType?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.config.mealRemindersEnabled, state.config.mealsPerDay) {
        // Reschedule notyfikacji przy każdej zmianie config-u (oraz przy pierwszym wejściu)
        vm.rescheduleReminders()
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Dieta", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero — kcal goal + makro pierścienie + settings + AI generator
                item {
                    DayHeroCard(
                        state = state,
                        onSettings = { showSettings = true },
                        onGenerateAi = { /* TODO v0.89.35: AI plan dnia */ }
                    )
                }

                // Sekcje posiłków (zgodnie z liczbą z DietConfig)
                items(state.groups.size) { idx ->
                    val group = state.groups[idx]
                    MealGroupCard(
                        group = group,
                        targetKcalPerMeal = state.perMealKcal,
                        onAdd = { addMealForType = group.type },
                        onDelete = { id -> vm.deleteMeal(id) }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showSettings) {
        DietSettingsDialog(
            initial = state.config,
            onSave = { vm.saveConfig(it) },
            onDismiss = { showSettings = false }
        )
    }

    // Dialog dodawania posiłku
    addMealForType?.let { mealType ->
        AddMealDialog(
            mealType = mealType,
            allProducts = state.productsAll,
            onDismiss = { addMealForType = null },
            onAdd = { productId, grams ->
                vm.addMeal(productId, grams, mealType)
                addMealForType = null
            },
            onSearchQueryChange = vm::setSearchQuery,
            onCategoryFilterChange = vm::setCategoryFilter,
            searchQuery = state.searchQuery,
            categoryFilter = state.categoryFilter,
            filteredProducts = state.filteredProducts
        )
    }
}

@Composable
private fun DayHeroCard(
    state: DietUiState,
    onSettings: () -> Unit,
    onGenerateAi: () -> Unit
) {
    val kcalNow = state.totals.kcal.roundToInt()
    val kcalGoal = state.goal.kcal
    val progress = if (kcalGoal > 0) (kcalNow.toFloat() / kcalGoal).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DZIŚ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceVariant, androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ustawienia diety",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$kcalNow",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkOnSurface,
                    letterSpacing = (-1).sp
                )
                Text(
                    " / $kcalGoal kcal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // Konfig wiersz: "${meals} posiłki w oknie 12:00 - 20:00 · ${perMealKcal} kcal/posiłek"
            Spacer(Modifier.height(2.dp))
            Text(
                "${state.config.mealsPerDay} posiłki · okno %02d:00-%02d:00 · %d kcal/posiłek".format(
                    state.config.windowStartHour,
                    state.config.windowEndHour(),
                    state.perMealKcal
                ),
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentOrange,
                trackColor = DarkSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Makro pierścienie
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                MacroRing(
                    label = "Białko",
                    current = state.totals.protein,
                    goal = state.goal.proteinG.toDouble(),
                    color = AccentOrange
                )
                MacroRing(
                    label = "Węgle",
                    current = state.totals.carbs,
                    goal = state.goal.carbsG.toDouble(),
                    color = SuccessGreen
                )
                MacroRing(
                    label = "Tłuszcz",
                    current = state.totals.fat,
                    goal = state.goal.fatG.toDouble(),
                    color = Color(0xFFFFB74D)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Button "Wygeneruj plan AI" — placeholder do v0.89.35
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onGenerateAi),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Wygeneruj plan dnia AI",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, current: Double, goal: Double, color: Color) {
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val sz = Size(size.width - stroke, size.height - stroke)
                val ofs = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = DarkSurfaceVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = ofs,
                    size = sz,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = ofs,
                    size = sz,
                    style = Stroke(width = stroke)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${current.roundToInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnSurface
                )
                Text(
                    "/${goal.roundToInt()}g",
                    fontSize = 9.sp,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun MealGroupCard(
    group: MealGroup,
    targetKcalPerMeal: Int,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.customLabel.ifBlank { mealTypeLabel(group.type) },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = DarkOnSurface
                        )
                        if (group.timeLabel.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    group.timeLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = AccentOrange
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${group.totals.kcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = AccentOrange
                    )
                    if (targetKcalPerMeal > 0) {
                        Text(
                            "cel: $targetKcalPerMeal",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
            if (group.entries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                group.entries.forEach { e ->
                    MealEntryRow(e, onDelete = { onDelete(e.entry.id) })
                }
            }
            Spacer(Modifier.height(8.dp))
            // Przycisk + dodaj — taki sam styl jak "+ Dodaj serię" w PlanEdit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Dodaj produkt",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun MealEntryRow(
    m: MealEntryWithMacros,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.product.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface,
                maxLines = 1
            )
            Text(
                "${m.entry.grams.roundToInt()} g · " +
                    "B ${m.protein.roundToInt()}g  W ${m.carbs.roundToInt()}g  T ${m.fat.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = DarkOnSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            "${m.kcal.roundToInt()} kcal",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            color = DarkOnSurface
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Usuń",
                tint = DarkOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

internal fun mealTypeLabel(t: MealType): String = when (t) {
    MealType.BREAKFAST -> "Śniadanie"
    MealType.LUNCH -> "Obiad"
    MealType.DINNER -> "Kolacja"
    MealType.SNACK -> "Przekąska"
}
