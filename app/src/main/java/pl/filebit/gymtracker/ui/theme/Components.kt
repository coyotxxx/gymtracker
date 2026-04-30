package pl.filebit.gymtracker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wspólne komponenty stylu kanonicznego (Coach Workout).
 * Czarne tło, miękkie radiusy 18-20dp, akcent żółty z glow.
 */

private val CardRadius = 18.dp
private val CardRadiusLarge = 20.dp
private val ButtonRadius = 14.dp
private val ChipRadius = 999.dp

/** Standardowa karta — surface, radius 18, lekka outline 6% white. */
@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val baseModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = baseModifier,
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutlineSoft),
            shape = RoundedCornerShape(CardRadius)
        ) { content() }
    } else {
        Card(
            modifier = baseModifier,
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutlineSoft),
            shape = RoundedCornerShape(CardRadius)
        ) { content() }
    }
}

/** Akcentowana karta z żółtym glow — używana dla CTA / aktualnej serii w Coach mode. */
@Composable
fun GymGlowCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(CardRadiusLarge)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to AccentOrange.copy(alpha = 0.12f),
                        1f to DarkSurface
                    )
                )
        ) { content() }
    }
}

/** Etykieta sekcji UPPERCASE z trackingiem. Akcent gdy `accent = true`. */
@Composable
fun LabelUp(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = if (accent) AccentOrange else DarkOnSurfaceVariant
    )
}

/** Główne CTA — żółty button z glow shadow, czarny bold tekst. */
@Composable
fun GymPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val height = if (compact) 48.dp else 56.dp
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) AccentOrange else DarkSurface3,
            disabledContainerColor = DarkSurface3
        ),
        shape = RoundedCornerShape(ButtonRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) Color.Black else DarkOnSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 15.sp else 16.sp
                ),
                color = if (enabled) Color.Black else DarkOnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Klikalny chip — np. filtr częstotliwości, kategoria. */
@Composable
fun GymChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AccentOrange.copy(alpha = 0.12f) else DarkSurfaceVariant
    val fg = if (selected) AccentOrange else DarkOnSurfaceVariant
    val borderColor = if (selected) AccentOrange.copy(alpha = 0.35f) else Color.Transparent
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(ChipRadius))
            .border(1.dp, borderColor, RoundedCornerShape(ChipRadius))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            ),
            color = fg
        )
    }
}

/** Wielka liczba mono ExtraBold w żółci + sufiks (np. "kg") mniejszy. */
@Composable
fun MonoBigValue(
    value: String,
    suffix: String? = null,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 44.sp,
    suffixSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    valueColor: Color = AccentOrange,
    suffixColor: Color = AccentOrange.copy(alpha = 0.85f)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = valueSize,
                letterSpacing = 0.5.sp
            ),
            color = valueColor
        )
        if (suffix != null) {
            Text(
                suffix,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = suffixSize
                ),
                color = suffixColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
    }
}

/** Hero blok ze środkiem wycentrowanym: label UPPERCASE + duża wartość mono. */
@Composable
fun HeroValueCard(
    label: String,
    value: String,
    suffix: String? = null,
    modifier: Modifier = Modifier
) {
    GymCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LabelUp(label, accent = true)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            MonoBigValue(value = value, suffix = suffix, valueSize = 56.sp, suffixSize = 18.sp)
        }
    }
}

/** Sekcja z nagłówkiem ('h2' w stylu) + opcjonalny link 'więcej →' po prawej. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    moreText: String? = null,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = DarkOnSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(modifier = Modifier.padding(0.dp).fillMaxWidth()) {
            if (moreText != null && onMoreClick != null) {
                Text(
                    moreText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AccentOrange,
                    modifier = Modifier
                        .clickable(onClick = onMoreClick)
                        .padding(8.dp)
                        .align(Alignment.CenterEnd)
                )
            }
        }
    }
}
