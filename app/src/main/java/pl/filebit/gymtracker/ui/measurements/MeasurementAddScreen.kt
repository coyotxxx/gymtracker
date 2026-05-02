package pl.filebit.gymtracker.ui.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.util.filterWeightInput
import pl.filebit.gymtracker.util.formatWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FormMode(val label: String) { Quick("Szybki"), Full("Pełny") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementAddScreen(
    measurementId: Long?,
    onBack: () -> Unit,
    vm: MeasurementsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val initial = remember(measurementId, state.measurements) {
        measurementId?.let { id -> state.measurements.firstOrNull { it.id == id } }
    }

    var mode by remember { mutableStateOf(FormMode.Full) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: System.currentTimeMillis()) }
    var weight by remember(initial) { mutableStateOf(initial?.weightKg?.let { formatWeight(it) } ?: "") }
    var bodyFat by remember(initial) { mutableStateOf(initial?.bodyFatPercent?.let { formatWeight(it) } ?: "") }
    var muscleMass by remember(initial) { mutableStateOf(initial?.muscleMassKg?.let { formatWeight(it) } ?: "") }
    var chest by remember(initial) { mutableStateOf(initial?.chestCm?.let { formatWeight(it) } ?: "") }
    var arm by remember(initial) { mutableStateOf(initial?.armCm?.let { formatWeight(it) } ?: "") }
    var biceps by remember(initial) { mutableStateOf(initial?.bicepsCm?.let { formatWeight(it) } ?: "") }
    var neck by remember(initial) { mutableStateOf(initial?.neckCm?.let { formatWeight(it) } ?: "") }
    var waist by remember(initial) { mutableStateOf(initial?.waistCm?.let { formatWeight(it) } ?: "") }
    var belly by remember(initial) { mutableStateOf(initial?.bellyCm?.let { formatWeight(it) } ?: "") }
    var hips by remember(initial) { mutableStateOf(initial?.hipsCm?.let { formatWeight(it) } ?: "") }
    var thigh by remember(initial) { mutableStateOf(initial?.thighCm?.let { formatWeight(it) } ?: "") }
    var calf by remember(initial) { mutableStateOf(initial?.calfCm?.let { formatWeight(it) } ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedToast) {
        if (state.savedToast != null) {
            vm.consumeToast()
            onBack()
        }
    }

    val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("pl", "PL"))

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = if (initial != null) "Edycja pomiaru" else "Dodaj pomiar",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = {
                            saveMeasurement(
                                vm = vm,
                                initial = initial,
                                date = date,
                                weight = weight,
                                bodyFat = bodyFat,
                                muscleMass = muscleMass,
                                chest = chest,
                                arm = arm,
                                biceps = biceps,
                                neck = neck,
                                waist = waist,
                                belly = belly,
                                hips = hips,
                                thigh = thigh,
                                calf = calf,
                                notes = notes
                            )
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Zapisz", tint = AccentOrange)
                        }
                    }
                )
            }

            // Selektor daty
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, DarkOutlineSoft, RoundedCornerShape(12.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        dateFmt.format(Date(date)),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▾", color = DarkOnSurfaceVariant)
                }
            }

            // Toggle Szybki / Pełny
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    FormMode.entries.forEach { m ->
                        ModeChip(
                            label = m.label,
                            selected = mode == m,
                            onClick = { mode = m },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sekcja PODSTAWOWE — zawsze widoczna
            item {
                SectionCard(
                    icon = Icons.Default.Scale,
                    title = "Podstawowe"
                ) {
                    InputField("Waga", "np. 82,4", weight, "kg") { weight = filterWeightInput(it) }
                    InputField("Tkanka tłuszczowa", "np. 14,8", bodyFat, "%") { bodyFat = filterWeightInput(it) }
                    InputField("Masa mięśniowa", "np. 64,2", muscleMass, "kg") { muscleMass = filterWeightInput(it) }
                }
            }

            // Pełny tryb — pozostałe sekcje
            if (mode == FormMode.Full) {
                item {
                    SectionCard(
                        icon = Icons.Default.FitnessCenter,
                        title = "Góra ciała"
                    ) {
                        InputField("Klatka piersiowa", "np. 105", chest, "cm") { chest = filterWeightInput(it) }
                        InputField("Ramię", "np. 38", arm, "cm") { arm = filterWeightInput(it) }
                        InputField("Biceps (zgięty)", "np. 42", biceps, "cm") { biceps = filterWeightInput(it) }
                        InputField("Kark", "np. 40", neck, "cm") { neck = filterWeightInput(it) }
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Default.AccessibilityNew,
                        title = "Środek"
                    ) {
                        InputField("Talia (najwęższa)", "np. 80", waist, "cm") { waist = filterWeightInput(it) }
                        InputField("Pas (na pępku)", "np. 86", belly, "cm") { belly = filterWeightInput(it) }
                        InputField("Biodra", "np. 98", hips, "cm") { hips = filterWeightInput(it) }
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Default.DirectionsRun,
                        title = "Nogi"
                    ) {
                        InputField("Udo", "np. 59", thigh, "cm") { thigh = filterWeightInput(it) }
                        InputField("Łydka", "np. 38", calf, "cm") { calf = filterWeightInput(it) }
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Default.Straighten,
                        title = "Notatki"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, DarkOutlineSoft, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                textStyle = TextStyle(
                                    color = DarkOnSurface,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(AccentOrange),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (notes.isEmpty()) {
                                        Text(
                                            "np. dzień po cheat meal, więcej snu",
                                            style = TextStyle(
                                                color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dolny CTA "Zapisz pomiar"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(AccentOrange, RoundedCornerShape(16.dp))
                    .clickable {
                        saveMeasurement(
                            vm = vm,
                            initial = initial,
                            date = date,
                            weight = weight,
                            bodyFat = bodyFat,
                            muscleMass = muscleMass,
                            chest = chest,
                            arm = arm,
                            biceps = biceps,
                            neck = neck,
                            waist = waist,
                            belly = belly,
                            hips = hips,
                            thigh = thigh,
                            calf = calf,
                            notes = notes
                        )
                    }
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Zapisz pomiar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = Color.Black
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun saveMeasurement(
    vm: MeasurementsViewModel,
    initial: BodyMeasurement?,
    date: Long,
    weight: String,
    bodyFat: String,
    muscleMass: String,
    chest: String,
    arm: String,
    biceps: String,
    neck: String,
    waist: String,
    belly: String,
    hips: String,
    thigh: String,
    calf: String,
    notes: String
) {
    val toDouble: (String) -> Double? = { s ->
        s.replace(',', '.').toDoubleOrNull()
    }
    val m = (initial ?: BodyMeasurement(date = date)).copy(
        date = date,
        weightKg = toDouble(weight),
        bodyFatPercent = toDouble(bodyFat),
        muscleMassKg = toDouble(muscleMass),
        chestCm = toDouble(chest),
        armCm = toDouble(arm),
        bicepsCm = toDouble(biceps),
        neckCm = toDouble(neck),
        waistCm = toDouble(waist),
        bellyCm = toDouble(belly),
        hipsCm = toDouble(hips),
        thighCm = toDouble(thigh),
        calfCm = toDouble(calf),
        notes = notes
    )
    vm.saveFullMeasurement(m)
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AccentOrange else Color.Transparent
    val fg = if (selected) Color.Black else DarkOnSurface
    Box(
        modifier = modifier
            .height(40.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    GymCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(8.dp))
                LabelUp(title, accent = true)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InputField(
    label: String,
    placeholder: String,
    value: String,
    unit: String,
    onChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(36.dp)
                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, DarkOutlineSoft, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = DarkOnSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AccentOrange),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = TextStyle(
                                    color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            " $unit",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
    }
}
