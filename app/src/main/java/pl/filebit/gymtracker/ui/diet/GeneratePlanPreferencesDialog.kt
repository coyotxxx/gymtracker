package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import pl.filebit.gymtracker.ai.CookingDevice
import pl.filebit.gymtracker.ai.MealStyle
import pl.filebit.gymtracker.ai.MealStylePreferences
import pl.filebit.gymtracker.ai.PlanStyle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.SelectableChip

/**
 * Okno wyboru stylu posiłków przed wygenerowaniem planu AI.
 *
 * v1.29 (Wariant B): styl globalny rozdzielony na „Charakter dań" (jeden,
 * chip-pigułka) i „Dodatkowo" (modyfikatory, multi). Sekcja urządzeń
 * kuchennych. Posiłki per slot zwinięte do akordeonu.
 *
 * Po kliknięciu „Wygeneruj" wraca MealStylePreferences do ViewModel.
 * „Pomiń" generuje plan z domyślnymi preferencjami (CLASSIC).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratePlanPreferencesDialog(
    mealsCount: Int,
    onGenerate: (MealStylePreferences) -> Unit,
    onDismiss: () -> Unit,
    /** v1.27.1: ostatnio użyte preferencje — okno otwiera się z nimi. */
    initial: MealStylePreferences = MealStylePreferences()
) {
    var character by remember { mutableStateOf(initial.character) }
    var modifiers by remember { mutableStateOf(initial.modifiers) }
    var devices by remember { mutableStateOf(initial.devices) }
    var slotStyles by remember { mutableStateOf(initial.slotStyles) }
    var freeText by remember { mutableStateOf(initial.freeText) }
    var preferFavorites by remember { mutableStateOf(initial.preferFavorites) }
    var expandedIdx by remember { mutableStateOf(-1) }

    // v2.73.0: jedno źródło slotów (obsługa 2-8); etykiety „Posiłek N".
    val typesForSlots: List<MealType> = pl.filebit.gymtracker.util.MealSlots.typesFor(mealsCount)

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
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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

                    // === CHARAKTER DAŃ (jeden) ===
                    SectionLabel("Charakter dań")
                    SectionHint("Wybierz jeden — nadaje ton wszystkim posiłkom.")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlanStyle.characters.forEach { style ->
                            SelectableChip(
                                text = style.label,
                                selected = character == style,
                                pill = true,
                                onClick = { character = style }
                            )
                        }
                    }

                    // === DODATKOWO (modyfikatory, multi) ===
                    SectionLabel("Dodatkowo")
                    SectionHint("Możesz łączyć dowolnie — dokładają się do charakteru.")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlanStyle.modifiers.forEach { style ->
                            SelectableChip(
                                text = style.label,
                                selected = style in modifiers,
                                onClick = {
                                    modifiers = if (style in modifiers) modifiers - style
                                        else modifiers + style
                                }
                            )
                        }
                    }

                    // === URZĄDZENIA KUCHENNE (multi) ===
                    SectionLabel("Moje urządzenia kuchenne")
                    SectionHint("Możesz mieć kilka — zapamiętuje się na kolejne razy.")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CookingDevice.entries.forEach { device ->
                            SelectableChip(
                                text = device.label,
                                selected = device in devices,
                                onClick = {
                                    devices = if (device in devices) devices - device
                                        else devices + device
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "AI dobierze instrukcje pod Twój sprzęt i oznaczy przepis chipem. " +
                            "Danie niepasujące do urządzenia (sałatka, koktajl) — zwykły przepis.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = DarkOnSurfaceVariant
                    )

                    // === PER POSIŁEK (akordeon) ===
                    SectionLabel("Preferencja per posiłek (opcjonalnie)")
                    Spacer(Modifier.height(5.dp))
                    typesForSlots.forEachIndexed { idx, type ->
                        MealSlotRow(
                            label = pl.filebit.gymtracker.util.MealSlots.label(idx + 1),
                            type = type,
                            current = slotStyles[type] ?: MealStyle.DEFAULT,
                            expanded = expandedIdx == idx,
                            onToggleExpand = { expandedIdx = if (expandedIdx == idx) -1 else idx },
                            onPick = { style ->
                                slotStyles = slotStyles.toMutableMap().apply {
                                    if (style == MealStyle.DEFAULT) remove(type) else put(type, style)
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    // === ULUBIONE PRODUKTY ===
                    SectionLabel("Ulubione produkty")
                    Spacer(Modifier.height(5.dp))
                    SelectableChip(
                        text = if (preferFavorites) "❤ Generuję z ulubionych" else "❤ Generuj z ulubionych",
                        selected = preferFavorites,
                        onClick = { preferFavorites = !preferFavorites }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI w pierwszej kolejności użyje produktów oznaczonych ❤. " +
                            "Jeśli z samych ulubionych nie wyjdą makro — dobierze pozostałe.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DarkOnSurfaceVariant
                    )

                    // === UWAGI ===
                    SectionLabel("Twoje uwagi do AI (opcjonalnie)")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { if (it.length <= 500) freeText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        placeholder = {
                            Text(
                                "np. Mam tylko 5 min na śniadanie, dziś bez nabiału, mocniejszy posiłek przedtreningowy…",
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

                // === ACTIONS (sticky) ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                    TextButton(onClick = { onGenerate(MealStylePreferences()) }) {
                        Text("Pomiń", color = DarkOnSurfaceVariant)
                    }
                    TextButton(onClick = {
                        onGenerate(
                            MealStylePreferences(
                                character = character,
                                modifiers = modifiers,
                                slotStyles = slotStyles,
                                devices = devices,
                                freeText = freeText.trim(),
                                preferFavorites = preferFavorites
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

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = DarkOnSurface
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = DarkOnSurfaceVariant,
        modifier = Modifier.padding(top = 1.dp, bottom = 6.dp)
    )
}

/** Zwijany wiersz posiłku — zwinięty pokazuje wybór, rozwinięty chipy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealSlotRow(
    label: String,
    type: MealType,
    current: MealStyle,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPick: (MealStyle) -> Unit
) {
    val isDefault = current == MealStyle.DEFAULT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (expanded) AccentOrange.copy(alpha = 0.05f) else DarkSurface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (expanded) AccentOrange.copy(alpha = 0.32f) else DarkOutline,
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (isDefault) "Domyślny" else current.label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Bold
                ),
                color = if (isDefault) DarkOnSurfaceVariant else AccentOrange
            )
            Spacer(Modifier.width(9.dp))
            Text(
                if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                slotChoicesFor(type).forEach { style ->
                    SelectableChip(
                        text = style.label,
                        selected = current == style,
                        onClick = { onPick(style) }
                    )
                }
            }
        }
    }
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
