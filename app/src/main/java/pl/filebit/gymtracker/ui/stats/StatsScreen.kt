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

            // === 4 karty 2×2 ===
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
                        value = if (state.bestSquatKg > 0) formatWeight(state.bestSquatKg) else "—",
                        suffix = if (state.bestSquatKg > 0) "kg" else null,
                        label = "PR przysiadu",
                        valueColor = AccentOrange,
                        suffixColor = AccentOrange.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        value = formatVolumeShort(state.volumeWeek),
                        suffix = "kg",
                        label = "Vol. tygodnia",
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

            // === Bar chart "OBJĘTOŚĆ TYGODNIOWO" z toggle 8/26 ===
            item {
                WeeklyVolumeCard(
                    weeks8 = state.volumePerWeek8,
                    weeks26 = state.volumePerWeek26
                )
            }

            // === VOLUME PER PARTIA — bieżący tydzień ===
            if (state.muscleVolumeReport.any { it.sets > 0 }) {
                item {
                    MuscleVolumeCard(reports = state.muscleVolumeReport)
                }
            }

            // === Top odznaki ===
            if (state.achievements.isNotEmpty()) {
                item {
                    TopAchievementsCard(
                        achievements = state.achievements,
                        onOpenAll = onOpenAchievements
                    )
                }
            }

            // === Streak + cel tygodnia (zostawione jako bonus) ===
            state.streak?.let { s ->
                item { StreakCard(current = s.current, best = s.best) }
            }
            state.weekProgress?.let { w ->
                item { WeekTargetCard(current = w.current, target = w.target, percent = w.percent) }
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

@Composable
private fun TopAchievementsCard(
    achievements: List<Achievement>,
    onOpenAll: () -> Unit
) {
    val unlocked = achievements.count { it.unlocked }
    val total = achievements.size
    // Ostatnio odblokowane najpierw, potem najbliższe ukończenia
    val top6 = achievements
        .sortedWith(compareByDescending<Achievement> { it.unlocked }
            .thenByDescending { it.unlockedAt ?: 0L }
            .thenByDescending { it.progress })
        .take(6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TOP ODZNAKI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$unlocked / $total →",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AccentOrange,
                    modifier = Modifier.clickable(onClick = onOpenAll)
                )
            }
            Spacer(Modifier.height(12.dp))
            // 2 wiersze po 3
            top6.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { a ->
                        AchievementTile(
                            emoji = a.emoji,
                            unlocked = a.unlocked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Wypełnij brakujące miejsca placeholdersem
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(emoji: String, unlocked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                if (unlocked) AccentOrange.copy(alpha = 0.15f) else DarkSurfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (unlocked) AccentOrange.copy(alpha = 0.30f) else DarkOutlineSoft,
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            emoji,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
        )
    }
}

// ============================================================
// Streak + Week Target — zachowane (bonus pod głównymi sekcjami)
// ============================================================

@Composable
private fun StreakCard(current: Int, best: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔥",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp)
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$current ${if (current == 1) "tydz." else "tyg."} z rzędu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnSurface
                )
                Text(
                    "Najlepszy streak: $best ${if (best == 1) "tydz." else "tyg."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekTargetCard(current: Int, target: Int, percent: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TYGODNIOWY CEL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$current",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 32.sp
                            ),
                            color = AccentOrange
                        )
                        Text(
                            " / $target",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = DarkOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Text(
                    "${percent.coerceAtMost(999)}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (percent >= 100) AccentOrange else DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentOrange,
                trackColor = DarkSurfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun MuscleVolumeCard(reports: List<pl.filebit.gymtracker.util.MuscleVolumeReport>) {
    pl.filebit.gymtracker.ui.theme.GymCard {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp)
        ) {
            pl.filebit.gymtracker.ui.theme.LabelUp("VOLUME / PARTIA — TEN TYDZIEŃ")
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
            androidx.compose.material3.Text(
                "Optymalnie 10-20 setów na partię tygodniowo (hipertrofia).",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
            reports.filter { it.sets > 0 || it.status == pl.filebit.gymtracker.util.VolumeStatus.UNDER }
                .take(10)
                .forEach { r ->
                    MuscleVolumeRow(r)
                }
        }
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
            "${r.sets}/${r.range.low}-${r.range.high}",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
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
