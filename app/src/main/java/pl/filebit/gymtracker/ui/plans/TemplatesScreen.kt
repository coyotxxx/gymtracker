package pl.filebit.gymtracker.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    vm: PlanListViewModel = hiltViewModel()
) {
    var freqFilter by remember { mutableStateOf<Int?>(null) }  // null = wszystkie
    val filtered = remember(freqFilter) {
        if (freqFilter == null) PlanTemplates.all
        else PlanTemplates.all.filter { it.daysPerWeek == freqFilter }
    }
    val grouped = filtered.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filtr częstotliwości
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = freqFilter == null,
                        onClick = { freqFilter = null },
                        label = { Text("Wszystkie") }
                    )
                }
                items(listOf(2, 3, 4, 5, 6).size) { idx ->
                    val days = listOf(2, 3, 4, 5, 6)[idx]
                    FilterChip(
                        selected = freqFilter == days,
                        onClick = { freqFilter = days },
                        label = { Text("${days}× / tydzień") }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Brak planów dla tej częstotliwości — wybierz inną z chipów powyżej.",
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
