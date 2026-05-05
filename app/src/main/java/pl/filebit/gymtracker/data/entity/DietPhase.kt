package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DietPhaseType {
    CUT,            // redukcja (deficyt)
    MAINTENANCE,    // utrzymanie wagi (przerwa od cutu/bulku, kcal=TDEE)
    BULK,           // budowa masy (nadwyżka)
    REFEED_DAY,     // 1 dzień +200-400 kcal (głównie węgle), regeneracja leptyny
    DIET_BREAK      // 7-14 dni maintenance po długim cucie (>8-12 tyg), reset hormonalny
}

/**
 * Faza diety — etap długofalowej strategii.
 *
 * Filozofia: po 8-12 tygodniach cut'u potrzebny diet break (kcal=TDEE).
 * Po długiej redukcji adherence spada, głód rośnie, RPE rośnie — silnik
 * proponuje diet break ZAMIAST cięcia kcal.
 *
 * Faza może być:
 *  - aktywna: endDateMs == null (lub > now)
 *  - zakończona: endDateMs <= now
 *  - utworzona przez silnik (createdBySystem=true) lub usera (false)
 */
@Entity(
    tableName = "diet_phases",
    indices = [Index("startDateMs"), Index("endDateMs"), Index("createdAt")]
)
data class DietPhase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DietPhaseType,
    val startDateMs: Long,
    /** null = trwa nadal lub planowana. */
    val endDateMs: Long? = null,
    /** Ile kcal odchylenie od bazowego planu (np. CUT -500, REFEED +300). */
    val kcalAdjustment: Int = 0,
    /** Tempo zmiany wagi w kg/tydz (CUT: -0.3..-1.0, BULK: +0.2..+0.5, MAINT: 0, REFEED: 0). */
    val expectedRateKgPerWeek: Double = 0.0,
    val reason: String = "",
    /** True = silnik zaproponował, False = user manualnie. */
    val createdBySystem: Boolean = false,
    /** True = user zaakceptował (lub start automatu). */
    val accepted: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isActiveAt(timestampMs: Long): Boolean =
        timestampMs >= startDateMs && (endDateMs == null || timestampMs < endDateMs)

    fun durationDays(): Int {
        val end = endDateMs ?: System.currentTimeMillis()
        return ((end - startDateMs) / (24L * 3600 * 1000)).toInt()
    }
}
