package pl.filebit.gymtracker.ui.achievement

import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wykrywa świeżo odblokowane odznaki na podstawie `Achievement.unlockedAt`.
 *
 * Filozofia (v1.11.2):
 * - StatsRepository.unlockedAchievements() ustawia `unlockedAt = now` przy
 *   PIERWSZYM wykryciu odblokowania (insertIfNew do UnlockedAchievement).
 * - Po finish workout → wywołujemy watcher → bierzemy wszystkie z `unlockedAt`
 *   nowszym niż 5 minut → te są "świeże" i pokazujemy modal.
 *
 * Brak potrzeby SharedPrefs / bootstrap — odznaki mają persistentny timestamp w DB.
 * Po imporcie testowych danych (60 dni temu) — wszystkie istniejące odznaki mają
 * stary unlockedAt → modal się NIE pokazuje. Świeżo odblokowane (po treningu)
 * mają unlockedAt = teraz → pokazują się.
 */
@Singleton
class AchievementWatcher @Inject constructor(
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository
) {
    /**
     * @return lista odznak właśnie odblokowanych (unlockedAt < FRESH_WINDOW_MS temu).
     */
    suspend fun checkForNewlyUnlocked(): List<Achievement> {
        val target = profileRepo.get().daysPerWeek
        val all = runCatching { statsRepo.unlockedAchievements(target) }.getOrNull().orEmpty()
        val freshThreshold = System.currentTimeMillis() - FRESH_WINDOW_MS
        return all.filter { a ->
            a.unlocked && (a.unlockedAt ?: 0L) >= freshThreshold
        }.sortedBy { it.unlockedAt }   // najstarsze pierwsze (jakby się "odblokowywały po kolei")
    }

    companion object {
        /** Okno "świeżości" — odznaka jest "nowa" przez 5 minut od pierwszego wykrycia. */
        private const val FRESH_WINDOW_MS = 5L * 60 * 1000
    }
}
