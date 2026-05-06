package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SuccessGreen

/**
 * Dialog cyklicznych kcal — refeed/deficyt per dzień tygodnia.
 *
 * Filozofia z xlsx Macieja 2022 redukcja:
 *   PN/WT/ND → wyższe kcal (refeed)
 *   ŚR/CZW    → niższe kcal (deficyt)
 *   PT/SOB    → TDEE
 *
 * UI: 7 wierszy (PN-ND) z polem kcal. Pole puste = użyj domyślnego.
 * Plus szybkie szablony: "Wszystkie równo", "Refeed pn/wt/nd 2750", itd.
 */
@Composable
fun WeeklyKcalDialog(
    initial: Map<Int, Int>,
    defaultKcal: Int,
    onSave: (Map<Int, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    // Map: 1=PN..7=ND → tekst (puste = brak override)
    val texts: SnapshotStateMap<Int, String> = remember {
        val m = mutableStateMapOf<Int, String>()
        for (d in 1..7) m[d] = initial[d]?.toString() ?: ""
        m
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🔁 Refeed / deficyt cykliczny",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                Text(
                    "Filozofia: w niektóre dni więcej kcal (refeed po treningu siłowym), " +
                        "w inne mniej (deficyt). Puste pole = domyślne $defaultKcal kcal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                // SZABLONY (uniwersalne, bez nazwisk)
                val refeedKcal = (defaultKcal * 1.10).toInt()
                val deficitKcal = (defaultKcal * 0.90).toInt()
                Text("Szybkie szablony", color = DarkOnSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TemplateButton(
                        label = "Wszystkie dni równo ($defaultKcal kcal)",
                        onClick = { for (d in 1..7) texts[d] = "" }
                    )
                    TemplateButton(
                        label = "Refeed weekendowy: sob+nd $refeedKcal, reszta $defaultKcal",
                        onClick = {
                            for (d in 1..5) texts[d] = ""
                            texts[6] = refeedKcal.toString()
                            texts[7] = refeedKcal.toString()
                        }
                    )
                    TemplateButton(
                        label = "Cykliczny: dni treningowe $refeedKcal, reszta $deficitKcal",
                        onClick = {
                            // Zakładamy domyślnie: pn/śr/pt = trening, reszta = rest
                            texts[1] = refeedKcal.toString()
                            texts[2] = deficitKcal.toString()
                            texts[3] = refeedKcal.toString()
                            texts[4] = deficitKcal.toString()
                            texts[5] = refeedKcal.toString()
                            texts[6] = deficitKcal.toString()
                            texts[7] = deficitKcal.toString()
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 7 dni
                Text("Per dzień tygodnia", color = DarkOnSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                listOf(
                    1 to "Poniedziałek",
                    2 to "Wtorek",
                    3 to "Środa",
                    4 to "Czwartek",
                    5 to "Piątek",
                    6 to "Sobota",
                    7 to "Niedziela"
                ).forEach { (day, label) ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = DarkOnSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.width(120.dp)
                        )
                        OutlinedTextField(
                            value = texts[day] ?: "",
                            onValueChange = { v ->
                                texts[day] = v.filter { it.isDigit() }.take(5)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text("$defaultKcal (domyślnie)", color = DarkOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            },
                            suffix = { Text("kcal", color = DarkOnSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = DarkOutline,
                                cursorColor = AccentOrange
                            )
                        )
                    }
                }

                }   // koniec scroll-owanego Column

                Spacer(Modifier.height(8.dp))

                // ACTIONS sticky
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        // Wyczyść WSZYSTKIE i zapisz pustą mapę (= wszystkie równo)
                        onSave(emptyMap())
                    }) {
                        Text("Wyzeruj", color = DarkOnSurfaceVariant)
                    }
                    TextButton(onClick = {
                        val finalMap = texts.entries
                            .mapNotNull { (d, s) ->
                                val v = s.toIntOrNull()
                                if (v != null && v > 0) d to v else null
                            }
                            .toMap()
                        onSave(finalMap)
                    }) {
                        Text("✓ Zapisz", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateButton(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(10.dp),
            color = SuccessGreen,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
