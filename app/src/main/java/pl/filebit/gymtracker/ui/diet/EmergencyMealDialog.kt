package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.ui.theme.DarkBg
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
import pl.filebit.gymtracker.ai.EmergencyMode
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Wybór trybu awaryjnego posiłku — 5 buttonów + opcjonalnie DamageControl entry.
 */
@Composable
fun EmergencyMealDialog(
    onSelectMode: (EmergencyMode) -> Unit,
    onOpenDamageControl: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "🚨 Awaryjne sytuacje",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Wybierz tryb dla szybkiego posiłku albo zgłoś nieplanowane jedzenie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "GENERUJ POSIŁEK AI:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    ModeButton("⏱ 5 minut", "Tylko gotowe produkty (twaróg/banan/wafle)") {
                        onSelectMode(EmergencyMode.QUICK_5MIN); onDismiss()
                    }
                    ModeButton("🥗 Bez gotowania", "Sałatka, kanapka, owsianka na zimno") {
                        onSelectMode(EmergencyMode.NO_COOKING); onDismiss()
                    }
                    ModeButton("🏢 W pracy", "Przenośny posiłek do pojemnika") {
                        onSelectMode(EmergencyMode.AT_WORK); onDismiss()
                    }
                    ModeButton("🛒 Ze sklepu", "Biedronka/Lidl, bez gotowania") {
                        onSelectMode(EmergencyMode.STORE_SHOP); onDismiss()
                    }
                    ModeButton("🌙 Późna kolacja", "Max 30g węgli, kazeina, lekkie") {
                        onSelectMode(EmergencyMode.LATE_NIGHT); onDismiss()
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ZJADŁEM COŚ NIEPLANOWANEGO:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    ModeButton("📊 Przelicz dzień (kebab/pizza/itp.)", "Aplikacja przeliczy pozostałe sloty") {
                        onOpenDamageControl(); onDismiss()
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}
