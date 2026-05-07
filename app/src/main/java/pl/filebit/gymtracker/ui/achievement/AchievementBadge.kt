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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange

/**
 * Medal v1.11.20 — JEDNOLITY pomarańcz, ZERO gradient.
 *
 * ZNALEZIONA prawdziwa przyczyna "drugiego koła":
 * Brush.radialGradient z plateau (30-80% jednakowy kolor + 80-100%
 * przejście do ciemniejszego) tworzy WIDOCZNĄ KRAWĘDŹ między plateau
 * a ciemnym edge → wygląda jak DWA koncentryczne koła.
 *
 * Fix: SolidColor AccentOrange. Jednolity kolor = jedno koło. Koniec.
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AccentOrange)
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
