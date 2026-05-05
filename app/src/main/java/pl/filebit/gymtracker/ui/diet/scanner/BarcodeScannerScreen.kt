package pl.filebit.gymtracker.ui.diet.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.util.concurrent.Executors

/**
 * Skaner kodu kreskowego z CameraX preview + ML Kit + lookup w OpenFoodFacts.
 *
 * Po znalezieniu produktu user może:
 *  - dodać go do bazy + wybrać do posiłku (FoundNew/FoundExisting)
 *  - poprawić ręcznie jeśli dane niekompletne (Partial)
 *  - wpisać barkod ręcznie (fallback gdy kamera nie ma permission)
 */
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onProductSelected: (productId: Long) -> Unit,
    vm: BarcodeScannerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedProductId by vm.selectedProductId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(selectedProductId) {
        selectedProductId?.let { id ->
            onProductSelected(id)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Skaner kodu", onBack = onBack)

            if (hasCameraPermission) {
                CameraPreview(
                    onBarcodeDetected = { code -> vm.onBarcodeDetected(code) }
                )
            } else {
                NoPermissionView(onManualBarcode = { vm.manualBarcodeEntry(it) })
            }

            Spacer(Modifier.height(8.dp))

            // Status + akcje
            when (val s = state) {
                is ScanState.Scanning -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Wyceluj kamerę w kod kreskowy produktu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
                is ScanState.Loading -> {
                    StatusBox("⏳ Wyszukiwanie w bazie OpenFoodFacts...", AccentOrange)
                }
                is ScanState.FoundExisting -> {
                    ProductPreview(
                        title = "✓ Już w Twojej bazie",
                        product = s.product,
                        actionLabel = "Wybierz",
                        actionColor = SuccessGreen,
                        onAction = { vm.acceptExisting(s.product) },
                        onScanAgain = { vm.resetScan() }
                    )
                }
                is ScanState.FoundNew -> {
                    ProductPreview(
                        title = "✨ Nowy produkt z OpenFoodFacts",
                        product = s.product,
                        actionLabel = "Dodaj do bazy + wybierz",
                        actionColor = AccentOrange,
                        onAction = { vm.acceptAndAddToBase(s.product) },
                        onScanAgain = { vm.resetScan() }
                    )
                }
                is ScanState.Partial -> {
                    ProductPreview(
                        title = "⚠ Niekompletne dane (${s.missing.joinToString(", ")})",
                        product = s.product,
                        actionLabel = "Dodaj mimo to",
                        actionColor = AccentOrange,
                        onAction = { vm.acceptAndAddToBase(s.product) },
                        onScanAgain = { vm.resetScan() }
                    )
                }
                is ScanState.NotFound -> {
                    StatusBox("❌ Nie znaleziono produktu w OpenFoodFacts", AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(40.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .clickable { vm.resetScan() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Skanuj ponownie", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                    }
                }
                is ScanState.Error -> {
                    StatusBox("❌ ${s.message}", AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(40.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .clickable { vm.resetScan() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Spróbuj ponownie", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128
                )
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp)
            .border(2.dp, AccentOrange, RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                bindCamera(ctx, lifecycleOwner, previewView, executor, barcodeScanner, onBarcodeDetected)
                previewView
            }
        )
        // Reticle (overlay)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(220.dp, 100.dp)
                .border(3.dp, AccentOrange, RoundedCornerShape(8.dp))
        )
    }
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    executor: java.util.concurrent.ExecutorService,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val cameraProvider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { ia ->
                ia.setAnalyzer(executor) { proxy ->
                    processImage(proxy, scanner, onBarcodeDetected)
                }
            }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview, analysis
            )
        } catch (_: Exception) {
            // ignoruj — np. brak kamery
        }
    }, ContextCompat.getMainExecutor(context))
}

@androidx.camera.core.ExperimentalGetImage
private fun processImage(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = proxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { onBarcodeDetected(it) }
            }
            .addOnCompleteListener {
                proxy.close()
            }
    } else {
        proxy.close()
    }
}

@Composable
private fun NoPermissionView(onManualBarcode: (String) -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Brak dostępu do kamery. Możesz wpisać barkod ręcznie:",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurface
        )
        OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it.filter { c -> c.isDigit() }.take(13) },
            label = { Text("Barkod (8-13 cyfr)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { if (manualCode.length >= 8) onManualBarcode(manualCode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wyszukaj", color = AccentOrange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBox(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProductPreview(
    title: String,
    product: pl.filebit.gymtracker.data.entity.FoodProduct,
    actionLabel: String,
    actionColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
            ),
            color = actionColor
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DarkOnSurface
                )
                product.brand?.let {
                    Text(
                        "Marka: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                product.barcode?.let {
                    Text(
                        "Barkod: $it",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = DarkOnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${product.kcalPer100g.toInt()} kcal/100g · B${product.proteinPer100g.toInt()}/W${product.carbsPer100g.toInt()}/T${product.fatPer100g.toInt()}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                    ),
                    color = AccentOrange
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .clickable(onClick = onScanAgain),
                contentAlignment = Alignment.Center
            ) {
                Text("Skanuj jeszcze raz", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(actionColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = actionColor)
            }
        }
    }
}
