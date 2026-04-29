package pl.filebit.gymtracker.ui.plans

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
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
    vm: PlanListViewModel = hiltViewModel()
) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    var dayPickerForPlan by remember { mutableStateOf<PlanListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_plans)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNewPlan) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.plans_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(plans, key = { it.plan.id }) { item ->
                    PlanCard(
                        item = item,
                        onEdit = { onEditPlan(item.plan.id) },
                        onStart = { dayPickerForPlan = item }
                    )
                }
            }
        }
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
private fun PlanCard(item: PlanListItem, onEdit: () -> Unit, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.plan.name.ifBlank { "(plan bez nazwy)" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDays(item.daysWithExercises),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.plan_exercise_count, item.exerciseCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStart,
                enabled = item.exerciseCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.plan_start_button))
            }
        }
    }
}

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
