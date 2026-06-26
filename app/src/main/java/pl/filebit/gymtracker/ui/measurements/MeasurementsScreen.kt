package pl.filebit.gymtracker.ui.measurements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.util.TrendInfo
import pl.filebit.gymtracker.util.chartYStep
import pl.filebit.gymtracker.util.filterMeasurementsByRange
import pl.filebit.gymtracker.util.formatWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementsScreen(
    onBack: () -> Unit,
    onAddMeasurement: () -> Unit,
    onEditMeasurement: (Long) -> Unit,
    onOpenBodyMap: () -> Unit,
    vm: MeasurementsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // v2.80.0: „Pełna historia" rozwija listę w miejscu (dane są już w state.measurements).
    var showAllHistory by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedToast, state.errorMessage) {
        state.savedToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header z subtitle
            item {
                Column {
                    ScreenHeader(title = "Pomiary", onBack = onBack)
                    Text(
                        "Śledź wymiary ciała i masę",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 0.dp, bottom = 4.dp)
                    )
                }
            }

            // 3 kafelki top — Waga, Tkanka, Ostatni pomiar
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Scale,
                        label = "Waga",
                        value = state.latestWeight?.let { formatWeight(it) } ?: "—",
                        suffix = if (state.latestWeight != null) "kg" else null,
                        trend = state.weightTrend30d,
                        goalType = state.goalType,
                        isWeight = true
                    )
                    TopMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Straighten,
                        label = "Tkanka tł.",
                        value = state.latestBf?.let { formatWeight(it) }
                            ?: state.estimatedBf?.let { "~" + formatWeight(it.percent) } ?: "—",
                        suffix = if (state.latestBf != null || state.estimatedBf != null) "%" else null,
                        trend = state.bfTrend30d,
                        goalType = state.goalType,
                        isWeight = false,
                        bfStyle = true,
                        subtitle = if (state.latestBf == null)
                            state.estimatedBf?.let { "szac. • ${it.method.label}" } else null
                    )
                    LastDateCard(
                        modifier = Modifier.weight(1f),
                        latest = state.measurements.firstOrNull()
                    )
                }
            }

            // AKTUALNE WYMIARY — pełna lista
            item {
                CurrentMeasurementsCard(
                    latest = state.measurements.firstOrNull(),
                    measurements = state.measurements,
                    onMapClick = onOpenBodyMap
                )
            }

            // Wykres trend wagi
            if (state.measurements.count { it.weightKg != null } >= 2) {
                item {
                    TrendChartCard(
                        measurements = state.measurements,
                        selectedRange = state.selectedRange,
                        onRangeChange = vm::setRange,
                        targetWeight = state.targetWeightKg
                    )
                }
            }

            // Cel wagowy (jeśli ustawiony)
            if (state.goalType != WeightGoalType.NONE && state.targetWeightKg != null) {
                item {
                    GoalCard(
                        goalType = state.goalType,
                        currentWeight = state.latestWeight,
                        targetWeight = state.targetWeightKg!!,
                        progressPct = state.goalProgressPct,
                        etaWeeks = state.etaWeeks,
                        onEdit = { /* TODO goal dialog jeśli potrzeba */ }
                    )
                }
            }

            // Historia — skrót (5) z przełącznikiem „Pełna historia ↔ Zwiń" (rozwija w miejscu).
            if (state.measurements.size >= 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LabelUp("Historia", modifier = Modifier.weight(1f))
                        // Link tylko gdy jest co rozwijać (>5 pomiarów).
                        if (state.measurements.size > 5) {
                            Text(
                                if (showAllHistory) "Zwiń ↑" else "Pełna historia (${state.measurements.size}) ↓",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = AccentOrange,
                                modifier = Modifier
                                    .clickable { showAllHistory = !showAllHistory }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
                val shown = if (showAllHistory) state.measurements else state.measurements.take(5)
                shown.forEach { m ->
                    item(key = m.id) {
                        HistoryRow(measurement = m, onClick = { onEditMeasurement(m.id) })
                    }
                }
            }
        }

        // Floating Add Button (żółty CTA na dole)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(AccentOrange, RoundedCornerShape(16.dp))
                    .clickable(onClick = onAddMeasurement)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Dodaj pomiar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = Color.Black
                )
            }
        }
    }
}

