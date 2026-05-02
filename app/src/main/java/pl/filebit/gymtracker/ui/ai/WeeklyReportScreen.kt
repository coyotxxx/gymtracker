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

            // Treść raportu
            if (expanded) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkOutlineSoft))
                }
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    report.content.split("\n").forEach { line ->
                        RenderMarkdownLine(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMarkdownLine(line: String) {
    when {
        line.startsWith("# ") -> {
            Spacer(Modifier.height(8.dp))
            Text(
                line.removePrefix("# "),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
        }
        line.startsWith("## ") -> {
            Spacer(Modifier.height(10.dp))
            Text(
                line.removePrefix("## "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(4.dp))
        }
        line.startsWith("### ") -> {
            Spacer(Modifier.height(6.dp))
            Text(
                line.removePrefix("### "),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface
            )
        }
        line.startsWith("- ") || line.startsWith("• ") -> {
            Text(
                "• " + line.removePrefix("- ").removePrefix("• "),
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
            )
        }
        line.matches(Regex("^\\d+\\..*")) -> {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
            )
        }
        line.isBlank() -> {
            Spacer(Modifier.height(4.dp))
        }
        line == "---" -> {
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DarkOutlineSoft))
            Spacer(Modifier.height(8.dp))
        }
        else -> {
            val cleaned = line.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            Text(
                cleaned,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (line.contains("**")) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = DarkOnSurface,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
