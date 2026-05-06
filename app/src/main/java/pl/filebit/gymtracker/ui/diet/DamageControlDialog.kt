package pl.filebit.gymtracker.ui.diet

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import pl.filebit.gymtracker.data.repository.DamageControlResult
import pl.filebit.gymtracker.data.repository.EmergencyFoodEstimates
import pl.filebit.gymtracker.data.repository.FoodEstimate
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SuccessGreen

@Composable
fun DamageControlDialog(
    onPickEstimate: (FoodEstimate) -> Unit,
    onCustomKcal: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var customKcalText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 Co zjadłeś nieplanowanego?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Wybierz z listy lub wpisz kcal ręcznie. System przeliczy pozostały dzień bez restrykcji.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                Text(
                    "POPULARNE:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(EmergencyFoodEstimates.POPULAR.size) { idx ->
                        val est = EmergencyFoodEstimates.POPULAR[idx]
                        EstimateRow(est) { onPickEstimate(est); onDismiss() }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "ALBO RĘCZNIE:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                OutlinedTextField(
                    value = customKcalText,
                    onValueChange = { customKcalText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Kcal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kcal = customKcalText.toIntOrNull()?.coerceIn(50, 5000) ?: 0
                if (kcal > 0) {
                    onCustomKcal(kcal)
                    onDismiss()
                }
            }) {
                Text("Zapisz kcal", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun EstimateRow(est: FoodEstimate, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    est.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Text(
                    "B${est.proteinG} W${est.carbsG} T${est.fatG}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
            }
            Text(
                "${est.kcal} kcal",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                ),
                color = AccentOrange
            )
        }
    }
}

/**
 * Pokazuje wynik przeliczenia po nieplanowanym jedzeniu.
 */
@Composable
fun DamageControlResultDialog(
    result: DamageControlResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 Przeliczono pozostały dzień", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "Nieplanowane: +${result.unplannedKcal} kcal",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = AccentOrange
                        )
                        Text(
                            "Zjedzono dziś: ${result.alreadyConsumedKcal} / ${result.originalDailyGoal} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurface
                        )
                        if (result.remainingSlots > 0) {
                            Text(
                                "Pozostałe ${result.remainingSlots} slot${if (result.remainingSlots > 1) "y" else ""}: ${result.newKcalPerRemainingSlot} kcal/slot",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                ),
                                color = SuccessGreen
                            )
                        }
                    }
                }
                Text(
                    result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        }
    )
}
