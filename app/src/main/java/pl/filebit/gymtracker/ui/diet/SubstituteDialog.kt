package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.repository.MatchTolerance
import pl.filebit.gymtracker.data.repository.Substitute
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import kotlin.math.roundToInt

/**
 * Dialog wyboru zamiennika produktu.
 * Pokazuje top-N alternatyw posortowanych po podobieństwie makro.
 * Po kliknięciu — wywołuje onSelect(product, suggestedGrams).
 */
@Composable
fun SubstituteDialog(
    original: FoodProduct,
    originalGrams: Double,
    substitutes: List<Substitute>,
    tolerance: MatchTolerance,
    onToleranceChange: (MatchTolerance) -> Unit,
    onSelect: (FoodProduct, Double) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🔄 Zamień produkt", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Zamiast: ${original.name} (${originalGrams.roundToInt()} g)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )

                // Toggle tolerancji
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToleranceChip("Ścisłe ±10%", tolerance == MatchTolerance.STRICT) {
                        onToleranceChange(MatchTolerance.STRICT)
                    }
                    ToleranceChip("Luźne ±25%", tolerance == MatchTolerance.LOOSE) {
                        onToleranceChange(MatchTolerance.LOOSE)
                    }
                    ToleranceChip("Tylko kcal", tolerance == MatchTolerance.KCAL_ONLY) {
                        onToleranceChange(MatchTolerance.KCAL_ONLY)
                    }
                }

                if (substitutes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Brak pasujących zamienników w tej tolerancji.\nSpróbuj 'Luźne' albo 'Tylko kcal'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(substitutes.size) { idx ->
                            SubstituteCard(substitutes[idx]) {
                                onSelect(it.product, it.suggestedGrams)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
        },
        dismissButton = null
    )
}

@Composable
private fun ToleranceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = if (selected) AccentOrange else DarkOnSurface
        )
    }
}

@Composable
private fun SubstituteCard(s: Substitute, onClick: (Substitute) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(s) },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${s.suggestedGrams.roundToInt()} g",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AccentOrange
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeltaText("kcal", s.kcalDelta)
                DeltaText("B", s.proteinDelta)
                DeltaText("W", s.carbsDelta)
                DeltaText("T", s.fatDelta)
            }
        }
    }
}

@Composable
private fun DeltaText(label: String, delta: Double) {
    val rounded = delta.roundToInt()
    val color = when {
        rounded == 0 -> DarkOnSurfaceVariant
        kotlin.math.abs(rounded) <= 2 -> SuccessGreen
        else -> AccentOrange
    }
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> ""
        else -> "±"
    }
    Text(
        "$label $sign$rounded",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = color
    )
}
