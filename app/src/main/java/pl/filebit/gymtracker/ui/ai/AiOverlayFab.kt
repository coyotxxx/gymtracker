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
 * - Rozmiar 40dp (mieści się w obszarze TopAppBar)
 * - Kolor tertiaryContainer z półprzezroczystym tłem — nie konkuruje
 *   z głównymi akcjami w TopAppBar
 * - Domyślnie w prawym górnym rogu, pod statusBar
 */
@Composable
fun AiOverlayFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = "Asystent AI",
            modifier = Modifier.size(20.dp)
        )
    }
}
