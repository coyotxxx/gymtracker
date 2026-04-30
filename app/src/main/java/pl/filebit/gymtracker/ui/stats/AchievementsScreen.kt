package pl.filebit.gymtracker.ui.stats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.data.repository.AchievementCategory
import pl.filebit.gymtracker.data.repository.AchievementLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    vm: StatsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val achievements = state.achievements
    val unlockedCount = achievements.count { it.unlocked }

    Scaffold(
        containerColor = pl.filebit.gymtracker.ui.theme.DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Odznaki ($unlockedCount/${achievements.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = pl.filebit.gymtracker.ui.theme.DarkBg,
                    titleContentColor = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                    navigationIconContentColor = pl.filebit.gymtracker.ui.theme.DarkOnSurface
                )
            )
        }
    ) { padding ->
        if (achievements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Brak odznak — zacznij trening żeby je zdobywać!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        val grouped = achievements.groupBy { it.category }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hero header: X / Y ZDOBYTYCH
            item {
                AchievementsHero(unlocked = unlockedCount, total = achievements.size)
            }
            // Kolejność kategorii zgodna z definicją enuma (z generalnymi na końcu)
            AchievementCategory.entries.forEach { cat ->
                val items = grouped[cat].orEmpty()
                if (items.isEmpty()) return@forEach
                val unlockedInCat = items.count { it.unlocked }
                item(key = "header_${cat.name}") {
                    CategorySectionHeader(cat, unlockedInCat, items.size)
                }
                items(items.size, key = { idx -> items[idx].id }) { idx ->
                    BigAchievementRow(items[idx])
                }
                item(key = "spacer_${cat.name}") {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AchievementsHero(unlocked: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$unlocked",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = pl.filebit.gymtracker.ui.theme.AccentOrange
            )
            Text(
                " / $total",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "ODZNAK ZDOBYTYCH",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            ),
            color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun CategorySectionHeader(
    category: AchievementCategory,
    unlocked: Int,
    total: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${category.emoji}  ${category.labelPl}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$unlocked / $total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BigAchievementRow(a: Achievement) {
    val levelColor = a.level.color()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (a.unlocked) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji w kółku w kolorze levelu
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (a.unlocked) levelColor.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    a.emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (a.unlocked) Color.Unspecified
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = if (a.unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LevelBadge(a.level, faded = !a.unlocked)
                }
                Text(
                    a.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!a.unlocked) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { a.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = levelColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "${a.currentValue} / ${a.targetValue}  (${a.progress}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: AchievementLevel, faded: Boolean) {
    val color = level.color().copy(alpha = if (faded) 0.4f else 1f)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = if (faded) 0.15f else 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            level.labelPl,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun AchievementLevel.color(): Color = when (this) {
    AchievementLevel.BRONZE -> Color(0xFFCD7F32)
    AchievementLevel.SILVER -> Color(0xFFB0B0B5)
    AchievementLevel.GOLD -> Color(0xFFFFB300)
    AchievementLevel.PLATINUM -> Color(0xFF7FE3FF)
}
