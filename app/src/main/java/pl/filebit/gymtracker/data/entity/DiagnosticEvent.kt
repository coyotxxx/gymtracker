package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Strumień zdarzeń diagnostycznych — co apka faktycznie robi i decyduje.
 *
 * Komplementarny do `AiLog` (pełne transkrypty AI): DiagnosticEvent to lekki,
 * strukturalny log decyzji/zdarzeń/błędów. Pozwala na realnej bazie zobaczyć:
 * co zadziałało, co milczało (ciche runCatching), gdzie AI nie dowiózł,
 * co user odrzucił (sygnał błędnej propozycji).
 *
 * Offline-first: wyłącznie lokalnie (Room). Wychodzi z telefonu TYLKO przez
 * Debug-export na żądanie usera. Zero telemetrii na serwer.
 *
 * Kategorie/poziomy trzymane jako String (.name enuma) — bez TypeConvertera.
 */
@Entity(
    tableName = "diagnostic_events",
    indices = [Index("timestampMs"), Index("category")]
)
data class DiagnosticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    /** Kategoria — DiagnosticCategory.name (AI/DETECTOR/WORKER/DIET/NOTIFICATION/REPORT/ADHERENCE/USER_ACTION/ERROR). */
    val category: String,
    /** Poziom — DiagnosticLevel.name (INFO/WARN/ERROR). */
    val level: String,
    /** Klasa/feature źródłowy, np. "WeeklyReportWorker", "DeloadService". */
    val source: String,
    /** Krótki kod zdarzenia, np. "report_skipped", "deload_fired", "ai_parse_fail". */
    val event: String,
    /** Czytelny opis dla człowieka. */
    val message: String,
    /** Opcjonalny strukturalny payload (JSON). */
    val dataJson: String? = null,
    /** Pass/fail dla zdarzeń, które to mają (null = nie dotyczy). */
    val success: Boolean? = null
)

enum class DiagnosticCategory {
    AI, DETECTOR, WORKER, DIET, NOTIFICATION, REPORT, ADHERENCE, USER_ACTION, ERROR
}

enum class DiagnosticLevel { INFO, WARN, ERROR }
