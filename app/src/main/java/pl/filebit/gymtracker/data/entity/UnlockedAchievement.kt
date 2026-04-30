package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persyst odblokowanej odznaki — zachowuje moment odblokowania (timestamp)
 * pomiędzy uruchomieniami. Definicja samej odznaki (tytuł, próg, etc.)
 * pozostaje hardcoded w AchievementDefinitions — w DB trzymamy tylko fakt
 * odblokowania i kiedy.
 */
@Entity(tableName = "unlocked_achievements")
data class UnlockedAchievement(
    @PrimaryKey val code: String,
    val unlockedAt: Long,
    val valueAt: Double = 0.0   // wartość w momencie odblokowania (np. 105 kg)
)
