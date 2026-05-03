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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

private val DAY_FULL = listOf(
    "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"
)
private val DAY_SHORT = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")

private enum class PickerMode { MOVE, SWAP, COPY_TO, COPY_FROM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDayActionsSheet(
    day: Int,
    exercisesCount: Int,
    daysWithExercises: Set<Int>,
    onDismiss: () -> Unit,
    onEditDay: (Int) -> Unit,
    onMoveDay: (from: Int, to: Int) -> Unit,
    onSwapDays: (a: Int, b: Int) -> Unit,
    onCopyDay: (from: Int, to: Int) -> Unit,
    onClearDay: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val hasExercises = exercisesCount > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = DAY_FULL[day - 1],
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasExercises)
                    "$exercisesCount ${plExercises(exercisesCount)} w tym dniu"
                else
                    "Wolny dzień",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            ActionRow(
                icon = Icons.Filled.Edit,
                label = if (hasExercises) "Edytuj ten dzień" else "Dodaj ćwiczenia tego dnia",
                onClick = {
                    onEditDay(day)
                    onDismiss()
                }
            )

            if (hasExercises) {
                ActionRow(
                    icon = Icons.Filled.SwapHoriz,
                    label = "Przenieś na inny dzień",
                    subtitle = "Wszystkie ćwiczenia dostaną nowy dzień tygodnia",
                    onClick = { picker = PickerMode.MOVE }
                )
                ActionRow(
                    icon = Icons.Filled.SwapVert,
                    label = "Zamień z innym dniem",
                    subtitle = "Wymienisz ćwiczenia między dniami",
                    onClick = { picker = PickerMode.SWAP }
                )
                ActionRow(
                    icon = Icons.Filled.ContentCopy,
                    label = "Skopiuj do innego dnia",
                    subtitle = "Duplikat ćwiczeń (oryginał zostaje)",
                    onClick = { picker = PickerMode.COPY_TO }
                )
                ActionRow(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Wyczyść ten dzień",
                    subtitle = "Usuwa wszystkie ćwiczenia z tego dnia",
                    danger = true,
                    onClick = { showClearConfirm = true }
                )
            } else {
                if (daysWithExercises.isNotEmpty()) {
                    ActionRow(
                        icon = Icons.Filled.ContentCopy,
                        label = "Skopiuj z innego dnia",
                        subtitle = "Duplikat ćwiczeń z wybranego dnia",
                        onClick = { picker = PickerMode.COPY_FROM }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Zamknij", color = AccentOrange)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    picker?.let { mode ->
        DayPickerDialog(
            title = when (mode) {
                PickerMode.MOVE -> "Przenieś na…"
                PickerMode.SWAP -> "Zamień z…"
                PickerMode.COPY_TO -> "Skopiuj do…"
                PickerMode.COPY_FROM -> "Skopiuj z…"
            },
            sourceDay = day,
            daysWithExercises = daysWithExercises,
            mode = mode,
            onDismiss = { picker = null },
            onPick = { targetDay ->
                picker = null
                when (mode) {
                    PickerMode.MOVE -> onMoveDay(day, targetDay)
                    PickerMode.SWAP -> onSwapDays(day, targetDay)
                    PickerMode.COPY_TO -> onCopyDay(day, targetDay)
                    PickerMode.COPY_FROM -> onCopyDay(targetDay, day)
                }
                onDismiss()
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Wyczyścić ten dzień?") },
            text = {
                Text(
                    "Usunie się $exercisesCount ${plExercises(exercisesCount)} z dnia " +
                        "${DAY_FULL[day - 1]}. Tej akcji nie cofniesz.",
                    color = DarkOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearDay(day)
                        onDismiss()
                    }
                ) { Text("Wyczyść", color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) Color(0xFFFF6B6B) else AccentOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (danger) Color(0xFFFF6B6B) else DarkOnSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayPickerDialog(
    title: String,
    sourceDay: Int,
    daysWithExercises: Set<Int>,
    mode: PickerMode,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                (1..7).forEach { d ->
                    val isSource = d == sourceDay
                    // Reguły dostępności:
                    // MOVE: każdy dzień != source
                    // SWAP: każdy dzień != source (zamieni nawet z pustym)
                    // COPY_TO: każdy dzień != source
                    // COPY_FROM: tylko dni z ćwiczeniami i != source
                    val enabled = when {
                        isSource -> false
                        mode == PickerMode.COPY_FROM -> d in daysWithExercises
                        else -> true
                    }
                    val targetHasEx = d in daysWithExercises
                    val hint = when {
                        isSource -> "(ten dzień)"
                        mode == PickerMode.MOVE && targetHasEx -> "scali z istniejącymi"
                        mode == PickerMode.COPY_TO && targetHasEx -> "doda na końcu"
                        mode == PickerMode.COPY_FROM && !targetHasEx -> ""
                        mode == PickerMode.SWAP && !targetHasEx -> "obecnie wolny"
                        else -> ""
                    }
                    DayRow(day = d, hint = hint, enabled = enabled, onClick = { onPick(d) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun DayRow(day: Int, hint: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(DarkSurfaceVariant.copy(alpha = if (enabled) 0.4f else 0.15f), RoundedCornerShape(10.dp))
            .border(1.dp, AccentOrange.copy(alpha = if (enabled) 0.25f else 0.0f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(AccentOrange.copy(alpha = 0.18f * alpha), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    DAY_SHORT[day - 1],
                    color = AccentOrange.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                DAY_FULL[day - 1],
                color = DarkOnSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium
            )
            if (hint.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    hint,
                    color = DarkOnSurfaceVariant.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

private fun plExercises(count: Int): String = when {
    count == 1 -> "ćwiczenie"
    count % 10 in 2..4 && (count % 100 !in 12..14) -> "ćwiczenia"
    else -> "ćwiczeń"
}
