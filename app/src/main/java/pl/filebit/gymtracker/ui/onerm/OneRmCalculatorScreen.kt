package pl.filebit.gymtracker.ui.onerm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.HeroValueCard
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.util.formatWeight
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneRmCalculatorScreen(onBack: () -> Unit) {
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }

    val w by remember(weightText) {
        derivedStateOf { weightText.replace(',', '.').toDoubleOrNull() ?: 0.0 }
    }
    val r by remember(repsText) {
        derivedStateOf { repsText.toIntOrNull() ?: 0 }
    }

    val epley = remember(w, r) { if (r > 0 && w > 0) w * (1 + r / 30.0) else 0.0 }
    val brzycki = remember(w, r) {
        if (r in 1..36 && w > 0) w * 36.0 / (37.0 - r) else 0.0
    }
    val lombardi = remember(w, r) { if (r > 0 && w > 0) w * r.toDouble().pow(0.10) else 0.0 }
    val avg = remember(epley, brzycki, lombardi) {
        val list = listOf(epley, brzycki, lombardi).filter { it > 0 }
        if (list.isEmpty()) 0.0 else list.average()
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                pl.filebit.gymtracker.ui.theme.ScreenHeader(
                    title = stringResource(R.string.onerm_title),
                    onBack = onBack
                )
            }
            item {
                Text(
                    stringResource(R.string.onerm_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            item {
                GymCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LabelUp(stringResource(R.string.onerm_weight))
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            ),
                            colors = inputColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        LabelUp(stringResource(R.string.onerm_reps))
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            ),
                            colors = inputColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                HeroValueCard(
                    label = stringResource(R.string.onerm_average),
                    value = if (avg > 0) formatWeight(avg) else "—",
                    suffix = if (avg > 0) "kg" else null
                )
            }

            item {
                GymCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        FormulaRow(name = "Epley", value = epley)
                        HorizontalDivider(color = DarkOutlineSoft)
                        FormulaRow(name = "Brzycki", value = brzycki)
                        HorizontalDivider(color = DarkOutlineSoft)
                        FormulaRow(name = "Lombardi", value = lombardi)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulaRow(name: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (value > 0) formatWeight(value) else "—",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = DarkOnSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "kg",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentOrange.copy(alpha = 0.6f),
    unfocusedBorderColor = DarkSurfaceVariant,
    cursorColor = AccentOrange,
    focusedTextColor = DarkOnSurface,
    unfocusedTextColor = DarkOnSurface
)
