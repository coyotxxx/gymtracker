package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage stanu deload: kiedy user dismissed alert, kiedy aktywny deload trwa,
 * snapshot oryginalnych wag (do restore po tygodniu).
 *
 * Nie używamy DB — to ulotny stan sesyjny. SharedPreferences wystarczą.
 */
@Singleton
class DeloadPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("deload_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun dismissedAtMs(): Long = prefs.getLong(KEY_DISMISSED_AT, 0L)

    fun setDismissedNow() {
        prefs.edit().putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
    }

    fun activeDeload(): ActiveDeloadState? {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
        return runCatching { json.decodeFromString<ActiveDeloadState>(raw) }.getOrNull()
    }

    fun setActiveDeload(state: ActiveDeloadState) {
        prefs.edit().putString(KEY_ACTIVE, json.encodeToString(ActiveDeloadState.serializer(), state)).apply()
    }

    fun clearActiveDeload() {
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    companion object {
        private const val KEY_DISMISSED_AT = "deload_dismissed_at"
        private const val KEY_ACTIVE = "deload_active_state"
    }
}

@Serializable
data class ActiveDeloadState(
    val startedAtMs: Long,
    val planId: Long,
    val planName: String,
    val factor: Double,                    // np. 0.9 (-10%) albo 0.8 (-20%)
    val originalWeights: Map<Long, Double> // setId → originalWeightKg
)
