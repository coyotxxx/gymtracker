package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import pl.filebit.gymtracker.ai.AiMealRecipe
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SelectableChip

private const val CLASSIC_TAG = "Klasyczny"

/**
 * Pokazuje PEŁEN przepis: nazwa + składniki + makro + instrukcje krok po kroku.
 * Otwierany z MealGroupCard (przycisk "📝 Przepis"), karta od spodu.
 *
 * v1.29.2: gdy danie da się zrobić na kilku urządzeniach — chipy przełącznika
 * (Klasyczny / Cosori / Thermomix). Wariant pobiera się z AI raz i jest
 * zapisany w przepisie; kolejne tapnięcia tylko go pokazują.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDialog(
    recipe: AiMealRecipe,
    /** Tagi do chipów przełącznika — np. ["Klasyczny","Cosori"]. ≤1 = brak przełącznika. */
    deviceChips: List<String>,
    /** Tag, dla którego pobranie wariantu się nie powiodło (null = brak błędu). */
    errorTag: String?,
    /** Żądanie pobrania wariantu pod dany tag. */
    onSelectDevice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val homeTag = recipe.device?.takeIf { it.isNotBlank() } ?: CLASSIC_TAG
    val showSwitcher = deviceChips.size > 1
    var selectedTag by remember(recipe.name) { mutableStateOf(homeTag) }

    fun instructionsFor(tag: String): String? =
        if (tag == homeTag) recipe.instructions else recipe.instructionsByDevice[tag]

    ScrollableSheetShell(
        title = recipe.name,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        headerExtra = if (!showSwitcher && homeTag != CLASSIC_TAG) {
            { DeviceTag(homeTag) }
        } else null,
        bodyArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Makro / czas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    "${recipe.kcal} kcal · B${recipe.proteinG} W${recipe.carbsG} T${recipe.fatG}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    ),
                    color = AccentOrange
                )
                Text(
                    "⏱ ${recipe.prepMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }

        // Przełącznik urządzeń
        if (showSwitcher) {
            Text(
                "PRZYGOTOWANIE NA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                deviceChips.forEach { tag ->
                    SelectableChip(
                        text = tag,
                        selected = tag == selectedTag,
                        onClick = {
                            selectedTag = tag
                            if (instructionsFor(tag) == null) onSelectDevice(tag)
                        }
                    )
                }
            }
        }

        // Składniki
        Text(
            "SKŁADNIKI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recipe.ingredients.forEach { ing ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentOrange,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            ing.productName,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${ing.grams} g",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }

        // Instrukcja — dla wybranego urządzenia
        Text(
            "JAK PRZYRZĄDZIĆ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        val instr = instructionsFor(selectedTag)
        when {
            instr != null && instr.isNotBlank() -> Text(
                instr,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface
            )
            instr != null -> Text(
                "Brak szczegółowej instrukcji dla tego dania. Standardowy sposób: " +
                    "ugotuj/usmaż białko, przygotuj węgle, dodaj warzywa.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            errorTag == selectedTag -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Nie udało się pobrać wersji „$selectedTag”. Sprawdź połączenie i klucz AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                TextButton(
                    onClick = { onSelectDevice(selectedTag) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Spróbuj ponownie", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AccentOrange
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Generuję wersję: $selectedTag…",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

/** Chip urządzenia przy tytule przepisu — np. „Cosori", „Thermomix". */
@Composable
private fun DeviceTag(name: String) {
    Box(
        modifier = Modifier
            .background(AccentOrange.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold, fontSize = 11.sp
            ),
            color = AccentOrange
        )
    }
}
