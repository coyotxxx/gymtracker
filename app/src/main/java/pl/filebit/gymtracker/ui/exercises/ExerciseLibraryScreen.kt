package pl.filebit.gymtracker.ui.exercises

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.util.formatWeight

@Composable
fun ExerciseLibraryScreen(
    onOpenDetail: (Long) -> Unit,
    vm: ExerciseLibraryViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val muscleFilter by vm.muscleFilter.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ćwiczenia",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text("Szukaj ćwiczenia…", color = DarkOnSurfaceVariant)
            },
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

        // Chips per partia
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MuscleChip(
                    text = "Wszystkie",
                    selected = muscleFilter == null,
                    onClick = { vm.setMuscleFilter(null) }
                )
            }
            items(MuscleGroup.entries.filter { it != MuscleGroup.OTHER }) { m ->
                MuscleChip(
                    text = m.displayName(),
                    selected = muscleFilter == m,
                    onClick = { vm.setMuscleFilter(m) }
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(exercises, key = { it.id }) { ex ->
                val pr = prs[ex.id]
                ExerciseRow(
                    name = ex.name,
                    muscle = ex.primaryMuscle.displayName(),
                    equipment = ex.equipment.displayName(),
                    prMaxWeight = pr?.maxWeightKg,
                    prReps = pr?.repsAtMaxWeight,
                    onClick = { onOpenDetail(ex.id) }
                )
            }
        }
    }
}

@Composable
private fun MuscleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) AccentOrange.copy(alpha = 0.12f) else DarkSurfaceVariant
    val fg = if (selected) AccentOrange else DarkOnSurfaceVariant
    val borderColor = if (selected) AccentOrange.copy(alpha = 0.50f) else Color.Transparent
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun ExerciseRow(
    name: String,
    muscle: String,
    equipment: String,
    prMaxWeight: Double?,
    prReps: Int?,
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
            // Ikona w czarnym kwadracie z żółtym akcentem
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        AccentOrange.copy(alpha = 0.10f),
                        RoundedCornerShape(10.dp)
                    ),
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
            // Środek: nazwa + tagi
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
                Spacer(Modifier.height(2.dp))
                Text(
                    "$muscle · $equipment",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 1
                )
            }
            // Prawo: PR (kg × reps) + chip PR
            if (prMaxWeight != null && prMaxWeight > 0 && prReps != null) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatWeight(prMaxWeight),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = DarkOnSurface
                        )
                        Text(
                            " kg × $prReps",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = DarkOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, AccentOrange.copy(alpha = 0.50f), RoundedCornerShape(50))
                            .background(AccentOrange.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "🏆 PR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = AccentOrange
                        )
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
