package pl.filebit.gymtracker.ui.periodization

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.filebit.gymtracker.ui.components.ScreenHeader
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface

/**
 * v1.16.0 — Ekran "Plan cyklu" pokazujący historię mezo-cykli + aktywny cykl.
 *
 * Struktura: LazyColumn z kartami per cykl (od najnowszego). Aktywny cykl ma
 * większe accent (full AccentOrange border + większy padding), historia ma
 * dyskretny border (phase color z alpha 0.4).
 *
 * Dane: ViewModel observuje `mesoDao.observeRecent(12)` — ostatnie 12 cykli
 * z bazy (włącznie z backfilled z v1.13.0).
 */
@Composable
fun PeriodizationPlanScreen(
    onBack: () -> Unit,
    vm: PeriodizationPlanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            ScreenHeader(
                title = "Plan cyklu",
                onBack = onBack
            )
        }
    ) { padding ->
        if (state.cycles.isEmpty() && !state.isLoading) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.activeCard != null) {
                    item("active-header") {
                        SectionLabel("AKTYWNY CYKL", accent = true)
                    }
                    item("active") {
                        MesocycleCard(
                            ui = state.activeCard!!,
                            emphasized = true
                        )
                    }
                }
                if (state.historyCards.isNotEmpty()) {
                    item("history-header") {
                        Spacer(Modifier.height(8.dp))
                        SectionLabel("HISTORIA (${state.historyCards.size})")
                    }
                    items(state.historyCards, key = { it.id }) { ui ->
                        MesocycleCard(ui = ui, emphasized = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = if (accent) pl.filebit.gymtracker.ui.theme.AccentOrange else DarkOnSurfaceVariant
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "📊",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Brak mesocykli w bazie",
                style = MaterialTheme.typography.titleMedium,
                color = DarkOnSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pierwszy mesocykl utworzy się automatycznie gdy masz ≥4 ukończone treningi w bazie. " +
                    "Algorytm wykryje fazę z historii volume i rozpocznie cykl.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Karta jednego mesocyklu. `emphasized=true` dla aktywnego (większy padding, accent border),
 * false dla historycznych (dyskretny border).
 */
@Composable
private fun MesocycleCard(
    ui: MesocycleCardUi,
    emphasized: Boolean
) {
    val phaseColor = ui.phase.color()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) phaseColor.copy(alpha = 0.12f) else DarkSurface
        ),
        border = BorderStroke(
            width = if (emphasized) 1.5.dp else 1.dp,
            color = if (emphasized) phaseColor.copy(alpha = 0.55f) else phaseColor.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(if (emphasized) 16.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.phase.emoji(),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ui.phase.labelPl(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = phaseColor
                    )
                    Text(
                        ui.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                StatusBadge(label = ui.statusLabel, color = ui.statusColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ui.dateRangeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            if (emphasized && ui.progressPct > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ui.progressPct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = phaseColor,
                    trackColor = phaseColor.copy(alpha = 0.20f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${ui.progressPct}% — zostało ${ui.daysRemainingLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
            if (ui.notes.isNotBlank() && emphasized) {
                Spacer(Modifier.height(6.dp))
                Text(
                    ui.notes,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = color
        )
    }
}
