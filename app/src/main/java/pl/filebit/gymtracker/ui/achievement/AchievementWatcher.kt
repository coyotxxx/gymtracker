package pl.filebit.gymtracker.ui.achievement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wykrywa nowo odblokowane odznaki — zwraca tylko te, których ID jeszcze nie
 * zostało "zobaczone" przez user'a (zapisane w SharedPrefs).
 *
 * Pierwsze uruchomienie po imporcie/instalacji NIE wyświetla wszystkiego —
 * inicjalizuje seenIds wszystkimi obecnie odblokowanymi (cichy start).
 */
@Singleton
class AchievementWatcher @Inject constructor(
    @ApplicationContext context: Context,
    private val statsRepo: StatsRepository,
    private val profileRepo: UserProfileRepository
) {
    private val prefs = context.getSharedPreferences("achievement_watcher", Context.MODE_PRIVATE)

    /**
     * @return lista odznak które właśnie odblokował user (nigdy wcześniej nie pokazane).
     *         Pierwsze wywołanie zawsze zwraca pustą listę (cichy bootstrap).
     */
    suspend fun checkForNewlyUnlocked(): List<Achievement> {
        val target = profileRepo.get().daysPerWeek
        val all = runCatching { statsRepo.unlockedAchievements(target) }.getOrNull().orEmpty()
        val unlocked = all.filter { it.unlocked }

        val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        if (!initialized) {
            // Pierwszy raz — tylko zapisz aktualnie unlocked, nie pokazuj nic
            prefs.edit()
                .putStringSet(KEY_SEEN_IDS, unlocked.map { it.id }.toSet())
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            return emptyList()
        }

        val newlyUnlocked = unlocked.filter { it.id !in seen }
        if (newlyUnlocked.isNotEmpty()) {
            // Zapisz wszystkie obecne (nie tylko nowo zobaczone — żeby kolejne checki były spójne)
            val newSeen = unlocked.map { it.id }.toSet()
            prefs.edit().putStringSet(KEY_SEEN_IDS, newSeen).apply()
        }
        return newlyUnlocked
    }

    /** Reset (debug / test). */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SEEN_IDS = "seen_ids"
        private const val KEY_INITIALIZED = "initialized"
    }
}
