package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    vm: WeeklyReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    title = "Trener AI — tydzień",
                    onBack = onBack
                )
            }

            // Header explanation
            item {
                GymCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
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

            // Generate / Regenerate button
            item {
                GymPrimaryButton(
                    onClick = { vm.generate() },
                    text = if (state.report == null) "Wygeneruj raport"
                    else "Odśwież raport",
                    leadingIcon = if (state.report == null) Icons.Default.AutoAwesome
                    else Icons.Default.Refresh,
                    enabled = !state.isLoading
                )
            }

            // Loading indicator
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentOrange)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "AI analizuje Twoje dane…",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error
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
                            LabelUp("Błąd", accent = false)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }

            // Report content (markdown rendered as plain text — szybki MVP)
            state.report?.let { md ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            // Render markdown jako plain z lekką stylizacją nagłówków
                            md.split("\n").forEach { line ->
                                RenderMarkdownLine(line)
                            }
                        }
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
                    fontSize = 20.sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
        }
        line.startsWith("## ") -> {
            Spacer(Modifier.height(12.dp))
            Text(
                line.removePrefix("## "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(4.dp))
        }
        line.startsWith("### ") -> {
            Spacer(Modifier.height(8.dp))
            Text(
                line.removePrefix("### "),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(2.dp))
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
            Spacer(Modifier.height(6.dp))
        }
        line == "---" -> {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DarkOutlineSoft)
            )
            Spacer(Modifier.height(8.dp))
        }
        else -> {
            // Bold tekst z **...**
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
