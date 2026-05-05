package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Log automatycznych korekt planu dietetycznego.
 *
 * Każda decyzja CalorieAdjustmentEngine (zatwierdzona przez usera lub auto)
 * zapisuje się tutaj z pełnym snapshotem inputów — audytowalny historia.
 *
 * Reguła z spec usera: 'Każda automatyczna zmiana musi mieć wyjaśnienie dla
 * użytkownika.' To pole 'explanation' (z silnika) + 'aiExplanation' (opcjonalnie
 * z AI dla uczynienia po ludzku).
 */
@Entity(tableName = "diet_adjustments")
data class DietAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,                       // kiedy zarejestrowano

    // === ZMIANA ===
    val oldKcal: Int,
    val newKcal: Int,
    /** Action z AdjustmentAction enum (HOLD/DECREASE_KCAL/INCREASE_KCAL/...). */
    val actionCode: String,
    /** kod reason z silnika (np. 'cut_stagnation_high_adherence'). */
    val reason: String,
    /** Wyjaśnienie z silnika regułowego (deterministic, bez LLM). */
    val engineExplanation: String,
    /** Opcjonalne rozszerzenie od AI (po ludzku). null = nie wygenerowano. */
    val aiExplanation: String? = null,
    /** Pewność decyzji (LOW/MEDIUM/HIGH). */
    val confidence: String,

    // === SNAPSHOT INPUTÓW (dla audytu) ===
    val snapshotAvgWeight7d: Double? = null,
    val snapshotAvgWeight14d: Double? = null,
    val snapshotSlopeKgPerWeek: Double? = null,
    val snapshotAdherence14dKcal: Int = 0,
    val snapshotAdherence14dProtein: Int = 0,
    val snapshotWorkoutsDone: Int = 0,
    val snapshotWorkoutsPlanned: Int = 0,

    // === STATUS ===
    /** True gdy user zatwierdził i changes weszły w życie. */
    val applied: Boolean = false,
    val appliedAt: Long? = null,
    /** True gdy ta korekta była zaproponowana ale user kliknął anuluj. */
    val dismissed: Boolean = false
)
