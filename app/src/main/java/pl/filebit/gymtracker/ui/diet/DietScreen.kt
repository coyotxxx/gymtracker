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
import androidx.compose.material.icons.filled.Delete
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

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Dieta", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero — kcal goal + makro pierścienie
                item { DayHeroCard(state) }

                // 3 sekcje posiłków + przekąski
                items(state.groups, key = { it.type.name }) { group ->
                    MealGroupCard(
                        group = group,
                        onAdd = { addMealForType = group.type },
                        onDelete = { id -> vm.deleteMeal(id) }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
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
private fun DayHeroCard(state: DietUiState) {
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
            Text(
                "DZIŚ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
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
                Text(
                    mealTypeLabel(group.type),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${group.totals.kcal.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = AccentOrange
                )
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
