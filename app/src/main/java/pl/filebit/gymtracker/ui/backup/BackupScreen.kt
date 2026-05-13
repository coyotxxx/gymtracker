package pl.filebit.gymtracker.ui.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.util.formatDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    vm: BackupViewModel = hiltViewModel()
) {
    val status by vm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showWipeDialog by remember { mutableStateOf(false) }
    // v1.24.36: replaceMode w Backup screen — fix Bug #5+#7 (raport SYM 3tyg).
    // Gdy on → przed importem db.clearAllTables(), żadnej kumulacji danych.
    var replaceMode by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            vm.clearStatus()
        }
    }

    var includeApiKey by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.export(it, includeApiKey) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.import(it, replaceMode = replaceMode) } }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(it) } }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                pl.filebit.gymtracker.ui.theme.ScreenHeader(
                    title = stringResource(R.string.backup_section),
                    onBack = onBack
                )
            }
            // === Eksport danych ===
            item {
                SectionCard(
                    title = "Eksport danych",
                    subtitle = "Pełny pakiet ZIP: dane + zdjęcia progresu."
                ) {
                    // Toggle: dołącz klucz API AI
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Switch(
                            checked = includeApiKey,
                            onCheckedChange = { includeApiKey = it }
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dołącz klucz API AI",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = pl.filebit.gymtracker.ui.theme.DarkOnSurface
                            )
                            Text(
                                "Klucz w pliku jako plain text — uważaj komu udostępniasz backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed
                            )
                        }
                    }
                    OutlinedActionButton(
                        text = "Eksportuj kopię (ZIP)",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val name = "gymtracker-backup-${
                                formatDate(System.currentTimeMillis())
                                    .replace(" ", "_").replace(":", "-")
                            }.zip"
                            exportLauncher.launch(name)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedActionButton(
                        text = stringResource(R.string.backup_export_csv),
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val name = "gymtracker-${
                                formatDate(System.currentTimeMillis())
                                    .replace(" ", "_").replace(":", "-")
                            }.csv"
                            csvLauncher.launch(name)
                        }
                    )
                }
            }

            // === Import danych ===
            item {
                SectionCard(
                    title = "Import danych",
                    subtitle = "Wczytaj plik backupu (ZIP od v0.58 lub starszy JSON)."
                ) {
                    // v1.24.36: Switch "Zastąp dane" — gdy on, import czyści bazę przed
                    // wczytaniem (fix kumulacji z raportu SYM 3tyg).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Zastąp istniejące dane",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (replaceMode) ErrorRed else DarkOnSurface
                            )
                            Text(
                                if (replaceMode) {
                                    "Wszystkie obecne treningi, plany, pomiary zostaną SKASOWANE przed importem."
                                } else {
                                    "OFF: dane z pliku dodadzą się do obecnych (może powodować duplikaty / fałszywe streaki)."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = replaceMode,
                            onCheckedChange = { replaceMode = it }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedActionButton(
                        text = stringResource(R.string.backup_import),
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            // Akceptujemy ZIP (nowy) i JSON (starsze backupy).
                            // Niektóre menedżery raportują nietypowe MIME — fallback */*.
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/json",
                                    "application/octet-stream",
                                    "text/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            }

            // === Crash log (utility) ===
            item {
                SectionCard(
                    title = "Diagnostyka",
                    subtitle = null
                ) {
                    OutlinedActionButton(
                        text = "Skopiuj log ostatniego crashu",
                        icon = Icons.Default.BugReport,
                        onClick = {
                            val crashFile = File(context.filesDir, "last_crash.txt")
                            if (crashFile.exists()) {
                                val text = crashFile.readText()
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                                coroutineScope.launch {
                                    snackbar.showSnackbar("Skopiowano - wklej w czacie")
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbar.showSnackbar("Brak crashu - aplikacja stabilna")
                                }
                            }
                        }
                    )
                }
            }

            // === Strefa niebezpieczna ===
            item {
                DangerZoneCard(
                    onWipe = { showWipeDialog = true }
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
            },
            title = { Text("Wyczyścić wszystkie dane?") },
            text = {
                Text(
                    "Skasujesz wszystkie treningi, plany, pomiary, cele, ustawienia AI " +
                        "i klucz API. Tej operacji nie można cofnąć."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        vm.wipeAll(onDone = {})
                    }
                ) { Text("Skasuj wszystko", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = DarkOnSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun OutlinedActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val border = if (danger) ErrorRed.copy(alpha = 0.55f) else DarkSurfaceVariant
    val fg = if (danger) ErrorRed else DarkOnSurface
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) ErrorRed.copy(alpha = 0.10f) else Color.Transparent
        ),
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg)
            Spacer(Modifier.size(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = fg
            )
        }
    }
}

@Composable
private fun DangerZoneCard(onWipe: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ErrorRed.copy(alpha = 0.07f)
        ),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.40f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    "Strefa niebezpieczna",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = ErrorRed
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Skasuje wszystkie dane lokalne. Nie można cofnąć.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedActionButton(
                text = "Wyczyść wszystkie dane",
                icon = Icons.Default.DeleteForever,
                onClick = onWipe,
                danger = true
            )
        }
    }
}
