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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurface3
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.MonoBigValue
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.util.formatDate
import pl.filebit.gymtracker.util.formatWeight
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementsScreen(
    onBack: () -> Unit,
    vm: BodyMeasurementsViewModel = hiltViewModel()
) {
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentOrange,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.body_title),
                    onBack = onBack
                )
            }
            if (measurements.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.body_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Wykres wagi (jeśli ≥2 punkty z weightKg)
                val weightPoints = measurements
                    .filter { it.weightKg != null }
                    .sortedBy { it.date }

                // Karta celu (jeśli ustawiony) — pokazuj zawsze gdy goal != NONE i target ustawiony
                val target = profile.targetWeightKg
                val goalType = profile.weightGoalType
                val latestWeight = weightPoints.lastOrNull()?.weightKg
                if (goalType != WeightGoalType.NONE && target != null && target > 0) {
                    item {
                        WeightGoalCard(
                            goalType = goalType,
                            target = target,
                            current = latestWeight,
                            previous = weightPoints.dropLast(1).lastOrNull()?.weightKg,
                            start = weightPoints.firstOrNull()?.weightKg
                        )
                    }
                }
                if (weightPoints.size >= 2) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                LabelUp("Postęp wagi (90 dni)")
                                Spacer(Modifier.height(12.dp))
                                WeightLineChart(
                                    points = weightPoints,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                )
                            }
                        }
                    }
                    item {
                        LabelUp(
                            "Ostatnie pomiary",
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
    val date = formatDate(m.date)
    // skrót: "29 kwi" – druga część daty
    val shortDate = date.substringAfter(" ", date)
    val secondary = buildList {
        m.chestCm?.let { add("Klatka ${formatWeight(it)}") }
        m.waistCm?.let { add("Pas ${formatWeight(it)}") }
        m.bodyFatPercent?.let { add("BF ${formatWeight(it)}%") }
    }.joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lewa: duża żółta waga
            m.weightKg?.let { kg ->
                MonoBigValue(
                    value = formatWeight(kg),
                    suffix = "kg",
                    valueSize = 24.sp,
                    suffixSize = 12.sp
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shortDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (secondary.isNotBlank()) {
                    Text(
                        secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                if (m.notes.isNotBlank()) {
                    Text(
                        "📝 ${m.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = DarkOnSurfaceVariant
                )
            }
        }
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
private fun WeightGoalCard(
    goalType: WeightGoalType,
    target: Double,
    current: Double?,
    previous: Double?,
    start: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabelUp("Aktualna waga", accent = true)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                val cur = current
                if (cur != null) {
                    MonoBigValue(
                        value = formatWeight(cur),
                        suffix = "kg",
                        valueSize = 44.sp,
                        suffixSize = 16.sp,
                        valueColor = DarkOnSurface,
                        suffixColor = DarkOnSurfaceVariant
                    )
                } else {
                    Text("—", style = MaterialTheme.typography.headlineLarge, color = DarkOnSurface)
                }
                Spacer(Modifier.weight(1f))
                if (cur != null && previous != null) {
                    val delta = cur - previous
                    if (abs(delta) >= 0.05) {
                        val isLoss = delta < 0
                        val goodLoss = goalType == WeightGoalType.CUT
                        val goodGain = goalType == WeightGoalType.BULK
                        val good = (isLoss && goodLoss) || (!isLoss && goodGain) ||
                            (goalType == WeightGoalType.MAINTAIN && abs(delta) <= 1.0)
                        val color = if (good) SuccessGreen else ErrorRed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isLoss) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                            Text(
                                "${if (delta > 0) "+" else "−"}${formatWeight(abs(delta))} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val cur = current
            val maintainTolerance = 1.5
            val displayProgress = when {
                cur == null -> 0f
                goalType == WeightGoalType.MAINTAIN -> {
                    val distance = abs(cur - target)
                    if (distance <= maintainTolerance) 1f
                    else {
                        // Liniowo opada od krawędzi tolerancji w skali 5kg
                        val outsideBy = distance - maintainTolerance
                        (1f - (outsideBy.toFloat() / 5f)).coerceIn(0f, 1f)
                    }
                }
                start == null || start <= 0.0 -> 0f
                goalType == WeightGoalType.CUT -> {
                    val total = start - target
                    if (total > 0.01) ((start - cur) / total).toFloat().coerceIn(0f, 1f) else 0f
                }
                goalType == WeightGoalType.BULK -> {
                    val total = target - start
                    if (total > 0.01) ((cur - start) / total).toFloat().coerceIn(0f, 1f) else 0f
                }
                else -> 0f
            }
            val statusText = when {
                cur == null -> stringResource(
                    R.string.body_goal_progress_to_target, formatWeight(target), "—"
                )
                goalType == WeightGoalType.MAINTAIN && abs(cur - target) <= maintainTolerance ->
                    stringResource(R.string.body_goal_maintain_in_range, formatWeight(target))
                (goalType == WeightGoalType.CUT && cur <= target) ||
                (goalType == WeightGoalType.BULK && cur >= target) ->
                    stringResource(R.string.body_goal_reached, formatWeight(target))
                else -> "Cel: ${formatWeight(target)} kg (zostało ${formatWeight(abs(target - cur))} kg)"
            }

            LinearProgressIndicator(
                progress = { displayProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = AccentOrange,
                trackColor = DarkSurface3,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
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

    val lineColor = AccentOrange
    val gridColor = DarkOutlineSoft

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
