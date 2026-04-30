package pl.filebit.gymtracker.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import pl.filebit.gymtracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListScreen(
    onEditPlan: (Long) -> Unit,
    onCreateNewPlan: () -> Unit,
    onStartedCoachWorkout: () -> Unit,
    onOpenTemplates: () -> Unit,
    vm: PlanListViewModel = hiltViewModel()
) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val activePlanId by vm.activePlanId.collectAsStateWithLifecycle()
    var dayPickerForPlan by remember { mutableStateOf<PlanListItem?>(null) }
    var newMenuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.nav_plans),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Wielki przycisk szablonów na górze
            item {
                TemplatesBigButton(
                    count = pl.filebit.gymtracker.data.template.PlanTemplates.all.size,
                    onClick = onOpenTemplates
                )
            }

            if (plans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.plans_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(plans, key = { it.plan.id }) { item ->
                    PlanCard(
                        item = item,
                        isActive = item.plan.id == activePlanId,
                        onEdit = { onEditPlan(item.plan.id) },
                        onStart = { dayPickerForPlan = item },
                        onDuplicate = { vm.duplicatePlan(item.plan.id) { newId -> onEditPlan(newId) } },
                        onDelete = { showDeleteDialog = item.plan.id }
                    )
                }
            }

            // Dashed 'Nowy pusty plan' na dole listy
            item {
                NewEmptyPlanCard(onClick = onCreateNewPlan)
            }
        }
    }

    showDeleteDialog?.let { idToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.plan_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlan(idToDelete)
                    showDeleteDialog = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    dayPickerForPlan?.let { item ->
        DayPickerDialog(
            planName = item.plan.name,
            daysWithExercises = item.daysWithExercises.toSet(),
            onDismiss = { dayPickerForPlan = null },
            onPickDay = { day ->
                dayPickerForPlan = null
                vm.startWorkoutFromPlanForDay(item.plan.id, day, onStartedCoachWorkout)
            }
        )
    }
}

@Composable
private fun PlanCard(
    item: PlanListItem,
    isActive: Boolean,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Górny rząd: tytuł + chip AKTYWNY/AI + 3 kropki
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.plan.name.ifBlank { "(plan bez nazwy)" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Spacer(Modifier.width(6.dp))
                    ActiveBadge()
                }
                if (item.plan.createdByAi) {
                    Spacer(Modifier.width(6.dp))
                    AiBadge()
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plans_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
            // Subtitle: częstotliwość + dni
            Text(
                buildString {
                    val daysCount = item.daysWithExercises.size
                    if (daysCount > 0) {
                        append("${daysCount}×/tydz · ")
                    }
                    append(formatDays(item.daysWithExercises))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            // Dolny rząd: liczba ćwiczeń (mono) + Start CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${item.exerciseCount}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        "ĆWICZEŃ ŁĄCZNIE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                    )
                }
                Card(
                    onClick = onStart,
                    enabled = item.exerciseCount > 0,
                    colors = CardDefaults.cardColors(
                        containerColor = pl.filebit.gymtracker.ui.theme.AccentOrange,
                        disabledContainerColor = pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (item.exerciseCount > 0)
                                androidx.compose.ui.graphics.Color.Black
                            else pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Start",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            ),
                            color = if (item.exerciseCount > 0)
                                androidx.compose.ui.graphics.Color.Black
                            else pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesBigButton(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        colors = CardDefaults.cardColors(containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, pl.filebit.gymtracker.ui.theme.DarkOutline
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Wybierz z gotowych szablonów ($count)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun NewEmptyPlanCard(onClick: () -> Unit) {
    // Dashed outline na 'Nowy pusty plan' — wizualne odróżnienie od listy
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Dashed border przez Modifier.drawBehind z PathEffect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color.Transparent)
                .drawDashedBorder(
                    color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceDim,
                    cornerRadius = 14.dp,
                    strokeWidth = 1.dp
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Nowy pusty plan",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                )
            }
        }
    }
}

private fun Modifier.drawDashedBorder(
    color: androidx.compose.ui.graphics.Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp
): Modifier = this.then(
    Modifier.drawBehind {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8f, 6f), 0f
            )
        )
        drawRoundRect(
            color = color,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                cornerRadius.toPx(), cornerRadius.toPx()
            ),
            style = stroke
        )
    }
)

@Composable
private fun DayPickerDialog(
    planName: String,
    daysWithExercises: Set<Int>,
    onDismiss: () -> Unit,
    onPickDay: (Int) -> Unit
) {
    val today = remember {
        Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_pick_day_title, planName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (daysWithExercises.isEmpty()) {
                    Text(stringResource(R.string.plan_no_days_with_exercises))
                } else {
                    listOf(
                        1 to R.string.day_mon_long,
                        2 to R.string.day_tue_long,
                        3 to R.string.day_wed_long,
                        4 to R.string.day_thu_long,
                        5 to R.string.day_fri_long,
                        6 to R.string.day_sat_long,
                        7 to R.string.day_sun_long
                    ).forEach { (day, labelRes) ->
                        val enabled = daysWithExercises.contains(day)
                        val isToday = day == today
                        TextButton(
                            onClick = { onPickDay(day) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val prefix = if (isToday) "▶ " else ""
                            Text(
                                prefix + stringResource(labelRes) +
                                    if (isToday) " " + stringResource(R.string.plan_day_today_suffix) else "",
                                color = if (isToday && enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun formatDays(days: List<Int>): String {
    if (days.isEmpty()) return stringResource(R.string.plan_no_schedule)
    val labels = days.sorted().mapNotNull { dayShortLabel(it) }
    return labels.joinToString(", ")
}

@Composable
private fun AiBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = stringResource(R.string.plan_ai_badge_full),
            tint = pl.filebit.gymtracker.ui.theme.AccentOrange,
            modifier = Modifier.height(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.plan_ai_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = pl.filebit.gymtracker.ui.theme.AccentOrange
        )
    }
}

@Composable
private fun ActiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = pl.filebit.gymtracker.ui.theme.SuccessGreen.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    pl.filebit.gymtracker.ui.theme.SuccessGreen,
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "AKTYWNY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                fontSize = 10.sp
            ),
            color = pl.filebit.gymtracker.ui.theme.SuccessGreen
        )
    }
}

@Composable
private fun dayShortLabel(day: Int): String? = when (day) {
    1 -> stringResource(R.string.day_mon_short)
    2 -> stringResource(R.string.day_tue_short)
    3 -> stringResource(R.string.day_wed_short)
    4 -> stringResource(R.string.day_thu_short)
    5 -> stringResource(R.string.day_fri_short)
    6 -> stringResource(R.string.day_sat_short)
    7 -> stringResource(R.string.day_sun_short)
    else -> null
}
