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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.util.DailyMacroGoal

/**
 * Dialog edukacyjny: pokazuje JAK wyliczyliśmy kcal+makro + pozwala edytować deficyt.
 * Wywoływany kliknięciem w hero kcal w DietScreen.
 */
@Composable
fun GoalBreakdownDialog(
    goal: DailyMacroGoal,
    config: DietConfig,
    onSave: (config: DietConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val br = goal.breakdown
    var deficitPick by remember { mutableStateOf(br.deficitOrSurplus.toFloat()) }
    val livePreviewKcal = br.tdeeKcal + deficitPick.toInt()
    val livePreviewWeeklyKg = -deficitPick.toInt() / 1100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Twój dzienny cel — breakdown", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Krok 1: TDEE
                BreakdownStep(
                    number = "1",
                    title = "TDEE — ile zużywasz dziennie",
                    body = "${br.tdeeKcal} kcal",
                    explanation = "Wzór: ${br.tdeeFormulaText}\n" +
                        "(${"%.0f".format(br.weightKg)} kg × bazowy multiplier 33 kcal × ${br.genderLabel} + ${config.mealsPerDay} treningi/tydz × 30 kcal)"
                )

                // Krok 2: Adjustment per cel
                BreakdownStep(
                    number = "2",
                    title = "Adjustment per cel",
                    body = "${if (br.deficitOrSurplus >= 0) "+" else ""}${br.deficitOrSurplus} kcal",
                    explanation = br.deficitLabel,
                    bodyColor = when {
                        br.deficitOrSurplus < 0 -> SuccessGreen
                        br.deficitOrSurplus > 0 -> AccentOrange
                        else -> DarkOnSurface
                    }
                )

                // === Edycja deficytu / nadwyżki ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "REGULUJ DEFICYT/NADWYŻKĘ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = AccentOrange
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${if (deficitPick.toInt() >= 0) "+" else ""}${deficitPick.toInt()} kcal/dziennie",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = DarkOnSurface
                        )
                        Slider(
                            value = deficitPick,
                            onValueChange = { deficitPick = (it / 50).toInt() * 50f },
                            valueRange = -1000f..500f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentOrange,
                                activeTrackColor = AccentOrange
                            )
                        )
                        // Quick presets
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(-750, -500, -300, -200, 0, 200, 300, 500)) { d ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (deficitPick.toInt() == d) AccentOrange else DarkSurfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { deficitPick = d.toFloat() }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${if (d >= 0) "+" else ""}$d",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = if (deficitPick.toInt() == d) DarkSurface else DarkOnSurface
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "→ Cel: $livePreviewKcal kcal | Tempo: ${
                                if (livePreviewWeeklyKg > 0) "+%.2f kg masy/tydz".format(livePreviewWeeklyKg)
                                else if (livePreviewWeeklyKg < 0) "%.2f kg/tydz redukcja".format(-livePreviewWeeklyKg)
                                else "stała waga"
                            }",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = DarkOnSurface
                        )
                    }
                }

                // Krok 3: Total
                BreakdownStep(
                    number = "3",
                    title = "TWOJE DZIENNE KCAL",
                    body = "${goal.kcal} kcal",
                    explanation = "TDEE ${br.tdeeKcal} + Adjustment ${br.deficitOrSurplus} = ${br.tdeeKcal + br.deficitOrSurplus} kcal" +
                        if (br.isManualOverride) "\n⚠ Manualnie nadpisane" else "",
                    bodyColor = AccentOrange
                )

                // Krok 4: Makro
                BreakdownStep(
                    number = "4",
                    title = "Białko (priorytet)",
                    body = "${goal.proteinG} g",
                    explanation = "${"%.1f".format(br.proteinPerKg)} g/kg masy ciała × ${"%.0f".format(br.weightKg)} kg = ${goal.proteinG}g\n" +
                        "Białko chroni masę mięśniową w deficycie i buduje na nadwyżce."
                )

                BreakdownStep(
                    number = "5",
                    title = "Tłuszcz",
                    body = "${goal.fatG} g",
                    explanation = "${"%.1f".format(br.fatPerKg)} g/kg = ${goal.fatG}g\n" +
                        "Min. 0.6 g/kg dla zdrowia hormonalnego (testosteron, estrogen)."
                )

                BreakdownStep(
                    number = "6",
                    title = "Węglowodany",
                    body = "${goal.carbsG} g",
                    explanation = "Reszta po białku i tłuszczu: ${br.carbsCalculation}\n" +
                        "Główne paliwo dla treningu — w dni treningowe więcej w obiad/post-WO."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newConfig = config.copy(customDeficit = deficitPick.toInt())
                onSave(newConfig)
                onDismiss()
            }) {
                Text("Zastosuj", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun BreakdownStep(
    number: String,
    title: String,
    body: String,
    explanation: String,
    bodyColor: androidx.compose.ui.graphics.Color = DarkOnSurface
) {
    Row {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = AccentOrange
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Text(
                body,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = bodyColor
            )
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}