// ============================================================
// 3 kafelki TOP — Waga, Tkanka, Ostatni pomiar
// ============================================================

@Composable
private fun TopMetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    suffix: String?,
    trend: TrendInfo?,
    goalType: WeightGoalType,
    isWeight: Boolean,
    bfStyle: Boolean = false,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkOnSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = DarkOnSurface
            )
            if (suffix != null) {
                Text(
                    " $suffix",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        if (trend != null) {
            MiniTrend(trend = trend, goalType = goalType, isWeight = isWeight, bfStyle = bfStyle)
        } else if (subtitle != null) {
            // v2.69.0 — wartość szacowana (Navy/Deurenberg), nie z wagi impedancyjnej.
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = AccentOrange.copy(alpha = 0.85f)
            )
        } else {
            Text(
                "brak trendu",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniTrend(
    trend: TrendInfo,
    goalType: WeightGoalType,
    isWeight: Boolean,
    bfStyle: Boolean
) {
    val delta = trend.delta
    val absDelta = if (delta < 0) -delta else delta
    val (icon, color) = when {
        absDelta < 0.05 -> Icons.Default.TrendingFlat to DarkOnSurfaceVariant
        delta > 0 -> Icons.Default.TrendingUp to colorForDelta(delta, goalType, isWeight, bfStyle)
        else -> Icons.Default.TrendingDown to colorForDelta(delta, goalType, isWeight, bfStyle)
    }
    val sign = if (delta > 0) "+" else ""
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            "$sign${formatWeight(delta)}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            color = color
        )
    }
}

private fun colorForDelta(
    delta: Double,
    goalType: WeightGoalType,
    isWeight: Boolean,
    bfStyle: Boolean
): Color = when {
    isWeight && goalType == WeightGoalType.CUT -> if (delta < 0) SuccessGreen else ErrorRed
    isWeight && goalType == WeightGoalType.BULK -> if (delta > 0) SuccessGreen else ErrorRed
    bfStyle && goalType != WeightGoalType.BULK -> if (delta < 0) SuccessGreen else ErrorRed
    else -> AccentOrange
}

@Composable
private fun LastDateCard(
    modifier: Modifier = Modifier,
    latest: BodyMeasurement?
) {
    val sf = SimpleDateFormat("d MMM yyyy", Locale("pl", "PL"))
    val sfDay = SimpleDateFormat("EEEE", Locale("pl", "PL"))
    Column(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Ostatni",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkOnSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            latest?.let { sf.format(Date(it.date)) } ?: "—",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = DarkOnSurface
        )
        Text(
            latest?.let { sfDay.format(Date(it.date)) } ?: "brak pomiarów",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )
    }
}

// ============================================================
// AKTUALNE WYMIARY — duża karta z listą
// ============================================================

