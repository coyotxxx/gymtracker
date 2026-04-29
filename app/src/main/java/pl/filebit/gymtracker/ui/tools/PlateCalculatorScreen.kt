package pl.filebit.gymtracker.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlin.math.absoluteValue

private val DEFAULT_PLATE_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorScreen(onBack: () -> Unit) {
    var totalText by remember { mutableStateOf("") }
    var barText by remember { mutableStateOf("20") }

    val total by remember(totalText) {
        derivedStateOf { totalText.replace(',', '.').toDoubleOrNull() ?: 0.0 }
    }
    val bar by remember(barText) {
        derivedStateOf { barText.replace(',', '.').toDoubleOrNull() ?: 20.0 }
    }

    val perSide by remember(total, bar) {
        derivedStateOf { ((total - bar) / 2.0).coerceAtLeast(0.0) }
    }

    val plates: List<Pair<Double, Int>> by remember(perSide) {
        derivedStateOf {
            // Greedy: po największych talerzach
            val result = mutableListOf<Pair<Double, Int>>()
            var remaining = perSide
            for (p in DEFAULT_PLATE_KG) {
                val count = (remaining / p).toInt()
                if (count > 0) {
                    result.add(p to count)
                    remaining -= count * p
                }
            }
            result
        }
    }

    val achieved by remember(plates, bar) {
        derivedStateOf {
            bar + 2.0 * plates.sumOf { (kg, n) -> kg * n }
        }
    }
    val diff by remember(achieved, total) {
        derivedStateOf { achieved - total }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.plate_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = totalText,
                        onValueChange = { totalText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text(stringResource(R.string.plate_total)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = barText,
                        onValueChange = { barText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text(stringResource(R.string.plate_bar)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.plate_per_side),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatWeight(perSide)} kg",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            if (plates.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.plate_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.plate_load_per_side),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(plates.size) { idx ->
                    val (kg, n) = plates[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${formatWeight(kg)} kg",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "× $n",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.plate_achieved, formatWeight(achieved)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (diff.absoluteValue > 0.01) {
                        Text(
                            stringResource(
                                R.string.plate_diff,
                                if (diff > 0) "+" else "",
                                formatWeight(diff)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
