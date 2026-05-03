package pl.filebit.gymtracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton

private val WELLBEING_EMOJI = listOf("😞", "😕", "😐", "🙂", "💪")
private val WELLBEING_LABEL = listOf("źle", "słabo", "ok", "dobrze", "świetnie")

private val PAIN_GROUPS: List<Pair<MuscleGroup, String>> = listOf(
    MuscleGroup.CHEST to "Klatka",
    MuscleGroup.BACK to "Plecy",
    MuscleGroup.SHOULDERS to "Barki",
    MuscleGroup.BICEPS to "Biceps",
    MuscleGroup.TRICEPS to "Triceps",
    MuscleGroup.QUADS to "Czworogłowe",
    MuscleGroup.HAMSTRINGS to "Dwugłowe",
    MuscleGroup.GLUTES to "Pośladki",
    MuscleGroup.CALVES to "Łydki",
    MuscleGroup.CORE to "Brzuch",
    MuscleGroup.OTHER to "Inne"
)

/**
 * Bottom-sheet zbierający feedback po zakończonym treningu:
 * — wellbeing (1-5 emoji)
 * — opcjonalnie ból (partia + krótki opis)
 *
 * Dane idą do Workout.wellbeingRating/painArea/painNotes — AI używa ich
 * do wykrywania regenercji i sugerowania zmian planu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostWorkoutFeedbackSheet(
    onSkip: () -> Unit,
    onSave: (rating: Int?, painArea: String?, painNotes: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rating by remember { mutableStateOf<Int?>(null) }
    var hasPain by remember { mutableStateOf(false) }
    var painArea by remember { mutableStateOf<MuscleGroup?>(null) }
    var painNotes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = sheetState,
        containerColor = DarkBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Jak się czujesz po treningu?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Twój feedback pomoże AI dopasować kolejne treningi i wykryć potencjalne kontuzje.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // Wellbeing 1-5
            Text(
                "Samopoczucie",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..5).forEach { v ->
                    val sel = rating == v
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(
                                if (sel) AccentOrange.copy(alpha = 0.20f) else DarkSurface,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                if (sel) 1.5.dp else 1.dp,
                                if (sel) AccentOrange else DarkOutlineSoft,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { rating = v },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(WELLBEING_EMOJI[v - 1], fontSize = 18.sp)
                            Text(
                                WELLBEING_LABEL[v - 1],
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.4.sp
                                ),
                                color = if (sel) AccentOrange else DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Pain toggle
            Text(
                "Czy odczuwałeś ból?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = AccentOrange
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YesNoChip(text = "Nie", selected = !hasPain) {
                    hasPain = false
                    painArea = null
                    painNotes = ""
                }
                YesNoChip(text = "Tak", selected = hasPain, danger = true) {
                    hasPain = true
                }
            }

            if (hasPain) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Gdzie boli?",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // Wrap-row pillów
                FlowRow(
                    items = PAIN_GROUPS,
                    selected = painArea,
                    onSelect = { painArea = it }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = painNotes,
                    onValueChange = { painNotes = it },
                    label = { Text("Opis (opcjonalnie)", color = DarkOnSurfaceVariant) },
                    placeholder = {
                        Text(
                            "np. ostry ból przy pełnym zakresie",
                            color = DarkOnSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(20.dp))

            GymPrimaryButton(
                onClick = {
                    onSave(
                        rating,
                        painArea?.name,
                        painNotes.takeIf { it.isNotBlank() }
                    )
                },
                text = "Zapisz",
                leadingIcon = Icons.Default.AutoAwesome,
                enabled = rating != null || hasPain
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) {
                    Text("Pomiń", color = DarkOnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun YesNoChip(
    text: String,
    selected: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = if (danger) ErrorRed else AccentOrange
    val bg = if (selected) activeColor.copy(alpha = 0.18f) else DarkSurface
    val border = if (selected) activeColor.copy(alpha = 0.5f) else DarkOutlineSoft
    val fg = if (selected) activeColor else DarkOnSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = fg
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    items: List<Pair<MuscleGroup, String>>,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (group, label) ->
            val sel = selected == group
            Box(
                modifier = Modifier
                    .background(
                        if (sel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        if (sel) AccentOrange.copy(alpha = 0.5f) else DarkOutlineSoft,
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(group) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (sel) AccentOrange else DarkOnSurfaceVariant
                )
            }
        }
    }
}
