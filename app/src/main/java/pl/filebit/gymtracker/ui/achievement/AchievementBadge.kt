package pl.filebit.gymtracker.ui.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange

/**
 * Medal MINIMAL — v1.11.17. ABSOLUTNIE NIC poza:
 * - Box clip Circle background SolidColor (AccentOrange)
 * - Emoji Text w środku
 *
 * Brak gradient, brak border, brak drawBehind, brak graphicsLayer,
 * brak animacji, brak nic. Jeśli to też pokazuje "podwójne koło" —
 * problem jest w czymś OUTSIDE komponentu (modal scrim, parent layer).
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SolidColor(AccentOrange), CircleShape)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = TextStyle(
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** Stub — kompatybilność. */
object BadgeDiagnostics {
    var recomposeCount: Int = 0
    fun reset() { recomposeCount = 0 }
}
