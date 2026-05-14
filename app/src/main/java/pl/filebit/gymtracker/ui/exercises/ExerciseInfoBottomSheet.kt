package pl.filebit.gymtracker.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.util.ExerciseDbLabels

/**
 * v1.25.4 — bottom sheet "Jak wykonać ćwiczenie?" do otwierania z aktywnego treningu
 * (CoachWorkoutScreen, ActiveWorkoutScreen) bez wychodzenia z sesji.
 *
 * Zawartość:
 *  - 🎬 GIF animacja wykonania (ExerciseDB CDN, Coil cache)
 *  - 📋 Technika krok po kroku — PL z cache, EN fallback
 *  - 🎯 Główne mięśnie + 💪 drugorzędne (z PL mapping)
 *  - 🛠 Sprzęt
 *
 * Pojawia się od dołu z handle (Material 3 ModalBottomSheet). User swipe w dół
 * lub tap poza sheet → zamyka.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseInfoBottomSheet(
    exercise: Exercise,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        contentColor = DarkOnSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tytuł
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface
            )

            // GIF — jeśli ćwiczenie ma externalId z ExerciseDB
            if (!exercise.gifUrl.isNullOrBlank()) {
                Column {
                    Text(
                        "🎬 ANIMACJA WYKONANIA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    coil3.compose.AsyncImage(
                        model = exercise.gifUrl,
                        contentDescription = "Animacja: ${exercise.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 320.dp)
                            .background(DarkSurface, RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }

            // Technika krok po kroku
            val plSteps: List<String> = remember(exercise.instructionsPlJson) {
                runCatching {
                    exercise.instructionsPlJson?.let {
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(it)
                            as? kotlinx.serialization.json.JsonArray
                        arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    }
                }.getOrNull().orEmpty()
            }
            val enSteps: List<String> = remember(exercise.instructionsEnJson) {
                runCatching {
                    exercise.instructionsEnJson?.let {
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(it)
                            as? kotlinx.serialization.json.JsonArray
                        arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    }
                }.getOrNull().orEmpty()
            }
            val showPl = plSteps.isNotEmpty()
            val steps = if (showPl) plSteps
                else enSteps.map { it.removePrefix("Step:").trimStart { it.isDigit() || it == ' ' } }

            if (steps.isNotEmpty()) {
                Column {
                    Text(
                        "📋 TECHNIKA KROK PO KROKU" + if (!showPl) " (EN)" else "",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    steps.forEachIndexed { idx, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${idx + 1}.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = AccentOrange,
                                modifier = Modifier.size(width = 24.dp, height = 22.dp)
                            )
                            Text(
                                step.trim(),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = DarkOnSurface
                            )
                        }
                        if (idx < steps.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // Mięśnie + sprzęt z ExerciseDB (PL mapping)
            val targetMuscles = exercise.targetMusclesCsv?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val secondaryMuscles = exercise.secondaryMusclesCsv?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val equipment = exercise.equipmentDbCsv?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            if (targetMuscles.isNotEmpty() || secondaryMuscles.isNotEmpty() || equipment.isNotEmpty()) {
                if (targetMuscles.isNotEmpty()) {
                    Column {
                        Text(
                            "🎯 GŁÓWNE MIĘŚNIE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                            ),
                            color = DarkOnSurfaceVariant
                        )
                        Text(
                            targetMuscles.joinToString(", ") { ExerciseDbLabels.muscle(it) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurface
                        )
                    }
                }
                if (secondaryMuscles.isNotEmpty()) {
                    Column {
                        Text(
                            "💪 MIĘŚNIE DRUGORZĘDNE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                            ),
                            color = DarkOnSurfaceVariant
                        )
                        Text(
                            secondaryMuscles.joinToString(", ") { ExerciseDbLabels.muscle(it) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurface
                        )
                    }
                }
                if (equipment.isNotEmpty()) {
                    Column {
                        Text(
                            "🛠 SPRZĘT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                            ),
                            color = DarkOnSurfaceVariant
                        )
                        Text(
                            equipment.joinToString(", ") { ExerciseDbLabels.equipment(it) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurface
                        )
                    }
                }
            }

            // Fallback: jeśli brak danych z ExerciseDB ale ma user.description
            if (exercise.gifUrl.isNullOrBlank() && steps.isEmpty() && exercise.description.isNotBlank()) {
                Column {
                    Text(
                        "OPIS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        exercise.description,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = DarkOnSurface
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
