package pl.filebit.gymtracker.ui.plans

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.template.PlanGoalCategory
import pl.filebit.gymtracker.data.template.PlanTemplate
import pl.filebit.gymtracker.data.template.PlanTemplates
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SelectableChip
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    vm: PlanListViewModel = hiltViewModel()
) {
    var freqFilter by remember { mutableStateOf<Int?>(null) }  // null = wszystkie
    var goalFilter by remember { mutableStateOf<PlanGoalCategory?>(null) }  // null = wszystkie
    val filtered = remember(freqFilter, goalFilter) {
        PlanTemplates.all.filter { tmpl ->
            (freqFilter == null || tmpl.daysPerWeek == freqFilter) &&
                (goalFilter == null || tmpl.category == goalFilter)
        }
    }
    val grouped = filtered.groupBy { it.category }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.templates_title),
                onBack = onBack
            )
            // v2.8.1: kompaktowe filtry — chip pokazuje aktualny wybór, tap rozwija
            // okno opcji pod spodem. Wcześniej dwa pełne bloki chipów (Cel ~4 rzędy,
            // Częstotliwość ~2 rzędy) zajmowały ~połowę ekranu przed listą.
            var expanded by remember { mutableStateOf<TplFilter?>(null) }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownFilterChip(
                    text = goalFilter?.let { "${it.emoji} ${it.labelPl}" } ?: "Cel",
                    selected = goalFilter != null,
                    expanded = expanded == TplFilter.GOAL,
                    onClick = { expanded = if (expanded == TplFilter.GOAL) null else TplFilter.GOAL }
                )
                DropdownFilterChip(
                    text = freqFilter?.let { "$it× / tydz" } ?: "Częstotliwość",
                    selected = freqFilter != null,
                    expanded = expanded == TplFilter.FREQ,
                    onClick = { expanded = if (expanded == TplFilter.FREQ) null else TplFilter.FREQ }
                )
            }

            AnimatedVisibility(visible = expanded != null) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (expanded) {
                        TplFilter.GOAL -> {
                            SelectableChip(
                                text = "Wszystkie",
                                selected = goalFilter == null,
                                onClick = { goalFilter = null; expanded = null }
                            )
                            PlanGoalCategory.entries.forEach { cat ->
                                SelectableChip(
                                    text = "${cat.emoji} ${cat.labelPl}",
                                    selected = goalFilter == cat,
                                    onClick = { goalFilter = cat; expanded = null }
                                )
                            }
                        }
                        TplFilter.FREQ -> {
                            SelectableChip(
                                text = "Wszystkie",
                                selected = freqFilter == null,
                                onClick = { freqFilter = null; expanded = null }
                            )
                            listOf(2, 3, 4, 5, 6).forEach { days ->
                                SelectableChip(
                                    text = "$days× / tydz",
                                    selected = freqFilter == days,
                                    onClick = { freqFilter = days; expanded = null }
                                )
                            }
                        }
                        null -> {}
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Brak planów dla wybranych filtrów — zmień cel lub częstotliwość.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                PlanGoalCategory.entries.forEach { category ->
                    val templates = grouped[category].orEmpty()
                    if (templates.isEmpty()) return@forEach
                    item(key = "header_${category.name}") {
                        CategoryHeader(category = category, count = templates.size)
                    }
                    items(
                        count = templates.size,
                        key = { idx -> templates[idx].id }
                    ) { idx ->
                        val tmpl = templates[idx]
                        TemplateCard(
                            template = tmpl,
                            onClick = {
                                vm.createFromTemplate(tmpl) { newPlanId -> onCreated(newPlanId) }
                            }
                        )
                    }
                    item(key = "spacer_${category.name}") {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private enum class TplFilter { GOAL, FREQ }

/** v2.8.1 — kompaktowy chip filtra z rozwijaną strzałką; pokazuje aktualny wybór. */
@Composable
private fun DropdownFilterChip(
    text: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (selected) AccentOrange else DarkOutlineSoft,
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) AccentOrange else DarkOnSurface
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = if (selected) AccentOrange else DarkOnSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CategoryHeader(category: PlanGoalCategory, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${category.emoji}  ${category.labelPl}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TemplateCard(template: PlanTemplate, onClick: () -> Unit) {
    val daysCount = template.days.size
    val totalExercises = template.days.sumOf { it.exercises.size }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                template.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.templates_summary, daysCount, totalExercises),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
