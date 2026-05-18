package pl.filebit.gymtracker.ui.muscles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import pl.filebit.gymtracker.ui.theme.SelectableChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.repository.MuscleAnalysis
import pl.filebit.gymtracker.data.repository.MuscleStatus
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleEngagementScreen(
    onBack: () -> Unit,
    vm: MuscleEngagementViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ScreenHeader bez horizontal padding (spójność z innymi ekranami)
            ScreenHeader(
                title = stringResource(R.string.muscles_title),
                onBack = onBack
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(EngagementPeriod.entries.size) { idx ->
                    val p = EngagementPeriod.entries[idx]
                    SelectableChip(
                        text = stringResource(p.labelRes),
                        selected = state.period == p,
                        onClick = { vm.setPeriod(p) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.stats_loading))
                }
                return@Column
            }
            val analysis = state.analysis
            if (analysis == null || analysis.analyses.all { it.status == MuscleStatus.NEGLECTED }) {
                if (state.engagement.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.muscles_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                    return@Column
                }
            }

            val analyses = analysis?.analyses.orEmpty()
            val problems = analyses.filter {
                it.status == MuscleStatus.NEGLECTED || it.status == MuscleStatus.UNDER
            }
            val balanced = analyses.filter { it.status == MuscleStatus.BALANCED }
            val over = analyses.filter { it.status == MuscleStatus.OVER }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { BodyHeatmapCard(analyses) }
                item { LegendCard() }
                if (problems.isNotEmpty()) {
                    item { SectionHeader("⚠️ Wymagają uwagi", problems.size) }
                    items(problems.size) { idx -> MuscleAnalysisRow(problems[idx]) }
                }
                if (over.isNotEmpty()) {
                    item { SectionHeader("🔥 Wysoka objętość", over.size) }
                    items(over.size) { idx -> MuscleAnalysisRow(over[idx]) }
                }
                if (balanced.isNotEmpty()) {
                    item { SectionHeader("✅ W balansie", balanced.size) }
                    items(balanced.size) { idx -> MuscleAnalysisRow(balanced[idx]) }
                }
            }
        }
    }
}

private fun MuscleGroup.maskAsset(): String? = when (this) {
    MuscleGroup.CHEST -> "muscle_masks/mask_chest.webp"
    MuscleGroup.BACK -> "muscle_masks/mask_back.webp"
    MuscleGroup.SHOULDERS -> "muscle_masks/mask_shoulders.webp"
    MuscleGroup.BICEPS -> "muscle_masks/mask_biceps.webp"
    MuscleGroup.TRICEPS -> "muscle_masks/mask_triceps.webp"
    MuscleGroup.QUADS -> "muscle_masks/mask_quads.webp"
    MuscleGroup.HAMSTRINGS -> "muscle_masks/mask_hamstrings.webp"
    MuscleGroup.GLUTES -> "muscle_masks/mask_glutes.webp"
    MuscleGroup.CALVES -> "muscle_masks/mask_calves.webp"
    MuscleGroup.CORE -> "muscle_masks/mask_core.webp"
    MuscleGroup.CARDIO, MuscleGroup.OTHER -> null
}

@Composable
private fun BodyHeatmapCard(analyses: List<MuscleAnalysis>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.muscles_silhouette_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.muscles_front),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.muscles_back),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            BodyHeatmap(analyses = analyses)
        }
    }
}

/**
 * Heatmap ciała: kolor maski zależy od statusu mięśnia, nie tylko intensywności.
 * Dzięki temu po 30 dniach widać GDZIE są problemy, a nie wszystko zaznaczone.
 */
@Composable
private fun BodyHeatmap(analyses: List<MuscleAnalysis>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
    ) {
        AsyncImage(
            model = "file:///android_asset/muscle_masks/body_base.webp",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        analyses.forEach { a ->
            val asset = a.muscle.maskAsset() ?: return@forEach
            val color = a.status.tint()
            val alpha = when (a.status) {
                MuscleStatus.NEGLECTED -> 0.55f
                MuscleStatus.UNDER -> 0.55f
                MuscleStatus.OVER -> 0.75f
                MuscleStatus.BALANCED -> 0.55f
            }
            AsyncImage(
                model = "file:///android_asset/$asset",
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(color.copy(alpha = alpha)),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(StatusColor.NEGLECTED, "Zaniedb.")
            LegendDot(StatusColor.UNDER, "Mało")
            LegendDot(StatusColor.BALANCED, "Balans")
            LegendDot(StatusColor.OVER, "Dużo")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MuscleAnalysisRow(a: MuscleAnalysis) {
    val statusColor = a.status.tint()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    a.muscle.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${a.actualPercent}% / ${a.recommendedPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(6.dp))
            // Pasek pokazuje aktualny % z markerem zalecanego
            val progress = (a.actualPercent / 30f).coerceIn(0f, 1f) // skala 0..30%
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    if (a.totalSets > 0) {
                        append(formatWeight(a.volumeKg))
                        append(" · ")
                        append("${a.totalSets} serii")
                    } else {
                        append("Brak treningów w okresie")
                    }
                    a.daysSinceLast?.let {
                        append(" · ")
                        append(
                            when {
                                it == 0 -> "dziś"
                                it == 1 -> "wczoraj"
                                else -> "$it dni temu"
                            }
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            statusHint(a)?.let { hint ->
                Spacer(Modifier.height(4.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun statusHint(a: MuscleAnalysis): String? = when (a.status) {
    MuscleStatus.NEGLECTED ->
        if (a.daysSinceLast == null) "Nie trenowane w tym okresie"
        else "Zaniedbane — ostatni raz ${a.daysSinceLast} dni temu"
    MuscleStatus.UNDER ->
        "Poniżej zalecanego — dodaj 1-2 ćwiczenia w tygodniu"
    MuscleStatus.OVER ->
        "Wysoka objętość — uważaj na regenerację"
    MuscleStatus.BALANCED -> null
}

/**
 * Stałe kolory statusów — celowo NIE z theme, bo czerwony/zielony powinien
 * być rozpoznawalny niezależnie od trybu jasny/ciemny.
 */
private object StatusColor {
    val NEGLECTED = Color(0xFFE74C3C)  // czerwony
    val UNDER = Color(0xFFF39C12)      // pomarańczowy
    val BALANCED = Color(0xFF2ECC71)   // zielony
    val OVER = Color(0xFF9B59B6)       // fiolet
}

private fun MuscleStatus.tint(): Color = when (this) {
    MuscleStatus.NEGLECTED -> StatusColor.NEGLECTED
    MuscleStatus.UNDER -> StatusColor.UNDER
    MuscleStatus.BALANCED -> StatusColor.BALANCED
    MuscleStatus.OVER -> StatusColor.OVER
}

// v1.20.1 — usunięto duplikat displayName(); używamy MuscleGroup.displayName() z entity/Exercise.kt
