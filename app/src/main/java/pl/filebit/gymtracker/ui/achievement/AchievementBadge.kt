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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim

/**
 * Medal v1.11.19 — STATYCZNY, BEZ animacji.
 *
 * Po szczegółowej analizie linia-po-linia usunięte 3 podejrzane elementy
 * powodujące "drugie koło":
 * - .border() biały — dodawał JASNY pierścień wokół tarczy
 * - .drawBehind { drawCircle } — dodawał drugi pierścień WEWNĄTRZ tarczy
 * - Text shadow blur 8px — wyciekał poza emoji jako halo
 *
 * Plus usunięte WSZYSTKIE animacje (Spring overshoot, keyframes 1.15, rotation)
 * → niezawodne 60fps.
 *
 * Medal ma TYLKO:
 * - clip Circle
 * - background gradient (tarcza)
 * - emoji Text bez shadow, bez animacji
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val discBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFFFE082),
                0.30f to AccentOrange,
                0.80f to AccentOrange,
                1.0f to AccentOrangeDim
            )
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(discBrush)
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
