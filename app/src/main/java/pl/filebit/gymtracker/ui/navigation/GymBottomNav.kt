package pl.filebit.gymtracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface

/**
 * Custom bottom navigation zgodny z propozycją Claude Design:
 * - Wysokość 80dp, tło surface, top border 4% white
 * - Item aktywny: subtle pill 36x28dp z 10% akcentem za ikoną + żółta kropka
 *   4x4 z glow pod ikoną. Ikona i label żółte (AccentOrange).
 * - Item nieaktywny: ikona i label szare (OnSurfaceVariant).
 * - Label: 10sp SemiBold (Material3 NavigationBarItem ma większy)
 */
@Composable
internal fun GymBottomNav(
    currentRoute: String?,
    tabs: List<TabItem>,
    onTabClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(DarkSurface)
            .drawBehind {
                // Top border 4% white (1px)
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.screen.route
                NavItem(
                    icon = tab.icon,
                    label = stringResource(tab.labelRes),
                    selected = selected,
                    onClick = { onTabClick(tab.screen.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Box z pill-em za ikoną + dot pod ikoną gdy aktywne
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                // Mały pill 36x28dp z 10% akcentem (subtelne tło)
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 28.dp)
                        .background(
                            AccentOrange.copy(alpha = 0.10f),
                            RoundedCornerShape(14.dp)
                        )
                )
            }
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) AccentOrange else DarkOnSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        // Dot indicator (4dp) pod ikoną gdy aktywne, w innym przypadku spacer 4dp
        if (selected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(AccentOrange, CircleShape)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (selected) AccentOrange else DarkOnSurfaceVariant
        )
    }
}
