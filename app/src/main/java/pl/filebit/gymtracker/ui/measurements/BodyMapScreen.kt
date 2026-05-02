package pl.filebit.gymtracker.ui.measurements

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.GymCard
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.ui.theme.ScreenHeader

@Composable
fun BodyMapScreen(
    initialGender: Gender,
    onBack: () -> Unit,
    onGoToMeasure: () -> Unit
) {
    var gender by remember { mutableStateOf(initialGender) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    ScreenHeader(title = "Mapa pomiarów", onBack = onBack)
                    Text(
                        "Wybierz gdzie mierzyć i sprawdź jak to robić poprawnie",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 0.dp, bottom = 4.dp)
                    )
                }
            }

            // Toggle Mężczyzna / Kobieta
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    GenderChip(
                        icon = Icons.Default.Male,
                        label = "Mężczyzna",
                        selected = gender == Gender.MALE,
                        onClick = { gender = Gender.MALE },
                        modifier = Modifier.weight(1f)
                    )
                    GenderChip(
                        icon = Icons.Default.Female,
                        label = "Kobieta",
                        selected = gender == Gender.FEMALE,
                        onClick = { gender = Gender.FEMALE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Sylwetka
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(18.dp))
                        .border(1.dp, DarkOutlineSoft, RoundedCornerShape(18.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            if (gender == Gender.MALE) R.drawable.body_male
                            else R.drawable.body_female
                        ),
                        contentDescription = "Sylwetka — punkty pomiarowe",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Sekcja "Jak mierzyć"
            item {
                GymCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Jak mierzyć",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = DarkOnSurface
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        InstructionRow(
                            icon = Icons.Default.Straighten,
                            text = "Mierz na luźno — taśma nie może uciskać"
                        )
                        InstructionRow(
                            icon = Icons.Default.SwapVert,
                            text = "Stań prosto i rozluźnij mięśnie"
                        )
                        InstructionRow(
                            icon = Icons.Default.Straighten,
                            text = "Zawsze w tym samym miejscu (porównujesz to samo)"
                        )
                        InstructionRow(
                            icon = Icons.Default.Schedule,
                            text = "O podobnej porze dnia (najlepiej rano, na czczo)"
                        )
                    }
                }
            }

            // Karty edukacyjne per partia
            item { LabelUp("Co dokładnie mierzyć", accent = true) }
            measurementGuides(gender).forEach { guide ->
                item(key = guide.title) {
                    GuideCard(guide = guide)
                }
            }
        }

        // Sticky CTA "Przejdź do pomiaru"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(AccentOrange, RoundedCornerShape(16.dp))
                    .clickable(onClick = onGoToMeasure)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Przejdź do pomiaru",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun GenderChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AccentOrange else Color.Transparent
    val fg = if (selected) Color.Black else DarkOnSurface
    Row(
        modifier = modifier
            .height(40.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun InstructionRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(AccentOrange.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = DarkOnSurface
        )
    }
}

private data class MeasurementGuide(
    val title: String,
    val description: String
)

private fun measurementGuides(gender: Gender): List<MeasurementGuide> {
    val common = listOf(
        MeasurementGuide(
            "Klatka piersiowa",
            "Taśma na linii sutków, równolegle do podłogi. Wdech do połowy."
        ),
        MeasurementGuide(
            "Talia",
            "Najwęższe miejsce między żebrami a biodrami, zwykle nad pępkiem."
        ),
        MeasurementGuide(
            "Pas",
            "Na wysokości pępka, równolegle do podłogi. Brzuch rozluźniony."
        ),
        MeasurementGuide(
            "Biodra",
            "Najszersze miejsce na wysokości pośladków."
        ),
        MeasurementGuide(
            "Udo",
            "Najszersze miejsce na górnej części uda. Mierz to samo udo zawsze."
        ),
        MeasurementGuide(
            "Łydka",
            "Najszersze miejsce na łydce, stopa płasko na podłodze."
        )
    )
    val maleExtra = listOf(
        MeasurementGuide(
            "Kark",
            "Tuż pod krtanią, najmniejszy obwód szyi."
        ),
        MeasurementGuide(
            "Ramię (rozluźnione)",
            "Środek ramienia, ręka opuszczona swobodnie wzdłuż ciała."
        ),
        MeasurementGuide(
            "Przedramię",
            "Najszersze miejsce poniżej łokcia, ręka rozluźniona."
        ),
        MeasurementGuide(
            "Nadgarstek",
            "Tuż pod kością nadgarstka — orientacja proporcji ciała."
        ),
        MeasurementGuide(
            "Staw skokowy",
            "Tuż nad kostką — trend pomocny przy retencji wody."
        )
    )
    val femaleExtra = listOf(
        MeasurementGuide(
            "Biceps (zgięty)",
            "Ramię zgięte 90°, mierz na szczycie napiętego bicepsa."
        )
    )
    return common + if (gender == Gender.MALE) maleExtra else femaleExtra
}

@Composable
private fun GuideCard(guide: MeasurementGuide) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AccentOrange.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Straighten,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                guide.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = DarkOnSurface
            )
            Text(
                guide.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}
