package pl.filebit.gymtracker.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Karta aktualizacji do umieszczenia na ekranie Profilu (lub Backup).
 * Auto-sprawdza GitHub releases przy wejściu i pokazuje stan akcji.
 *
 * Jeśli nie ma nowej wersji — zwraca pustą Box (nic nie pokazuje), żeby
 * nie zaśmiecać ekranu. Sprawdzanie ręczne dostępne pod 'Refresh' kafelek.
 */
@Composable
fun UpdateCard(
    modifier: Modifier = Modifier,
    showWhenUpToDate: Boolean = false,
    vm: UpdateViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state is UpdateUiState.Idle) vm.checkForUpdate(silent = true)
    }

    when (val s = state) {
        UpdateUiState.Idle -> IdleCheckCard(modifier, vm.currentVersion) {
            vm.checkForUpdate(silent = false)
        }
        UpdateUiState.Checking -> CheckingCard(modifier, vm.currentVersion)
        UpdateUiState.UpToDate -> UpToDateCard(modifier, vm.currentVersion) {
            vm.checkForUpdate(silent = false)
        }
        is UpdateUiState.Available -> AvailableCard(
            modifier, vm.currentVersion, s.info, onUpdate = { vm.downloadAndInstall(s.info) }
        )
        is UpdateUiState.Downloading -> DownloadingCard(modifier, s.info, s.percent)
        is UpdateUiState.ReadyToInstall -> ReadyCard(modifier, s.info) { vm.installAgain(s.file) }
        is UpdateUiState.Failed -> FailedCard(modifier, s.message) { vm.checkForUpdate() }
    }
}

@Composable
private fun IdleCheckCard(modifier: Modifier, version: String, onCheck: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wersja: $version", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCheck) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Sprawdź aktualizacje")
            }
        }
    }
}

@Composable
private fun CheckingCard(modifier: Modifier, version: String) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.padding(6.dp))
            Text("Sprawdzanie aktualizacji… (v$version)",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UpToDateCard(modifier: Modifier, version: String, onRefresh: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Masz najnowszą wersję", fontWeight = FontWeight.SemiBold)
                Text("v$version", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AvailableCard(
    modifier: Modifier,
    currentVersion: String,
    info: pl.filebit.gymtracker.data.repository.UpdateInfo,
    onUpdate: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "✨ Dostępna nowa wersja",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "v$currentVersion → v${info.versionName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    info.notes.lines().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    maxLines = 6
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Aktualizuj teraz")
            }
        }
    }
}

@Composable
private fun DownloadingCard(
    modifier: Modifier,
    info: pl.filebit.gymtracker.data.repository.UpdateInfo,
    percent: Int
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Pobieranie v${info.versionName}…",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ReadyCard(
    modifier: Modifier,
    info: pl.filebit.gymtracker.data.repository.UpdateInfo,
    onInstallAgain: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "v${info.versionName} pobrana — zainstaluj",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "System Android otworzy instalator. Jeśli pyta o pozwolenie na 'instalację z nieznanych źródeł' — zezwól (raz).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onInstallAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Otwórz instalator ponownie")
            }
        }
    }
}

@Composable
private fun FailedCard(modifier: Modifier, message: String, onRetry: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Błąd aktualizacji",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
            }
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}
