package pl.filebit.gymtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ui.diet.HydrationLogDialog
import pl.filebit.gymtracker.ui.diet.RecoveryDialog
import pl.filebit.gymtracker.ui.diet.StepsDialog
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * v1.28.5 — samodzielna sekcja dziennych kafelków WODA / KROKI / REGEN.
 *
 * Przeniesiona z ekranu Dieta na Home (codzienne logi na wierzchu). Komponent
 * jest samowystarczalny: własny [DailyTilesViewModel] + obsługa 3 dialogów.
 * Wystarczy wstawić `DailyTilesSection()` na dowolnym ekranie.
 */
@Composable
fun DailyTilesSection(
    vm: DailyTilesViewModel = hiltViewModel()
) {
    val hydrationToday by vm.hydrationToday.collectAsStateWithLifecycle()
    val hydrationGoal by vm.hydrationGoal.collectAsStateWithLifecycle()
    val hydrationLogs by vm.hydrationLogs.collectAsStateWithLifecycle()
    val stepsToday by vm.stepsToday.collectAsStateWithLifecycle()
    val hcConnected by vm.hcConnected.collectAsStateWithLifecycle()
    val showHydration by vm.showHydrationDialog.collectAsStateWithLifecycle()
    val showSteps by vm.showStepsDialog.collectAsStateWithLifecycle()
    val showRecovery by vm.showRecoveryDialog.collectAsStateWithLifecycle()

    MiniTilesRow(
        hydrationToday = hydrationToday,
        hydrationGoal = hydrationGoal,
        onOpenHydration = { vm.openHydrationDialog() },
        stepsToday = stepsToday,
        hcConnected = hcConnected,
        onOpenSteps = { vm.openStepsDialog() },
        onOpenRecovery = { vm.openRecoveryDialog() }
    )

    if (showHydration) {
        HydrationLogDialog(
            logs = hydrationLogs,
            consumedToday = hydrationToday,
            goal = hydrationGoal,
            onDelete = { id -> vm.deleteHydration(id) },
            onAdd = { ml, source -> vm.addHydration(ml, source) },
            onDismiss = { vm.dismissHydrationDialog() }
        )
    }
    if (showSteps) {
        StepsDialog(
            currentSteps = stepsToday,
            onSave = { steps -> vm.setSteps(steps) },
            onDismiss = { vm.dismissStepsDialog() }
        )
    }
    if (showRecovery) {
        RecoveryDialog(
            onSave = { sleep, sleepQ, stress, hunger, energy, soreness, difficulty ->
                vm.saveRecoveryLog(sleep, sleepQ, stress, hunger, energy, soreness, difficulty)
            },
            onDismiss = { vm.dismissRecoveryDialog() }
        )
    }
}

// === UI kafelków (przeniesione z DietScreen — v1.28.5) ===

private fun formatHydrationCompact(ml: Int): String {
    if (ml < 1000) return "$ml"
    val l = ml / 1000.0
    return if (l >= 10) "${l.toInt()}k" else "%.1fk".format(l).replace(",", ".")
}

private fun formatHydrationGoalCompact(ml: Int): String {
    if (ml < 1000) return "${ml}ml"
    val l = ml / 1000.0
    return if (l == l.toInt().toDouble()) "${l.toInt()}L" else "%.1fL".format(l).replace(",", ".")
}

private fun formatStepsCompact(steps: Int): String {
    if (steps < 1000) return "$steps"
    val k = steps / 1000.0
    return when {
        k >= 10 -> "${k.toInt()}k"
        k == k.toInt().toDouble() -> "${k.toInt()}k"
        else -> "%.1fk".format(k).replace(",", ".")
    }
}

/** 3 mini kafelki w jednym wierszu: Woda / Kroki / Regeneracja. */
@Composable
private fun MiniTilesRow(
    hydrationToday: Int,
    hydrationGoal: Int,
    onOpenHydration: () -> Unit,
    stepsToday: Int,
    hcConnected: Boolean,
    onOpenSteps: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    val stepsGoal = 8000
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // WODA
        MiniTile(
            label = "WODA", icon = "💧", onClick = onOpenHydration,
            modifier = Modifier.weight(1f)
        ) {
            TileValue(
                main = formatHydrationCompact(hydrationToday),
                goal = "/ ${formatHydrationGoalCompact(hydrationGoal)}"
            )
            Spacer(Modifier.height(7.dp))
            TileProgressBar(
                if (hydrationGoal > 0) hydrationToday.toFloat() / hydrationGoal else 0f
            )
        }
        // KROKI
        MiniTile(
            label = if (hcConnected) "KROKI 🔗" else "KROKI",
            icon = "🚶", onClick = onOpenSteps,
            modifier = Modifier.weight(1f)
        ) {
            TileValue(
                main = if (stepsToday > 0) formatStepsCompact(stepsToday) else "—",
                goal = "/ ${formatStepsCompact(stepsGoal)}"
            )
            Spacer(Modifier.height(7.dp))
            TileProgressBar(
                if (stepsToday > 0) stepsToday.toFloat() / stepsGoal else 0f
            )
        }
        // REGEN — brak liczby, akcja oceny
        MiniTile(
            label = "REGEN", icon = "🛌", onClick = onOpenRecovery,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "Sen·Stres",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "OCEŃ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp
                ),
                color = AccentOrange
            )
        }
    }
}

/** Wspólny szkielet mini-kafelka: label + ikona w rogu, pod spodem treść. */
@Composable
private fun MiniTile(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Text(icon, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Wartość kafelka: duża liczba + mniejszy cel obok. */
@Composable
private fun TileValue(main: String, goal: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            main,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 19.sp
            ),
            color = DarkOnSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(
            goal,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 11.sp
            ),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

/** Cienki pasek postępu — pomarańczowe wypełnienie na ciemnym torze. */
@Composable
private fun TileProgressBar(fraction: Float) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(DarkSurfaceVariant)
    ) {
        if (f > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AccentOrange)
            )
        }
    }
}
