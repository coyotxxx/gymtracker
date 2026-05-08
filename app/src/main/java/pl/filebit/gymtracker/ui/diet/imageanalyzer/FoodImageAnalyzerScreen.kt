package pl.filebit.gymtracker.ui.diet.imageanalyzer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.io.ByteArrayOutputStream

@Composable
fun FoodImageAnalyzerScreen(
    onBack: () -> Unit,
    vm: FoodImageAnalyzerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = readImageBytes(context, it)
            if (bytes != null) {
                imageBytes = bytes
                imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Zdjęcie posiłku → AI", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "AI rozpozna posiłek na zdjęciu + szacuje kcal i makro. " +
                            "Wynik zawsze możesz poprawić przed zapisem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }

                // Image preview lub picker
                item {
                    if (imageBitmap != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, DarkOutlineSoft),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Image(
                                bitmap = imageBitmap!!.asImageBitmap(),
                                contentDescription = "Wybrane zdjęcie",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                                .clickable { pickImageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📷", style = MaterialTheme.typography.displayLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Dotknij by wybrać zdjęcie",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = DarkOnSurface
                                )
                                Text(
                                    "(galeria lub aparat)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Akcje
                if (imageBytes != null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                                    .clickable {
                                        imageBytes = null
                                        imageBitmap = null
                                        vm.reset()
                                        pickImageLauncher.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📁 Inne zdjęcie", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                                    .background(AccentOrange.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        imageBytes?.let { vm.analyzeImage(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔍 Analizuj AI",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AccentOrange)
                            }
                        }
                    }
                }

                // Status + wynik
                when (val s = state) {
                    is AnalysisState.Idle -> { /* nothing */ }
                    is AnalysisState.Analyzing -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⏳ AI analizuje zdjęcie... (~10-15 sek)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }
                    is AnalysisState.Error -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text("❌ ${s.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentOrange)
                            }
                        }
                    }
                    is AnalysisState.Success -> {
                        item { AnalysisResultCard(s.analysis, vm) }
                    }
                    is AnalysisState.Saved -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text("✓ Zapisano ${s.mealsAdded} składników do dziennika dnia",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SuccessGreen)
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Wróć do diety", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisResultCard(
    analysis: pl.filebit.gymtracker.ai.FoodAnalysis,
    vm: FoodImageAnalyzerViewModel
) {
    var selectedMealType by remember { mutableStateOf(MealType.LUNCH) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                analysis.dishName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface
            )
            ConfidenceBadge(analysis.overallConfidence)
            Spacer(Modifier.height(8.dp))

            // Total
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "${analysis.totalKcal} kcal · B${analysis.totalProteinG} W${analysis.totalCarbsG} T${analysis.totalFatG}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    ),
                    color = AccentOrange
                )
            }

            // Składniki
            Spacer(Modifier.height(8.dp))
            Text(
                "SKŁADNIKI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            analysis.ingredients.forEach { ing ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("• ${ing.productName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f))
                    Text("${ing.grams} g",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = DarkOnSurfaceVariant)
                }
            }

            if (analysis.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "ℹ ${analysis.notes}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant
                )
            }

            // Slot picker
            Spacer(Modifier.height(12.dp))
            Text(
                "DODAJ DO SLOTU",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MealType.values().forEach { mt ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (selectedMealType == mt) AccentOrange.copy(alpha = 0.18f)
                                else DarkSurfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMealType = mt },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (mt) {
                                MealType.BREAKFAST -> "🌅 Śn"
                                MealType.LUNCH -> "🍽 Obiad"
                                MealType.DINNER -> "🌙 Kol"
                                MealType.SNACK -> "🥨 Prz"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedMealType == mt) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (selectedMealType == mt) AccentOrange else DarkOnSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AccentOrange.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .clickable {
                        vm.saveAsMealEntries(
                            analysis = analysis,
                            mealType = selectedMealType,
                            dateMs = System.currentTimeMillis()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓ Zapisz do ${slotLabel(selectedMealType)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = AccentOrange
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    val (label, color) = when (confidence.uppercase()) {
        "HIGH" -> "Wysoka pewność" to SuccessGreen
        "MEDIUM" -> "Średnia pewność" to AccentOrange
        else -> "Niska pewność — sprawdź wartości" to AccentOrange
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp
                ),
                color = color
            )
        }
    }
}

private fun slotLabel(mt: MealType): String = when (mt) {
    MealType.BREAKFAST -> "śniadania"
    MealType.LUNCH -> "obiadu"
    MealType.DINNER -> "kolacji"
    MealType.SNACK -> "przekąski"
}

private fun readImageBytes(context: Context, uri: Uri): ByteArray? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val raw = stream.readBytes()
        // ZAWSZE dekoduj i enkoduj jako JPEG, niezależnie od źródła:
        // - rozwiązuje mismatch media_type (PNG/HEIC/WebP wysyłane jako image/jpeg → 400 z Anthropic)
        // - skaluje long edge do 1568 px (zalecenie Anthropic — większe są i tak downscalowane)
        // - mniejszy base64 = szybsza analiza
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@use raw
        val maxEdge = 1568
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longEdge > maxEdge) {
            val ratio = maxEdge.toFloat() / longEdge
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val output = ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
        output.toByteArray()
    }
} catch (_: Exception) {
    null
}
