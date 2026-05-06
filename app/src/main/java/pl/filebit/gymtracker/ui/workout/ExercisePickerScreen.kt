package pl.filebit.gymtracker.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SelectableChip

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
    val equipmentFilter by vm.equipmentFilter.collectAsStateWithLifecycle()
    val favoritesOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.workout_add_exercise),
                onBack = onClose
            )
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.picker_search), color = DarkOnSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkOnSurfaceVariant)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = DarkOutline,
                    cursorColor = AccentOrange
                )
            )

            Spacer(Modifier.height(12.dp))

            // Filtr "❤ tylko ulubione"
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectableChip(
                    text = if (favoritesOnly) "❤ Tylko ulubione" else "🤍 Filtruj ulubione",
                    selected = favoritesOnly,
                    onClick = { vm.setFavoritesOnly(!favoritesOnly) }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Filtr po partii mięśniowej
            FilterSectionLabel("Partia")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SelectableChip(
                        text = stringResource(R.string.picker_all),
                        selected = muscleFilter == null,
                        onClick = { vm.setMuscleFilter(null) }
                    )
                }
                items(MuscleGroup.entries.filter { it != MuscleGroup.OTHER }) { m ->
                    SelectableChip(
                        text = m.displayName(),
                        selected = muscleFilter == m,
                        onClick = { vm.setMuscleFilter(m) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Filtr po sprzęcie
            FilterSectionLabel("Sprzęt")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SelectableChip(
                        text = stringResource(R.string.picker_all),
                        selected = equipmentFilter == null,
                        onClick = { vm.setEquipmentFilter(null) }
                    )
                }
                items(Equipment.entries.filter { it != Equipment.OTHER }) { eq ->
                    SelectableChip(
                        text = eq.displayName(),
                        selected = equipmentFilter == eq,
                        onClick = { vm.setEquipmentFilter(eq) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "${exercises.size} ĆWICZEŃ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(exercises, key = { it.id }) { ex ->
                    PickerExerciseRow(
                        name = ex.name,
                        summary = shortSummary(ex.description),
                        muscle = ex.primaryMuscle.displayName(),
                        equipment = ex.equipment.displayName(),
                        onClick = {
                            if (mode == "PLAN") vm.pickForPlan(ex, onPickedForPlan)
                            else vm.pickForWorkout(ex, onPicked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = DarkOnSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
    )
}

/**
 * Wyciąga krótki tagline z pełnego opisu — pierwsze "zdanie semantyczne"
 * (do przecinka/myślnika/kropki), max 80 znaków.
 */
private fun shortSummary(desc: String, max: Int = 80): String? {
    if (desc.isBlank()) return null
    val text = desc.trim()
    val cutIdx = text.indexOfAny(charArrayOf('.', '!', '?', ',', '—', ':'))
    val first = if (cutIdx > 0) text.substring(0, cutIdx).trim() else text
    return if (first.length <= max) first
    else first.take(max - 1).trimEnd() + "…"
}

@Composable
private fun PickerExerciseRow(
    name: String,
    summary: String?,
    muscle: String,
    equipment: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = DarkOnSurface,
                    maxLines = 2
                )
                if (summary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DarkOnSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "$muscle · $equipment",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 1
                )
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
