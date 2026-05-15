package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.filebit.gymtracker.ai.MealStyle
import pl.filebit.gymtracker.ai.MealStylePreferences
import pl.filebit.gymtracker.ai.PlanStyle
import pl.filebit.gymtracker.data.entity.MealType
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
        healthConnectSyncEnabled = prefs.getBoolean(KEY_HC_SYNC, false),
        customDeficit = prefs.getInt(KEY_DEFICIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
        manualKcal = prefs.getInt(KEY_KCAL_OVERRIDE, 0).takeIf { it > 0 },
        weeklyKcalOverrides = parseWeeklyOverrides(prefs.getString(KEY_WEEKLY_KCAL, null))
    )

    /** Format: "1=2750,2=2750,3=2500,4=2500,7=2750" */
    private fun parseWeeklyOverrides(s: String?): Map<Int, Int> {
        if (s.isNullOrBlank()) return emptyMap()
        return s.split(",").mapNotNull { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val day = parts[0].trim().toIntOrNull()
                val kcal = parts[1].trim().toIntOrNull()
                if (day != null && kcal != null && day in 1..7 && kcal > 0) day to kcal else null
            } else null
        }.toMap()
    }

    private fun serializeWeeklyOverrides(m: Map<Int, Int>): String =
        m.entries.joinToString(",") { "${it.key}=${it.value}" }

    fun save(config: DietConfig) {
        prefs.edit()
            .putInt(KEY_MEALS, config.mealsPerDay)
            .putInt(KEY_WINDOW_HOURS, config.eatingWindowHours)
            .putInt(KEY_WINDOW_START, config.windowStartHour)
            .putBoolean(KEY_REMINDERS, config.mealRemindersEnabled)
            .putBoolean(KEY_AUTO_CHECK, config.autoCheckAdjustments)
            .putBoolean(KEY_HC_SYNC, config.healthConnectSyncEnabled)
            .also { editor ->
                if (config.customDeficit != null) editor.putInt(KEY_DEFICIT, config.customDeficit)
                else editor.remove(KEY_DEFICIT)
                if (config.manualKcal != null) editor.putInt(KEY_KCAL_OVERRIDE, config.manualKcal)
                else editor.remove(KEY_KCAL_OVERRIDE)
                if (config.weeklyKcalOverrides.isNotEmpty()) {
                    editor.putString(KEY_WEEKLY_KCAL, serializeWeeklyOverrides(config.weeklyKcalOverrides))
                } else {
                    editor.remove(KEY_WEEKLY_KCAL)
                }
            }
            .apply()
        _state.value = config
    }

    /**
     * v1.27.1: preferencje generowania planu AI (styl, preferencje per posiłek,
     * uwagi) — żeby wybór usera ZOSTAWAŁ między otwarciami okna generowania.
     */
    fun loadMealStylePreferences(): MealStylePreferences {
        val global = runCatching {
            PlanStyle.valueOf(prefs.getString(KEY_STYLE_GLOBAL, PlanStyle.CLASSIC.name)!!)
        }.getOrDefault(PlanStyle.CLASSIC)
        val slots = (prefs.getString(KEY_STYLE_SLOTS, null) ?: "")
            .split(",")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size != 2) return@mapNotNull null
                val type = runCatching { MealType.valueOf(parts[0].trim()) }.getOrNull()
                val style = runCatching { MealStyle.valueOf(parts[1].trim()) }.getOrNull()
                if (type != null && style != null) type to style else null
            }
            .toMap()
        return MealStylePreferences(
            globalStyle = global,
            slotStyles = slots,
            freeText = prefs.getString(KEY_STYLE_FREETEXT, "") ?: ""
        )
    }

    fun saveMealStylePreferences(p: MealStylePreferences) {
        prefs.edit()
            .putString(KEY_STYLE_GLOBAL, p.globalStyle.name)
            .putString(KEY_STYLE_SLOTS,
                p.slotStyles.entries.joinToString(",") { "${it.key.name}=${it.value.name}" })
            .putString(KEY_STYLE_FREETEXT, p.freeText)
            .apply()
    }

    companion object {
        private const val KEY_MEALS = "meals_per_day"
        private const val KEY_STYLE_GLOBAL = "plan_style_global"
        private const val KEY_STYLE_SLOTS = "plan_style_slots"
        private const val KEY_STYLE_FREETEXT = "plan_style_freetext"
        private const val KEY_WINDOW_HOURS = "eating_window_hours"
        private const val KEY_WINDOW_START = "window_start_hour"
        private const val KEY_REMINDERS = "meal_reminders_enabled"
        private const val KEY_AUTO_CHECK = "auto_check_adjustments"
        private const val KEY_HC_SYNC = "health_connect_sync"
        private const val KEY_DEFICIT = "custom_deficit_kcal"
        private const val KEY_KCAL_OVERRIDE = "manual_kcal_override"
        private const val KEY_WEEKLY_KCAL = "weekly_kcal_overrides"
    }
}

data class DietConfig(
    val mealsPerDay: Int = 3,
    val eatingWindowHours: Int = 8,
    val windowStartHour: Int = 12,
    val mealRemindersEnabled: Boolean = true,
    /** Co 14 dni AI sprawdza trend wagi/adherence i sugeruje korektę kcal (notyfikacja). */
    val autoCheckAdjustments: Boolean = true,
    /** Sync kroków z Google Health Connect (zamiast manual entry). */
    val healthConnectSyncEnabled: Boolean = false,
    /** Override default deficit (-750..+500). null = użyj wartości domyślnej z celu (CUT=-500, BULK=+300). */
    val customDeficit: Int? = null,
    /** Manualne nadpisanie kcal — jeśli != null, ignoruje TDEE+deficit. */
    val manualKcal: Int? = null,
    /**
     * Cykliczne kcal per dzień tygodnia (refeed/deficyt — filozofia z xlsx Macieja).
     *
     * Klucz: java.time.DayOfWeek.value (1=PN, 2=WT, ..., 7=ND).
     * Wartość: kcal targetu dla tego dnia. null = użyj manualKcal/TDEE.
     *
     * Pusta mapa = wszystkie dni równo (klasycznie).
     * Niepełna mapa = dni bez wpisu używają manualKcal/TDEE.
     *
     * Przykład Macieja 2022 redukcja:
     *   PN/WT/ND → 2750 (refeed)
     *   ŚR/CZW    → 2500 (deficyt)
     *   PT/SOB    → null (TDEE z celu)
     */
    val weeklyKcalOverrides: Map<Int, Int> = emptyMap()
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

    /**
     * Zwraca override kcal dla konkretnej daty na bazie weeklyKcalOverrides,
     * lub null jeśli nie ma wpisu dla tego dnia tygodnia.
     *
     * Klucz: java.time.DayOfWeek.value (1=PN, 2=WT, ..., 7=ND).
     */
    fun kcalForDate(dateMs: Long): Int? {
        if (weeklyKcalOverrides.isEmpty()) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        // Calendar.MONDAY = 2, ..., SUNDAY = 1 → konwersja do DayOfWeek (1=PN..7=ND)
        val dayOfWeek = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> return null
        }
        return weeklyKcalOverrides[dayOfWeek]
    }
}
