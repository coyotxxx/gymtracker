package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog wyboru akcji po kliknięciu FAB AI:
 * - Otwórz pełnego asystenta (rozmowa z historią)
 * - Zapytaj o ten ekran (krótki popup z kontekstem aktualnego ekranu)
 */
@Composable
fun AiChoiceDialog(
    screenLabel: String,
    onDismiss: () -> Unit,
    onOpenAssistant: () -> Unit,
    onAskAboutScreen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asystent AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "Otwórz Asystenta",
                    subtitle = "Pełna rozmowa z historią — plany, analizy, propozycje",
                    onClick = onOpenAssistant
                )
                ChoiceCard(
                    icon = Icons.Default.QuestionAnswer,
                    title = "Zapytaj o ten ekran",
                    subtitle = screenLabel,
                    onClick = onAskAboutScreen
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun ChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

/** Mapowanie aktualnej route na czytelną nazwę ekranu — używane jako kontekst dla AI. */
fun String?.screenLabel(): String = when (this) {
    "home" -> "Ekran główny — przegląd"
    "history" -> "Historia treningów"
    "plans" -> "Lista planów treningowych"
    "exercises" -> "Biblioteka ćwiczeń"
    "profile" -> "Profil użytkownika"
    "stats" -> "Statystyki"
    "stats/achievements" -> "Odznaki"
    "tools/1rm" -> "Kalkulator 1RM"
    "tools/plate" -> "Kalkulator obciążeń"
    "tools/body" -> "Pomiary ciała"
    "tools/muscles" -> "Mapa zaangażowania mięśni"
    "tools/photos" -> "Zdjęcia progresu"
    "tools/strength" -> "Standardy siłowe"
    "goals" -> "Cele"
    "glossary" -> "Słowniczek"
    "backup" -> "Kopia zapasowa"
    "workout/active" -> "Aktywny trening (ad-hoc)"
    "workout/coach" -> "Aktywny trening (z planu)"
    "plans/{planId}" -> "Edycja planu"
    "plans/templates" -> "Szablony planów"
    "exercises/{exerciseId}" -> "Szczegóły ćwiczenia"
    "workout/{workoutId}" -> "Szczegóły treningu"
    else -> "Ekran aplikacji"
}
