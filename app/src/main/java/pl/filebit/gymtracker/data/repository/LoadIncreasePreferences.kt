package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage stanu zwiększenia obciążenia (DETRAINING → +10% setów).
 * Analog DeloadPreferences — snapshot do restore.
 */
@Singleton
class LoadIncreasePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("load_increase_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun dismissedAtMs(): Long = prefs.getLong(KEY_DISMISSED_AT, 0L)

    fun setDismissedNow() {
        prefs.edit().putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
    }

    fun activeIncrease(): ActiveLoadIncreaseState? {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
        return runCatching { json.decodeFromString<ActiveLoadIncreaseState>(raw) }.getOrNull()
    }

    fun setActiveIncrease(state: ActiveLoadIncreaseState) {
        prefs.edit().putString(KEY_ACTIVE, json.encodeToString(ActiveLoadIncreaseState.serializer(), state)).apply()
    }

    fun clearActiveIncrease() {
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    companion object {
        private const val KEY_DISMISSED_AT = "load_increase_dismissed_at"
        private const val KEY_ACTIVE = "load_increase_active_state"
    }
}

@Serializable
data class ActiveLoadIncreaseState(
    val startedAtMs: Long,
    val planId: Long,
    val planName: String,
    val factor: Double,                    // np. 1.05 (+5%)
    val originalWeights: Map<Long, Double> // setId → originalWeightKg (prawdziwy snapshot)
)
