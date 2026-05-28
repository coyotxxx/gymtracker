package pl.filebit.gymtracker.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    vm: DebugViewModel = hiltViewModel()
) {
    val status by vm.status.collectAsStateWithLifecycle()
    val dbStateDialog by vm.dbStateDialog.collectAsStateWithLifecycle()
    var showWipeConfirm by remember { mutableStateOf(false) }

    // v2.7.1: wynik "Stan bazy" / "Wyczyść" w popupie — widoczny od razu.
    if (dbStateDialog != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissDbStateDialog() },
            confirmButton = {
                TextButton(onClick = { vm.dismissDbStateDialog() }) { Text("Zamknij") }
            },
            title = { Text("Baza danych") },
            text = { Text(dbStateDialog ?: "") },
            containerColor = DarkSurface,
            titleContentColor = DarkOnSurface,
            textContentColor = DarkOnSurface
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    vm.wipeWorkoutHistory()
                }) { Text("Wyczyść", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Anuluj") }
            },
            title = { Text("Wyczyścić historię treningów?") },
            text = {
                Text(
                    "Usunie WSZYSTKIE treningi i serie (oraz pochodne eventy/mezocykle). " +
                        "Ćwiczenia, plany, profil i dieta zostaną. Operacja nieodwracalna."
                )
            },
            containerColor = DarkSurface,
            titleContentColor = DarkOnSurface,
            textContentColor = DarkOnSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug — diagnostyka", color = DarkOnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = DarkOnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { InfoCard() }
            item {
                DebugAction(
                    title = "Stan bazy aplikacji",
                    subtitle = "Ile treningów/serii realnie jest w bazie + od kiedy. Sprawdź czy 'zaczynamy od nowa'.",
                    onClick = vm::showDbState
                )
            }
            item {
                DebugAction(
                    title = "Wyczyść historię treningów",
                    subtitle = "Usuwa wszystkie treningi i serie (od nowa). Ćwiczenia, plany, profil i dieta zostają.",
                    onClick = { showWipeConfirm = true }
                )
            }
            // v1.24.41: Importuj na samej górze — emulator ma broken scrolling przy LazyColumn,
            // a Import to najczęstsza akcja przy debugowaniu scenariuszy E2E.
            item {
                DebugAction(
                    title = "Importuj 'import.json'",
                    subtitle = "1-klik — szuka pliku w /sdcard/Android/data/.../files/Download/ (app-scope, bez permission) albo /sdcard/Download/ (MediaStore). Wystarczy adb push.",
                    onClick = vm::importFromDownloads
                )
            }
            item {
                DebugAction(
                    title = "Eksportuj JSON → Downloads",
                    subtitle = "Tworzy /Downloads/GymTracker/gymtracker-debug-*.json z wszystkimi diagnostykami",
                    onClick = vm::exportJsonToDownloads
                )
            }
            item {
                DebugAction(
                    title = "Kopiuj JSON do schowka",
                    subtitle = "Wklej w Telegramie/mailu do dewelopera",
                    onClick = vm::copyJsonToClipboard
                )
            }
            item {
                DebugAction(
                    title = "Eksportuj pełną bazę SQL",
                    subtitle = "Plik .db do otwarcia w SQLite browser",
                    onClick = vm::exportDbToDownloads
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Test deload guard (v1.22.0)")
            }
            item {
                DebugAction(
                    title = "1. Setup plan testowy",
                    subtitle = "Tworzy plan DEBUG_DELOAD_TEST z 3 setami × 80kg",
                    onClick = vm::setupDeloadTestPlan
                )
            }
            item {
                DebugAction(
                    title = "2. Apply deload HIGH (factor 0.8)",
                    subtitle = "Klikaj kilka razy — guard powinien zwracać alreadyActive=true od 2. wywołania",
                    onClick = vm::applyTestDeload
                )
            }
            item {
                DebugAction(
                    title = "3. Reset (cancel deload + del plan)",
                    subtitle = "Po teście — przywróć stan początkowy",
                    onClick = vm::resetDeloadTest
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                if (status.isNotBlank()) {
                    StatusBox(text = status)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = AccentOrange,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "⚠ Ekran developerski",
                color = AccentOrange,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Wykonaj eksport gdy widzisz dziwne wagi w planie lub błędne stany. Wyślij plik deweloperowi — pomoże szybciej zdiagnozować problem.",
                color = DarkOnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DebugAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = DarkOnSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DarkOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Wykonaj") }
        }
    }
}

@Composable
private fun StatusBox(text: String) {
    val isError = text.startsWith("✗")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isError) ErrorRed.copy(alpha = 0.15f) else DarkSurface)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text, color = if (isError) ErrorRed else DarkOnSurface)
        }
    }
}
