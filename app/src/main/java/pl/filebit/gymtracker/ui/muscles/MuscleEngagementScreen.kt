package pl.filebit.gymtracker.ui.muscles

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.repository.MuscleEngagement
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleEngagementScreen(
    onBack: () -> Unit,
    vm: MuscleEngagementViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.muscles_title)) },
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
                .padding(16.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(EngagementPeriod.entries.size) { idx ->
                    val p = EngagementPeriod.entries[idx]
                    FilterChip(
                        selected = state.period == p,
                        onClick = { vm.setPeriod(p) },
                        label = { Text(stringResource(p.labelRes)) }
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
            if (state.engagement.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.muscles_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    BodyHeatmapCard(state.engagement)
                }
                items(state.engagement.size) { idx ->
                    MuscleRow(state.engagement[idx])
                }
            }
        }
    }
}

@Composable
private fun BodyHeatmapCard(engagement: List<MuscleEngagement>) {
    val byMuscle = engagement.associateBy { it.muscle }
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
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.muscles_front),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BodySilhouette(byMuscle = byMuscle, isFront = true)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.muscles_back),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BodySilhouette(byMuscle = byMuscle, isFront = false)
                }
            }
        }
    }
}

private data class MuscleRegion(
    val muscle: MuscleGroup,
    // współrzędne ułamkowe 0..1 względem rozmiaru sylwetki
    val cx: Float, val cy: Float, val w: Float, val h: Float
)

@Composable
private fun BodySilhouette(
    byMuscle: Map<MuscleGroup, MuscleEngagement>,
    isFront: Boolean
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val baseFill = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    val regions = if (isFront) listOf(
        MuscleRegion(MuscleGroup.SHOULDERS, cx = 0.5f, cy = 0.18f, w = 0.7f, h = 0.06f),
        MuscleRegion(MuscleGroup.CHEST,     cx = 0.5f, cy = 0.27f, w = 0.5f, h = 0.10f),
        MuscleRegion(MuscleGroup.BICEPS,    cx = 0.5f, cy = 0.37f, w = 0.78f, h = 0.05f),
        MuscleRegion(MuscleGroup.CORE,      cx = 0.5f, cy = 0.42f, w = 0.45f, h = 0.10f),
        MuscleRegion(MuscleGroup.QUADS,     cx = 0.5f, cy = 0.65f, w = 0.5f,  h = 0.16f),
        MuscleRegion(MuscleGroup.CALVES,    cx = 0.5f, cy = 0.88f, w = 0.4f,  h = 0.08f)
    ) else listOf(
        MuscleRegion(MuscleGroup.SHOULDERS, cx = 0.5f, cy = 0.18f, w = 0.7f, h = 0.06f),
        MuscleRegion(MuscleGroup.BACK,      cx = 0.5f, cy = 0.30f, w = 0.5f, h = 0.16f),
        MuscleRegion(MuscleGroup.TRICEPS,   cx = 0.5f, cy = 0.37f, w = 0.78f, h = 0.05f),
        MuscleRegion(MuscleGroup.GLUTES,    cx = 0.5f, cy = 0.50f, w = 0.5f,  h = 0.08f),
        MuscleRegion(MuscleGroup.HAMSTRINGS, cx = 0.5f, cy = 0.65f, w = 0.5f,  h = 0.16f),
        MuscleRegion(MuscleGroup.CALVES,    cx = 0.5f, cy = 0.88f, w = 0.4f,  h = 0.08f)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.5f)
    ) {
        val w = size.width
        val h = size.height

        // Sylwetka — uproszczone kształty
        // Głowa
        drawCircle(color = baseFill, radius = w * 0.13f, center = Offset(w / 2, h * 0.07f))
        // Szyja
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.45f, h * 0.12f),
            size = Size(w * 0.10f, h * 0.04f)
        )
        // Tułów (od barków do bioder)
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.20f, h * 0.16f),
            size = Size(w * 0.60f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        // Ramiona — boczne owale
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.02f, h * 0.18f),
            size = Size(w * 0.18f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.05f)
        )
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.80f, h * 0.18f),
            size = Size(w * 0.18f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.05f)
        )
        // Nogi
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.22f, h * 0.52f),
            size = Size(w * 0.24f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        drawRoundRect(
            color = baseFill,
            topLeft = Offset(w * 0.54f, h * 0.52f),
            size = Size(w * 0.24f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.06f)
        )

        // Heatmap regiony
        regions.forEach { r ->
            val percent = byMuscle[r.muscle]?.percentOfTotal ?: 0
            if (percent <= 0) return@forEach
            val intensity = (percent / 50f).coerceIn(0.15f, 1f)
            val color = accent.copy(alpha = intensity)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * (r.cx - r.w / 2), h * (r.cy - r.h / 2)),
                size = Size(w * r.w, h * r.h),
                cornerRadius = CornerRadius(w * 0.04f, h * 0.02f)
            )
        }
    }
}

@Composable
private fun MuscleRow(e: MuscleEngagement) {
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
                Text(
                    e.muscle.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${e.percentOfTotal}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { e.percentOfTotal / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = colorForPercent(e.percentOfTotal),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.muscles_volume_sets, formatWeight(e.volumeKg), e.totalSets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun colorForPercent(pct: Int): androidx.compose.ui.graphics.Color = when {
    pct >= 25 -> MaterialTheme.colorScheme.primary
    pct >= 10 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outline
}

private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.CHEST -> "Klatka piersiowa"
    MuscleGroup.BACK -> "Plecy"
    MuscleGroup.SHOULDERS -> "Barki"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.QUADS -> "Czworogłowe (uda)"
    MuscleGroup.HAMSTRINGS -> "Dwugłowe (uda)"
    MuscleGroup.GLUTES -> "Pośladki"
    MuscleGroup.CALVES -> "Łydki"
    MuscleGroup.CORE -> "Brzuch / core"
    MuscleGroup.CARDIO -> "Cardio"
    MuscleGroup.OTHER -> "Inne"
}

