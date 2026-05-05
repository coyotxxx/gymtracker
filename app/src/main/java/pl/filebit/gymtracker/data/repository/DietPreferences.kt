package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konfiguracja dnia żywieniowego: ile posiłków, długość okna, start okna,
 * powiadomienia. SharedPrefs (nie DB) — to ustawienia, nie dane.
 *
 * Domyślnie 3 posiłki w oknie 8h startującym o 12:00 (czyli 12:00-20:00),
 * powiadomienia włączone — klasyczny IF 16/8.
 */
@Singleton
class DietPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("diet_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<DietConfig> = _state.asStateFlow()

    fun load(): DietConfig = DietConfig(
        mealsPerDay = prefs.getInt(KEY_MEALS, 3).coerceIn(2, 6),
        eatingWindowHours = prefs.getInt(KEY_WINDOW_HOURS, 8).coerceIn(4, 24),
        windowStartHour = prefs.getInt(KEY_WINDOW_START, 12).coerceIn(0, 23),
        mealRemindersEnabled = prefs.getBoolean(KEY_REMINDERS, true),
        autoCheckAdjustments = prefs.getBoolean(KEY_AUTO_CHECK, true),
        customDeficit = prefs.getInt(KEY_DEFICIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
        manualKcal = prefs.getInt(KEY_KCAL_OVERRIDE, 0).takeIf { it > 0 }
    )

    fun save(config: DietConfig) {
        prefs.edit()
            .putInt(KEY_MEALS, config.mealsPerDay)
            .putInt(KEY_WINDOW_HOURS, config.eatingWindowHours)
            .putInt(KEY_WINDOW_START, config.windowStartHour)
            .putBoolean(KEY_REMINDERS, config.mealRemindersEnabled)
            .putBoolean(KEY_AUTO_CHECK, config.autoCheckAdjustments)
            .also { editor ->
                if (config.customDeficit != null) editor.putInt(KEY_DEFICIT, config.customDeficit)
                else editor.remove(KEY_DEFICIT)
                if (config.manualKcal != null) editor.putInt(KEY_KCAL_OVERRIDE, config.manualKcal)
                else editor.remove(KEY_KCAL_OVERRIDE)
            }
            .apply()
        _state.value = config
    }

    companion object {
        private const val KEY_MEALS = "meals_per_day"
        private const val KEY_WINDOW_HOURS = "eating_window_hours"
        private const val KEY_WINDOW_START = "window_start_hour"
        private const val KEY_REMINDERS = "meal_reminders_enabled"
        private const val KEY_AUTO_CHECK = "auto_check_adjustments"
        private const val KEY_DEFICIT = "custom_deficit_kcal"
        private const val KEY_KCAL_OVERRIDE = "manual_kcal_override"
    }
}

data class DietConfig(
    val mealsPerDay: Int = 3,
    val eatingWindowHours: Int = 8,
    val windowStartHour: Int = 12,
    val mealRemindersEnabled: Boolean = true,
    /** Co 14 dni AI sprawdza trend wagi/adherence i sugeruje korektę kcal (notyfikacja). */
    val autoCheckAdjustments: Boolean = true,
    /** Override default deficit (-750..+500). null = użyj wartości domyślnej z celu (CUT=-500, BULK=+300). */
    val customDeficit: Int? = null,
    /** Manualne nadpisanie kcal — jeśli != null, ignoruje TDEE+deficit. */
    val manualKcal: Int? = null
) {
    /**
     * Godziny posiłków rozłożone równo w oknie żywieniowym.
     * Pierwsze: windowStart. Ostatnie: windowEnd. Pozostałe: równo między nimi.
     *
     * 3 posiłki, okno 12-20: [12, 16, 20]
     * 4 posiłki, okno 12-20: [12, 14:40, 17:20, 20] → [12.0, 14.67, 17.33, 20]
     * 2 posiłki, okno 12-20: [12, 20]
     */
    fun mealHoursDecimal(): List<Double> {
        if (mealsPerDay < 2) return listOf(windowStartHour.toDouble())
        val end = windowStartHour + eatingWindowHours
        val step = eatingWindowHours.toDouble() / (mealsPerDay - 1)
        return (0 until mealsPerDay).map { i ->
            (windowStartHour + i * step).coerceIn(0.0, 23.99)
        }
    }

    fun windowEndHour(): Int = (windowStartHour + eatingWindowHours).coerceIn(0, 24)

    fun formatTime(hourDecimal: Double): String {
        val h = hourDecimal.toInt()
        val m = ((hourDecimal - h) * 60).toInt()
        return "%02d:%02d".format(h, m)
    }
}
