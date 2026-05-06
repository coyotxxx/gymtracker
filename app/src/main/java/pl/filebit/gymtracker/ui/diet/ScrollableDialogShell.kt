package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface

/**
 * Uniwersalny dialog z gwarantowanym scrollem i sticky akcjami.
 *
 * Struktura:
 *  - Dialog (overlay full-screen)
 *  - Card 92% wysokości ekranu
 *  - Header sticky (tytuł + opcjonalna ikona/akcja)
 *  - Body scrollowalne (weight 1f + verticalScroll)
 *  - Actions sticky na dole
 *
 * Pattern używany w QuickComposeDialog/AddMealDialog/GeneratePlanPreferencesDialog —
 * sprawdzony, działa na każdym ekranie.
 *
 * Użycie:
 * ```
 * ScrollableDialogShell(
 *     title = "Tytuł",
 *     onDismiss = { ... },
 *     actions = {
 *         TextButton(onClick = onDismiss) { Text("Anuluj") }
 *         TextButton(onClick = onSave) { Text("Zapisz") }
 *     }
 * ) {
 *     // scrollowalna treść
 *     Text("...")
 *     ...
 * }
 * ```
 */
@Composable
fun ScrollableDialogShell(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    headerSubtitle: String? = null,
    headerExtra: @Composable (() -> Unit)? = null,
    bodyArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    body: @Composable () -> Unit
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
                // Header sticky
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    headerExtra?.invoke()
                }
                if (headerSubtitle != null) {
                    Text(
                        headerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Body scrollowalne
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = bodyArrangement
                ) {
                    body()
                }

                Spacer(Modifier.height(8.dp))

                // Actions sticky
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
    }
}
