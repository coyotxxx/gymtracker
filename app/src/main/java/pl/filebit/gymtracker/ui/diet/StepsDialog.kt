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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

@Composable
fun StepsDialog(
    currentSteps: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (currentSteps > 0) currentSteps.toString() else "") }

    ScrollableDialogShell(
        title = "🚶 Kroki dziś",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
            TextButton(onClick = {
                val n = text.toIntOrNull()?.coerceIn(0, 100000) ?: 0
                onSave(n)
                onDismiss()
            }) {
                Text("Zapisz", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        bodyArrangement = Arrangement.spacedBy(10.dp)
    ) {
                Text(
                    "Wpisz ile zrobiłeś dziś kroków. Możesz odczytać z telefonu (Google Fit / Krokomierz).",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(6) },
                    label = { Text("Kroki") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Quick presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3000, 5000, 7000, 10000, 12000).forEach { preset ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                .clickable { text = preset.toString() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${preset / 1000}k",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
                if (currentSteps > 0) {
                    Text(
                        "Aktualnie zapisane: $currentSteps kroków",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
    }
}
