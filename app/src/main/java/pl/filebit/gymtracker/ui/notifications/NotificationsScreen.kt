package pl.filebit.gymtracker.ui.notifications

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ai.AppNotification
import pl.filebit.gymtracker.ai.NotificationAction
import pl.filebit.gymtracker.ai.NotificationSeverity
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onAction: (NotificationAction) -> Unit,
    vm: NotificationsViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    // Auto-mark wszystkich jako przeczytane gdy user wchodzi na ekran (po załadowaniu listy).
    LaunchedEffect(items) {
        if (items.isNotEmpty()) vm.markAllAsRead()
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Powiadomienia", onBack = onBack)

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentOrange)
                }
            }
            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🔔",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Brak powiadomień",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = DarkOnSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Wszystko w normie. Algorytm cię zaalarmuje gdy wykryje trwały trend wymagający uwagi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { notif ->
                        NotificationCard(notif = notif, onClick = { onAction(notif.actionType) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: AppNotification, onClick: () -> Unit) {
    val accent = when (notif.severity) {
        NotificationSeverity.CRITICAL -> ErrorRed
        NotificationSeverity.WARNING -> AccentOrange
        NotificationSeverity.INFO -> SuccessGreen
    }
    val clickable = notif.actionType != NotificationAction.NONE
    Card(
        modifier = Modifier.fillMaxWidth().let {
            if (clickable) it.clickable(onClick = onClick) else it
        },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                notif.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
            Spacer(Modifier.height(6.dp))
            Text(
                notif.message,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            if (clickable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    actionLabel(notif.actionType),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = accent
                )
            }
        }
    }
}

private fun actionLabel(action: NotificationAction): String = when (action) {
    NotificationAction.APPLY_DELOAD -> "Zastosuj deload →"
    NotificationAction.AUDIT_PLAN -> "Audyt planu →"
    NotificationAction.ADD_WEIGHT -> "Dodaj pomiar wagi →"
    NotificationAction.SEND_HEALTH_SCREEN -> "Wyślij zrzut z zegarka →"
    NotificationAction.START_WORKOUT -> "Rozpocznij trening →"
    NotificationAction.NONE -> ""
}
