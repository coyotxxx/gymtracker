package pl.filebit.gymtracker.ui.ai

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.AiLog
import pl.filebit.gymtracker.ui.diet.ScrollableDialogShell
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.ui.theme.ErrorRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista logów AI — wszystkie pytania i odpowiedzi (gdy logging włączony).
 * Toggle on/off + clear all + szczegóły w dialogu.
 */
@Composable
fun AiLogScreen(
    onBack: () -> Unit,
    vm: AiLogViewModel = hiltViewModel()
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val loggingEnabled by vm.loggingEnabled.collectAsStateWithLifecycle()
    val selected by vm.selectedLog.collectAsStateWithLifecycle()

    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz", tint = DarkOnSurface)
            }
            Text(
                "Logi AI",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            if (logs.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Wyczyść", tint = ErrorRed)
                }
            }
        }

        // Toggle + opis
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutlineSoft),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = loggingEnabled,
                        onCheckedChange = { vm.setLoggingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (loggingEnabled) "✓ Logowanie WŁĄCZONE" else "✗ Logowanie WYŁĄCZONE",
                            color = if (loggingEnabled) SuccessGreen else DarkOnSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Zapisuje pełen prompt i odpowiedź każdego wywołania AI. Lokalnie, nie wysyła nigdzie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
                if (logs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Logów: ${logs.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Lista
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loggingEnabled) "Brak logów. Wykonaj jakieś działanie AI (np. wygeneruj plan diety)."
                    else "Logowanie wyłączone — włącz powyżej żeby zacząć zbierać logi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogRow(log = log, onClick = { vm.selectLog(log) })
                }
            }
        }
    }

    // Detail dialog
    selected?.let { log ->
        AiLogDetailDialog(log = log, onDismiss = { vm.selectLog(null) })
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Wyczyścić wszystkie logi?", fontWeight = FontWeight.Bold) },
            text = { Text("Usunie ${logs.size} logów. Tej akcji nie da się cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    showClearConfirm = false
                }) {
                    Text("Wyczyść", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun LogRow(log: AiLog, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            1.dp,
            if (log.success) DarkOutlineSoft else ErrorRed.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (log.success) "✓" else "✗",
                    color = if (log.success) SuccessGreen else ErrorRed,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    log.service,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatTime(log.createdAt),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${log.provider} · ${log.model} · ${log.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Q: ${log.fullPrompt.take(120).replace("\n", " ")}…",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = DarkOnSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            log.errorMessage?.let { err ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "ERR: $err",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ErrorRed,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun AiLogDetailDialog(log: AiLog, onDismiss: () -> Unit) {
    ScrollableDialogShell(
        title = "${log.service} · ${formatTime(log.createdAt)}",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // Meta info
        Column(modifier = Modifier.fillMaxWidth().background(AccentOrange.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(10.dp)) {
            InfoLine("Status:", if (log.success) "✓ Sukces" else "✗ Błąd")
            InfoLine("Service:", log.service)
            InfoLine("Provider:", log.provider)
            InfoLine("Model:", log.model)
            InfoLine("Czas:", "${log.durationMs}ms")
            InfoLine("Data:", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt)))
            log.errorMessage?.let { InfoLine("Błąd:", it) }
        }

        Spacer(Modifier.height(12.dp))

        // Prompt
        SectionLabel("PROMPT (${log.fullPrompt.length} znaków)")
        Text(
            log.fullPrompt,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 10.sp
            ),
            color = DarkOnSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(6.dp))
                .padding(8.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Response
        SectionLabel("RESPONSE (${log.fullResponse.length} znaków)")
        Text(
            log.fullResponse.ifBlank { "(brak)" },
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 10.sp
            ),
            color = if (log.success) DarkOnSurface else ErrorRed,
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(6.dp))
                .padding(8.dp)
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontSize = 10.sp
        ),
        color = AccentOrange
    )
    Spacer(Modifier.height(4.dp))
}

private fun formatTime(ms: Long): String {
    val sdf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ms))
}
