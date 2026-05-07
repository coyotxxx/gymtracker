package pl.filebit.gymtracker.ui.achievement

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.Achievement
import javax.inject.Inject

sealed interface AchievementUiState {
    data object Hidden : AchievementUiState
    data class Showing(
        val achievement: Achievement,
        val queueRemaining: Int = 0
    ) : AchievementUiState
}

/**
 * Singleton VM dla globalnego modala odznak.
 *
 * Filozofia:
 * - Repozytorium odznak (StatsRepository.unlockedAchievements) jest źródłem prawdy
 * - Po finish workout / istotnym evencie wywołujemy showAchievement(achievement)
 * - VM kolejkuje (gdy wiele naraz) i pokazuje sekwencyjnie
 * - Auto-dismiss po 5s lub klik X / poza kartą
 */
@HiltViewModel
class AchievementViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val watcher: AchievementWatcher
) : ViewModel() {

    /** Sprawdza nowe odznaki i kolejkuje do pokazania. Wywoływane po finish workout. */
    fun checkForNewAchievements() {
        viewModelScope.launch {
            val newOnes = runCatching { watcher.checkForNewlyUnlocked() }.getOrNull().orEmpty()
            if (newOnes.isNotEmpty()) showMany(newOnes)
        }
    }

    private val _uiState = MutableStateFlow<AchievementUiState>(AchievementUiState.Hidden)
    val uiState: StateFlow<AchievementUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<Achievement>()
    private var isShowing = false

    /** Wywołane gdy nowa odznaka odblokowana — może być wielokrotne, dodaje do kolejki. */
    fun showAchievement(achievement: Achievement) {
        // Deduplikacja — nie dodawaj jeśli ten sam id już jest w kolejce
        if (queue.any { it.id == achievement.id }) return
        if (_uiState.value is AchievementUiState.Showing &&
            (_uiState.value as AchievementUiState.Showing).achievement.id == achievement.id) return
        queue.addLast(achievement)
        if (!isShowing) processNext()
    }

    fun showMany(achievements: List<Achievement>) {
        achievements.forEach { showAchievement(it) }
    }

    fun dismiss() {
        viewModelScope.launch {
            _uiState.value = AchievementUiState.Hidden
            delay(450)
            isShowing = false
            processNext()
        }
    }

    private fun processNext() {
        if (queue.isEmpty()) return
        val next = queue.removeFirst()
        isShowing = true
        _uiState.value = AchievementUiState.Showing(
            achievement = next,
            queueRemaining = queue.size
        )

        // Auto-dismiss po 5s
        viewModelScope.launch {
            delay(5000)
            val cur = _uiState.value
            if (cur is AchievementUiState.Showing && cur.achievement.id == next.id) {
                dismiss()
            }
        }
    }

    fun onShareClick() {
        val cur = _uiState.value as? AchievementUiState.Showing ?: return
        val a = cur.achievement
        val text = "${a.emoji} ${a.title}\n${a.description}\n\nZdobyte w GymTracker"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nowa odznaka — ${a.title}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Udostępnij odznakę").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
    }
}
