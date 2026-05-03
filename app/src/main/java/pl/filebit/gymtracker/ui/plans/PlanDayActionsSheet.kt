package pl.filebit.gymtracker.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.common.ActionSheetRow
import pl.filebit.gymtracker.ui.common.DAY_FULL_LABELS
import pl.filebit.gymtracker.ui.common.DayPickerDialog
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface

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
                text = DAY_FULL_LABELS[day - 1],
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
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

            ActionSheetRow(
                icon = Icons.Filled.Edit,
                label = if (hasExercises) "Edytuj ten dzień" else "Dodaj ćwiczenia tego dnia",
                onClick = {
                    onEditDay(day)
                    onDismiss()
                }
            )

            if (hasExercises) {
                ActionSheetRow(
                    icon = Icons.Filled.SwapHoriz,
                    label = "Przenieś na inny dzień",
                    subtitle = "Wszystkie ćwiczenia dostaną nowy dzień tygodnia",
                    onClick = { picker = PickerMode.MOVE }
                )
                ActionSheetRow(
                    icon = Icons.Filled.SwapVert,
                    label = "Zamień z innym dniem",
                    subtitle = "Wymienisz ćwiczenia między dniami",
                    onClick = { picker = PickerMode.SWAP }
                )
                ActionSheetRow(
                    icon = Icons.Filled.ContentCopy,
                    label = "Skopiuj do innego dnia",
                    subtitle = "Duplikat ćwiczeń (oryginał zostaje)",
                    onClick = { picker = PickerMode.COPY_TO }
                )
                ActionSheetRow(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Wyczyść ten dzień",
                    subtitle = "Usuwa wszystkie ćwiczenia z tego dnia",
                    danger = true,
                    onClick = { showClearConfirm = true }
                )
            } else if (daysWithExercises.isNotEmpty()) {
                ActionSheetRow(
                    icon = Icons.Filled.ContentCopy,
                    label = "Skopiuj z innego dnia",
                    subtitle = "Duplikat ćwiczeń z wybranego dnia",
                    onClick = { picker = PickerMode.COPY_FROM }
                )
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
            },
            hintFor = { d ->
                val targetHasEx = d in daysWithExercises
                when {
                    mode == PickerMode.MOVE && targetHasEx -> "scali z istniejącymi"
                    mode == PickerMode.COPY_TO && targetHasEx -> "doda na końcu"
                    mode == PickerMode.SWAP && !targetHasEx -> "obecnie wolny"
                    else -> ""
                }
            },
            enabledFor = { d ->
                if (d == day) false
                else if (mode == PickerMode.COPY_FROM) d in daysWithExercises
                else true
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
                        "${DAY_FULL_LABELS[day - 1]}. Tej akcji nie cofniesz.",
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

private fun plExercises(count: Int): String = when {
    count == 1 -> "ćwiczenie"
    count % 10 in 2..4 && (count % 100 !in 12..14) -> "ćwiczenia"
    else -> "ćwiczeń"
}
