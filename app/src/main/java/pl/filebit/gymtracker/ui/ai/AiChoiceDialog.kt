package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.common.ActionSheetRow
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant

/**
 * Bottom-sheet wyboru akcji po kliknięciu FAB AI:
 * - Otwórz pełnego asystenta (rozmowa z historią)
 * - Zapytaj o ten ekran (krótki popup z kontekstem aktualnego ekranu)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChoiceDialog(
    screenLabel: String,
    onDismiss: () -> Unit,
    onOpenAssistant: () -> Unit,
    onAskAboutScreen: () -> Unit,
    onSendHealthScreenshot: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Asystent AI",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Wybierz, jak chcesz porozmawiać z trenerem",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            ActionSheetRow(
                icon = Icons.Default.AutoAwesome,
                label = "Otwórz Asystenta",
                subtitle = "Pełna rozmowa z historią — plany, analizy, propozycje",
                onClick = onOpenAssistant
            )
            ActionSheetRow(
                icon = Icons.Default.QuestionAnswer,
                label = "Zapytaj o ten ekran",
                subtitle = screenLabel,
                onClick = onAskAboutScreen
            )
            ActionSheetRow(
                icon = Icons.Default.PhotoLibrary,
                label = "Wyślij screen z zegarka",
                subtitle = "Sen, waga, HRV, stres — AI uzupełni dane (Huawei/Mi/Garmin/Samsung)",
                onClick = onSendHealthScreenshot
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Anuluj", color = AccentOrange)
                }
            }
            Spacer(Modifier.height(8.dp))
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