@Composable
private fun CurrentMeasurementsCard(
    latest: BodyMeasurement?,
    measurements: List<BodyMeasurement>,
    onMapClick: () -> Unit
) {
    GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabelUp("Aktualne wymiary", modifier = Modifier.weight(1f), accent = true)
                Text(
                    "Mapa →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = AccentOrange,
                    modifier = Modifier.clickable(onClick = onMapClick).padding(4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Każda metryka jako wiersz (ikona | nazwa | wartość | trend)
            val rows = listOf(
                MetricRowDef("Waga", "kg", { it.weightKg }, Icons.Default.Scale),
                MetricRowDef("Klatka piersiowa", "cm", { it.chestCm }, Icons.Default.Straighten),
                MetricRowDef("Talia", "cm", { it.waistCm }, Icons.Default.Straighten),
                MetricRowDef("Pas", "cm", { it.bellyCm }, Icons.Default.Straighten),
                MetricRowDef("Biodra", "cm", { it.hipsCm }, Icons.Default.Straighten),
                MetricRowDef("Kark", "cm", { it.neckCm }, Icons.Default.Straighten),
                MetricRowDef("Ramię", "cm", { it.armCm }, Icons.Default.Straighten),
                MetricRowDef("Biceps", "cm", { it.bicepsCm }, Icons.Default.Straighten),
                MetricRowDef("Udo", "cm", { it.thighCm }, Icons.Default.Straighten),
                MetricRowDef("Łydka", "cm", { it.calfCm }, Icons.Default.Straighten)
            )

            rows.forEach { def ->
                val current = latest?.let(def.extractor)
                if (current != null) {
                    val trend = pl.filebit.gymtracker.util.computeTrend(
                        measurements,
                        def.extractor,
                        rangeDays = 30
                    )
                    MetricRow(
                        icon = def.icon,
                        name = def.name,
                        value = formatWeight(current),
                        unit = def.unit,
                        trend = trend
                    )
                }
            }
        }
    }
}

private data class MetricRowDef(
    val name: String,
    val unit: String,
    val extractor: (BodyMeasurement) -> Double?,
    val icon: ImageVector
)

@Composable
private fun MetricRow(
    icon: ImageVector,
    name: String,
    value: String,
    unit: String,
    trend: TrendInfo?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(AccentOrange.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = DarkOnSurface
        )
        Text(
            " $unit",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.width(58.dp)) {
            if (trend != null) {
                MiniTrend(
                    trend = trend,
                    goalType = WeightGoalType.NONE,
                    isWeight = false,
                    bfStyle = false
                )
            }
        }
    }
}

// ============================================================
// Wykres trend wagi
// ============================================================

