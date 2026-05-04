package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sesja okna żywieniowego (intermittent fasting 16/8 lub konfigurowalne).
 * Tworzona gdy user kliknie "Otwórz okno żywieniowe" — start jedzenia.
 * Zamknięta gdy user kliknie "Zamknij okno" lub upłynie eatingDurationHours.
 */
@Entity(tableName = "fasting_windows")
data class FastingWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start okna jedzenia (kliknięcie "Otwórz"). */
    val eatingStartMs: Long,
    /** Koniec okna jedzenia. Null = jeszcze otwarte (aktywna sesja). */
    val eatingEndMs: Long? = null,
    /** Planowany czas okna jedzenia (h). Default 8 dla 16/8. */
    val plannedEatingHours: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
)
