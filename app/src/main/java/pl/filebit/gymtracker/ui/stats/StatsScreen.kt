package pl.filebit.gymtracker.ui.stats

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit = {},
    vm: StatsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        if (state.loading || state.overview == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = stringResource(R.string.stats_title),
                    onBack = onBack
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.stats_loading))
                }
            }
            return
        }

        val o = state.overview!!
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.stats_title),
                    onBack = onBack
                )
            }

            // === 4 kafelki TOP — czyste analityczne metryki ===
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        value = "${o.totalWorkouts}",
                        suffix = null,
                        label = "Treningów łącznie",
                        valueColor = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${state.personalRecords.size}",
                        suffix = null,
                        label = "Personal Records",
                        valueColor = AccentOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        value = formatVolumeShort(state.volumeWeek),
                        suffix = "kg",
                        label = if (state.volumeWeekDelta != 0.0)
                            "Vol. tygodnia ${if (state.volumeWeekDelta >= 0) "+" else ""}${"%.0f".format(state.volumeWeekDelta)}%"
                        else "Vol. tygodnia",
                        valueColor = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = formatDuration(state.avgWorkoutDurationMillis),
                        suffix = null,
                        label = "Średni czas",
                        valueColor = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // === WYKRES VOLUME 8/26 tyg ===
            item {
                WeeklyVolumeCard(
                    weeks8 = state.volumePerWeek8,
                    weeks26 = state.volumePerWeek26
                )
            }

            // === CALENDAR HEATMAP — ostatnie 12 tygodni (84 dni) ===
            if (state.calendarHeatmap.isNotEmpty()) {
                item {
                    CalendarHeatmapCard(heatmap = state.calendarHeatmap)
                }
            }

            // === VOLUME PER PARTIA — toggle 1/2/4 tyg ===
            item {
                MuscleVolumeCard(
                    reports = state.muscleVolumeReport,
                    period = state.muscleVolumePeriod,
                    daysToWeekEnd = state.daysToWeekEnd,
                    onPeriodChange = vm::setVolumePeriod
                )
            }

            // === RECOVERY — ostatni trening per partia ===
            if (state.recovery.isNotEmpty()) {
                item {
                    RecoveryCard(recovery = state.recovery)
                }
            }

            // === STAGNACJE — jeśli są ===
            if (state.stagnations.isNotEmpty()) {
                item {
                    StagnationsCard(stagnations = state.stagnations)
                }
            }

            // === PERSONAL RECORDS — pełna lista ===
            if (state.personalRecords.isNotEmpty()) {
                item {
                    PersonalRecordsHeader(count = state.personalRecords.size)
                }
                state.personalRecords.take(15).forEach { pr ->
                    item(key = pr.exerciseId) {
                        PrRow(pr = pr)
                    }
                }
                if (state.personalRecords.size > 15) {
                    item {
                        Text(
                            "+ ${state.personalRecords.size - 15} kolejnych ćwiczeń",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// StatTile — kafelek 2×2
// ============================================================

@Composable
private fun StatTile(
    value: String,
    suffix: String?,
    label: String,
    valueColor: Color,
    suffixColor: Color = DarkOnSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = valueColor
                )
                if (suffix != null) {
                    Text(
                        suffix,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = suffixColor,
                        modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = DarkOnSurfaceVariant
            )
        }
    }
}

// ============================================================
// Bar chart "OBJĘTOŚĆ TYGODNIOWO" + toggle 8/26 tyg
// ============================================================

@Composable
private fun WeeklyVolumeCard(
    weeks8: List<Double>,
    weeks26: List<Double>
) {
    var range by remember { mutableStateOf(8) }
    val data = if (range == 8) weeks8 else weeks26

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "OBJĘTOŚĆ TYGODNIOWO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                RangeChip(text = "8 TYG", selected = range == 8) { range = 8 }
                Spacer(Modifier.size(6.dp))
                RangeChip(text = "26 TYG", selected = range == 26) { range = 26 }
            }
            Spacer(Modifier.height(16.dp))
            VolumeBarChart(
                values = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) AccentOrange.copy(alpha = 0.12f) else DarkSurfaceVariant,
                RoundedCornerShape(999.dp)
            )
            .border(
                1.dp,
                if (selected) AccentOrange.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            ),
            color = if (selected) AccentOrange else DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun VolumeBarChart(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Brak danych",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
        return
    }
    val maxVal = values.max().coerceAtLeast(1.0)
    val activeColor = AccentOrange
    val pastColor = AccentOrange.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = values.size
        val gap = 4f
        val barW = ((w - gap * (n - 1)) / n).coerceAtLeast(1f)
        val maxBarH = h - 4f
        values.forEachIndexed { i, v ->
            val ratio = (v / maxVal).toFloat().coerceIn(0f, 1f)
            val barH = (maxBarH * ratio).coerceAtLeast(2f)
            val x = i * (barW + gap)
            val y = h - barH
            val color = if (i == n - 1) activeColor else pastColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}

private fun formatVolumeShort(kg: Double): String = when {
    kg >= 10_000 -> "${"%.1f".format(kg / 1000).replace(',', '.')}k"
    kg >= 1000 -> "${"%.1f".format(kg / 1000).replace(',', '.')}k"
    else -> formatWeight(kg)
}

// ============================================================
// Top odznaki — 3×2 grid kafelków
// ============================================================

// (Usunięte v0.73.0: TopAchievementsCard, AchievementTile, StreakCard,
// WeekTargetCard — duplikaty Home/Achievements; zastąpione PR/Recovery/
// Stagnations/CalendarHeatmap z realną wartością analityczną.)

@Composable
private fun MuscleVolumeCard(
    reports: List<pl.filebit.gymtracker.util.MuscleVolumeReport>,
    period: VolumePeriod,
    daysToWeekEnd: Int,
    onPeriodChange: (VolumePeriod) -> Unit
) {
    pl.filebit.gymtracker.ui.theme.GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            pl.filebit.gymtracker.ui.theme.LabelUp("Volume / partia", accent = true)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optymalnie 10-20 setów / partia / tydzień (hipertrofia).",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            // Hint o trwającym tygodniu (tylko dla 1 tyg, jeśli jest <7 dni do końca)
            if (period == VolumePeriod.WEEK_1 && daysToWeekEnd > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tydzień trwa: ${daysToWeekEnd} ${if (daysToWeekEnd == 1) "dzień" else "dni"} do niedzieli — wartości jeszcze rosną.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = AccentOrange.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Toggle 1/2/4 tyg
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VolumePeriod.entries.forEach { p ->
                    PeriodChip(
                        label = p.label,
                        selected = p == period,
                        onClick = { onPeriodChange(p) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val visible = reports.filter { it.sets > 0 || it.status == pl.filebit.gymtracker.util.VolumeStatus.UNDER }
                .take(10)
            if (visible.isEmpty()) {
                Text(
                    "Brak danych w tym oknie. Zacznij trening — zobaczysz tu wzór.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            } else {
                visible.forEach { r -> MuscleVolumeRow(r) }
            }
        }
    }
}

@Composable
private fun PeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AccentOrange.copy(alpha = 0.10f) else DarkSurfaceVariant
    val borderColor = if (selected) AccentOrange else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (selected) AccentOrange else DarkOnSurfaceVariant
    Box(
        modifier = modifier
            .height(32.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (selected) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun MuscleVolumeRow(r: pl.filebit.gymtracker.util.MuscleVolumeReport) {
    val (label, color) = when (r.status) {
        pl.filebit.gymtracker.util.VolumeStatus.UNDER -> "ZA MAŁO" to pl.filebit.gymtracker.ui.theme.AccentOrange
        pl.filebit.gymtracker.util.VolumeStatus.OK -> "OK" to pl.filebit.gymtracker.ui.theme.SuccessGreen
        pl.filebit.gymtracker.util.VolumeStatus.OVER -> "ZA DUŻO" to pl.filebit.gymtracker.ui.theme.ErrorRed
    }
    val muscleName = when (r.muscle) {
        pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST -> "Klatka"
        pl.filebit.gymtracker.data.entity.MuscleGroup.BACK -> "Plecy"
        pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS -> "Barki"
        pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS -> "Biceps"
        pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS -> "Triceps"
        pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS -> "Czworogłowe"
        pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS -> "Dwugłowe (uda)"
        pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES -> "Pośladki"
        pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES -> "Łydki"
        pl.filebit.gymtracker.data.entity.MuscleGroup.CORE -> "Brzuch"
        else -> r.muscle.name
    }
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            muscleName,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )
        androidx.compose.material3.Text(
            "${r.sets} setów",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp
            ),
            color = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
        )
        androidx.compose.material3.Text(
            " / ${r.range.low}-${r.range.high}",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant,
            modifier = androidx.compose.ui.Modifier.padding(end = 12.dp)
        )
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .background(color.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            androidx.compose.material3.Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp
                ),
                color = color
            )
        }
    }
}

// ============================================================
// CALENDAR HEATMAP — 12 tyg (84 dni)
// ============================================================

@Composable
private fun CalendarHeatmapCard(heatmap: Map<Long, Double>) {
    pl.filebit.gymtracker.ui.theme.GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            pl.filebit.gymtracker.ui.theme.LabelUp("Aktywność — 12 tygodni", accent = true)
            Spacer(Modifier.height(2.dp))
            Text(
                "Każdy kwadrat = 1 dzień. Wiersze = dni tygodnia. Im jaśniej, tym większa objętość.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Edge case: brak danych w okresie → komunikat
            val totalDaysWithData = heatmap.count { it.value > 0 }
            if (totalDaysWithData == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Trenuj 2 tygodnie — tu zobaczysz wzór",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                return@Column
            }

            val today = System.currentTimeMillis() / 86_400_000L
            val maxVol = heatmap.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            // Wyrównanie do poniedziałku — najwcześniejsza kolumna zaczyna się w pn.
            // dayOfWeek (Calendar): 1=Niedz, 2=Pn, ..., 7=Sb. Dla today obliczamy dni
            // od ostatniego niedzieli (koniec tygodnia).
            val cal = java.util.Calendar.getInstance().apply {
                firstDayOfWeek = java.util.Calendar.MONDAY
                timeInMillis = System.currentTimeMillis()
            }
            val isoDay = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1  // 1=Pn..7=Nd
            val daysToSundayInclusive = 8 - isoDay  // ile dni do końca aktualnego tygodnia
            val endDayInclusive = today + (daysToSundayInclusive - 1)
            val startDay = endDayInclusive - 12 * 7 + 1

            val dayLabels = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
            val monthLabels = listOf("sty","lut","mar","kwi","maj","cze","lip","sie","wrz","paź","lis","gru")

            Row {
                // Kolumna z dniami tygodnia — wyrównana wysokością do kwadratów heatmap
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Spacer(Modifier.height(14.dp))  // miejsce na nagłówek miesięcy
                    for (i in 0..6) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .width(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (i % 2 == 0) {
                                Text(
                                    dayLabels[i],
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Etykiety miesięcy — proporcjonalna siatka jak w heatmap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        var prevMonth = -1
                        for (week in 0 until 12) {
                            val firstDayOfWeek = startDay + week * 7
                            val ms = firstDayOfWeek * 86_400_000L
                            val tmpCal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
                            val month = tmpCal.get(java.util.Calendar.MONTH)
                            val show = month != prevMonth
                            prevMonth = month
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (show) {
                                    Text(
                                        monthLabels[month],
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = DarkOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))

                    // Siatka 12 × 7 — kolumny rozciągnięte na pełną szerokość
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (week in 0 until 12) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                for (dayOfWeek in 0..6) {
                                    val day = startDay + week * 7 + dayOfWeek
                                    val vol = heatmap[day] ?: 0.0
                                    val intensity = if (vol > 0)
                                        (vol / maxVol).coerceIn(0.20, 1.0).toFloat() else 0f
                                    val isFuture = day > today
                                    val color = when {
                                        isFuture -> DarkSurfaceVariant.copy(alpha = 0.3f)
                                        intensity > 0 -> AccentOrange.copy(alpha = intensity * 0.95f)
                                        else -> DarkSurfaceVariant
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(color, RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Legenda + statystyki
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$totalDaysWithData ${if (totalDaysWithData == 1) "dzień" else "dni"} z treningiem",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "mniej",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                listOf(0.20f, 0.45f, 0.70f, 0.95f).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(AccentOrange.copy(alpha = a), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    "więcej",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// RECOVERY — ostatni trening per partia
// ============================================================

@Composable
private fun RecoveryCard(recovery: List<pl.filebit.gymtracker.data.repository.MuscleRecovery>) {
    pl.filebit.gymtracker.ui.theme.GymCard {
        Column(modifier = Modifier.padding(16.dp)) {
            pl.filebit.gymtracker.ui.theme.LabelUp("Recovery", accent = true)
            Spacer(Modifier.height(2.dp))
            Text(
                "Ostatni trening per partia. ⚠ = czas najwyżej.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            recovery.forEach { rec ->
                RecoveryRow(rec)
            }
        }
    }
}

@Composable
private fun RecoveryRow(rec: pl.filebit.gymtracker.data.repository.MuscleRecovery) {
    val (status, color) = when {
        rec.daysAgo >= 14 -> "⚠ ZA DAWNO" to pl.filebit.gymtracker.ui.theme.ErrorRed
        rec.daysAgo >= 7 -> "WARTO" to AccentOrange
        rec.daysAgo >= 2 -> "OK" to pl.filebit.gymtracker.ui.theme.SuccessGreen
        else -> "RECOVERY" to DarkOnSurfaceVariant
    }
    val muscleName = muscleLabelPl(rec.muscle)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            muscleName,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            when (rec.daysAgo) {
                0 -> "dziś"
                1 -> "wczoraj"
                in 2..6 -> "${rec.daysAgo} dni"
                else -> "${rec.daysAgo} dni"
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = DarkOnSurface,
            modifier = Modifier.padding(end = 10.dp)
        )
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = color
            )
        }
    }
}

// ============================================================
// STAGNACJE
// ============================================================

@Composable
private fun StagnationsCard(stagnations: List<pl.filebit.gymtracker.data.repository.StagnationAlert>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(pl.filebit.gymtracker.ui.theme.ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, pl.filebit.gymtracker.ui.theme.ErrorRed.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            pl.filebit.gymtracker.ui.theme.LabelUp("Stagnacje (${stagnations.size})")
            Spacer(Modifier.height(8.dp))
            stagnations.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.exerciseName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${formatWeight(s.stuckAtKg)} kg × ${s.workoutsAtSameWeight} treningów",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pl.filebit.gymtracker.ui.theme.ErrorRed
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Sugestia: deload (-10%) lub wymiana wariantu ćwiczenia.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}

// ============================================================
// PERSONAL RECORDS
// ============================================================

@Composable
private fun PersonalRecordsHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pl.filebit.gymtracker.ui.theme.LabelUp("Personal Records ($count)", accent = true)
    }
}

@Composable
private fun PrRow(pr: pl.filebit.gymtracker.data.repository.PersonalRecordRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pr.exerciseName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface,
                maxLines = 1
            )
            Text(
                muscleLabelPl(pr.muscle),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatWeight(pr.pr.maxWeightKg),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = AccentOrange
                )
                Text(
                    " kg × ${pr.pr.repsAtMaxWeight}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant
                )
            }
            Text(
                "1RM ~${formatWeight(pr.pr.estimated1RM)} kg",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}

private fun muscleLabelPl(muscle: pl.filebit.gymtracker.data.entity.MuscleGroup): String = when (muscle) {
    pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST -> "Klatka"
    pl.filebit.gymtracker.data.entity.MuscleGroup.BACK -> "Plecy"
    pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS -> "Barki"
    pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS -> "Biceps"
    pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS -> "Triceps"
    pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS -> "Czworogłowe"
    pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS -> "Dwugłowe (uda)"
    pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES -> "Pośladki"
    pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES -> "Łydki"
    pl.filebit.gymtracker.data.entity.MuscleGroup.CORE -> "Brzuch"
    pl.filebit.gymtracker.data.entity.MuscleGroup.CARDIO -> "Cardio"
    pl.filebit.gymtracker.data.entity.MuscleGroup.OTHER -> "Inne"
}
