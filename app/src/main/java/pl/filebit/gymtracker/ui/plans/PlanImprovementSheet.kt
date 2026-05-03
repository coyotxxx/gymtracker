package pl.filebit.gymtracker.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ai.AiPlanDay
import pl.filebit.gymtracker.ai.AiPlanProposal
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton
import pl.filebit.gymtracker.ui.theme.SuccessGreen

private val DAY_FULL = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")

private enum class DiffMark { ADDED, REMOVED, KEPT }

private data class DiffLine(
    val mark: DiffMark,
    val day: Int,
    val text: String
)

/**
 * Preview poprawionego planu od AI — pokazuje diff stary vs nowy w sekcjach
 * dziennych. User wybiera: Zastąp / Kopia / Anuluj.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanImprovementSheet(
    proposal: AiPlanProposal,
    currentExercisesByDay: Map<Int, List<String>>,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onSaveAsCopy: () -> Unit,
    headline: String = "Poprawiona wersja planu"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReplaceConfirm by remember { mutableStateOf(false) }
    val diff = remember(proposal, currentExercisesByDay) { buildDiff(proposal, currentExercisesByDay) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                proposal.name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = AccentOrange
            )
            if (proposal.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    proposal.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            // Legend
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(SuccessGreen, "dodane")
                LegendDot(ErrorRed, "usunięte")
                LegendDot(DarkOnSurfaceVariant, "bez zmian")
            }
            Spacer(Modifier.height(12.dp))

            // Diff list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(diff) { line ->
                    DiffRow(line)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Akcje
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GymPrimaryButton(
                    text = if (isApplying) "Zapisuję…" else "💾 Zapisz jako KOPIĘ",
                    onClick = { if (!isApplying) onSaveAsCopy() }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, ErrorRed.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isApplying) { showReplaceConfirm = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isApplying) "Zapisuję…" else "⚠ Zastąp obecny plan",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ErrorRed
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isApplying) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("Zastąpić obecny plan?") },
            text = {
                Text(
                    "Aktualne ćwiczenia w planie zostaną usunięte i zastąpione propozycją AI. " +
                        "Tej akcji nie cofniesz. Jeśli wolisz zachować oryginał — wybierz 'Zapisz jako kopię'.",
                    color = DarkOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirm = false
                    onReplace()
                }) { Text("Zastąp", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DiffRow(line: DiffLine) {
    val (markColor, prefix) = when (line.mark) {
        DiffMark.ADDED -> SuccessGreen to "+ "
        DiffMark.REMOVED -> ErrorRed to "− "
        DiffMark.KEPT -> DarkOnSurfaceVariant to "  "
    }
    val isDayHeader = line.text.startsWith("##")
    if (isDayHeader) {
        Spacer(Modifier.height(6.dp))
        Text(
            DAY_FULL[(line.day - 1).coerceIn(0, 6)],
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = AccentOrange
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when (line.mark) {
                        DiffMark.ADDED -> SuccessGreen.copy(alpha = 0.08f)
                        DiffMark.REMOVED -> ErrorRed.copy(alpha = 0.08f)
                        DiffMark.KEPT -> Color.Transparent
                    },
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                prefix,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = markColor
            )
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = DarkOnSurface
            )
        }
    }
}

private fun buildDiff(
    proposal: AiPlanProposal,
    currentByDay: Map<Int, List<String>>
): List<DiffLine> {
    val out = mutableListOf<DiffLine>()
    val newByDay: Map<Int, List<String>> = proposal.days.associate { d ->
        d.dayOfWeek to d.exercises.map { it.exerciseName.trim() }
    }
    val oldByDay: Map<Int, List<String>> = currentByDay.mapValues { (_, list) ->
        list.map { it.trim() }
    }
    val allDays = (newByDay.keys + oldByDay.keys).sorted()
    allDays.forEach { day ->
        out += DiffLine(DiffMark.KEPT, day, "##")  // header marker
        val newEx = newByDay[day] ?: emptyList()
        val oldEx = oldByDay[day] ?: emptyList()
        // Najpierw KEPT (te które są w obu)
        oldEx.forEach { exName ->
            val matched = newEx.any { it.equals(exName, ignoreCase = true) ||
                exName.contains(it, ignoreCase = true) ||
                it.contains(exName, ignoreCase = true) }
            if (matched) out += DiffLine(DiffMark.KEPT, day, exName)
            else out += DiffLine(DiffMark.REMOVED, day, exName)
        }
        // Potem ADDED (nowe nie pokrywające się z old)
        newEx.forEach { exName ->
            val matched = oldEx.any { it.equals(exName, ignoreCase = true) ||
                exName.contains(it, ignoreCase = true) ||
                it.contains(exName, ignoreCase = true) }
            if (!matched) out += DiffLine(DiffMark.ADDED, day, exName)
        }
        if (newEx.isEmpty() && oldEx.isEmpty()) {
            out += DiffLine(DiffMark.KEPT, day, "(wolny)")
        }
    }
    return out
}
