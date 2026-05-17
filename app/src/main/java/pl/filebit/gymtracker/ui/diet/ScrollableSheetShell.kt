package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant

/**
 * Bliźniak [ScrollableDialogShell] renderowany jako rozwijana karta od spodu
 * (Material3 ModalBottomSheet) — ten sam wzorzec co "Tydzień" (WeekPlanSheet).
 *
 * IDENTYCZNE API jak ScrollableDialogShell — wystarczy podmienić nazwę w wywołaniu.
 * Cała zawartość (tytuł + treść + akcje) scrolluje się razem; arkusz dopasowuje
 * wysokość do treści (mały dla krótkiej, prawie pełny dla długiej).
 *
 * Użycie:
 * ```
 * ScrollableSheetShell(
 *     title = "Tytuł",
 *     onDismiss = { ... },
 *     actions = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
 * ) { /* treść */ }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableSheetShell(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    headerSubtitle: String? = null,
    headerExtra: @Composable (() -> Unit)? = null,
    bodyArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    body: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
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
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            // Body
            Column(verticalArrangement = bodyArrangement) {
                body()
            }

            Spacer(Modifier.height(12.dp))

            // Actions
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
