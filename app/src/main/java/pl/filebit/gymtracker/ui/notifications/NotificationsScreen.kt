package pl.filebit.gymtracker.ui.notifications

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    vm: NotificationsViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    // Wejście na ekran = wszystko przeczytane (licznik dzwonka gaśnie).
    LaunchedEffect(Unit) { vm.markSeen() }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Powiadomienia", onBack = onBack)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
            items.isEmpty() -> EmptyState()
            else -> {
                // Grupowanie po dniu: DZIŚ / WCZORAJ / data.
                val grouped = items.groupBy { dayBucket(it.timeMs) }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    grouped.forEach { (header, group) ->
                        item(key = "hdr_$header") { SectionHeader(header) }
                        items(group.size) { idx ->
                            NotificationRow(group[idx])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = DarkOnSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 2.dp)
    )
}

@Composable
private fun NotificationRow(item: NotifHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = if (item.unread) 0.45f else 0.18f)),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            if (item.unread) {
                Box(
                    Modifier.padding(top = 5.dp, end = 10.dp).size(7.dp)
                        .background(AccentOrange, CircleShape)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        timeLabel(item.timeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                if (item.body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔔", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Brak powiadomień",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tu wyląduje historia powiadomień, które apka Ci wyśle — nawet jeśli zamkniesz je z paska.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

private fun timeLabel(ms: Long): String = timeFmt.format(Date(ms))

private fun dayBucket(ms: Long): String {
    val startToday = startOfDay(System.currentTimeMillis())
    val msPerDay = 24L * 3600 * 1000
    return when {
        ms >= startToday -> "DZIŚ"
        ms >= startToday - msPerDay -> "WCZORAJ"
        else -> dateFmt.format(Date(ms)).uppercase(Locale.getDefault())
    }
}

private fun startOfDay(ms: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
