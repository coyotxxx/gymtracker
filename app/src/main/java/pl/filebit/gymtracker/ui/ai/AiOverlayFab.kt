package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.filebit.gymtracker.ui.theme.AccentOrange

/**
 * Mały pływający FAB asystenta AI — 40dp, żółty gradient z glow ringiem
 * i pulse pierścieniem.
 * Pozycjonowany przez AppNavigation w prawym górnym rogu pod statusbarem.
 */
@Composable
fun AiOverlayFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow ring (lekki obrys żółty)
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(
                    width = 1.dp,
                    color = AccentOrange.copy(alpha = 0.30f),
                    shape = CircleShape
                )
        )
        // Główny przycisk
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    brush = Brush.linearGradient(
                        0f to Color(0xFFFFC107),
                        1f to Color(0xFFFFB300)
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Asystent AI",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
