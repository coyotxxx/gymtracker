package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant

/**
 * Codzienny mini-quiz regeneracji. Slidery 1-5 + sleep h.
 * Wszystkie pola opcjonalne — można pominąć dowolne.
 */
@Composable
fun RecoveryDialog(
    onSave: (
        sleepHours: Double?,
        sleepQuality: Int?,
        stressLevel: Int?,
        hungerLevel: Int?,
        energyLevel: Int?,
        sorenessLevel: Int?,
        difficultyAdherence: Int?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var sleep by remember { mutableStateOf(7.5f) }
    var sleepQ by remember { mutableStateOf(3) }
    var stress by remember { mutableStateOf(3) }
    var hunger by remember { mutableStateOf(3) }
    var energy by remember { mutableStateOf(3) }
    var soreness by remember { mutableStateOf(3) }
    var difficulty by remember { mutableStateOf(3) }

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
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "🛌 Jak się dziś czujesz?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "30 sekund — pomoże silnikowi nie ciąć kcal kiedy nie powinien.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SleepHoursRow(sleep, onChange = { sleep = it })
                    Rating15Row("😴 Jakość snu", sleepQ, onChange = { sleepQ = it }, low = "źle", high = "świetnie")
                    Rating15Row("😰 Stres", stress, onChange = { stress = it }, low = "spokój", high = "skrajny")
                    Rating15Row("🍽️ Głód", hunger, onChange = { hunger = it }, low = "brak", high = "ciągły")
                    Rating15Row("⚡ Energia", energy, onChange = { energy = it }, low = "wyczerpana", high = "pełna")
                    Rating15Row("💪 Soreness", soreness, onChange = { soreness = it }, low = "brak", high = "ciężki DOMS")
                    Rating15Row("🎯 Trudność diety", difficulty, onChange = { difficulty = it }, low = "łatwo", high = "bardzo trudno")
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Pomiń", color = DarkOnSurfaceVariant)
                    }
                    TextButton(onClick = {
                        onSave(
                            sleep.toDouble(),
                            sleepQ, stress, hunger, energy, soreness, difficulty
                        )
                        onDismiss()
                    }) {
                        Text("Zapisz", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepHoursRow(value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🛏️ Sen", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
            Text(
                "%.1f h".format(value),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                ),
                color = AccentOrange
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 3f..12f,
            steps = 17,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange
            )
        )
    }
}

@Composable
private fun Rating15Row(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    low: String,
    high: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
            Text(
                "$value/5",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                ),
                color = AccentOrange
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(low, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = DarkOnSurfaceVariant)
            Text(high, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = DarkOnSurfaceVariant)
        }
    }
}
