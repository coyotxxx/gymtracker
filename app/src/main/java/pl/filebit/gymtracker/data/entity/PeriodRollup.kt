package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v1.11.66 — Period rollups: prekomputowane podsumowania okresów.
 *
 * Filozofia z research (TrainingPeaks / Whoop / RP): historię kompresuje się
 * w postaci agregatów (tygodniowy → miesięczny → kwartalny). Stare surowe dane
 * zostają w bazie (do tool calling v1.11.67), ale AI dostaje tylko rollupy:
 *  - last 4 weeks (szczegół tygodnia)
 *  - last 6 months (szczegół miesiąca)
 *  - last 4 quarters (szczegół kwartału)
 *
 * Total ~2-3 KB w kontekście AI = pokrycie 1+ roku historii.
 *
 * KAŻDY ROLLUP COMPUTOWANY RAZ — przy zamknięciu okresu (close-of-period).
 * Nigdy więcej nie re-summary (drift propaguje przy re-computation).
 */

@Entity(
    tableName = "weekly_rollups",
    indices = [Index(value = ["weekStartMs"], unique = true)]
)
data class WeeklyRollup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Poniedziałek 00:00 epoch ms — klucz tygodnia. */
    val weekStartMs: Long,

    val totalVolumeKg: Double,
    val sessionsCount: Int,
    val totalSets: Int,
    val avgRpe: Double,
    val avgWellbeing: Double?,

    /**
     * Najlepsze sety per główny lift w tym tygodniu (JSON):
     * {"Wyciskanie sztangi leżąc": "75 kg × 6 (e1RM 90)", ...}
     */
    val mainLiftsBestJson: String,

    /** % wolumenu per partia mięśniowa w tym tygodniu (JSON). */
    val muscleVolumePctsJson: String,

    /** Auto-notatki: "deload week", "PR week" itp. */
    val autoNotes: String = "",

    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "monthly_rollups",
    indices = [Index(value = ["monthStartMs"], unique = true)]
)
data class MonthlyRollup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Pierwszy dzień miesiąca 00:00 epoch ms. */
    val monthStartMs: Long,

    val totalVolumeKg: Double,
    val sessionsCount: Int,
    val totalSets: Int,
    val avgRpe: Double,

    /** e1RM końca miesiąca per główny lift (JSON). */
    val mainLiftsE1rmEndJson: String,

    /** Waga ciała na koniec miesiąca (jeśli pomiar dostępny). */
    val bodyWeightEndKg: Double? = null,

    /** Zmiana wagi w miesiącu (kg). */
    val bodyWeightDeltaKg: Double? = null,

    /** Liczba PR-ów ustanowionych w tym miesiącu. */
    val prCount: Int,

    /** Liczba zmian planu (PLAN_START/END events). */
    val planChanges: Int,

    /** Liczba deloadów wykrytych. */
    val deloadCount: Int,

    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quarterly_rollups",
    indices = [Index(value = ["quarterStartMs"], unique = true)]
)
data class QuarterlyRollup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Pierwszy dzień kwartału 00:00 epoch ms (1 stycznia / 1 kwietnia / 1 lipca / 1 października). */
    val quarterStartMs: Long,

    val totalVolumeKg: Double,
    val sessionsCount: Int,

    /** e1RM początek kwartału per główny lift (JSON). */
    val mainLiftsE1rmStartJson: String,

    /** e1RM koniec kwartału per główny lift (JSON). */
    val mainLiftsE1rmEndJson: String,

    /** Zmiana wagi w kwartale (kg). */
    val bodyWeightDeltaKg: Double? = null,

    val prCount: Int,
    val planChanges: Int,
    val deloadCount: Int,

    /** Highlights JSON: ["Bench +5kg", "Redukcja -3kg", ...] */
    val highlightsJson: String = "[]",

    val createdAt: Long = System.currentTimeMillis()
)
