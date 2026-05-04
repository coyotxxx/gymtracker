package pl.filebit.gymtracker.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenBackup: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCalculators: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenAiTrainer: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenAiWeeklyReport: () -> Unit,
    onOpenTrainingSettings: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenAchievements: () -> Unit = {},
    onRestartOnboarding: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var draft by remember(profile) { mutableStateOf(profile) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.profile_saved)
    var showEditName by remember { mutableStateOf(false) }

    LaunchedEffect(profile) { draft = profile }

    // Auto-save: każda zmiana drafta zapisuje profil bez przycisku Zapisz
    LaunchedEffect(draft) {
        if (draft != profile) {
            vm.save(draft) { /* no-op */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.nav_profile),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    ),
                    color = DarkOnSurface,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                )
            }

            // === HERO: avatar + imię + level + dni + ołówek ===
            item {
                ProfileHeaderCard(
                    profile = draft,
                    onEdit = { showEditName = true }
                )
            }

            item {
                pl.filebit.gymtracker.ui.update.UpdateCard(showWhenUpToDate = true)
            }

            // === POSTĘP ===
            item { ProfileSectionLabel("Postęp") }
            item {
                ProfileNavRow(
                    icon = Icons.Default.QueryStats,
                    title = stringResource(R.string.stats_title),
                    subtitle = "Wykresy, mapa mięśni, standardy siłowe, PR",
                    onClick = onOpenStats
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.MonitorWeight,
                    title = "Pomiary ciała",
                    subtitle = "Waga, wymiary, tkanka tłuszczowa",
                    onClick = onOpenMeasurements
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Zdjęcia progresu",
                    subtitle = "Galeria + porównanie",
                    onClick = onOpenPhotos
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.Flag,
                    title = "Cele",
                    subtitle = "Cele długoterminowe",
                    onClick = onOpenGoals
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.EmojiEvents,
                    title = "Odznaki",
                    subtitle = "Wszystkie zdobyte odznaki",
                    onClick = onOpenAchievements
                )
            }

            // === ASYSTENT AI ===
            item { ProfileSectionLabel("Asystent AI") }
            item {
                ProfileNavRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Trener AI",
                    subtitle = "Plany, analizy i sugestie",
                    onClick = onOpenAiTrainer
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Raport tygodniowy",
                    subtitle = "AI analizuje 7 dni i daje rekomendacje",
                    onClick = onOpenAiWeeklyReport
                )
            }

            // === NARZĘDZIA ===
            item { ProfileSectionLabel("Narzędzia") }
            item {
                ProfileNavRow(
                    icon = Icons.Default.Calculate,
                    title = "Kalkulatory",
                    subtitle = "1RM, obciążenia (talerze)",
                    onClick = onOpenCalculators
                )
            }

            // === USTAWIENIA ===
            item { ProfileSectionLabel("Ustawienia") }
            item {
                ProfileNavRow(
                    icon = Icons.Default.FitnessCenter,
                    title = "Ustawienia treningu",
                    subtitle = "Cel, doświadczenie, jednostka, dni, czas, rest, powiadomienia",
                    onClick = onOpenTrainingSettings
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.Key,
                    title = "Połączenie AI",
                    subtitle = "Klucz API i model",
                    onClick = onOpenAiSettings
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Uruchom kreator ponownie",
                    subtitle = "Przejdź wizard od nowa",
                    onClick = onRestartOnboarding
                )
            }

            // === POMOC ===
            item { ProfileSectionLabel("Pomoc") }
            item {
                ProfileNavRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Słowniczek",
                    subtitle = "Skróty: RPE, RIR, AMRAP…",
                    onClick = onOpenGlossary
                )
            }
            item {
                ProfileNavRow(
                    icon = Icons.Default.CloudUpload,
                    title = stringResource(R.string.backup_section),
                    subtitle = "Eksport / Import / Wyczyść",
                    onClick = onOpenBackup
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                AppVersionFooter()
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showEditName) {
        EditNameDialog(
            current = draft.displayName,
            onDismiss = { showEditName = false },
            onSave = { newName ->
                draft = draft.copy(displayName = newName)
                showEditName = false
                scope.launch { snackbar.showSnackbar(savedMsg) }
            }
        )
    }
}

// ============================================================
// Komponenty pomocnicze
// ============================================================

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = DarkOnSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DarkOnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    profile: UserProfile,
    onEdit: () -> Unit
) {
    val initial = profile.displayName.firstOrNull()?.uppercase() ?: "?"
    val displayName = profile.displayName.ifBlank { "Twoje imię" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(AccentOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    ),
                    color = Color.Black
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = if (profile.displayName.isBlank()) DarkOnSurfaceVariant else DarkOnSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${profile.experience.label()} · ${profile.daysPerWeek}×/tydz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edytuj imię",
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EditNameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imię") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    placeholder = { Text("np. Maciej") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pokaże się na ekranie głównym jako \"Cześć, Imię\". Zostaw puste żeby wyłączyć.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun AppVersionFooter() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "?"
        }.getOrDefault("?")
    }
    Text(
        text = "GymTracker v$version",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = DarkOnSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

private fun ExperienceLevel.label(): String = when (this) {
    ExperienceLevel.BEGINNER -> "Początkujący"
    ExperienceLevel.INTERMEDIATE -> "Średnio zaawansowany"
    ExperienceLevel.ADVANCED -> "Zaawansowany"
}
