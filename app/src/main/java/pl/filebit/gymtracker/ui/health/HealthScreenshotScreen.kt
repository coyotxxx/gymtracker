package pl.filebit.gymtracker.ui.health

import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ai.HealthScreenshotData
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen

@Composable
fun HealthScreenshotScreen(
    onBack: () -> Unit,
    vm: HealthScreenshotViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    vm.analyzeImage(bytes, mime)
                }
            } catch (_: Exception) { /* ignored — UI shows generic error */ }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Skan zdrowotny", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                IntroCard()
            }

            when (val s = state) {
                HealthScreenshotState.Idle -> {
                    item { PickImageButton(onPick = { pickImageLauncher.launch("image/*") }) }
                    item { ExamplesCard() }
                }
                HealthScreenshotState.Analyzing -> {
                    item { AnalyzingCard() }
                }
                is HealthScreenshotState.Success -> {
                    item { ResultCard(data = s.data) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.save(s.data) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuccessGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Zapisz", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { vm.reset() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Anuluj", color = DarkOnSurfaceVariant)
                            }
                        }
                    }
                }
                is HealthScreenshotState.Error -> {
                    item { ErrorCard(message = s.message, onRetry = { vm.reset() }) }
                }
                is HealthScreenshotState.Saved -> {
                    item { SavedCard(savedFields = s.savedFields, onDone = { vm.reset() }) }
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "📸 Wyślij screen z zegarka",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Twój smartwatch (Huawei, Mi, Garmin, Samsung, Polar) zbiera dane o śnie, wadze, tętnie, stresie. " +
                    "AI je przeczyta z zrzutu ekranu i zapisze w aplikacji — żeby trener mógł je uwzględnić.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickImageButton(onPick: () -> Unit) {
    Button(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentOrange,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Wybierz zrzut z galerii", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExamplesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Co AI rozpozna",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "• Sen (np. \"4 godz. 19 min\")\n" +
                    "• Waga (np. \"77,2 kg\")\n" +
                    "• Tętno spoczynkowe (np. \"64 ud./min\")\n" +
                    "• SpO2 (np. \"97%\")\n" +
                    "• Stres (Huawei: 0-99)\n" +
                    "• Kroki, kalorie, VO2Max\n" +
                    "• HRV jeśli widoczne",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Zapisuje do RecoveryLog (sen, stres) i BodyMeasurement (waga). Pozostałe metryki są informacyjne i wykorzystywane przez AI w prompcie.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalyzingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = AccentOrange,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "AI analizuje zrzut...",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun ResultCard(data: HealthScreenshotData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Wynik analizy",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.weight(1f))
                ConfidenceBadge(level = data.overallConfidence)
            }
            Spacer(Modifier.height(10.dp))
            data.detectedDate?.let { ResultLine("Data", it) }
            data.sleepHours?.let { ResultLine("Sen", "%.1fh".format(it)) }
            data.weightKg?.let { ResultLine("Waga", "%.1f kg".format(it)) }
            data.steps?.let { ResultLine("Kroki", "$it") }
            data.restingHeartRateBpm?.let { ResultLine("Tętno spoczynkowe", "$it bpm") }
            data.spO2Pct?.let { ResultLine("SpO2", "$it%") }
            data.stressLevel1to5?.let { ResultLine("Stres (1-5)", "$it") }
            data.hrvMs?.let { ResultLine("HRV", "%.0f ms".format(it)) }
            data.vo2max?.let { ResultLine("VO2Max", "%.1f ml/kg/min".format(it)) }
            data.activeCalories?.let { ResultLine("Kalorie aktywne", "$it kcal") }
            if (data.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    data.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = DarkOnSurface
        )
    }
}

@Composable
private fun ConfidenceBadge(level: String) {
    val (color, text) = when (level.uppercase()) {
        "HIGH" -> SuccessGreen to "Wysokie"
        "LOW" -> ErrorRed to "Niskie"
        else -> AccentOrange to "Średnie"
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Błąd analizy",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ErrorRed
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry) {
                Text("Spróbuj ponownie", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SavedCard(savedFields: List<String>, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Zapisano",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = SuccessGreen
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (savedFields.isEmpty()) "Brak danych do zapisania."
                else "Dodano: " + savedFields.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Wyślij kolejny zrzut", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
