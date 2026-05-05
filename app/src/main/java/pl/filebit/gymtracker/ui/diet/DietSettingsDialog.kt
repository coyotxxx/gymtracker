package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SelectableChip

@Composable
fun DietSettingsDialog(
    initial: DietConfig,
    onSave: (DietConfig) -> Unit,
    onEditProfile: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var meals by remember { mutableStateOf(initial.mealsPerDay) }
    var window by remember { mutableStateOf(initial.eatingWindowHours) }
    var startHour by remember { mutableStateOf(initial.windowStartHour) }
    var reminders by remember { mutableStateOf(initial.mealRemindersEnabled) }
    var autoCheck by remember { mutableStateOf(initial.autoCheckAdjustments) }

    val previewConfig = initial.copy(
        mealsPerDay = meals,
        eatingWindowHours = window,
        windowStartHour = startHour,
        mealRemindersEnabled = reminders,
        autoCheckAdjustments = autoCheck
    )
    val mealHours = previewConfig.mealHoursDecimal()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfiguracja diety", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Liczba posiłków
                SettingSection("Liczba posiłków: $meals") {
                    Slider(
                        value = meals.toFloat(),
                        onValueChange = { meals = it.toInt().coerceIn(2, 6) },
                        valueRange = 2f..6f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentOrange,
                            activeTrackColor = AccentOrange
                        )
                    )
                }

                // Długość okna
                SettingSection("Długość okna żywieniowego") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(6, 8, 10, 12, 24)) { hours ->
                            SelectableChip(
                                text = if (hours == 24) "24h (bez IF)" else "${hours}h",
                                selected = window == hours,
                                onClick = { window = hours }
                            )
                        }
                    }
                }

                // Start okna (godzina)
                SettingSection("Start okna: %02d:00".format(startHour)) {
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt().coerceIn(4, 18) },
                        valueRange = 4f..18f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentOrange,
                            activeTrackColor = AccentOrange
                        )
                    )
                    Text(
                        "Okno: %02d:00 - %02d:00".format(startHour, (startHour + window).coerceAtMost(24)),
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Powiadomienia
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminders,
                        onCheckedChange = { reminders = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text("Powiadomienia o posiłkach", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                        Text(
                            "Pora posiłku przypomni notyfikacja",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Auto-korekty co 14 dni
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = { autoCheck = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text("Auto-sprawdzanie planu", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                        Text(
                            "Co 14 dni AI analizuje trend wagi i sugeruje korekty kcal (notyfikacja)",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Preview godzin
                SettingSection("Pory posiłków") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            mealHours.joinToString("  ·  ") { previewConfig.formatTime(it) },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = AccentOrange
                        )
                    }
                }

                // Edytuj profil dietetyczny (alergie, preferencje, budżet, choroby)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                        .clickable {
                            onEditProfile()
                            onDismiss()
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "🩺 Edytuj profil dietetyczny",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = DarkOnSurface
                        )
                        Text(
                            "Wiek, wzrost, alergie, preferencje, budżet, stany zdrowia",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(previewConfig); onDismiss() }) {
                Text("Zapisz", color = AccentOrange, fontWeight = FontWeight.Bold)
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
private fun SettingSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.padding(top = 4.dp))
        content()
    }
}
