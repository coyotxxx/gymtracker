package pl.filebit.gymtracker.ui.onerm

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.filebit.gymtracker.R
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onerm_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.onerm_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.onerm_weight)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.onerm_reps)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.onerm_average),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (avg > 0) "${formatWeight(avg)} kg" else "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider()

            FormulaRow(
                name = "Epley",
                formula = "w × (1 + reps / 30)",
                value = epley
            )
            FormulaRow(
                name = "Brzycki",
                formula = "w × 36 / (37 − reps)",
                value = brzycki
            )
            FormulaRow(
                name = "Lombardi",
                formula = "w × reps^0.10",
                value = lombardi
            )
        }
    }
}

@Composable
private fun FormulaRow(name: String, formula: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(formula, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (value > 0) "${formatWeight(value)} kg" else "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
