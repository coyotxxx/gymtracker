package pl.filebit.gymtracker.ui.diet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.MealPrepActionType
import pl.filebit.gymtracker.data.entity.MealPrepStep
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MealPrepScreen(
    onBack: () -> Unit,
    vm: MealPrepViewModel = hiltViewModel()
) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2000)
            vm.consumeStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Gotowanie na zapas", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Generuj
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkOutlineSoft),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Bierze dzisiejszy plan posiłków i mnoży przez liczbę dni:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = DarkOnSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenerateButton("1 dzień", { vm.generate(1) }, generating, Modifier.weight(1f))
                                GenerateButton("2 dni", { vm.generate(2) }, generating, Modifier.weight(1f))
                                GenerateButton("3 dni", { vm.generate(3) }, generating, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "💡 Wskazówka: zaplanuj posiłki na dziś (np. wygeneruj plan diety AI), " +
                                    "potem wybierz na ile dni gotujesz — gramatury pomnożą się same.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }

                statusMessage?.let { msg ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("✓ $msg", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        }
                    }
                }

                plan?.let { p ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = DarkOnSurface
                                )
                                val df = SimpleDateFormat("d MMM yyyy", Locale("pl", "PL"))
                                Text(
                                    "${df.format(Date(p.fromDateMs))} – ${df.format(Date(p.toDateMs - 1))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkOnSurfaceVariant
                                )
                                if (steps.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "🍱 ${p.containersCount} pojemników",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                            ),
                                            color = AccentOrange
                                        )
                                        Text(
                                            "⏱ ${p.totalMinutes} min",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                            ),
                                            color = AccentOrange
                                        )
                                        val done = steps.count { it.isCompleted }
                                        Text(
                                            "✓ $done/${steps.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                            ),
                                            color = SuccessGreen
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SmallActionButton("📋 Kopiuj", Modifier.weight(1f)) {
                                            scope.launch {
                                                val text = vm.exportText()
                                                copyToClipboard(context, text)
                                            }
                                        }
                                        SmallActionButton("📤 Udostępnij", Modifier.weight(1f)) {
                                            scope.launch {
                                                val text = vm.exportText()
                                                shareText(context, text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (steps.isEmpty() && !generating) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Brak posiłków na dziś. Zaplanuj dzisiejsze posiłki w zakładce Dieta i wygeneruj ponownie.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }

                    items(steps.size) { idx ->
                        StepRow(steps[idx], idx + 1) { vm.toggleStep(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(
    label: String,
    onClick: () -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                if (disabled) DarkSurfaceVariant.copy(alpha = 0.3f) else AccentOrange.copy(alpha = 0.15f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (disabled) DarkOnSurfaceVariant else AccentOrange
        )
    }
}

@Composable
private fun SmallActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = DarkOnSurface
        )
    }
}

@Composable
private fun StepRow(
    step: MealPrepStep,
    orderNum: Int,
    onToggle: (MealPrepStep) -> Unit
) {
    val emoji = when (step.action) {
        MealPrepActionType.BOIL -> "🥘"
        MealPrepActionType.BAKE -> "🔥"
        MealPrepActionType.GRILL -> "🍖"
        MealPrepActionType.PAN_FRY -> "🍳"
        MealPrepActionType.CHOP -> "🔪"
        MealPrepActionType.MIX -> "🥄"
        MealPrepActionType.PORTION -> "🍱"
        MealPrepActionType.STORE -> "📦"
        MealPrepActionType.OTHER -> "•"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(step) },
        colors = CardDefaults.cardColors(
            containerColor = if (step.isCompleted) DarkSurface.copy(alpha = 0.6f) else DarkSurface
        ),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = step.isCompleted,
                onCheckedChange = { onToggle(step) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentOrange,
                    uncheckedColor = DarkOnSurfaceVariant
                )
            )
            Spacer(Modifier.width(4.dp))
            Text("$emoji", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$orderNum. ${step.description}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (step.isCompleted) DarkOnSurfaceVariant else DarkOnSurface
                )
                Text(
                    "~${step.estimatedMinutes} min",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Meal prep", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Udostępnij plan meal prep")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

