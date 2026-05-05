package pl.filebit.gymtracker.ui.diet

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Dialog oceny wygenerowanych potraw — pokazywany po `generateAiDayPlan` z sukcesem.
 * User klika ★ (1-5) per posiłek. "Pomiń" zamyka bez zapisu.
 */
@Composable
fun MealRatingDialog(
    items: List<Pair<MealType, String>>,
    onRate: (displayName: String, rating: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    // Per-item rating state (w pamięci dialogu)
    val ratings = remember { mutableStateMapOf<String, Int>() }
    var savedCount by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Oceń wygenerowany plan",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "AI wygenerował te potrawy. Oceń (1-5★) — pomoże generować lepiej kolejnym razem. Pomiń jeśli nie chcesz oceniać.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                items.forEach { (type, name) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                mealTypeShortLabel(type),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp
                                ),
                                color = AccentOrange
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = DarkOnSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            StarRow(
                                current = ratings[name] ?: 0,
                                onSelect = { star -> ratings[name] = star }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ratings.forEach { (name, rating) ->
                    if (rating in 1..5) onRate(name, rating)
                }
                savedCount = ratings.count { it.value in 1..5 }
                onDismiss()
            }) {
                Text("Zapisz oceny", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Pomiń", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun StarRow(current: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(28.dp)
                    .clickable { onSelect(star) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (star <= current) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (star <= current) AccentOrange else DarkOnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when (current) {
                0 -> ""
                1 -> "Nie lubię"
                2 -> "Średnio"
                3 -> "OK"
                4 -> "Smaczne"
                5 -> "Ulubione"
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant
        )
    }
}

private fun mealTypeShortLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "ŚNIADANIE"
    MealType.LUNCH -> "OBIAD"
    MealType.DINNER -> "KOLACJA"
    MealType.SNACK -> "PRZEKĄSKA"
}
