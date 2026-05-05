package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ai.AiMealRecipe
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Dialog pokazujący PEŁEN przepis: nazwa + składniki + makro + instrukcje krok po kroku.
 * Otwierany z MealGroupCard (przycisk "📝 Przepis").
 */
@Composable
fun RecipeDialog(
    recipe: AiMealRecipe,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Makro / czas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "${recipe.kcal} kcal · B${recipe.proteinG} W${recipe.carbsG} T${recipe.fatG}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                            ),
                            color = AccentOrange
                        )
                        Text(
                            "⏱ ${recipe.prepMinutes} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Składniki
                Text(
                    "SKŁADNIKI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        recipe.ingredients.forEach { ing ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentOrange,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    ing.productName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkOnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${ing.grams} g",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Instrukcja
                Text(
                    "JAK PRZYRZĄDZIĆ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                if (recipe.instructions.isBlank()) {
                    Text(
                        "Brak szczegółowej instrukcji dla tego dania. Standardowy sposób: " +
                            "ugotuj/usmaż białko, przygotuj węgle, dodaj warzywa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                } else {
                    Text(
                        recipe.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        }
    )
}
