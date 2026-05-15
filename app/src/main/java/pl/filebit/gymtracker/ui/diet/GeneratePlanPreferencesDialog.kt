package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.filebit.gymtracker.ai.MealStyle
import pl.filebit.gymtracker.ai.MealStylePreferences
import pl.filebit.gymtracker.ai.PlanStyle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SelectableChip

/**
 * Bottom-sheet dialog wyboru stylu posiłków przed wygenerowaniem planu AI.
 * Pozwala userowi określić: globalny styl planu + per-slot styl + wolny tekst.
 *
 * Po kliknięciu "Wygeneruj" wraca MealStylePreferences do ViewModel.
 * "Pomiń" generuje plan z domyślnymi preferencjami (CLASSIC).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratePlanPreferencesDialog(
    mealsCount: Int,
    onGenerate: (MealStylePreferences) -> Unit,
    onDismiss: () -> Unit
) {
    var globalStyle by remember { mutableStateOf(PlanStyle.CLASSIC) }
    val slotStyles = remember { mutableStateOf<Map<MealType, MealStyle>>(emptyMap()) }
    var freeText by remember { mutableStateOf("") }

    val typesForSlots: List<MealType> = when (mealsCount) {
        2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
        5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                Text(
                    "✨ Jaki plan dnia wygenerować?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DarkOnSurface
                )
                Text(
                    "Wybierz styl — AI dopasuje rodzaje posiłków. Możesz pominąć i wygenerować klasyk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // === GLOBALNY STYL ===
                Text(
                    "Styl globalny",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlanStyle.entries.forEach { style ->
                        SelectableChip(
                            text = style.label,
                            selected = globalStyle == style,
                            onClick = { globalStyle = style }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // === PER SLOT ===
                Text(
                    "Preferencja per posiłek (opcjonalnie)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                typesForSlots.forEachIndexed { idx, type ->
                    Spacer(Modifier.height(8.dp))
                    val label = labelForSlotIdx(idx + 1, mealsCount)
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        slotChoicesFor(type).forEach { style ->
                            SelectableChip(
                                text = style.label,
                                selected = (slotStyles.value[type] ?: MealStyle.DEFAULT) == style,
                                onClick = {
                                    val newMap = slotStyles.value.toMutableMap()
                                    if (style == MealStyle.DEFAULT) newMap.remove(type)
                                    else newMap[type] = style
                                    slotStyles.value = newMap
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // v1.26.1: pole "Twoje uwagi do AI" — multiline 500 znaków
                Text(
                    "Twoje uwagi do AI (opcjonalnie)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { if (it.length <= 500) freeText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    placeholder = {
                        Text(
                            "np. Mam tylko 5 min na śniadanie, dziś bez nabiału, mocniejszy posiłek przedtreningowy, wolę kurczaka niż wołowinę…",
                            color = DarkOnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    maxLines = 6,
                    supportingText = {
                        Text(
                            "${freeText.length}/500 znaków • AI uwzględni jeśli sensowne",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = DarkOutline,
                        cursorColor = AccentOrange
                    )
                )

                }   // koniec scroll-owanego Column

                Spacer(Modifier.height(8.dp))

                // === ACTIONS (sticky, poza scrollem) ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                    Spacer(Modifier.height(0.dp))
                    TextButton(onClick = {
                        onGenerate(MealStylePreferences())  // domyślne (klasyk)
                    }) {
                        Text("Pomiń", color = DarkOnSurfaceVariant)
                    }
                    TextButton(onClick = {
                        onGenerate(
                            MealStylePreferences(
                                globalStyle = globalStyle,
                                slotStyles = slotStyles.value,
                                freeText = freeText.trim()
                            )
                        )
                    }) {
                        Text("✨ Wygeneruj", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun labelForSlotIdx(slot: Int, total: Int): String = when {
    total == 2 && slot == 1 -> "Śniadanie"
    total == 2 -> "Kolacja"
    total == 3 && slot == 1 -> "Śniadanie"
    total == 3 && slot == 2 -> "Obiad"
    total == 3 -> "Kolacja"
    total == 4 && slot == 1 -> "Śniadanie"
    total == 4 && slot == 2 -> "II Śniadanie"
    total == 4 && slot == 3 -> "Obiad"
    total == 4 -> "Kolacja"
    total == 5 && slot == 1 -> "Śniadanie"
    total == 5 && slot == 2 -> "II Śniadanie"
    total == 5 && slot == 3 -> "Obiad"
    total == 5 && slot == 4 -> "Podwieczorek"
    total == 5 -> "Kolacja"
    else -> "Posiłek $slot"
}

/** Sensowne style do pokazania per typ posiłku. */
private fun slotChoicesFor(type: MealType): List<MealStyle> = when (type) {
    MealType.BREAKFAST -> listOf(
        MealStyle.DEFAULT,
        MealStyle.OATMEAL,
        MealStyle.EGGS,
        MealStyle.SMOOTHIE,
        MealStyle.SANDWICH,
        MealStyle.COTTAGE
    )
    MealType.LUNCH -> listOf(
        MealStyle.DEFAULT,
        MealStyle.MEAT_RICE,
        MealStyle.BOWL,
        MealStyle.SALAD,
        MealStyle.SOUP,
        MealStyle.PASTA,
        MealStyle.SANDWICH
    )
    MealType.DINNER -> listOf(
        MealStyle.DEFAULT,
        MealStyle.COTTAGE,
        MealStyle.SALAD,
        MealStyle.FISH,
        MealStyle.EGGS,
        MealStyle.BOWL
    )
    MealType.SNACK -> listOf(
        MealStyle.DEFAULT,
        MealStyle.SMOOTHIE,
        MealStyle.SANDWICH,
        MealStyle.COTTAGE
    )
}
