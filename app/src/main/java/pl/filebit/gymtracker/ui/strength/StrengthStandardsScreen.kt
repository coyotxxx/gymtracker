package pl.filebit.gymtracker.ui.strength

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.repository.StrengthEvaluation
import pl.filebit.gymtracker.data.strength.StrengthLevel
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrengthStandardsScreen(
    onBack: () -> Unit,
    vm: StrengthStandardsViewModel = hiltViewModel()
) {
    val evaluations by vm.evaluations.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    var bodyweightText by remember(profile.bodyweightKg) {
        mutableStateOf(profile.bodyweightKg?.let { "%.1f".format(it).replace(',', '.') } ?: "")
    }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.strength_title),
                    onBack = onBack
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.strength_inputs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        // Płeć
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(Gender.entries.toList()) { g ->
                                FilterChip(
                                    selected = gender == g,
                                    onClick = {
                                        gender = g
                                        vm.saveBodyweightAndGender(
                                            bodyweightText.replace(',', '.').toDoubleOrNull(),
                                            g
                                        )
                                    },
                                    label = {
                                        Text(when (g) {
                                            Gender.MALE -> stringResource(R.string.gender_male)
                                            Gender.FEMALE -> stringResource(R.string.gender_female)
                                        })
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Bodyweight
                        OutlinedTextField(
                            value = bodyweightText,
                            onValueChange = { v ->
                                bodyweightText = v
                                vm.saveBodyweightAndGender(
                                    v.replace(',', '.').toDoubleOrNull(),
                                    gender
                                )
                            },
                            label = { Text(stringResource(R.string.strength_bodyweight)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.strength_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(evaluations, key = { it.standard.exerciseNamePrefix }) { ev ->
                EvaluationCard(ev)
            }
        }
    }
}

@Composable
private fun EvaluationCard(ev: StrengthEvaluation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                ev.exercise?.name ?: ev.standard.exerciseNamePrefix,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            if (!ev.hasData) {
                Text(
                    stringResource(R.string.strength_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // Aktualne 1RM + ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "1RM: ${formatWeight(ev.current1RMKg)} kg",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "×${"%.2f".format(ev.ratio).replace(',', '.')} BW",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            // Belka 5 progów
            LevelBar(ev)

            Spacer(Modifier.height(8.dp))

            // Etykieta poziomu
            Box(
                modifier = Modifier
                    .background(
                        color = ev.level.color().copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    ev.level.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ev.level.color()
                )
            }

            ev.nextLevelKg?.let { delta ->
                if (delta > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.strength_next_level, formatWeight(delta)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBar(ev: StrengthEvaluation) {
    val pos = (ev.ratio / ev.eliteRatio).coerceIn(0.0, 1.05).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(5.dp)
            )
    ) {
        LinearProgressIndicator(
            progress = { pos.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = ev.level.color(),
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun StrengthLevel.color(): Color = when (this) {
    StrengthLevel.BELOW_BEGINNER -> Color(0xFF9E9E9E)
    StrengthLevel.BEGINNER -> Color(0xFFEF6C00)
    StrengthLevel.NOVICE -> Color(0xFFFB8C00)
    StrengthLevel.INTERMEDIATE -> Color(0xFF2E7D32)
    StrengthLevel.ADVANCED -> Color(0xFF1565C0)
    StrengthLevel.ELITE -> Color(0xFF6A1B9A)
}

@Composable
private fun StrengthLevel.label(): String = when (this) {
    StrengthLevel.BELOW_BEGINNER -> stringResource(R.string.strength_level_below_beginner)
    StrengthLevel.BEGINNER -> stringResource(R.string.strength_level_beginner)
    StrengthLevel.NOVICE -> stringResource(R.string.strength_level_novice)
    StrengthLevel.INTERMEDIATE -> stringResource(R.string.strength_level_intermediate)
    StrengthLevel.ADVANCED -> stringResource(R.string.strength_level_advanced)
    StrengthLevel.ELITE -> stringResource(R.string.strength_level_elite)
}

