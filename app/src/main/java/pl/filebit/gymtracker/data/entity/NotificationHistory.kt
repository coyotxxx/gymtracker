package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v2.47.0 — DEDYKOWANA historia powiadomień (dzwonek).
 *
 * Zapisywana w chwili wysłania push przez notifiery (jeden czysty wpis: ikona+tytuł+
 * opis+akcja). Zastępuje reużycie `diagnostic_events` (log debugowy leakował surowe
 * kody/JSON i stary backlog). Twarda granica: tu trafia TYLKO to, co user dostał.
 *
 * `kind` steruje ikoną i akcjami w UI. `payload` = dane akcji (np. typ posiłku dla
 * przycisków Zjedzone/Pominięte). Karta coacha = „co teraz"; ta tabela = „co wysłano".
 */
@Entity(
    tableName = "notification_history",
    indices = [Index("timestampMs")]
)
data class NotificationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    /** NotificationKind.name — steruje ikoną + dostępnymi akcjami. */
    val kind: String,
    /** Czysty tytuł (bez emoji prefiksu — emoji daje ikona z `kind`). */
    val title: String,
    /** Czytelny opis (jednowierszowy). */
    val body: String,
    /** Opcjonalne dane akcji, np. typ posiłku (MealType.name) dla MEAL. */
    val payload: String? = null
)

enum class NotificationKind { MEAL, REVIEW, COACH, ALERT, RECOVERY, GENERIC }
