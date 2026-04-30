package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mały pływający FAB asystenta AI.
 *
 * - Rozmiar 48dp (mniejszy niż standard 56dp żeby nie zasłaniał treści)
 * - Kolor tertiaryContainer — subtle, nie konkuruje z głównymi CTA
 * - Domyślnie nad bottom nav w prawym dolnym rogu
 */
@Composable
fun AiOverlayFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = "Asystent AI",
            modifier = Modifier.size(22.dp)
        )
    }
}
