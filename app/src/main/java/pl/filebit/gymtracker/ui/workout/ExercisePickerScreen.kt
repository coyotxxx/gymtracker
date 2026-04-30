package pl.filebit.gymtracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.ScreenHeader

@Composable
fun ExercisePickerScreen(
    mode: String = "WORKOUT",
    onPicked: () -> Unit,
    onClose: () -> Unit,
    onPickedForPlan: (Long) -> Unit = {},
    vm: ExercisePickerViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val muscleFilter by vm.muscleFilter.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.workout_add_exercise),
                onBack = onClose
            )
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

            // Muscle filter chips
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

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(exercises, key = { it.id }) { ex ->
                    Card(
                        onClick = {
                            if (mode == "PLAN") vm.pickForPlan(ex, onPickedForPlan)
                            else vm.pickForWorkout(ex, onPicked)
                        },
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

private fun pl.filebit.gymtracker.data.entity.Equipment.displayName(): String = when (this) {
    pl.filebit.gymtracker.data.entity.Equipment.BARBELL -> "Sztanga"
    pl.filebit.gymtracker.data.entity.Equipment.DUMBBELLS -> "Hantle"
    pl.filebit.gymtracker.data.entity.Equipment.MACHINE -> "Maszyna"
    pl.filebit.gymtracker.data.entity.Equipment.CABLE -> "Wyciąg"
    pl.filebit.gymtracker.data.entity.Equipment.BODYWEIGHT -> "Ciężar ciała"
    pl.filebit.gymtracker.data.entity.Equipment.OTHER -> "Inne"
}
