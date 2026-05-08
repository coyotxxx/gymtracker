package pl.filebit.gymtracker.ui.ai

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.AiWeeklyReport
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    vm: WeeklyReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val improvement by vm.improvement.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(title = "Trener AI — tydzień", onBack = onBack)
            }

            item {
                GymCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Analiza tygodnia",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = DarkOnSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "AI przeanalizuje Twoje sesje z bieżącego tygodnia (Pon–Nd) " +
                                "i da konkretne rekomendacje na następny tydzień: per partia mięśniowa, " +
                                "trend RPE, stagnacje, volume.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }

            item {
                GymPrimaryButton(
                    onClick = { vm.generate() },
                    text = if (state.isLoading) "AI analizuje…" else "Wygeneruj raport tego tygodnia",
                    leadingIcon = Icons.Default.AutoAwesome,
                    enabled = !state.isLoading
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentOrange)
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .border(1.dp, ErrorRed.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            LabelUp("Błąd")
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                        }
                    }
                }
            }

            // Sekcja akcji od AI dla NAJNOWSZEGO raportu
            val latestReport = reports.firstOrNull()
            if (latestReport != null) {
                item(key = "actions-card") {
                    val actions = remember(latestReport.id) { vm.parseActions(latestReport.content) }
                    if (actions.isNotEmpty()) {
                        WeeklyActionsCard(
                            actions = actions,
                            selectedActionIds = state.selectedActions,
                            plans = plans,
                            selectedPlanId = state.targetPlanId ?: plans.firstOrNull()?.id,
                            followUpMessage = state.followUpMessage,
                            improvement = improvement,
                            onToggleAction = { vm.toggleAction(it) },
                            onSelectPlan = { vm.setTargetPlan(it) },
                            onChangeFollowUp = { vm.setFollowUpMessage(it) },
                            onGenerate = { vm.requestImprovement(latestReport) }
                        )
                    }
                }
            }

            // Lista raportów (najnowsze pierwsze)
            if (reports.isNotEmpty()) {
                item {
                    LabelUp("Historia raportów (${reports.size})")
                }
                itemsIndexed(reports, key = { _, r -> r.id }) { idx, report ->
                    ReportCard(
                        report = report,
                        defaultExpanded = idx == 0,
                        onDelete = { vm.deleteReport(report.id) }
                    )
                }
            } else if (!state.isLoading) {
                item {
                    Text(
                        "Brak raportów. Wygeneruj pierwszy klikając przycisk wyżej.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // Preview poprawionego planu (po requestImprovement)
    val imp = improvement
    if (imp is WeeklyImprovementState.Preview) {
        val targetPlanId = state.targetPlanId ?: plans.firstOrNull()?.id
        // Zachowane stare ćwiczenia target planu — pobieramy synchronicznie
        var currentByDay by remember(imp.proposal, targetPlanId) {
            mutableStateOf<Map<Int, List<String>>>(emptyMap())
        }
        androidx.compose.runtime.LaunchedEffect(imp.proposal, targetPlanId) {
            if (targetPlanId != null) {
                val exes = vm.loadPlanExerciseNamesByDay(targetPlanId)
                currentByDay = exes
            }
        }
        pl.filebit.gymtracker.ui.plans.PlanImprovementSheet(
            proposal = imp.proposal,
            currentExercisesByDay = currentByDay,
            isApplying = false,
            onDismiss = { vm.dismissImprovement() },
            onReplace = { vm.applyImprovement(asCopy = false) },
            onSaveAsCopy = { vm.applyImprovement(asCopy = true) },
            headline = "Poprawiony plan z raportu"
        )
    } else if (imp is WeeklyImprovementState.Applying) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Zapisuję…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AccentOrange
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Stosuję zmiany w planie…")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun WeeklyActionsCard(
    actions: List<pl.filebit.gymtracker.ai.ReportAction>,
    selectedActionIds: Set<Int>,
    plans: List<pl.filebit.gymtracker.data.entity.TrainingPlan>,
    selectedPlanId: Long?,
    followUpMessage: String,
    improvement: WeeklyImprovementState,
    onToggleAction: (Int) -> Unit,
    onSelectPlan: (Long?) -> Unit,
    onChangeFollowUp: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentOrange.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .border(1.dp, AccentOrange.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Sugerowane akcje",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = DarkOnSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Zaznacz akcje, które chcesz zastosować do planu. AI przygotuje poprawioną wersję.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            actions.forEach { action ->
                ActionCheckRow(
                    action = action,
                    selected = action.id in selectedActionIds,
                    onToggle = { onToggleAction(action.id) }
                )
            }

            // Plan picker (jeśli > 1 plan)
            if (plans.size > 1) {
                Spacer(Modifier.height(12.dp))
                LabelUp("Plan do modyfikacji")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    plans.forEach { plan ->
                        val sel = plan.id == (selectedPlanId ?: plans.first().id)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (sel) AccentOrange.copy(alpha = 0.15f) else DarkSurface,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (sel) AccentOrange.copy(alpha = 0.45f) else DarkOutlineSoft,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectPlan(plan.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                plan.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sel) AccentOrange else DarkOnSurface,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = followUpMessage,
                onValueChange = onChangeFollowUp,
                label = { Text("Doprecyzuj (opcjonalnie)", color = DarkOnSurfaceVariant) },
                placeholder = {
                    Text(
                        "np. zostaw poniedziałek bez zmian",
                        color = DarkOnSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                maxLines = 3
            )

            if (improvement is WeeklyImprovementState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    improvement.message,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))
            val canGenerate = (selectedActionIds.isNotEmpty() || followUpMessage.isNotBlank()) &&
                plans.isNotEmpty() &&
                improvement !is WeeklyImprovementState.Loading
            GymPrimaryButton(
                onClick = { if (canGenerate) onGenerate() },
                text = if (improvement is WeeklyImprovementState.Loading)
                    "AI generuje plan…"
                else
                    "🪄 Wygeneruj poprawiony plan",
                leadingIcon = Icons.Default.AutoAwesome,
                enabled = canGenerate
            )
        }
    }
}

@Composable
private fun ActionCheckRow(
    action: pl.filebit.gymtracker.ai.ReportAction,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val severityColor = when (action.severity) {
        pl.filebit.gymtracker.ai.ReportActionSeverity.WARNING -> ErrorRed
        pl.filebit.gymtracker.ai.ReportActionSeverity.IMPORTANT -> AccentOrange
        pl.filebit.gymtracker.ai.ReportActionSeverity.NORMAL -> DarkOnSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.material3.Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = AccentOrange,
                uncheckedColor = DarkOnSurfaceVariant
            )
        )
        Spacer(Modifier.size(4.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            Text(
                action.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface
            )
            if (action.severity != pl.filebit.gymtracker.ai.ReportActionSeverity.NORMAL) {
                val sevLabel = when (action.severity) {
                    pl.filebit.gymtracker.ai.ReportActionSeverity.WARNING -> "PILNE"
                    pl.filebit.gymtracker.ai.ReportActionSeverity.IMPORTANT -> "PRIORYTET"
                    else -> ""
                }
                Text(
                    sevLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = severityColor
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: AiWeeklyReport,
    defaultExpanded: Boolean,
    onDelete: () -> Unit
) {
    var expanded by remember(report.id) { mutableStateOf(defaultExpanded) }
    val dateFmt = SimpleDateFormat("dd.MM", Locale("pl", "PL"))
    val gen = SimpleDateFormat("dd.MM HH:mm", Locale("pl", "PL"))
    val weekRange = "${dateFmt.format(Date(report.weekStartMillis))} – ${dateFmt.format(Date(report.weekEndMillis - 1))}"
    val generated = gen.format(Date(report.generatedAtMillis))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(16.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(16.dp))
            .animateContentSize()
    ) {
        Column {
            // Header — clickable to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Tydzień $weekRange",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = DarkOnSurface
                    )
                    Text(
                        "Wygenerowano $generated · ${report.aiModel}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Usuń",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = DarkOnSurfaceVariant
                )
            }

            // Treść raportu (bez bloku akcji JSON, który wyświetla osobna karta)
            if (expanded) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkOutlineSoft))
                }
                val cleaned = remember(report.id, report.content) {
                    stripActionsJsonBlock(report.content)
                }
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    pl.filebit.gymtracker.ui.components.AiMarkdown(
                        text = cleaned,
                        contentColor = DarkOnSurface
                    )
                }
            }
        }
    }
}

/**
 * Usuwa z raportu sekcję akcji JSON ('## AKCJE...' + blok ```json) — UI
 * wyświetla je osobną kartą jako klikalną checklistę, a nie jako surowy tekst.
 */
private fun stripActionsJsonBlock(content: String): String {
    // Usuń blok ```json ... ``` z tablicą [...] — najpewniejsze
    val withoutJson = content.replace(
        Regex("```json\\s*\\[[\\s\\S]+?\\]\\s*```", RegexOption.MULTILINE),
        ""
    )
    // Usuń też nagłówek '## AKCJE DO ZASTOSOWANIA (JSON)' i jego krótki opis
    val withoutHeader = withoutJson.replace(
        Regex("##\\s*AKCJE\\s+DO\\s+ZASTOSOWANIA[^\\n]*\\n+", RegexOption.IGNORE_CASE),
        ""
    )
    return withoutHeader.trim()
}

