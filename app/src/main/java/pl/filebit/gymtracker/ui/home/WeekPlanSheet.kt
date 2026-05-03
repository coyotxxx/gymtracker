package pl.filebit.gymtracker.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.util.ScheduleSlot

private val DAY_LABELS = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
private val DAY_FULL = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanSheet(
    weekSlots: Map<Int, List<ScheduleSlot>>,
    plansById: Map<Long, TrainingPlan>,
    completedDays: Set<Int>,
    todayDayOfWeek: Int,
    onDismiss: () -> Unit,
    onTrainNow: (planId: Long, sourceDay: Int) -> Unit,
    onPostpone: (planId: Long, originalDay: Int, targetDay: Int) -> Unit,
    onSkip: (planId: Long, originalDay: Int) -> Unit,
    onClearOverride: (planId: Long, originalDay: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerForSlot by remember { mutableStateOf<ScheduleSlot?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Plan tygodnia",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = DarkOnSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Text(
                "Tap na trening — przesuń, pomiń lub trenuj teraz.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp)
            )

            for (day in 1..7) {
                val slots = weekSlots[day].orEmpty()
                val isToday = day == todayDayOfWeek
                val isCompleted = day in completedDays
                DayRow(
                    dayLabel = DAY_LABELS[day - 1],
                    dayFull = DAY_FULL[day - 1],
                    slots = slots,
                    isToday = isToday,
                    isCompleted = isCompleted,
                    plansById = plansById,
                    onSlotClick = { slot -> pickerForSlot = slot },
                    onTrainNow = onTrainNow
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    pickerForSlot?.let { slot ->
        SlotActionsSheet(
            slot = slot,
            planName = plansById[slot.planId]?.name ?: "—",
            occupiedDays = weekSlots.keys,
            onDismiss = { pickerForSlot = null },
            onTrainNow = {
                pickerForSlot = null
                onTrainNow(slot.planId, slot.sourceDayOfWeek)
            },
            onMoveTo = { targetDay ->
                pickerForSlot = null
                onPostpone(slot.planId, slot.sourceDayOfWeek, targetDay)
            },
            onSkip = {
                pickerForSlot = null
                onSkip(slot.planId, slot.sourceDayOfWeek)
            },
            onResetToOriginal = if (slot.isMoved) {
                {
                    pickerForSlot = null
                    onClearOverride(slot.planId, slot.sourceDayOfWeek)
                }
            } else null
        )
    }
}

@Composable
private fun DayRow(
    dayLabel: String,
    dayFull: String,
    slots: List<ScheduleSlot>,
    isToday: Boolean,
    isCompleted: Boolean,
    plansById: Map<Long, TrainingPlan>,
    onSlotClick: (ScheduleSlot) -> Unit,
    onTrainNow: (planId: Long, sourceDay: Int) -> Unit
) {
    val borderColor = if (isToday) AccentOrange else DarkOutlineSoft
    val borderWidth = if (isToday) 1.5.dp else 1.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Lewa kolumna — dzień tyg
            Column(modifier = Modifier.width(56.dp)) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = if (isToday) AccentOrange else DarkOnSurface
                )
                Text(
                    if (isToday) "DZIŚ" else dayFull.lowercase().take(3),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = if (isToday) AccentOrange else DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            // Środek — treść
            Column(modifier = Modifier.weight(1f)) {
                if (slots.isEmpty()) {
                    Text(
                        "wolny",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = DarkOnSurfaceVariant
                    )
                } else {
                    slots.forEach { slot ->
                        SlotChip(
                            slot = slot,
                            planName = plansById[slot.planId]?.name ?: "—",
                            onClick = { onSlotClick(slot) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            // Prawa kolumna — status pill
            if (isCompleted) {
                StatusPill("✓ ZROBIONE", SuccessGreen)
            } else if (isToday && slots.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentOrange, CircleShape)
                        .clickable { onTrainNow(slots.first().planId, slots.first().sourceDayOfWeek) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Trenuj teraz",
                        tint = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotChip(
    slot: ScheduleSlot,
    planName: String,
    onClick: () -> Unit
) {
    val tintAlpha = if (slot.isMoved) 0.10f else 0.18f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(AccentOrange.copy(alpha = tintAlpha), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (slot.isMoved) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            planName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = AccentOrange
        )
        if (slot.isMoved) {
            Spacer(Modifier.width(6.dp))
            Text(
                "(z ${DAY_LABELS[slot.sourceDayOfWeek - 1]})",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = AccentOrange.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp
            ),
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotActionsSheet(
    slot: ScheduleSlot,
    planName: String,
    occupiedDays: Set<Int>,
    onDismiss: () -> Unit,
    onTrainNow: () -> Unit,
    onMoveTo: (targetDay: Int) -> Unit,
    onSkip: () -> Unit,
    onResetToOriginal: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPicker by remember { mutableStateOf(false) }

    val targetDay = slot.targetDayOfWeek
    val sublabel = if (slot.isMoved)
        "Slot: ${DAY_FULL[targetDay - 1]} (oryginalnie ${DAY_FULL[slot.sourceDayOfWeek - 1]})"
    else
        "Slot: ${DAY_FULL[targetDay - 1]}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = planName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            pl.filebit.gymtracker.ui.common.ActionSheetRow(
                icon = Icons.Default.PlayArrow,
                label = "Trenuj teraz",
                subtitle = "Uruchom coach mode dla tego treningu",
                onClick = onTrainNow
            )
            pl.filebit.gymtracker.ui.common.ActionSheetRow(
                icon = Icons.Default.SwapHoriz,
                label = "Przesuń na inny dzień",
                subtitle = "Tylko w tym tygodniu — nie modyfikuje planu",
                onClick = { showPicker = true }
            )
            pl.filebit.gymtracker.ui.common.ActionSheetRow(
                icon = Icons.Default.Block,
                label = "Pomiń ten trening",
                subtitle = "W tym tygodniu zniknie z planu",
                danger = true,
                onClick = onSkip
            )
            if (onResetToOriginal != null) {
                pl.filebit.gymtracker.ui.common.ActionSheetRow(
                    icon = Icons.Default.Replay,
                    label = "Cofnij przesunięcie",
                    subtitle = "Wraca na ${DAY_FULL[slot.sourceDayOfWeek - 1]}",
                    onClick = onResetToOriginal
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

    if (showPicker) {
        pl.filebit.gymtracker.ui.common.DayPickerDialog(
            title = "Przesuń na…",
            sourceDay = targetDay,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                showPicker = false
                onMoveTo(picked)
            },
            hintFor = { d ->
                if (d != targetDay && d in occupiedDays) "zajęty innym treningiem" else ""
            }
        )
    }
}
