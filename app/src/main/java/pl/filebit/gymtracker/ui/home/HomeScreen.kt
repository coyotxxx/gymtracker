package pl.filebit.gymtracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartCoachWorkout: () -> Unit,
    onStartAdhocWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onSelectPlanTab: () -> Unit,
    onOpenStats: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Bez wewnętrznego Scaffold — outer Scaffold w AppNavigation ma już bottomBar.
    // Tylko statusBarsPadding na góra żeby content nie chował się pod statusbar.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item { GreetingHeader() }

            // Hero card — Plan na dziś LUB Trening w toku LUB CTA "Wybierz plan"
            item {
                when {
                    state.activeWorkout != null -> ActiveTrainingHeroCard(
                        onResume = {
                            vm.continueActiveWorkout(
                                onCoach = onStartCoachWorkout,
                                onAdhoc = onStartAdhocWorkout
                            )
                        }
                    )
                    state.todaysPlan != null -> TodaysPlanHeroCard(
                        planName = state.todaysPlan!!.name,
                        exerciseCount = state.todaysPlanExerciseCount,
                        daysPerWeek = state.todaysPlan!!.daysOfWeek.size,
                        onStart = {
                            val isoDay = Clock.System
                                .todayIn(TimeZone.currentSystemDefault())
                                .dayOfWeek.isoDayNumber
                            vm.startWorkoutFromPlanForDay(
                                state.todaysPlan!!.id, isoDay, onStartCoachWorkout
                            )
                        }
                    )
                    else -> NoPlanHeroCard(onPickPlan = onSelectPlanTab)
                }
            }

            // 2-kolumnowa siatka quick cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        title = "Ad-hoc",
                        subtitle = "Bez planu — dodajesz w trakcie",
                        onClick = { vm.startWorkoutAdhoc(onStartAdhocWorkout) }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.QueryStats,
                        title = "Statystyki",
                        subtitle = "Tydzień · objętość · PR",
                        onClick = onOpenStats
                    )
                }
            }

            // Streak compact card
            item {
                StreakCompactCard(
                    weeks = state.streakWeeks,
                    best = state.streakBest,
                    weekCurrent = state.workoutsThisWeek,
                    weekTarget = state.weeklyTarget
                )
            }

            // Section header
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ostatnie treningi",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                )
            }

            if (state.recentWorkouts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Jeszcze nie było żadnego treningu.\nKliknij przycisk żeby zacząć.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.recentWorkouts, key = { it.workout.id }) { item ->
                    RecentWorkoutCard(item = item, onClick = { onOpenWorkout(item.workout.id) })
                }
            }
    }
}

@Composable
private fun GreetingHeader() {
    val today = remember {
        SimpleDateFormat("EEEE · d MMM", Locale("pl", "PL")).format(Date())
            .replaceFirstChar { it.titlecase(Locale("pl", "PL")) }
    }
    Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            today.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(AccentOrange, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "GymTracker",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
            )
        }
    }
}

/** Hero glow card — Plan na dziś. Gradient żółty + glow. */
@Composable
private fun TodaysPlanHeroCard(
    planName: String,
    exerciseCount: Int,
    daysPerWeek: Int,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.18f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            // Label z pulsującą kropką
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "PLAN NA DZIŚ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                planName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${exerciseCount} ćwiczeń · ${daysPerWeek}× / tydz.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
            // Separator
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Spacer(Modifier.height(14.dp))
            // Meta row
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MetaItem("$exerciseCount", "Ćwiczenia")
                MetaItem("$daysPerWeek", "Dni / tydz.")
                MetaItem("~${exerciseCount * 10}", "Czas", smallSuffix = "min")
            }
            Spacer(Modifier.height(14.dp))
            HeroPrimaryButton(
                text = "Rozpocznij trening",
                icon = Icons.Default.PlayArrow,
                onClick = onStart
            )
        }
    }
}

@Composable
private fun ActiveTrainingHeroCard(onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.22f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "TRENING W TOKU",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Aktywny trening",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(14.dp))
            HeroPrimaryButton(
                text = "Wróć do treningu",
                icon = Icons.Default.PlayArrow,
                onClick = onResume
            )
        }
    }
}

@Composable
private fun NoPlanHeroCard(onPickPlan: () -> Unit) {
    Card(
        onClick = onPickPlan,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.12f),
                        1f to DarkSurface
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                "BRAK PLANU NA DZIŚ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Wybierz plan",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Otwórz zakładkę Plany i ustaw harmonogram",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(AccentOrange, CircleShape)
    )
}

@Composable
private fun MetaItem(num: String, label: String, smallSuffix: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                num,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
            )
            if (smallSuffix != null) {
                Text(
                    smallSuffix,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun HeroPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = AccentOrange),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                color = Color.Black
            )
        }
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = DarkOnSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StreakCompactCard(
    weeks: Int,
    best: Int,
    weekCurrent: Int,
    weekTarget: Int
) {
    val percent = if (weekTarget > 0) (weekCurrent * 100 / weekTarget).coerceAtMost(100) else 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ikona flame w żółtym kwadracie
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = AccentOrange.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Środek: liczba tygodni + label
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$weeks",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AccentOrange
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "tyg. z rzędu",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        "Najlepszy: $best tyg.".uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
                // Po prawej: tygodniowy postęp
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$weekCurrent / $weekTarget",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        "TEN TYDZIEŃ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentOrange,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
private fun RecentWorkoutCard(item: RecentWorkoutItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Data
            val dateFmt = remember {
                SimpleDateFormat("EEE · d MMM", Locale("pl", "PL"))
            }
            Text(
                dateFmt.format(Date(item.workout.startedAt)),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(10.dp))
            // 4 stat-y w mono
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                MiniStat(
                    formatDuration(item.workout.durationMillis),
                    "Czas",
                    Modifier.weight(1f)
                )
                MiniStat("${item.exerciseCount}", "Ćwicz.", Modifier.weight(1f))
                MiniStat("${item.totalSets}", "Serie", Modifier.weight(1f))
                MiniStat(
                    formatWeight(item.totalVolumeKg),
                    "Vol.",
                    Modifier.weight(1f),
                    smallSuffix = "kg"
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    smallSuffix: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (smallSuffix != null) {
                Text(
                    smallSuffix,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}
