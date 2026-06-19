package pl.filebit.gymtracker.ui.health

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
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
import androidx.compose.foundation.lazy.items
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

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val images = uris.mapNotNull { uri ->
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    bytes to mime
                } else null
            } catch (_: Exception) { null }
        }
        if (images.isNotEmpty()) vm.analyzeImages(images)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    vm.analyzeImages(listOf(bytes to "image/jpeg"))
                }
            } catch (_: Exception) {}
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = prepareHealthCameraUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = prepareHealthCameraUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Skan zdrowotny", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { IntroCard() }

            when (val s = state) {
                HealthScreenshotState.Idle -> {
                    item {
                        PickImageRow(
                            onPickGallery = { pickImagesLauncher.launch("image/*") },
                            onTakePhoto = { launchCamera() }
                        )
                    }
                    item { ExamplesCard() }
                }
                is HealthScreenshotState.Analyzing -> {
                    item { AnalyzingCard(current = s.current, total = s.total) }
                }
                is HealthScreenshotState.Reviewing -> {
                    items(items = s.results, key = { it.index }) { r ->
                        if (r.errorMessage != null) {
                            ErrorCardItem(idx = r.index + 1, message = r.errorMessage)
                        } else {
                            ResultCard(idx = r.index + 1, total = s.results.size, data = r.data)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val anyValid = s.results.any { it.errorMessage == null }
                            Button(
                                onClick = { vm.saveAll(s.results) },
                                modifier = Modifier.weight(1f),
                                enabled = anyValid,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuccessGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Zapisz wszystkie", fontWeight = FontWeight.Bold)
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
                    item { ErrorCardItem(idx = null, message = s.message) }
                    item {
                        TextButton(onClick = { vm.reset() }) {
                            Text("Spróbuj ponownie", color = AccentOrange, fontWeight = FontWeight.Bold)
                        }
                    }
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
                "Możesz wybrać KILKA zdjęć naraz. AI przeanalizuje każdy zrzut po kolei i zapisze dane (sen, waga, stres) do RecoveryLog/BodyMeasurement. Wszystko w jednej operacji.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickImageRow(onPickGallery: () -> Unit, onTakePhoto: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPickGallery,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Galeria", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onTakePhoto,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkSurface,
                contentColor = AccentOrange
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Aparat", fontWeight = FontWeight.Bold)
        }
    }
}

private fun prepareHealthCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(dir, "health_${System.currentTimeMillis()}.jpg")
    if (!file.exists()) file.createNewFile()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
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
                    "• Tkanka tłuszczowa (np. \"24,9%\")\n" +
                    "• Masa mięśniowa (np. \"34,2 kg\")\n" +
                    "• Tętno spoczynkowe (np. \"64 ud./min\")\n" +
                    "• SpO2 (np. \"97%\")\n" +
                    "• Stres (Huawei: 0-99 → 1-5)\n" +
                    "• Kroki, kalorie, VO2Max\n" +
                    "• HRV jeśli widoczne",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sen i stres trafiają do RecoveryLog (używane przez analyzer regeneracji + AI). Waga, tkanka tłuszczowa i masa mięśniowa do BodyMeasurement (trend + dietetyk). Pozostałe metryki są informacyjne.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalyzingCard(current: Int, total: Int) {
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
                "AI analizuje zrzut $current z $total...",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun ResultCard(idx: Int, total: Int, data: HealthScreenshotData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Zrzut $idx / $total",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                ConfidenceBadge(level = data.overallConfidence)
            }
            Spacer(Modifier.height(8.dp))
            data.detectedDate?.let { ResultLine("Data", it) }
            data.sleepHours?.let { ResultLine("Sen", "%.1fh".format(it)) }
            data.weightKg?.let { ResultLine("Waga", "%.1f kg".format(it)) }
            data.bodyFatPercent?.let { ResultLine("Tkanka tłuszczowa", "%.1f%%".format(it)) }
            data.muscleMassKg?.let { ResultLine("Masa mięśniowa", "%.1f kg".format(it)) }
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
private fun ErrorCardItem(idx: Int?, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (idx != null) "Zrzut $idx — błąd analizy" else "Błąd",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ErrorRed
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
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
                Text("Wgraj kolejną serię", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

