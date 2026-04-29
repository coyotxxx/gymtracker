package pl.filebit.gymtracker.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onOpenDetail: (Long) -> Unit,
    vm: ExerciseLibraryViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val muscleFilter by vm.muscleFilter.collectAsStateWithLifecycle()
    val equipmentFilter by vm.equipmentFilter.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_exercises)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.picker_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = muscleFilter == null,
                        onClick = { vm.setMuscleFilter(null) },
                        label = { Text(stringResource(R.string.picker_all)) }
                    )
                }
                items(MuscleGroup.entries.filter { it != MuscleGroup.OTHER }) { m ->
                    FilterChip(
                        selected = muscleFilter == m,
                        onClick = { vm.setMuscleFilter(m) },
                        label = { Text(m.displayName()) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = equipmentFilter == null,
                        onClick = { vm.setEquipmentFilter(null) },
                        label = { Text(stringResource(R.string.library_equipment_all)) }
                    )
                }
                items(Equipment.entries.filter { it != Equipment.OTHER }) { e ->
                    FilterChip(
                        selected = equipmentFilter == e,
                        onClick = { vm.setEquipmentFilter(e) },
                        label = { Text(e.displayName()) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "${exercises.size} ćwiczeń",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(exercises, key = { it.id }) { ex ->
                    val pr = prs[ex.id]
                    Card(
                        onClick = { onOpenDetail(ex.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${ex.primaryMuscle.displayName()} · ${ex.equipment.displayName()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pr != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(
                                        R.string.exercise_pr_line,
                                        formatWeight(pr.maxWeightKg),
                                        pr.repsAtMaxWeight,
                                        formatWeight(pr.estimated1RM)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (ex.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "📝 ${ex.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.CHEST -> "Klatka"
    MuscleGroup.BACK -> "Plecy"
    MuscleGroup.SHOULDERS -> "Barki"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.QUADS -> "Czworogłowe"
    MuscleGroup.HAMSTRINGS -> "Dwugłowe"
    MuscleGroup.GLUTES -> "Pośladki"
    MuscleGroup.CALVES -> "Łydki"
    MuscleGroup.CORE -> "Brzuch"
    MuscleGroup.CARDIO -> "Cardio"
    MuscleGroup.OTHER -> "Inne"
}

private fun Equipment.displayName(): String = when (this) {
    Equipment.BARBELL -> "Sztanga"
    Equipment.DUMBBELLS -> "Hantle"
    Equipment.MACHINE -> "Maszyna"
    Equipment.CABLE -> "Wyciąg"
    Equipment.BODYWEIGHT -> "Ciężar ciała"
    Equipment.OTHER -> "Inne"
}
