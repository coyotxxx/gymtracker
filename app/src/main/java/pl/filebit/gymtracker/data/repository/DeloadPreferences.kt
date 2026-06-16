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

    /** Legacy: bez typu — zostawione dla wstecz-kompatybilności. Używaj `dismissedAtMs(type)`. */
    fun dismissedAtMs(): Long = prefs.getLong(KEY_DISMISSED_AT, 0L)

    fun setDismissedNow() {
        prefs.edit().putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * v1.24.0: per-type dismiss. X-owanie 'POWRÓT PO PRZERWIE' NIE blokuje
     * pokazywania 'WYKRYTO BÓL' — różne typy alertów = różne sprawy.
     */
    fun dismissedAtMs(type: AlertType): Long = prefs.getLong(keyForType(type), 0L)

    fun setDismissedNow(type: AlertType) {
        prefs.edit().putLong(keyForType(type), System.currentTimeMillis()).apply()
    }

    /** Hash ostatniej notyfikacji per typ — anti-spam dla HomeAlertNotifier. */
    fun lastNotifiedHash(type: AlertType): String? = prefs.getString(keyHashForType(type), null)

    fun setLastNotifiedHash(type: AlertType, hash: String) {
        prefs.edit().putString(keyHashForType(type), hash).apply()
    }

    private fun keyForType(type: AlertType): String = "dismissed_at_${type.name}"
    private fun keyHashForType(type: AlertType): String = "last_notified_hash_${type.name}"

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

    // === v2.59.0 (U9+U10): PAMIĘĆ PRZYCZYNY przerwy w treningach ===
    // Prawdziwy trener pyta „co się stało?", zapamiętuje powód i wraca do tematu po umówionym
    // czasie — zamiast w kółko „idź ćwiczyć". Trzymamy to TU (istniejący właściciel stanu alertów
    // treningowych, ma już AlertType.MISSED_WORKOUT i wzorzec serializacji ActiveDeloadState).

    /** Bieżąca przyczyna — null jeśli brak LUB minął termin powrotu (czas znów zapytać). */
    fun trainingPause(): TrainingPauseState? {
        val raw = prefs.getString(KEY_PAUSE, null) ?: return null
        val state = runCatching { json.decodeFromString<TrainingPauseState>(raw) }.getOrNull() ?: return null
        return if (state.resumeAtMs <= System.currentTimeMillis()) null else state
    }

    fun setTrainingPause(reason: TrainingPauseReason, note: String = "", resumeInDays: Int = 7) {
        val now = System.currentTimeMillis()
        val state = TrainingPauseState(
            reason = reason.name, note = note,
            recordedAtMs = now, resumeAtMs = now + resumeInDays.toLong() * 24L * 3600 * 1000
        )
        prefs.edit().putString(KEY_PAUSE, json.encodeToString(TrainingPauseState.serializer(), state)).apply()
    }

    /** Czy kiedykolwiek zapisano powód (choćby wygasły) — wariant pytania „wracasz?". */
    fun hadTrainingPause(): Boolean = prefs.contains(KEY_PAUSE)

    fun clearTrainingPause() {
        prefs.edit().remove(KEY_PAUSE).apply()
    }

    companion object {
        private const val KEY_DISMISSED_AT = "deload_dismissed_at"
        private const val KEY_ACTIVE = "deload_active_state"
        private const val KEY_PAUSE = "training_pause_state"
    }
}

/** Powód przerwy w treningach (U9+U10). label = etykieta przycisku na karcie pytania. */
enum class TrainingPauseReason(val label: String) {
    NO_TIME("Brak czasu"),
    NO_ACCESS("Brak sprzętu/miejsca"),
    INJURY("Kontuzja"),
    OTHER("Inny powód");

    /** Domyślny czas wyciszenia nagabywania (dni) zależny od powodu. */
    fun defaultResumeDays(): Int = if (this == INJURY) 14 else 7
}

@Serializable
data class TrainingPauseState(
    val reason: String,
    val note: String,
    val recordedAtMs: Long,
    val resumeAtMs: Long
) {
    fun reasonEnum(): TrainingPauseReason =
        runCatching { TrainingPauseReason.valueOf(reason) }.getOrDefault(TrainingPauseReason.OTHER)
    fun resumeInDays(nowMs: Long): Int =
        (((resumeAtMs - nowMs) / (24L * 3600 * 1000)).toInt()).coerceAtLeast(0)
}

/**
 * Typy alertów Home (DeloadCardState.*). Każdy ma własny dismiss timestamp
 * + last-notified hash. Zamknięcie jednego nie blokuje pokazania innego.
 */
enum class AlertType {
    DELOAD_SUGGESTION,
    RETURN_AFTER_BREAK,
    ACTIVE_INJURY,
    MISSED_WORKOUT   // v2.12.0 — opuszczony zaplanowany trening (append: stabilne ordinale)
}

@Serializable
data class ActiveDeloadState(
    val startedAtMs: Long,
    val planId: Long,
    val planName: String,
    val factor: Double,                    // np. 0.9 (-10%) albo 0.8 (-20%)
    val originalWeights: Map<Long, Double> // setId → originalWeightKg
)
