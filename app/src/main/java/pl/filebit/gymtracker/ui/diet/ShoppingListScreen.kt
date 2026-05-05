package pl.filebit.gymtracker.ui.diet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.ShoppingListItem
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    vm: ShoppingListViewModel = hiltViewModel()
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2000)
            vm.consumeStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Lista zakupów", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Generuj — wybór zakresu
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkOutlineSoft),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Wygeneruj na podstawie posiłków:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = DarkOnSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenerateButton("3 dni", { vm.generate(3) }, generating, Modifier.weight(1f))
                                GenerateButton("7 dni", { vm.generate(7) }, generating, Modifier.weight(1f))
                                GenerateButton("14 dni", { vm.generate(14) }, generating, Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Status
                statusMessage?.let { msg ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "✓ $msg",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen
                            )
                        }
                    }
                }

                // Header listy + akcje
                list?.let { l ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    l.name,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = DarkOnSurface
                                )
                                val df = SimpleDateFormat("d MMM yyyy", Locale("pl", "PL"))
                                Text(
                                    "${df.format(Date(l.fromDateMs))} – ${df.format(Date(l.toDateMs - 1))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkOnSurfaceVariant
                                )
                                if (items.isNotEmpty()) {
                                    val purchased = items.count { it.isPurchased }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "$purchased / ${items.size} kupione",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = AccentOrange
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallActionButton("📋 Kopiuj", Modifier.weight(1f)) {
                                        scope.launch {
                                            val text = vm.exportText()
                                            copyToClipboard(context, text)
                                        }
                                    }
                                    SmallActionButton("📤 Udostępnij", Modifier.weight(1f)) {
                                        scope.launch {
                                            val text = vm.exportText()
                                            shareText(context, text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (list != null && items.isEmpty() && !generating) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Brak posiłków w wybranym zakresie.\n\nDodaj posiłki w zakładce Dieta lub wygeneruj plan AI, potem ponownie wygeneruj listę.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }

                // Pozycje pogrupowane po kategorii
                val grouped = items.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
                grouped.forEach { (cat, group) ->
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            categoryHeader(cat).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp
                            ),
                            color = AccentOrange
                        )
                    }
                    items(group.size) { idx ->
                        ShoppingItemRow(group[idx]) { vm.togglePurchased(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(
    label: String,
    onClick: () -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                if (disabled) DarkSurfaceVariant.copy(alpha = 0.3f) else AccentOrange.copy(alpha = 0.15f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (disabled) DarkOnSurfaceVariant else AccentOrange
        )
    }
}

@Composable
private fun SmallActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = DarkOnSurface
        )
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingListItem,
    onToggle: (ShoppingListItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(item) },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPurchased) DarkSurface.copy(alpha = 0.6f) else DarkSurface
        ),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isPurchased,
                onCheckedChange = { onToggle(item) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentOrange,
                    uncheckedColor = DarkOnSurfaceVariant
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.productName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (item.isPurchased) DarkOnSurfaceVariant else DarkOnSurface
                )
                if (item.occurrences > 1) {
                    Text(
                        "${item.occurrences} dni w planie",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Text(
                formatGrams(item.grams),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = if (item.isPurchased) DarkOnSurfaceVariant else AccentOrange
            )
        }
    }
}

private fun formatGrams(g: Double): String =
    if (g >= 1000) "%.1f kg".format(g / 1000.0)
    else "${g.roundToInt()} g"

private fun categoryHeader(c: FoodCategory): String = when (c) {
    FoodCategory.PROTEIN -> "Białko"
    FoodCategory.CARBS -> "Węglowodany"
    FoodCategory.FAT -> "Tłuszcze"
    FoodCategory.DAIRY -> "Nabiał"
    FoodCategory.VEGETABLE -> "Warzywa"
    FoodCategory.FRUIT -> "Owoce"
    FoodCategory.OTHER -> "Inne"
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Lista zakupów", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Udostępnij listę zakupów")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
