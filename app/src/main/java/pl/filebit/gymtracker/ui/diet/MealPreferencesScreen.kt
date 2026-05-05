package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen

@Composable
fun MealPreferencesScreen(
    onBack: () -> Unit,
    vm: MealPreferencesViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites = items.filter { it.rating >= 4 }
    val disliked = items.filter { it.rating <= 2 }
    val others = items.filter { it.rating == 3 }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Preferencje smakowe", onBack = onBack)

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Brak ocen jeszcze.\n\nGdy AI wygeneruje plan dnia, zobaczysz dialog z prośbą o ocenę. Twoje oceny ulepszą kolejne plany.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (favorites.isNotEmpty()) {
                        item { SectionHeader("ULUBIONE (★4-5)", SuccessGreen) }
                        items(favorites.size) { idx ->
                            FeedbackRow(favorites[idx]) { vm.delete(it) }
                        }
                    }
                    if (disliked.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { SectionHeader("UNIKAM (★1-2)", AccentOrange) }
                        items(disliked.size) { idx ->
                            FeedbackRow(disliked[idx]) { vm.delete(it) }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { SectionHeader("NEUTRALNE (★3)", DarkOnSurfaceVariant) }
                        items(others.size) { idx ->
                            FeedbackRow(others[idx]) { vm.delete(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        ),
        color = color,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun FeedbackRow(fb: MealFeedback, onDelete: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fb.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Text(
                    "★".repeat(fb.rating) + "☆".repeat(5 - fb.rating) + " · zjedzono ${fb.timesEaten}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange
                )
                if (fb.tags.isNotBlank() || fb.notes.isNotBlank()) {
                    Text(
                        listOfNotNull(fb.tags.takeIf { it.isNotBlank() }, fb.notes.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { onDelete(fb.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Usuń",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}
