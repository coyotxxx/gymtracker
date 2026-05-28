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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pl.filebit.gymtracker.ui.theme.SelectableChip
import pl.filebit.gymtracker.util.formatWeight

@Composable
fun ExerciseLibraryScreen(
    onOpenDetail: (Long) -> Unit,
    vm: ExerciseLibraryViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val muscleFilter by vm.muscleFilter.collectAsStateWithLifecycle()
    val equipmentFilter by vm.equipmentFilter.collectAsStateWithLifecycle()
    val favoritesOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // v2.4.0: podczas wpisywania (query nieblank) chowamy filtry — klawiatura
        // zabiera dół, filtry zabierały górę, wyniki ściskały się do wąskiego paska.
        // Teraz w trybie wyszukiwania wyniki dostają całą dostępną wysokość.
        val searching = query.isNotBlank()

        if (!searching) {
            // Filtr "❤ tylko ulubione"
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(
                        if (favoritesOnly) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        if (favoritesOnly) AccentOrange else DarkOutlineSoft,
                        RoundedCornerShape(50)
                    )
                    .clickable { vm.setFavoritesOnly(!favoritesOnly) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (favoritesOnly) "❤ Tylko ulubione (${exercises.size})" else "🤍 Wszystkie / pokaż ulubione",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (favoritesOnly) AccentOrange else DarkOnSurface
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
                        text = "Wszystkie",
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
                        text = "Wszystkie",
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
        }

        Text(
            if (searching) "${exercises.size} WYNIKÓW DLA „$query”" else "${exercises.size} ĆWICZEŃ",
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),  // v2.4.1: weight zamiast fillMaxSize+imePadding (czarny pas)
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(exercises, key = { it.id }) { ex ->
                val pr = prs[ex.id]
                ExerciseRow(
                    name = ex.name,
                    queryHighlight = query,
                    summary = shortSummary(ex.description),
                    muscle = ex.primaryMuscle.displayName(),
                    equipment = ex.equipment.displayName(),
                    prMaxWeight = pr?.maxWeightKg,
                    prReps = pr?.repsAtMaxWeight,
                    isFavorite = ex.isFavorite,
                    onClick = { onOpenDetail(ex.id) },
                    onToggleFavorite = { vm.toggleFavorite(ex.id, !ex.isFavorite) }
                )
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
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp, top = 0.dp)
    )
}

/**
 * Wyciąga krótki tagline z pełnego opisu — pierwsze "zdanie semantyczne"
 * (do przecinka/myślnika/kropki), max 80 znaków. Pomaga amatorowi zrozumieć
 * od razu do czego służy ćwiczenie, bez wchodzenia w szczegóły.
 */
private fun shortSummary(desc: String, max: Int = 80): String? {
    if (desc.isBlank()) return null
    val text = desc.trim()
    // Dziel po pierwszym z: . ! ? , — :
    val cutIdx = text.indexOfAny(charArrayOf('.', '!', '?', ',', '—', ':'))
    val first = if (cutIdx > 0) text.substring(0, cutIdx).trim() else text
    return if (first.length <= max) first
    else first.take(max - 1).trimEnd() + "…"
}

@Composable
private fun ExerciseRow(
    name: String,
    summary: String?,
    muscle: String,
    equipment: String,
    prMaxWeight: Double?,
    prReps: Int?,
    isFavorite: Boolean = false,
    queryHighlight: String = "",
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null
) {
    val highlightedName = remember(name, queryHighlight) {
        buildHighlightedName(name, queryHighlight)
    }
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
                    highlightedName,
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
            // Heart icon (toggle favorite)
            if (onToggleFavorite != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isFavorite) "❤" else "🤍",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// v1.20.2 — usunięto duplikaty MuscleGroup.displayName() i Equipment.displayName();
// używamy method z entity/Exercise.kt (enum class).

/**
 * v2.3.0 — buduje AnnotatedString z pogrubionym + akcentowanym fragmentem
 * pasujacym do query. Insensitive na wielkosc liter i diakrytyki (ASCII fold).
 */
private fun buildHighlightedName(name: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return androidx.compose.ui.text.AnnotatedString(name)

    val combiningMarks = Regex("[\\u0300-\\u036f]+")
    fun fold(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace("ł", "l").replace("Ł", "L")
        .lowercase()

    val nameFolded = fold(name)
    val queryFolded = fold(query)
    val idx = nameFolded.indexOf(queryFolded)
    if (idx < 0) return androidx.compose.ui.text.AnnotatedString(name)

    return androidx.compose.ui.text.buildAnnotatedString {
        append(name.substring(0, idx))
        withStyle(
            androidx.compose.ui.text.SpanStyle(
                color = AccentOrange,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
            )
        ) {
            append(name.substring(idx, (idx + query.length).coerceAtMost(name.length)))
        }
        if (idx + query.length < name.length) {
            append(name.substring(idx + query.length))
        }
    }
}
