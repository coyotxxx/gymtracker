package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wygenerowany przez AI raport tygodniowy.
 * Trzymamy historię — user może wracać do raportów z poprzednich tygodni
 * i porównywać wnioski.
 */
@Entity(tableName = "ai_weekly_reports")
data class AiWeeklyReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartMillis: Long,        // Pon 00:00 lokalnej strefy
    val weekEndMillis: Long,          // Pon 00:00 następnego tygodnia (exclusive)
    val generatedAtMillis: Long,
    val content: String,              // markdown z odpowiedzi AI
    val aiProvider: String,           // "ANTHROPIC" lub "OPENAI"
    val aiModel: String
)
