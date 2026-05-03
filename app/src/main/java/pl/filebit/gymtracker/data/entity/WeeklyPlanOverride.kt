package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-tygodniowy override harmonogramu — pozwala przesunąć trening z planu na inny dzień
 * lub pominąć go bez modyfikacji samego planu.
 *
 * Zakres: jeden tydzień (od poniedziałku 00:00 lokalnej strefy).
 *
 * Semantyka:
 * - `targetDayOfWeek = SKIPPED (-1)` → pomiń ten trening w tym tygodniu
 * - `targetDayOfWeek != originalDayOfWeek` → wykonaj w innym dniu (przeniesienie)
 * - `targetDayOfWeek == originalDayOfWeek` → no-op (nie tworzymy takiego rekordu)
 */
@Entity(tableName = "weekly_plan_overrides")
data class WeeklyPlanOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartMillis: Long,         // Pon 00:00 lokalnej strefy
    val planId: Long,
    val originalDayOfWeek: Int,        // 1=Pn..7=Nd — gdzie plan oryginalnie ma trening
    val targetDayOfWeek: Int,          // 1=Pn..7=Nd lub -1 jeśli pominięty
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SKIPPED: Int = -1
    }
}
