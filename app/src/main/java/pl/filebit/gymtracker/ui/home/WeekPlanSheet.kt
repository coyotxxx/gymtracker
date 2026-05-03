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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
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
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
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
        SlotActionsDialog(
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

@Composable
private fun SlotActionsDialog(
    slot: ScheduleSlot,
    planName: String,
    occupiedDays: Set<Int>,
    onDismiss: () -> Unit,
    onTrainNow: () -> Unit,
    onMoveTo: (targetDay: Int) -> Unit,
    onSkip: () -> Unit,
    onResetToOriginal: (() -> Unit)?
) {
    var pickDayMode by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(planName) },
        text = {
            if (pickDayMode) {
                Column {
                    Text(
                        "Przesuń na:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    for (day in 1..7) {
                        val taken = day != slot.targetDayOfWeek && day in occupiedDays
                        val current = day == slot.targetDayOfWeek
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (current) AccentOrange.copy(alpha = 0.10f) else DarkSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !current) { onMoveTo(day) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                DAY_FULL[day - 1],
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current) AccentOrange else DarkOnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (current) Text(
                                "(obecny)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AccentOrange
                            ) else if (taken) Text(
                                "zajęty",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column {
                    ActionRow(Icons.Default.PlayArrow, "Trenuj teraz", onClick = onTrainNow)
                    ActionRow(Icons.Default.SwapHoriz, "Przesuń na inny dzień", onClick = { pickDayMode = true })
                    ActionRow(Icons.Default.Block, "Pomiń ten trening", onClick = onSkip)
                    if (onResetToOriginal != null) {
                        ActionRow(
                            Icons.Default.Replay,
                            "Cofnij przesunięcie (oryginalny dzień: ${DAY_FULL[slot.sourceDayOfWeek - 1]})",
                            onClick = onResetToOriginal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
    }
}
