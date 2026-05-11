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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                Spacer(Modifier.height(8.dp))
                if (status.isNotBlank()) {
                    StatusBox(text = status)
                }
            }
        }
    }
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
