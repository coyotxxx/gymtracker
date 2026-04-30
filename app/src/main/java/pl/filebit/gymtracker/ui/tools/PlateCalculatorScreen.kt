package pl.filebit.gymtracker.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.HeroValueCard
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.util.formatWeight
import kotlin.math.absoluteValue

private val DEFAULT_PLATE_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

private fun plateColor(kg: Double): Color = when (kg) {
    25.0 -> Color(0xFFE05B5B)        // czerwony
    20.0 -> Color(0xFF4488DD)        // niebieski
    15.0 -> AccentOrange             // żółty
    10.0 -> Color(0xFF4CAF7B)        // zielony
    5.0 -> Color(0xFFD0D0D8)         // jasny szary
    2.5 -> Color(0xFF6E6E78)         // szary
    1.25 -> Color(0xFF3A3D4A)        // ciemny szary
    else -> DarkOnSurfaceVariant
}

private fun plateHeightDp(kg: Double): Int = when {
    kg >= 25.0 -> 180
    kg >= 20.0 -> 165
    kg >= 15.0 -> 145
    kg >= 10.0 -> 125
    kg >= 5.0 -> 100
    kg >= 2.5 -> 80
    else -> 65
}

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
        derivedStateOf { bar + 2.0 * plates.sumOf { (kg, n) -> kg * n } }
    }
    val diff by remember(achieved, total) {
        derivedStateOf { achieved - total }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = DarkOnSurface,
                    navigationIconContentColor = DarkOnSurface
                )
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
                GymCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LabelUp(stringResource(R.string.plate_total))
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            ),
                            colors = inputColors(),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("0", style = MaterialTheme.typography.headlineSmall, color = DarkOnSurfaceVariant)
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        LabelUp(stringResource(R.string.plate_bar))
                        OutlinedTextField(
                            value = barText,
                            onValueChange = { barText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    label = stringResource(R.string.plate_per_side),
                    value = formatWeight(perSide),
                    suffix = "kg"
                )
            }

            item {
                GymCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        LabelUp("Talerze")
                        Spacer(Modifier.height(16.dp))
                        if (plates.isEmpty()) {
                            Text(
                                stringResource(R.string.plate_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkOnSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            PlateStack(plates = plates)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.plate_achieved, formatWeight(achieved)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkOnSurfaceVariant
                            )
                            if (diff.absoluteValue > 0.01) {
                                Spacer(Modifier.height(2.dp))
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
    }
}

@Composable
private fun PlateStack(plates: List<Pair<Double, Int>>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // gryf — szara pozioma linia
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DarkOnSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            plates.forEach { (kg, n) ->
                repeat(n) {
                    Plate(kg = kg)
                }
            }
        }
    }
}

@Composable
private fun Plate(kg: Double) {
    val height = plateHeightDp(kg).dp
    val width = if (kg >= 10.0) 18.dp else 14.dp
    Box(
        modifier = Modifier
            .height(height)
            .width(width)
            .background(plateColor(kg), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (kg == kg.toInt().toDouble()) "${kg.toInt()}" else formatWeight(kg),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (kg == 15.0) Color.Black else Color.White
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