@Composable
private fun TrendChartCard(
    measurements: List<BodyMeasurement>,
    selectedRange: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    targetWeight: Double?
) {
    GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Trend wagi",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = DarkOnSurface
                    )
                }
                Box {
                    Text(
                        "${selectedRange.label} ▾",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier
                            .clickable {
                                val current = ChartRange.entries.indexOf(selectedRange)
                                val next = (current + 1) % ChartRange.entries.size
                                onRangeChange(ChartRange.entries[next])
                            }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            val filtered = filterMeasurementsByRange(measurements, selectedRange.days)
                .filter { it.weightKg != null }

            if (filtered.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Za mało punktów",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                Box {
                    WeightLineChart(
                        points = filtered,
                        targetWeight = targetWeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.5f)
                    )
                    val latest = filtered.last().weightKg
                    if (latest != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(AccentOrange.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "${formatWeight(latest)} kg",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val sf = SimpleDateFormat("d MMM", Locale("pl", "PL"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        sf.format(Date(filtered.first().date)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                    Text(
                        sf.format(Date(filtered.last().date)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightLineChart(
    points: List<BodyMeasurement>,
    targetWeight: Double?,
    modifier: Modifier = Modifier
) {
    val weights = points.mapNotNull { it.weightKg }
    val dates = points.mapNotNull { p -> p.weightKg?.let { p.date } }
    val minW = weights.min().let { kotlin.math.floor(it - 0.5) }
    val maxW = weights.max().let { kotlin.math.ceil(it + 0.5) }
    val realMin = if (targetWeight != null) kotlin.math.min(minW, targetWeight - 0.5) else minW
    val realMax = if (targetWeight != null) kotlin.math.max(maxW, targetWeight + 0.5) else maxW
    val ySpan = (realMax - realMin).coerceAtLeast(1.0)
    val step = chartYStep(realMin, realMax)
    val gridColor = DarkOutlineSoft
    val lineColor = AccentOrange
    val targetColor = AccentOrange.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padL = 30f
        val padR = 8f
        val padT = 12f
        val padB = 12f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        var yVal = kotlin.math.ceil(realMin / step) * step
        while (yVal <= realMax) {
            val y = padT + (chartH * (1 - (yVal - realMin) / ySpan)).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(padL, y),
                end = Offset(w - padR, y),
                strokeWidth = 1f
            )
            yVal += step
        }

        if (targetWeight != null && targetWeight in realMin..realMax) {
            val y = padT + (chartH * (1 - (targetWeight - realMin) / ySpan)).toFloat()
            var x = padL
            while (x < w - padR) {
                drawLine(
                    color = targetColor,
                    start = Offset(x, y),
                    end = Offset(kotlin.math.min(x + 8f, w - padR), y),
                    strokeWidth = 1.5f
                )
                x += 14f
            }
        }

        val xSpan = (dates.last() - dates.first()).coerceAtLeast(1L)
        val coords = weights.zip(dates).map { (wValue, dValue) ->
            val xCoord = padL + (chartW * (dValue - dates.first()).toFloat() / xSpan.toFloat())
            val yCoord = padT + (chartH * (1 - (wValue - realMin) / ySpan)).toFloat()
            Offset(xCoord, yCoord)
        }
        for (i in 1 until coords.size) {
            drawLine(
                color = lineColor,
                start = coords[i - 1],
                end = coords[i],
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
        coords.forEach { c ->
            drawCircle(color = DarkBg, radius = 4.5f, center = c)
            drawCircle(color = lineColor, radius = 3.5f, center = c, style = Stroke(width = 1.5f))
        }
    }
}

// ============================================================
// CEL WAGOWY (mini)
// ============================================================

@Composable
private fun GoalCard(
    goalType: WeightGoalType,
    currentWeight: Double?,
    targetWeight: Double,
    progressPct: Double?,
    etaWeeks: Int?,
    onEdit: () -> Unit
) {
    val (typeLabel, typeColor) = when (goalType) {
        WeightGoalType.CUT -> "REDUKCJA" to ErrorRed.copy(alpha = 0.85f)
        WeightGoalType.BULK -> "MASA" to SuccessGreen
        WeightGoalType.MAINTAIN -> "UTRZYMANIE" to AccentOrange
        WeightGoalType.NONE -> "—" to DarkOnSurfaceVariant
    }

    GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, typeColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.0.sp
                        ),
                        color = typeColor
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Cel: ${formatWeight(targetWeight)} kg",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = AccentOrange
                )
            }
            Spacer(Modifier.height(10.dp))

            val pct = (progressPct ?: 0.0).coerceIn(0.0, 100.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(8.dp)
                        .background(AccentOrange, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (progressPct != null) "${"%.0f".format(progressPct)}% drogi" else "brak postępu",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurface
                )
                Text(
                    when {
                        etaWeeks == null -> "ETA niedostępne"
                        etaWeeks == 0 -> "🎯 Osiągnięty"
                        etaWeeks == 1 -> "ETA ~1 tydz."
                        etaWeeks < 5 -> "ETA ~$etaWeeks tyg."
                        else -> "ETA ~$etaWeeks tygodni"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AccentOrange
                )
            }
        }
    }
}

// ============================================================
// HISTORIA — wiersz
// ============================================================

@Composable
private fun HistoryRow(
    measurement: BodyMeasurement,
    onClick: () -> Unit
) {
    val sf = SimpleDateFormat("d MMM", Locale("pl", "PL"))
    val sfDay = SimpleDateFormat("EEEE", Locale("pl", "PL"))
    val date = sf.format(Date(measurement.date))
    val day = sfDay.format(Date(measurement.date)).replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(78.dp)) {
            Text(
                date,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface
            )
            Text(
                day.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val parts = buildList {
                measurement.weightKg?.let { add("${formatWeight(it)} kg") }
                measurement.waistCm?.let { add("${formatWeight(it)} talia") }
                measurement.chestCm?.let { add("${formatWeight(it)} klat") }
                measurement.bodyFatPercent?.let { add("${formatWeight(it)}% BF") }
            }
            Text(
                parts.firstOrNull() ?: "—",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface
            )
            if (parts.size > 1) {
                Text(
                    parts.drop(1).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = DarkOnSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
