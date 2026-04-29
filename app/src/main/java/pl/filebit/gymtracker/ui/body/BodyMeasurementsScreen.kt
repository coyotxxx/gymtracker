package pl.filebit.gymtracker.ui.body

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.util.formatDate
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementsScreen(
    onBack: () -> Unit,
    vm: BodyMeasurementsViewModel = hiltViewModel()
) {
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.body_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (measurements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.body_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Wykres wagi (jeśli ≥2 punkty z weightKg)
                val weightPoints = measurements
                    .filter { it.weightKg != null }
                    .sortedBy { it.date }
                if (weightPoints.size >= 2) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.body_weight_chart),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                                WeightLineChart(
                                    points = weightPoints,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        formatDate(weightPoints.first().date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatDate(weightPoints.last().date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                items(measurements, key = { it.id }) { m ->
                    MeasurementCard(m, onDelete = { vm.delete(m) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddMeasurementDialog(
            onDismiss = { showAddDialog = false },
            onSave = { m ->
                vm.upsert(m)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MeasurementCard(m: BodyMeasurement, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDate(m.date),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            m.weightKg?.let { Field(stringResource(R.string.body_weight), "${formatWeight(it)} kg") }
            m.chestCm?.let { Field(stringResource(R.string.body_chest), "${formatWeight(it)} cm") }
            m.waistCm?.let { Field(stringResource(R.string.body_waist), "${formatWeight(it)} cm") }
            m.hipsCm?.let { Field(stringResource(R.string.body_hips), "${formatWeight(it)} cm") }
            m.armCm?.let { Field(stringResource(R.string.body_arm), "${formatWeight(it)} cm") }
            m.thighCm?.let { Field(stringResource(R.string.body_thigh), "${formatWeight(it)} cm") }
            m.calfCm?.let { Field(stringResource(R.string.body_calf), "${formatWeight(it)} cm") }
            m.bodyFatPercent?.let { Field(stringResource(R.string.body_bf), "${formatWeight(it)} %") }
            if (m.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "📝 ${m.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddMeasurementDialog(
    onDismiss: () -> Unit,
    onSave: (BodyMeasurement) -> Unit
) {
    var weight by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var calf by remember { mutableStateOf("") }
    var bf by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun parseDouble(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_add_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { NumberField(label = stringResource(R.string.body_weight), value = weight, suffix = "kg") { weight = it } }
                item { NumberField(label = stringResource(R.string.body_chest), value = chest, suffix = "cm") { chest = it } }
                item { NumberField(label = stringResource(R.string.body_waist), value = waist, suffix = "cm") { waist = it } }
                item { NumberField(label = stringResource(R.string.body_hips), value = hips, suffix = "cm") { hips = it } }
                item { NumberField(label = stringResource(R.string.body_arm), value = arm, suffix = "cm") { arm = it } }
                item { NumberField(label = stringResource(R.string.body_thigh), value = thigh, suffix = "cm") { thigh = it } }
                item { NumberField(label = stringResource(R.string.body_calf), value = calf, suffix = "cm") { calf = it } }
                item { NumberField(label = stringResource(R.string.body_bf), value = bf, suffix = "%") { bf = it } }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.body_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    BodyMeasurement(
                        date = System.currentTimeMillis(),
                        weightKg = parseDouble(weight),
                        chestCm = parseDouble(chest),
                        waistCm = parseDouble(waist),
                        hipsCm = parseDouble(hips),
                        armCm = parseDouble(arm),
                        thighCm = parseDouble(thigh),
                        calfCm = parseDouble(calf),
                        bodyFatPercent = parseDouble(bf),
                        notes = notes
                    )
                )
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun NumberField(label: String, value: String, suffix: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WeightLineChart(
    points: List<BodyMeasurement>,
    modifier: Modifier = Modifier
) {
    val weights = points.mapNotNull { it.weightKg }
    if (weights.size < 2) return
    val maxW = weights.max()
    val minW = weights.min()
    val range = (maxW - minW).coerceAtLeast(0.5)

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 24f
        val plotW = w - 2 * padding
        val plotH = h - 2 * padding

        for (i in 0..2) {
            val y = padding + plotH * i / 2f
            drawLine(color = gridColor, start = Offset(padding, y), end = Offset(w - padding, y), strokeWidth = 1f)
        }

        val stepX = plotW / (weights.size - 1).toFloat()
        val path = Path()
        weights.forEachIndexed { i, kg ->
            val x = padding + i * stepX
            val ratio = ((kg - minW) / range).toFloat()
            val y = padding + plotH * (1 - ratio)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
        weights.forEachIndexed { i, kg ->
            val x = padding + i * stepX
            val ratio = ((kg - minW) / range).toFloat()
            val y = padding + plotH * (1 - ratio)
            drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
        }
    }
}
