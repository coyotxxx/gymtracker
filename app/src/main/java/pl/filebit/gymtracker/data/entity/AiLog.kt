package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Log każdego wywołania AI — pełen prompt + odpowiedź dla diagnostyki.
 *
 * Zapisywane TYLKO gdy AiPreferences.loggingEnabled = true.
 * User może w UI: oglądać listę, podejrzeć szczegóły, wyczyścić.
 *
 * Dane mogą być DUŻE — dlatego TEXT bez limitu, ale też prompt skrócony do
 * preview (pierwsze 500 znaków) oraz response do preview (500 znaków).
 * Jeśli user chce pełny — `fullPrompt` i `fullResponse` zawierają wszystko.
 */
@Entity(
    tableName = "ai_logs",
    indices = [Index("createdAt"), Index("service")]
)
data class AiLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Nazwa service: 'DietAi', 'WorkoutPlanAi', 'WeeklyReport', 'EmergencyMeal' itd. */
    val service: String,
    /** Provider: 'ANTHROPIC' / 'OPENAI'. */
    val provider: String,
    /** Model: 'claude-sonnet-4', 'gpt-4o' itd. */
    val model: String,
    /** Pełen prompt wysłany do AI. */
    val fullPrompt: String,
    /** Surowa odpowiedź AI (lub błąd). */
    val fullResponse: String,
    /** Czy wywołanie zakończyło się sukcesem. */
    val success: Boolean,
    /** Komunikat błędu jeśli !success. */
    val errorMessage: String? = null,
    /** Czas wykonania w milisekundach. */
    val durationMs: Long = 0,
    /** Tokens (jeśli dostępne z API response). */
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
) {
    /** Preview prompt — pierwsze 500 znaków. */
    val promptPreview: String get() = fullPrompt.take(500) + (if (fullPrompt.length > 500) "…" else "")
    /** Preview response — pierwsze 500 znaków. */
    val responsePreview: String get() = fullResponse.take(500) + (if (fullResponse.length > 500) "…" else "")
}
