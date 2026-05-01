package pl.filebit.gymtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.util.formatTimerSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerSheet(
    visible: Boolean,
    remainingSec: Int,
    totalSec: Int,
    paused: Boolean = false,
    exerciseName: String? = null,
    onAdd: () -> Unit,
    onSub: () -> Unit,
    onTogglePause: () -> Unit = {},
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val progress = if (totalSec > 0) remainingSec.toFloat() / totalSec.toFloat() else 0f
    // Kolor — czerwony w ostatnich 10s, żółty inaczej
    val ringColor = if (remainingSec in 1..10) ErrorRed else AccentOrange

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: "PRZERWA · {ćwiczenie}" + X
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = if (exerciseName != null) "PRZERWA · ${exerciseName.uppercase()}"
                else "PRZERWA"
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = DarkOnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Circular timer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressRing(
                    progress = progress,
                    color = ringColor,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatTimerSeconds(remainingSec),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = DarkOnSurface,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "z ${formatTimerSeconds(totalSec)} · zostało ${formatTimerSeconds(remainingSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // -15 / Pauza / +15
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimerActionButton(
                    label = "−15\nsekund",
                    onClick = onSub,
                    modifier = Modifier.weight(1f)
                )
                TimerActionButton(
                    icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (paused) "Wznów" else "Pauza",
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f)
                )
                TimerActionButton(
                    label = "+15\nsekund",
                    onClick = onAdd,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // CTA "Pomiń przerwę i zacznij serię"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(AccentOrange, RoundedCornerShape(16.dp))
                    .clickable(onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pomiń przerwę i zacznij serię",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = Color.Black
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DarkOnSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurfaceVariant
                )
            }
        } else {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = DarkOnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val track = DarkSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = 14f
        val pad = stroke / 2 + 4f
        val side = minOf(size.width, size.height) - 2 * pad
        val topLeft = Offset(
            (size.width - side) / 2f,
            (size.height - side) / 2f
        )
        val arcSize = Size(side, side)
        // Track (cienki ciemny okrąg)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Postęp (zostało) — od góry zegarowo (clockwise)
        if (progress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
