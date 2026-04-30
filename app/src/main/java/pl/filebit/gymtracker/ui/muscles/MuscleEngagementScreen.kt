package pl.filebit.gymtracker.ui.muscles

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
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

/**
 * Mapowanie grupy mięśniowej na plik maski w assets/muscle_masks/.
 * Każda maska ma TĘ SAMĄ rozdzielczość i layout (przód+tył) co body_base.webp,
 * więc mogą być nakładane jeden na drugi bez przesunięć.
 */
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
private fun BodyHeatmapCard(engagement: List<MuscleEngagement>) {
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
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
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
            BodyHeatmap(engagement = engagement)
        }
    }
}

/**
 * Heatmap ciała: warstwa bazy (szara sylwetka przód+tył) + dla każdego mięśnia
 * z engagement > 0 dodatkowa warstwa maski tinted na kolor primary z alpha
 * proporcjonalnym do procentu engagement.
 */
@Composable
private fun BodyHeatmap(engagement: List<MuscleEngagement>) {
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)   // body_base.webp jest kwadratowy (przód+tył obok siebie)
    ) {
        // Warstwa 1: szara sylwetka
        AsyncImage(
            model = "file:///android_asset/muscle_masks/body_base.webp",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Warstwa 2..N: kolorowe tints per mięsień
        engagement.forEach { e ->
            val asset = e.muscle.maskAsset() ?: return@forEach
            // Intensywność: percent/40 zmapowane do 0.20..1.0 (małe % nadal widoczne)
            val intensity = (e.percentOfTotal / 40f).coerceIn(0.20f, 1f)
            AsyncImage(
                model = "file:///android_asset/$asset",
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(tint.copy(alpha = intensity)),
                modifier = Modifier.fillMaxSize()
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

