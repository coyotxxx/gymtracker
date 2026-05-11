package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.19.0 — preferencje periodyzacji (długość cyklu, długość deloadu, auto-deload).
 *
 * Nie używamy Room (brak migracji DB w v1.19.0). SharedPreferences wystarczą — ustawienia
 * są lokalne, niewielkie, czytane głównie z TrainingSettings i PeriodizationOrchestrator.
 */
@Singleton
class PeriodizationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("periodization_prefs", Context.MODE_PRIVATE)

    /** Preferowana długość mesocyklu w tygodniach (4-12, default 6). */
    fun mesocycleWeeks(): Int = prefs.getInt(KEY_CYCLE_WEEKS, DEFAULT_CYCLE_WEEKS)
        .coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS)

    fun setMesocycleWeeks(value: Int) {
        prefs.edit().putInt(KEY_CYCLE_WEEKS, value.coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS)).apply()
    }

    /** Preferowana długość deloadu w dniach (4-14, default 7). */
    fun deloadDays(): Int = prefs.getInt(KEY_DELOAD_DAYS, DEFAULT_DELOAD_DAYS)
        .coerceIn(MIN_DELOAD_DAYS, MAX_DELOAD_DAYS)

    fun setDeloadDays(value: Int) {
        prefs.edit().putInt(KEY_DELOAD_DAYS, value.coerceIn(MIN_DELOAD_DAYS, MAX_DELOAD_DAYS)).apply()
    }

    /** Czy algorytm ma proponować deload przy stagnacji (default true). */
    fun autoDeloadEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_DELOAD, DEFAULT_AUTO_DELOAD)

    fun setAutoDeloadEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DELOAD, value).apply()
    }

    companion object {
        const val MIN_CYCLE_WEEKS = 4
        const val MAX_CYCLE_WEEKS = 12
        const val DEFAULT_CYCLE_WEEKS = 6
        const val MIN_DELOAD_DAYS = 4
        const val MAX_DELOAD_DAYS = 14
        const val DEFAULT_DELOAD_DAYS = 7
        const val DEFAULT_AUTO_DELOAD = true

        private const val KEY_CYCLE_WEEKS = "mesocycle_weeks"
        private const val KEY_DELOAD_DAYS = "deload_days"
        private const val KEY_AUTO_DELOAD = "auto_deload_enabled"
    }
}
